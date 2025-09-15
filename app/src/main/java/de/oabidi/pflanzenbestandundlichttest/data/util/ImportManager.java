package de.oabidi.pflanzenbestandundlichttest.data.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.text.NumberFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import java.nio.charset.StandardCharsets;

import de.oabidi.pflanzenbestandundlichttest.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.Reminder;
import de.oabidi.pflanzenbestandundlichttest.ReminderScheduler;
import de.oabidi.pflanzenbestandundlichttest.BulkReadDao;

/**
 * Manager responsible for importing measurements and diary entries from a CSV file.
 */
public class ImportManager {
    private static final String TAG = "ImportManager";
    private static final int SUPPORTED_VERSION = 1;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    /** Callback used to signal completion of the import operation. */
    public interface Callback {
        void onComplete(boolean success, @Nullable ImportError error,
                        List<ImportWarning> warnings, @NonNull String message);
    }

    /** Callback used to report incremental progress. */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }

    /** Information about a skipped row during import. */
    public static class ImportWarning {
        public final String category;
        public final int lineNumber;
        public final String reason;

        public ImportWarning(@NonNull String category, int lineNumber, @NonNull String reason) {
            this.category = category;
            this.lineNumber = lineNumber;
            this.reason = reason;
        }
    }

    /** Builds a human-readable summary of warnings for display. */
    @NonNull
    public static String summarizeWarnings(@NonNull List<ImportWarning> warnings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ImportWarning w : warnings) {
            String key = w.category + "|" + w.reason;
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(e.getValue()).append(' ').append(parts[0])
                .append(" skipped: ").append(parts[1]);
        }
        return sb.toString();
    }

    /** Import mode determining how incoming data is applied. */
    public enum Mode { MERGE, REPLACE }

    /** Possible high-level errors during import. */
    public enum ImportError {
        MISSING_VERSION,
        INVALID_VERSION,
        UNSUPPORTED_VERSION,
        NO_DATA,
        IO_ERROR
    }

    /** Sections within an exported CSV file. */
    enum Section {
        NONE,
        PLANTS,
        SPECIES_TARGETS,
        MEASUREMENTS,
        DIARY_ENTRIES,
        REMINDERS
    }

    public ImportManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Imports measurements and diary entries from the given URI.
     * The URI is expected to point to a ZIP file produced by
     * {@link de.oabidi.pflanzenbestandundlichttest.ExportManager}.
     *
     * @param uri      ZIP source chosen by the user
     * @param mode     whether to merge or replace existing data
     * @param callback invoked on the main thread with the result
     */
    public void importData(@NonNull Uri uri, @NonNull Mode mode, @NonNull Callback callback) {
        importData(uri, mode, callback, null);
    }

    public void importData(@NonNull Uri uri, @NonNull Mode mode, @NonNull Callback callback,
                           @Nullable ProgressCallback progressCallback) {
        ioExecutor.execute(() -> {
            boolean success = false;
            ImportError error = null;
            List<ImportWarning> warnings = new ArrayList<>();
            File tempDir = new File(context.getCacheDir(), "import_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                tempDir = null;
            }
            if (tempDir != null) {
                int zipEntries = 0;
                try (InputStream countIs = context.getContentResolver().openInputStream(uri);
                     ZipInputStream countZis = new ZipInputStream(countIs)) {
                    while (countZis.getNextEntry() != null) {
                        zipEntries++;
                        countZis.closeEntry();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to count import entries", e);
                }

                final int totalSteps = zipEntries + 5; // files + sections
                final AtomicInteger progress = new AtomicInteger(0);

                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    ZipEntry zipEntry;
                    File csvFile = null;
                    byte[] buffer = new byte[8192];
                    while ((zipEntry = zis.getNextEntry()) != null) {
                        File outFile = new File(tempDir, zipEntry.getName());
                        try (OutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        int cur = progress.incrementAndGet();
                        if (progressCallback != null) {
                            final int progressValue = cur;
                            mainHandler.post(() -> progressCallback.onProgress(progressValue, totalSteps));
                        }
                        if (zipEntry.getName().endsWith(".csv")) {
                            csvFile = outFile;
                        }
                        zis.closeEntry();
                    }
                    if (csvFile != null) {
                        PlantDatabase db = PlantDatabase.getDatabase(context);
                        final boolean[] successHolder = {false};
                        final ImportError[] errorHolder = {null};
                        try {
                            db.runInTransaction(() -> {
                                if (mode == Mode.REPLACE) {
                                    BulkReadDao bulk = db.bulkDao();
                                    try {
                                        for (Plant plant : bulk.getAllPlants()) {
                                            PhotoManager.deletePhoto(context, plant.getPhotoUri());
                                        }
                                        for (DiaryEntry diaryEntry : bulk.getAllDiaryEntries()) {
                                            PhotoManager.deletePhoto(context, diaryEntry.getPhotoUri());
                                        }
                                        for (Reminder reminder : bulk.getAllReminders()) {
                                            ReminderScheduler.cancelReminder(context, reminder.getId());
                                        }
                                        db.clearAllTables();
                                    } catch (Exception e) {
                                        Log.e(TAG, "Failed to clean database", e);
                                        errorHolder[0] = ImportError.IO_ERROR;
                                        throw new RuntimeException(e);
                                    }
                                }
                                if (errorHolder[0] == null) {
                                    try (BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
                                        ImportError parseResult = parseAndInsert(
                                            reader, tempDir, mode, warnings,
                                            progressCallback, progress, totalSteps);
                                        if (parseResult != null) {
                                            errorHolder[0] = parseResult;
                                            throw new RuntimeException();
                                        }
                                        successHolder[0] = true;
                                    } catch (IOException e) {
                                        Log.e(TAG, "Failed to parse import file", e);
                                        errorHolder[0] = ImportError.IO_ERROR;
                                        throw new RuntimeException(e);
                                    }
                                }
                            });
                        } catch (RuntimeException e) {
                            // Transaction failed; errorHolder already set
                        }
                        error = errorHolder[0];
                        success = successHolder[0];
                        if (error != null) {
                            success = false;
                        }
                    } else {
                        error = ImportError.IO_ERROR;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to open import file", e);
                    success = false;
                    error = ImportError.IO_ERROR;
                } finally {
                    deleteRecursive(tempDir);
                }
            } else {
                error = ImportError.IO_ERROR;
            }
            final boolean result = success;
            final ImportError finalError = error;
            final List<ImportWarning> warningList = new ArrayList<>(warnings);
            final String message;
            if (result) {
                message = context.getString(R.string.import_success);
            } else if (finalError != null) {
                switch (finalError) {
                    case MISSING_VERSION:
                        message = context.getString(R.string.import_error_missing_version);
                        break;
                    case INVALID_VERSION:
                        message = context.getString(R.string.import_error_invalid_version);
                        break;
                    case UNSUPPORTED_VERSION:
                        message = context.getString(R.string.import_error_unsupported_version);
                        break;
                    default:
                        message = context.getString(R.string.import_failure);
                        break;
                }
            } else {
                message = context.getString(R.string.import_failure);
            }
            mainHandler.post(() -> callback.onComplete(result, finalError, warningList, message));
        });
    }

    @VisibleForTesting
    @Nullable ImportError parseAndInsert(BufferedReader reader, File baseDir, Mode mode,
                                         List<ImportWarning> warnings,
                                   @Nullable ProgressCallback progressCallback,
                                   AtomicInteger progress,
                                   int totalSteps) throws IOException {
        ImportError versionError = validateVersion(reader);
        if (versionError != null) {
            return versionError;
        }

        PlantDatabase db = PlantDatabase.getDatabase(context);
        Map<Long, Long> plantIdMap = new HashMap<>();
        final boolean[] importedAny = {false};
        final NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        List<Uri> restoredUris = new ArrayList<>();
        final AtomicInteger lineNumber = new AtomicInteger(1);
        try {
            db.runInTransaction(() -> {
                try {
                    if (parsePlants(reader, mode, baseDir, plantIdMap, warnings,
                        lineNumber, restoredUris, db)) {
                        importedAny[0] = true;
                    }
                    int cur = progress.incrementAndGet();
                    if (progressCallback != null) {
                        final int progressValue = cur;
                        mainHandler.post(() -> progressCallback.onProgress(progressValue, totalSteps));
                    }

                    if (parseSpeciesTargets(reader, db, warnings, lineNumber, nf)) {
                        importedAny[0] = true;
                    }
                    cur = progress.incrementAndGet();
                    if (progressCallback != null) {
                        final int progressValue = cur;
                        mainHandler.post(() -> progressCallback.onProgress(progressValue, totalSteps));
                    }

                    if (parseMeasurements(reader, mode, plantIdMap, warnings,
                        lineNumber, nf, db)) {
                        importedAny[0] = true;
                    }
                    cur = progress.incrementAndGet();
                    if (progressCallback != null) {
                        final int progressValue = cur;
                        mainHandler.post(() -> progressCallback.onProgress(progressValue, totalSteps));
                    }

                    if (parseDiaryEntries(reader, mode, baseDir, plantIdMap, warnings,
                        lineNumber, restoredUris, db)) {
                        importedAny[0] = true;
                    }
                    cur = progress.incrementAndGet();
                    if (progressCallback != null) {
                        final int progressValue = cur;
                        mainHandler.post(() -> progressCallback.onProgress(progressValue, totalSteps));
                    }

                    if (parseReminders(reader, mode, plantIdMap, warnings,
                        lineNumber, db)) {
                        importedAny[0] = true;
                    }
                    cur = progress.incrementAndGet();
                    if (progressCallback != null) {
                        final int progressValue = cur;
                        mainHandler.post(() -> progressCallback.onProgress(progressValue, totalSteps));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            cleanupUris(restoredUris);
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            Log.e(TAG, "Failed to parse import", e);
            return ImportError.IO_ERROR;
        }
        if (!importedAny[0]) {
            cleanupUris(restoredUris);
            return ImportError.NO_DATA;
        }
        return null;
    }

    private @Nullable ImportError validateVersion(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null || !firstLine.startsWith("Version,")) {
            return ImportError.MISSING_VERSION;
        }
        int version;
        try {
            version = Integer.parseInt(firstLine.split(",", 2)[1]);
        } catch (Exception e) {
            return ImportError.INVALID_VERSION;
        }
        if (version != SUPPORTED_VERSION) {
            return ImportError.UNSUPPORTED_VERSION;
        }
        return null;
    }

    private @Nullable Section readSection(String line, Section current,
                                          BufferedReader reader,
                                          AtomicInteger lineNumber,
                                          AtomicInteger progress,
                                          @Nullable ProgressCallback progressCallback,
                                          int totalSteps) throws IOException {
        Section next = null;
        switch (line) {
            case "Plants":
                next = Section.PLANTS;
                break;
            case "SpeciesTargets":
                next = Section.SPECIES_TARGETS;
                break;
            case "Measurements":
                next = Section.MEASUREMENTS;
                break;
            case "DiaryEntries":
                next = Section.DIARY_ENTRIES;
                break;
            case "Reminders":
                next = Section.REMINDERS;
                break;
        }
        if (next == null) {
            return null;
        }
        if (reader.readLine() != null) {
            lineNumber.incrementAndGet();
        }
        int cur = progress.incrementAndGet();
        if (progressCallback != null) {
            final int finalCur = cur;
            mainHandler.post(() -> progressCallback.onProgress(finalCur, totalSteps));
        }
        return next;
    }

    private boolean parsePlants(BufferedReader reader, Mode mode, File baseDir,
                                Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                AtomicInteger lineNumber, List<Uri> restoredUris,
                                PlantDatabase db) throws IOException {
        String line;
        boolean imported = false;
        while ((line = reader.readLine()) != null) {
            lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) continue;
            if ("Plants".equals(line)) {
                break;
            }
        }
        if (line == null) {
            return false;
        }
        reader.readLine();
        lineNumber.incrementAndGet();
        reader.mark(1 << 16);
        while ((line = reader.readLine()) != null) {
            int currentLine = lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) {
                reader.mark(1 << 16);
                continue;
            }
            if ("SpeciesTargets".equals(line)) {
                reader.reset();
                lineNumber.decrementAndGet();
                break;
            }
            List<String> parts = parseCsv(line);
            if (parsePlantRow(parts, mode, baseDir, plantIdMap, warnings, currentLine,
                restoredUris, db)) {
                imported = true;
            }
            reader.mark(1 << 16);
        }
        return imported;
    }

    private boolean parseSpeciesTargets(BufferedReader reader, PlantDatabase db,
                                        List<ImportWarning> warnings,
                                        AtomicInteger lineNumber, NumberFormat nf)
        throws IOException {
        String line;
        boolean imported = false;
        while ((line = reader.readLine()) != null) {
            lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) continue;
            if ("SpeciesTargets".equals(line)) {
                break;
            }
        }
        if (line == null) {
            return false;
        }
        reader.readLine();
        lineNumber.incrementAndGet();
        reader.mark(1 << 16);
        while ((line = reader.readLine()) != null) {
            int currentLine = lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) {
                reader.mark(1 << 16);
                continue;
            }
            if ("Measurements".equals(line)) {
                reader.reset();
                lineNumber.decrementAndGet();
                break;
            }
            List<String> parts = parseCsv(line);
            if (parts.size() >= 3) {
                try {
                    String speciesKey = parts.get(0);
                    float ppfdMin = Objects.requireNonNull(nf.parse(parts.get(1))).floatValue();
                    float ppfdMax = Objects.requireNonNull(nf.parse(parts.get(2))).floatValue();
                    SpeciesTarget t = new SpeciesTarget(speciesKey, ppfdMin, ppfdMax);
                    db.speciesTargetDao().insert(t);
                    imported = true;
                } catch (Exception e) {
                    Log.e(TAG, "Malformed species target row: " + line, e);
                    warnings.add(new ImportWarning("species targets", currentLine, "malformed row"));
                }
            } else {
                Log.e(TAG, "Malformed species target row: " + line);
                warnings.add(new ImportWarning("species targets", currentLine, "malformed row"));
            }
            reader.mark(1 << 16);
        }
        return imported;
    }

    private boolean parseMeasurements(BufferedReader reader, Mode mode,
                                      Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                      AtomicInteger lineNumber, NumberFormat nf,
                                      PlantDatabase db) throws IOException {
        String line;
        boolean imported = false;
        while ((line = reader.readLine()) != null) {
            lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) continue;
            if ("Measurements".equals(line)) {
                break;
            }
        }
        if (line == null) {
            return false;
        }
        reader.readLine();
        lineNumber.incrementAndGet();
        reader.mark(1 << 16);
        while ((line = reader.readLine()) != null) {
            int currentLine = lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) {
                reader.mark(1 << 16);
                continue;
            }
            if ("DiaryEntries".equals(line)) {
                reader.reset();
                lineNumber.decrementAndGet();
                break;
            }
            List<String> parts = parseCsv(line);
            if (insertMeasurementRow(parts, mode, plantIdMap, warnings, currentLine, nf, db)) {
                imported = true;
            }
            reader.mark(1 << 16);
        }
        return imported;
    }

    private boolean parseDiaryEntries(BufferedReader reader, Mode mode, File baseDir,
                                      Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                      AtomicInteger lineNumber, List<Uri> restoredUris,
                                      PlantDatabase db) throws IOException {
        String line;
        boolean imported = false;
        while ((line = reader.readLine()) != null) {
            lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) continue;
            if ("DiaryEntries".equals(line)) {
                break;
            }
        }
        if (line == null) {
            return false;
        }
        reader.readLine();
        lineNumber.incrementAndGet();
        reader.mark(1 << 16);
        while ((line = reader.readLine()) != null) {
            int currentLine = lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) {
                reader.mark(1 << 16);
                continue;
            }
            if ("Reminders".equals(line)) {
                reader.reset();
                lineNumber.decrementAndGet();
                break;
            }
            List<String> parts = parseCsv(line);
            if (insertDiaryRow(parts, mode, baseDir, plantIdMap, warnings,
                currentLine, restoredUris, db)) {
                imported = true;
            }
            reader.mark(1 << 16);
        }
        return imported;
    }

    private boolean parseReminders(BufferedReader reader, Mode mode,
                                   Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                   AtomicInteger lineNumber, PlantDatabase db) throws IOException {
        String line;
        boolean imported = false;
        while ((line = reader.readLine()) != null) {
            lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) continue;
            if ("Reminders".equals(line)) {
                break;
            }
        }
        if (line == null) {
            return false;
        }
        reader.readLine();
        lineNumber.incrementAndGet();
        while ((line = reader.readLine()) != null) {
            int currentLine = lineNumber.incrementAndGet();
            if (line.trim().isEmpty()) {
                continue;
            }
            List<String> parts = parseCsv(line);
            if (insertReminderRow(parts, mode, plantIdMap, warnings,
                currentLine, db)) {
                imported = true;
            }
        }
        return imported;
    }

    private boolean insertDiaryRow(List<String> parts, Mode mode, File baseDir,
                                   Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                   int currentLine, List<Uri> restoredUris,
                                   PlantDatabase db) {
        if (parts.size() < 6) {
            Log.e(TAG, "Malformed diary row: " + parts);
            warnings.add(new ImportWarning("diary entries", currentLine, "malformed row"));
            return false;
        }
        try {
            long plantId;
            try {
                plantId = Long.parseLong(parts.get(1));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("diary entries", currentLine, "invalid plant id"));
                return false;
            }
            if (mode == Mode.MERGE) {
                Long mappedId = plantIdMap.get(plantId);
                if (mappedId == null) {
                    Log.w(TAG, "Skipping diary entry for missing plant " + plantId);
                    warnings.add(new ImportWarning("diary entries", currentLine, "unknown plant"));
                    return false;
                }
                plantId = mappedId;
            }
            long timeEpoch;
            try {
                timeEpoch = Long.parseLong(parts.get(2));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("diary entries", currentLine, "invalid timestamp"));
                return false;
            }
            String type = parts.get(3);
            String note = parts.get(4);
            String photoUri = parts.get(5);
            DiaryEntry d = new DiaryEntry(plantId, timeEpoch, type, note);
            if (!photoUri.isEmpty()) {
                Uri restored = restoreImage(new File(baseDir, photoUri));
                if (restored != null) {
                    d.setPhotoUri(restored.toString());
                    restoredUris.add(restored);
                } else {
                    warnings.add(new ImportWarning("diary entries", currentLine, "photo missing"));
                }
            }
            db.diaryDao().insert(d);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed diary row: " + parts, e);
            warnings.add(new ImportWarning("diary entries", currentLine, "malformed row"));
            return false;
        }
    }

    private boolean insertReminderRow(List<String> parts, Mode mode,
                                      Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                      int currentLine, PlantDatabase db) {
        if (parts.size() < 4) {
            Log.e(TAG, "Malformed reminder row: " + parts);
            warnings.add(new ImportWarning("reminders", currentLine, "malformed row"));
            return false;
        }
        try {
            long id;
            long plantId;
            try {
                id = Long.parseLong(parts.get(0));
                plantId = Long.parseLong(parts.get(1));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("reminders", currentLine, "invalid id"));
                return false;
            }
            if (mode == Mode.MERGE) {
                Long mappedId = plantIdMap.get(plantId);
                if (mappedId == null) {
                    Log.w(TAG, "Skipping reminder for missing plant " + plantId);
                    warnings.add(new ImportWarning("reminders", currentLine, "unknown plant"));
                    return false;
                }
                plantId = mappedId;
            }
            long triggerAt;
            try {
                triggerAt = Long.parseLong(parts.get(2));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("reminders", currentLine, "invalid timestamp"));
                return false;
            }
            String message = parts.get(3);
            Reminder r = new Reminder(triggerAt, message, plantId);
            long reminderId;
            if (mode == Mode.MERGE) {
                reminderId = db.reminderDao().insert(r);
            } else {
                r.setId(id);
                db.reminderDao().insert(r);
                reminderId = id;
            }
            ReminderScheduler.scheduleReminderAt(context, triggerAt, message, reminderId, plantId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed reminder row: " + parts, e);
            warnings.add(new ImportWarning("reminders", currentLine, "malformed row"));
            return false;
        }
    }

    private boolean parsePlantRow(List<String> parts, Mode mode, File baseDir,
                                  Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                  int currentLine, List<Uri> restoredUris,
                                  PlantDatabase db) {
        if (parts.size() < 7) {
            Log.e(TAG, "Malformed plant row: " + parts);
            warnings.add(new ImportWarning("plants", currentLine, "malformed row"));
            return false;
        }
        try {
            long id = Long.parseLong(parts.get(0));
            String name = parts.get(1);
            String description = parts.get(2).isEmpty() ? null : parts.get(2);
            String species = parts.get(3).isEmpty() ? null : parts.get(3);
            String location = parts.get(4).isEmpty() ? null : parts.get(4);
            long acquired = Long.parseLong(parts.get(5));
            String photo = parts.get(6);
            Uri photoUri = null;
            if (!photo.isEmpty()) {
                Uri restored = restoreImage(new File(baseDir, photo));
                if (restored != null) {
                    photoUri = restored;
                    restoredUris.add(restored);
                } else {
                    warnings.add(new ImportWarning("plants", currentLine, "photo missing"));
                }
            }
            Plant p = new Plant(name, description, species, location, acquired, photoUri);
            if (mode == Mode.MERGE) {
                p.setId(0);
                long newId = db.plantDao().insert(p);
                plantIdMap.put(id, newId);
            } else {
                p.setId(id);
                db.plantDao().insert(p);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed plant row: " + parts, e);
            warnings.add(new ImportWarning("plants", currentLine, "malformed row"));
            return false;
        }
    }

    private boolean insertMeasurementRow(List<String> parts, Mode mode,
                                         Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                         int currentLine, NumberFormat nf,
                                         PlantDatabase db) {
        if (parts.size() < 7) {
            Log.e(TAG, "Malformed measurement row: " + parts);
            warnings.add(new ImportWarning("measurements", currentLine, "malformed row"));
            return false;
        }
        try {
            long plantId;
            try {
                plantId = Long.parseLong(parts.get(1));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("measurements", currentLine, "invalid plant id"));
                return false;
            }
            if (mode == Mode.MERGE) {
                Long mappedId = plantIdMap.get(plantId);
                if (mappedId == null) {
                    Log.w(TAG, "Skipping measurement for missing plant " + plantId);
                    warnings.add(new ImportWarning("measurements", currentLine, "unknown plant"));
                    return false;
                }
                plantId = mappedId;
            }
            long timeEpoch;
            try {
                timeEpoch = Long.parseLong(parts.get(2));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("measurements", currentLine, "invalid timestamp"));
                return false;
            }
            float luxAvg;
            try {
                luxAvg = Objects.requireNonNull(nf.parse(parts.get(3))).floatValue();
            } catch (Exception e) {
                warnings.add(new ImportWarning("measurements", currentLine, "invalid lux value"));
                return false;
            }
            Float ppfd = null;
            if (!parts.get(4).isEmpty()) {
                try {
                    ppfd = Objects.requireNonNull(nf.parse(parts.get(4))).floatValue();
                } catch (Exception e) {
                    warnings.add(new ImportWarning("measurements", currentLine, "invalid PPFD value"));
                    return false;
                }
            }
            Float dli = null;
            if (!parts.get(5).isEmpty()) {
                try {
                    dli = Objects.requireNonNull(nf.parse(parts.get(5))).floatValue();
                } catch (Exception e) {
                    warnings.add(new ImportWarning("measurements", currentLine, "invalid DLI value"));
                    return false;
                }
            }
            String note = parts.get(6).isEmpty() ? null : parts.get(6);
            Measurement m = new Measurement(plantId, timeEpoch, luxAvg, ppfd, dli, note);
            db.measurementDao().insert(m);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed measurement row: " + parts, e);
            warnings.add(new ImportWarning("measurements", currentLine, "malformed row"));
            return false;
        }
    }

    private static List<String> parseCsv(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private void cleanupUris(List<Uri> uris) {
        ContentResolver resolver = context.getContentResolver();
        for (Uri uri : uris) {
            resolver.delete(uri, null, null);
        }
    }

    private Uri restoreImage(File exportedImage) {
        try {
            String name = "imported_" + System.currentTimeMillis() + "_" + exportedImage.getName();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/*");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/PlantImports");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            ContentResolver resolver = context.getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Failed to create MediaStore record");
            }

            try (InputStream in = new FileInputStream(exportedImage);
                 OutputStream out = resolver.openOutputStream(uri)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    Objects.requireNonNull(out).write(buffer, 0, len);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }

            return uri;
        } catch (IOException e) {
            Log.e(TAG, "Failed to restore image" , e);
            return null;
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
