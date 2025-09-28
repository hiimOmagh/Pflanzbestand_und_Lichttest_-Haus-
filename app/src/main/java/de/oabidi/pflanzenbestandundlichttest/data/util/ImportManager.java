package de.oabidi.pflanzenbestandundlichttest.data.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.text.NumberFormat;
import java.util.concurrent.ExecutorService;

import de.oabidi.pflanzenbestandundlichttest.ExecutorProvider;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import java.nio.charset.StandardCharsets;

import de.oabidi.pflanzenbestandundlichttest.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.Reminder;
import de.oabidi.pflanzenbestandundlichttest.ReminderScheduler;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.BulkReadDao;
import de.oabidi.pflanzenbestandundlichttest.PlantApp;
import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfileAssociation;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.data.util.FileUtils;

/**
 * Manager responsible for importing measurements and diary entries from a CSV file.
 */
public class ImportManager {
    static final String TAG = "ImportManager";
    private static final int CURRENT_VERSION = 4;
    private static final int[] SUPPORTED_VERSIONS = {1, 2, 3, 4};
    private static final int DEFAULT_ARCHIVE_PROGRESS_STEPS = 1_048_576; // 1 MiB heuristic
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor;
    private int importVersion = CURRENT_VERSION;

    public ImportManager(@NonNull Context context) {
        this(context, resolveExecutor(context));
    }

    @NonNull
    private static ExecutorService resolveExecutor(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        return ((ExecutorProvider) appContext).getIoExecutor();
    }

    public ImportManager(@NonNull Context context, @NonNull ExecutorService executor) {
        this.context = context.getApplicationContext();
        this.executor = executor;
    }

    /**
     * Builds a human-readable summary of warnings for display.
     */
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

    static List<String> parseCsv(String line) {
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
        executor.execute(() -> {
            boolean success = false;
            ImportError error = null;
            List<ImportWarning> warnings = new ArrayList<>();
            File tempDir = new File(context.getCacheDir(), "import_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                tempDir = null;
            }
            ArchiveKind archiveKind = determineArchiveKind(uri);
            if (tempDir != null) {
                if (archiveKind == ArchiveKind.JSON_STREAM) {
                    final AtomicInteger progress = new AtomicInteger(0);
                    final AtomicInteger totalSteps = new AtomicInteger(0);
                    File dataFile = new File(tempDir, "data.json");
                    try (InputStream is = context.getContentResolver().openInputStream(uri);
                         OutputStream os = new FileOutputStream(dataFile)) {
                        if (is == null) {
                            throw new IOException("Unable to open input stream");
                        }
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to read JSON import", e);
                        error = ImportError.IO_ERROR;
                    }
                    if (error == null) {
                        ProcessResult resultObj = processDataFile(dataFile, true, tempDir, mode,
                            warnings, progressCallback, progress, totalSteps);
                        success = resultObj.success;
                        error = resultObj.error;
                    }
                    FileUtils.deleteRecursive(tempDir);
                } else {
                    String tempCanonical = null;
                    try {
                        tempCanonical = tempDir.getCanonicalPath();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to resolve temp directory", e);
                    }
                    if (tempCanonical == null) {
                        error = ImportError.IO_ERROR;
                        FileUtils.deleteRecursive(tempDir);
                    } else {
                        final String tempCanonicalWithSep = tempCanonical.endsWith(File.separator)
                            ? tempCanonical
                            : tempCanonical + File.separator;
                        final long totalBytes = queryArchiveSize(uri);
                        final int archiveSteps = computeArchiveSteps(totalBytes);
                        final AtomicInteger totalSteps = new AtomicInteger(archiveSteps);
                        final AtomicInteger progress = new AtomicInteger(0);

                        try (InputStream is = context.getContentResolver().openInputStream(uri);
                             CountingInputStream countingIs = new CountingInputStream(is);
                             ZipInputStream zis = new ZipInputStream(countingIs)) {
                            ArchiveProgressTracker tracker = new ArchiveProgressTracker(totalBytes,
                                archiveSteps, progress, totalSteps, progressCallback);
                            ZipEntry zipEntry;
                            File csvFile = null;
                            File jsonFile = null;
                            byte[] buffer = new byte[8192];
                            while ((zipEntry = zis.getNextEntry()) != null) {
                                tracker.update(countingIs.getCount());
                                String entryName = zipEntry.getName();
                                File outFile = new File(tempDir, entryName);
                                String outCanonical;
                                try {
                                    outCanonical = outFile.getCanonicalPath();
                                } catch (IOException e) {
                                    Log.w(TAG, "Skipping zip entry with invalid path: " + entryName, e);
                                    zis.closeEntry();
                                    tracker.update(countingIs.getCount());
                                    continue;
                                }
                                if (!outCanonical.equals(tempCanonical)
                                    && !outCanonical.startsWith(tempCanonicalWithSep)) {
                                    Log.w(TAG, "Skipping suspicious zip entry: " + entryName);
                                    zis.closeEntry();
                                    tracker.update(countingIs.getCount());
                                    continue;
                            }
                            if (zipEntry.isDirectory()) {
                                if (!outFile.isDirectory() && !outFile.mkdirs()) {
                                    Log.w(TAG, "Failed to create directory for zip entry: " + entryName);
                                    }
                                zis.closeEntry();
                                tracker.update(countingIs.getCount());
                                continue;
                                }
                                File parent = outFile.getParentFile();
                                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                                    Log.w(TAG, "Failed to create parent directories for zip entry: " + entryName);
                                    zis.closeEntry();
                                    tracker.update(countingIs.getCount());
                                    continue;
                                }
                                try (OutputStream fos = new FileOutputStream(outFile)) {
                                    int len;
                                    while ((len = zis.read(buffer)) > 0) {
                                        fos.write(buffer, 0, len);
                                        tracker.update(countingIs.getCount());
                                    }
                                }
                                if (entryName.endsWith(".csv")) {
                                    csvFile = outFile;
                                } else if (entryName.endsWith(".json")) {
                                    jsonFile = outFile;
                                }
                                zis.closeEntry();
                                tracker.update(countingIs.getCount());
                            }
                            tracker.complete();
                            boolean useJson = jsonFile != null
                                && (archiveKind == ArchiveKind.JSON_ZIP || csvFile == null);
                            File dataFile = useJson ? jsonFile : csvFile;
                            if (dataFile != null) {
                                ProcessResult resultObj = processDataFile(dataFile, useJson, tempDir,
                                    mode, warnings, progressCallback, progress, totalSteps);
                                success = resultObj.success;
                                error = resultObj.error;
                            } else {
                                error = ImportError.IO_ERROR;
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to open import file", e);
                            success = false;
                            error = ImportError.IO_ERROR;
                        } finally {
                            FileUtils.deleteRecursive(tempDir);
                        }
                    }
                }
            } else {
                error = ImportError.IO_ERROR;
            }
            final boolean result = success;
            final ImportError finalError = error;
            final List<ImportWarning> warningList = new ArrayList<>(warnings);
            final String message = result
                ? context.getString(R.string.import_success)
                : context.getString(R.string.import_failure);
            mainHandler.post(() -> callback.onComplete(result, finalError, warningList, message));
        });
    }

    void stepProgress(@NonNull AtomicInteger progress,
                      @Nullable ProgressCallback progressCallback,
                      int totalSteps) {
        stepProgress(progress, progressCallback, totalSteps, 1);
    }

    void stepProgress(@NonNull AtomicInteger progress,
                      @Nullable ProgressCallback progressCallback,
                      int totalSteps,
                      int delta) {
        if (delta <= 0) {
            return;
        }
        final int[] previousHolder = new int[1];
        int current = progress.updateAndGet(previous -> {
            previousHolder[0] = previous;
            long candidate = (long) previous + delta;
            if (candidate < 0L) {
                return Integer.MAX_VALUE;
            }
            if (candidate > totalSteps) {
                return totalSteps;
            }
            return (int) candidate;
        });
        if (progressCallback != null && current != previousHolder[0]) {
            final int finalCurrent = current;
            final int finalTotal = totalSteps;
            mainHandler.post(() -> progressCallback.onProgress(finalCurrent, finalTotal));
        }
    }

    void publishProgress(@NonNull AtomicInteger progress,
                         @Nullable ProgressCallback progressCallback,
                         int totalSteps) {
        if (progressCallback == null) {
            return;
        }
        int current = progress.get();
        mainHandler.post(() -> progressCallback.onProgress(current, totalSteps));
    }

    @VisibleForTesting
    @Nullable
    ImportError parseAndInsert(BufferedReader reader, File baseDir, Mode mode,
                               List<ImportWarning> warnings,
                               @Nullable ProgressCallback progressCallback,
                               AtomicInteger progress,
                               AtomicInteger totalSteps) throws IOException {
        ImportError versionError = validateVersion(reader);
        if (versionError != null) {
            return versionError;
        }

        PlantDatabase db = PlantDatabase.getDatabase(context);
        Map<Long, Long> plantIdMap = new HashMap<>();
        Map<Long, Long> ledProfileIdMap = new HashMap<>();
        final boolean[] importedAny = {false};
        final NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        List<Uri> restoredUris = new ArrayList<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicInteger lineNumber = new AtomicInteger(1);
        try {
            db.runInTransaction(() -> {
                try {
                    SectionReader sectionReader = new SectionReader(reader, lineNumber);
                    SectionContext context = new SectionContext(this, mode, baseDir,
                        plantIdMap, ledProfileIdMap, warnings, restoredUris, db, nf, importVersion, cancelled);
                    SectionCoordinator coordinator = new SectionCoordinator(
                        this,
                        sectionReader,
                        Arrays.asList(
                            new LedProfilesSectionParser(),
                            new PlantsSectionParser(),
                            new PlantPhotosSectionParser(),
                            new SpeciesTargetsSectionParser(),
                            new MeasurementsSectionParser(),
                            new EnvironmentEntriesSectionParser(),
                            new DiaryEntriesSectionParser(),
                            new RemindersSectionParser(),
                            new ReminderSuggestionsSectionParser()
                        ),
                        context,
                        progress,
                        progressCallback,
                        totalSteps,
                        cancelled
                    );
                    if (coordinator.process()) {
                        importedAny[0] = true;
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

    @VisibleForTesting
    @Nullable
    ImportError parseAndInsertJson(JsonReader reader, File baseDir, Mode mode,
                                   List<ImportWarning> warnings,
                                   @Nullable ProgressCallback progressCallback,
                                   AtomicInteger progress,
                                   AtomicInteger totalSteps) throws IOException {
        PlantDatabase db = PlantDatabase.getDatabase(context);
        Map<Long, Long> plantIdMap = new HashMap<>();
        final boolean[] importedAny = {false};
        final NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        List<Uri> restoredUris = new ArrayList<>();
        final ImportError[] errorHolder = {null};
        try {
            db.runInTransaction(() -> {
                try {
                    reader.beginObject();
                    boolean versionSeen = false;
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        switch (name) {
                            case "version":
                                if (reader.peek() == JsonToken.NULL) {
                                    errorHolder[0] = ImportError.MISSING_VERSION;
                                    throw new RuntimeException();
                                }
                                int version = reader.nextInt();
                                ImportError versionError = validateVersionNumber(version);
                                if (versionError != null) {
                                    errorHolder[0] = versionError;
                                    throw new RuntimeException();
                                }
                                versionSeen = true;
                                break;
                            case "ledProfiles":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonLedProfilesArray(reader, mode,
                                        ledProfileIdMap, warnings, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            case "plants":
                                if (!versionSeen) {
                                    errorHolder[0] = ImportError.MISSING_VERSION;
                                    reader.skipValue();
                                    throw new RuntimeException();
                                }
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonPlantsArray(reader, mode, baseDir,
                                        plantIdMap, ledProfileIdMap, warnings, restoredUris, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            case "plantPhotos":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonPlantPhotosArray(reader, mode, baseDir,
                                        plantIdMap, warnings, restoredUris, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            case "speciesTargets":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonSpeciesTargetsArray(reader, warnings, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            case "measurements":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonMeasurementsArray(reader, mode,
                                        plantIdMap, warnings, nf, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            case "environmentEntries":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonEnvironmentEntriesArray(reader, mode, baseDir,
                                        plantIdMap, warnings, restoredUris, nf, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            case "diaryEntries":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonDiaryEntriesArray(reader, mode, baseDir,
                                        plantIdMap, warnings, restoredUris, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            case "reminders":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonRemindersArray(reader, mode, plantIdMap,
                                        warnings, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            case "reminderSuggestions":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    ParseResult result = parseJsonReminderSuggestionsArray(reader, mode, plantIdMap,
                                        warnings, db);
                                    if (result.imported) {
                                        importedAny[0] = true;
                                    }
                                    applySectionProgress(result, totalSteps, progress, progressCallback);
                                }
                                break;
                            default:
                                reader.skipValue();
                                break;
                        }
                        if (errorHolder[0] != null) {
                            break;
                        }
                    }
                    reader.endObject();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            cleanupUris(restoredUris);
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            if (errorHolder[0] != null) {
                return errorHolder[0];
            }
            Log.e(TAG, "Failed to parse JSON import", e);
            return ImportError.IO_ERROR;
        }
        if (errorHolder[0] != null) {
            cleanupUris(restoredUris);
            return errorHolder[0];
        }
        if (!importedAny[0]) {
            cleanupUris(restoredUris);
            return ImportError.NO_DATA;
        }
        return null;
    }

    private ArchiveKind determineArchiveKind(@NonNull Uri uri) {
        String type = null;
        try {
            type = context.getContentResolver().getType(uri);
        } catch (Exception e) {
            Log.w(TAG, "Unable to determine MIME type", e);
        }
        if (type != null) {
            String lower = type.toLowerCase(Locale.US);
            if (lower.contains("json")) {
                return ArchiveKind.JSON_STREAM;
            }
        }
        String name = resolveDisplayName(uri);
        if (name != null) {
            String lowerName = name.toLowerCase(Locale.US);
            if (lowerName.endsWith(".json.zip")) {
                return ArchiveKind.JSON_ZIP;
            }
            if (lowerName.endsWith(".json")) {
                return ArchiveKind.JSON_STREAM;
            }
        }
        return ArchiveKind.ZIP;
    }

    @Nullable
    private String resolveDisplayName(@NonNull Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) {
                    return name;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to query display name", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String lastSegment = uri.getLastPathSegment();
        return lastSegment != null ? lastSegment : null;
    }

    private long queryArchiveSize(@NonNull Uri uri) {
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd != null) {
                long statSize = pfd.getStatSize();
                if (statSize > 0) {
                    return statSize;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to determine import size", e);
        }
        return -1L;
    }

    @VisibleForTesting
    int computeArchiveSteps(long totalBytes) {
        long maxSteps = (long) Integer.MAX_VALUE / 2;
        if (totalBytes > 0) {
            long clamped = Math.min(totalBytes, maxSteps);
            return (int) Math.max(1L, clamped);
        }
        return (int) Math.max(1L, Math.min(DEFAULT_ARCHIVE_PROGRESS_STEPS, maxSteps));
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
        return validateVersionNumber(version);
    }

    private @Nullable ImportError validateVersionNumber(int version) {
        boolean supported = false;
        for (int supportedVersion : SUPPORTED_VERSIONS) {
            if (supportedVersion == version) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            return ImportError.UNSUPPORTED_VERSION;
        }
        importVersion = version;
        return null;
    }

    private Section readSection(@NonNull String line) throws IOException {
        Section next = Section.fromHeader(line);
        if (next == null || next == Section.NONE) {
            throw new IOException("Malformed section header: " + line);
        }
        return next;
    }

    boolean insertDiaryRow(List<String> parts, Mode mode, File baseDir,
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

    boolean insertReminderRow(List<String> parts, Mode mode,
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

    boolean insertReminderSuggestionRow(List<String> parts, Mode mode,
                                        Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                        int currentLine, PlantDatabase db) {
        if (parts.size() < 5) {
            Log.e(TAG, "Malformed reminder suggestion row: " + parts);
            warnings.add(new ImportWarning("reminder suggestions", currentLine, "malformed row"));
            return false;
        }
        try {
            long plantId;
            try {
                plantId = Long.parseLong(parts.get(0));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("reminder suggestions", currentLine, "invalid plant id"));
                return false;
            }
            if (mode == Mode.MERGE) {
                Long mappedId = plantIdMap.get(plantId);
                if (mappedId == null) {
                    warnings.add(new ImportWarning("reminder suggestions", currentLine, "unknown plant"));
                    return false;
                }
                plantId = mappedId;
            }
            int intervalDays;
            try {
                intervalDays = Integer.parseInt(parts.get(1));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("reminder suggestions", currentLine, "invalid interval"));
                return false;
            }
            long evaluatedAt;
            try {
                evaluatedAt = Long.parseLong(parts.get(2));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("reminder suggestions", currentLine, "invalid timestamp"));
                return false;
            }
            float confidence = 0f;
            String confidenceRaw = parts.get(3);
            if (!confidenceRaw.isEmpty()) {
                try {
                    confidence = Float.parseFloat(confidenceRaw);
                } catch (NumberFormatException e) {
                    warnings.add(new ImportWarning("reminder suggestions", currentLine, "invalid confidence"));
                    return false;
                }
            }
            String explanation = parts.get(4);
            ReminderSuggestion suggestion = new ReminderSuggestion();
            suggestion.setPlantId(plantId);
            suggestion.setSuggestedIntervalDays(intervalDays);
            suggestion.setLastEvaluatedAt(evaluatedAt);
            suggestion.setConfidenceScore(confidence);
            if (explanation != null && !explanation.isEmpty()) {
                suggestion.setExplanation(explanation);
            } else {
                suggestion.setExplanation(null);
            }
            db.reminderSuggestionDao().upsert(suggestion);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed reminder suggestion row: " + parts, e);
            warnings.add(new ImportWarning("reminder suggestions", currentLine, "malformed row"));
            return false;
        }
    }

    boolean parsePlantRow(List<String> parts, Mode mode, File baseDir,
                          Map<Long, Long> plantIdMap, Map<Long, Long> ledProfileIdMap,
                          List<ImportWarning> warnings,
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
            Long profileId = null;
            if (parts.size() > 7) {
                String profileValue = parts.get(7);
                if (!profileValue.isEmpty()) {
                    try {
                        profileId = Long.parseLong(profileValue);
                    } catch (NumberFormatException e) {
                        warnings.add(new ImportWarning("plants", currentLine, "invalid led profile id"));
                        profileId = null;
                    }
                }
            }
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
            Long resolvedProfileId = null;
            if (profileId != null) {
                Long mapped = ledProfileIdMap.get(profileId);
                if (mapped == null) {
                    warnings.add(new ImportWarning("plants", currentLine, "unknown led profile"));
                } else {
                    resolvedProfileId = mapped;
                    p.setLedProfileId(mapped);
                }
            }
            if (mode == Mode.MERGE) {
                p.setId(0);
                long newId = db.plantDao().insert(p);
                plantIdMap.put(id, newId);
                if (resolvedProfileId != null) {
                    db.ledProfileAssociationDao().upsert(new LedProfileAssociation(newId, resolvedProfileId));
                }
            } else {
                p.setId(id);
                long newId = db.plantDao().insert(p);
                if (resolvedProfileId != null) {
                    db.ledProfileAssociationDao().upsert(new LedProfileAssociation(newId, resolvedProfileId));
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed plant row: " + parts, e);
            warnings.add(new ImportWarning("plants", currentLine, "malformed row"));
            return false;
        }
    }

    boolean insertPlantPhotoRow(List<String> parts, Mode mode, File baseDir,
                                Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                int currentLine, List<Uri> restoredUris,
                                PlantDatabase db) {
        if (parts.size() < 4) {
            Log.e(TAG, "Malformed plant photo row: " + parts);
            warnings.add(new ImportWarning("plant photos", currentLine, "malformed row"));
            return false;
        }
        try {
            long id = Long.parseLong(parts.get(0));
            long plantId;
            try {
                plantId = Long.parseLong(parts.get(1));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("plant photos", currentLine, "invalid plant id"));
                return false;
            }
            if (mode == Mode.MERGE) {
                Long mappedId = plantIdMap.get(plantId);
                if (mappedId == null) {
                    warnings.add(new ImportWarning("plant photos", currentLine, "unknown plant"));
                    return false;
                }
                plantId = mappedId;
            }
            String fileName = parts.get(2);
            long createdAt;
            try {
                createdAt = Long.parseLong(parts.get(3));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("plant photos", currentLine, "invalid timestamp"));
                return false;
            }
            if (fileName.isEmpty()) {
                warnings.add(new ImportWarning("plant photos", currentLine, "photo missing"));
                return false;
            }
            File exported = new File(baseDir, fileName);
            if (!exported.isFile()) {
                warnings.add(new ImportWarning("plant photos", currentLine, "photo missing"));
                return false;
            }
            Uri savedUri;
            try {
                savedUri = PhotoManager.savePlantPhoto(context, Uri.fromFile(exported));
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist plant photo from " + exported, e);
                warnings.add(new ImportWarning("plant photos", currentLine, "photo missing"));
                return false;
            }
            restoredUris.add(savedUri);
            PlantPhoto photo = new PlantPhoto(plantId, savedUri.toString(), createdAt);
            if (mode == Mode.REPLACE) {
                photo.setId(id);
            }
            db.plantPhotoDao().insert(photo);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed plant photo row: " + parts, e);
            warnings.add(new ImportWarning("plant photos", currentLine, "malformed row"));
            return false;
        }
    }

    boolean insertLedProfileRow(List<String> parts, Mode mode,
                                Map<Long, Long> ledProfileIdMap, List<ImportWarning> warnings,
                                int currentLine, PlantDatabase db) {
        if (parts.size() < 6) {
            Log.e(TAG, "Malformed LED profile row: " + parts);
            warnings.add(new ImportWarning("led profiles", currentLine, "malformed row"));
            return false;
        }
        try {
            long originalId;
            try {
                originalId = Long.parseLong(parts.get(0));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("led profiles", currentLine, "invalid profile id"));
                return false;
            }
            String name = parts.get(1);
            String type = parts.get(2).isEmpty() ? null : parts.get(2);
            Float mountingDistance = null;
            if (parts.size() > 3 && !parts.get(3).isEmpty()) {
                try {
                    mountingDistance = Float.parseFloat(parts.get(3));
                } catch (NumberFormatException e) {
                    warnings.add(new ImportWarning("led profiles", currentLine, "invalid distance"));
                    return false;
                }
            }
            Float ambient = null;
            if (parts.size() > 4 && !parts.get(4).isEmpty()) {
                try {
                    ambient = Float.parseFloat(parts.get(4));
                } catch (NumberFormatException e) {
                    warnings.add(new ImportWarning("led profiles", currentLine, "invalid ambient factor"));
                    return false;
                }
            }
            Float camera = null;
            if (parts.size() > 5 && !parts.get(5).isEmpty()) {
                try {
                    camera = Float.parseFloat(parts.get(5));
                } catch (NumberFormatException e) {
                    warnings.add(new ImportWarning("led profiles", currentLine, "invalid camera factor"));
                    return false;
                }
            }
            if ((ambient != null && ambient <= 0f) || (camera != null && camera <= 0f)) {
                warnings.add(new ImportWarning("led profiles", currentLine, "non-positive factor"));
                return false;
            }
            LedProfile profile = new LedProfile();
            profile.setName(name);
            profile.setType(type);
            profile.setMountingDistanceCm(mountingDistance);
            Map<String, Float> factors = new HashMap<>();
            if (ambient != null) {
                factors.put(LedProfile.CALIBRATION_KEY_AMBIENT, ambient);
            }
            if (camera != null) {
                factors.put(LedProfile.CALIBRATION_KEY_CAMERA, camera);
            }
            profile.setCalibrationFactors(factors);
            long storedId;
            if (mode == Mode.MERGE) {
                profile.setId(0);
                storedId = db.ledProfileDao().insert(profile);
            } else {
                profile.setId(originalId);
                storedId = db.ledProfileDao().insert(profile);
            }
            ledProfileIdMap.put(originalId, storedId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed LED profile row: " + parts, e);
            warnings.add(new ImportWarning("led profiles", currentLine, "malformed row"));
            return false;
        }
    }

    boolean insertMeasurementRow(List<String> parts, Mode mode,
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

    boolean insertEnvironmentEntryRow(List<String> parts, Mode mode, File baseDir,
                                      Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                      int currentLine, List<Uri> restoredUris, NumberFormat nf,
                                      PlantDatabase db) {
        if (parts.size() < 10) {
            Log.e(TAG, "Malformed environment entry row: " + parts);
            warnings.add(new ImportWarning("environment entries", currentLine, "malformed row"));
            return false;
        }
        try {
            long plantId;
            try {
                plantId = Long.parseLong(parts.get(1));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("environment entries", currentLine, "invalid plant id"));
                return false;
            }
            if (mode == Mode.MERGE) {
                Long mappedId = plantIdMap.get(plantId);
                if (mappedId == null) {
                    warnings.add(new ImportWarning("environment entries", currentLine, "unknown plant"));
                    return false;
                }
                plantId = mappedId;
            }
            long timestamp;
            try {
                timestamp = Long.parseLong(parts.get(2));
            } catch (NumberFormatException e) {
                warnings.add(new ImportWarning("environment entries", currentLine, "invalid timestamp"));
                return false;
            }
            Float temperature = null;
            if (!parts.get(3).isEmpty()) {
                try {
                    temperature = Objects.requireNonNull(nf.parse(parts.get(3))).floatValue();
                } catch (Exception e) {
                    warnings.add(new ImportWarning("environment entries", currentLine, "invalid temperature"));
                    return false;
                }
            }
            Float humidity = null;
            if (!parts.get(4).isEmpty()) {
                try {
                    humidity = Objects.requireNonNull(nf.parse(parts.get(4))).floatValue();
                } catch (Exception e) {
                    warnings.add(new ImportWarning("environment entries", currentLine, "invalid humidity"));
                    return false;
                }
            }
            Float soilMoisture = null;
            if (!parts.get(5).isEmpty()) {
                try {
                    soilMoisture = Objects.requireNonNull(nf.parse(parts.get(5))).floatValue();
                } catch (Exception e) {
                    warnings.add(new ImportWarning("environment entries", currentLine, "invalid soil moisture"));
                    return false;
                }
            }
            Float height = null;
            if (!parts.get(6).isEmpty()) {
                try {
                    height = Objects.requireNonNull(nf.parse(parts.get(6))).floatValue();
                } catch (Exception e) {
                    warnings.add(new ImportWarning("environment entries", currentLine, "invalid height"));
                    return false;
                }
            }
            Float width = null;
            if (!parts.get(7).isEmpty()) {
                try {
                    width = Objects.requireNonNull(nf.parse(parts.get(7))).floatValue();
                } catch (Exception e) {
                    warnings.add(new ImportWarning("environment entries", currentLine, "invalid width"));
                    return false;
                }
            }
            Float naturalDli = null;
            if (parts.size() > 8 && !parts.get(8).isEmpty()) {
                try {
                    naturalDli = Objects.requireNonNull(nf.parse(parts.get(8))).floatValue();
                } catch (Exception e) {
                    warnings.add(new ImportWarning("environment entries", currentLine, "invalid natural DLI"));
                    return false;
                }
            }
            String notes = parts.size() > 9 ? parts.get(9) : "";
            String photoFile = parts.size() > 10 ? parts.get(10) : "";
            EnvironmentEntry entry = new EnvironmentEntry();
            entry.setPlantId(plantId);
            entry.setTimestamp(timestamp);
            entry.setTemperature(temperature);
            entry.setHumidity(humidity);
            entry.setSoilMoisture(soilMoisture);
            entry.setHeight(height);
            entry.setWidth(width);
            entry.setNaturalDli(naturalDli);
            entry.setNotes(notes.isEmpty() ? null : notes);
            if (!photoFile.isEmpty()) {
                Uri restored = restoreImage(new File(baseDir, photoFile));
                if (restored != null) {
                    entry.setPhotoUri(restored.toString());
                    restoredUris.add(restored);
                } else {
                    warnings.add(new ImportWarning("environment entries", currentLine, "photo missing"));
                }
            }
            db.environmentEntryDao().insert(entry);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Malformed environment entry row: " + parts, e);
            warnings.add(new ImportWarning("environment entries", currentLine, "malformed row"));
            return false;
        }
    }

    private static final class ParseResult {
        final boolean imported;
        final int workUnits;

        ParseResult(boolean imported, int workUnits) {
            this.imported = imported;
            this.workUnits = workUnits;
        }
    }

    private void applySectionProgress(@NonNull ParseResult result,
                                      @NonNull AtomicInteger totalSteps,
                                      @NonNull AtomicInteger progress,
                                      @Nullable ProgressCallback progressCallback) {
        if (result.workUnits > 0) {
            int newTotal = totalSteps.addAndGet(result.workUnits);
            stepProgress(progress, progressCallback, newTotal, result.workUnits);
        } else {
            publishProgress(progress, progressCallback, totalSteps.get());
        }
    }

    private ParseResult parseJsonPlantsArray(JsonReader reader, Mode mode, File baseDir,
                                             Map<Long, Long> plantIdMap, Map<Long, Long> ledProfileIdMap,
                                             List<ImportWarning> warnings,
                                         List<Uri> restoredUris, PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            long id = 0L;
            String name = null;
            String description = null;
            String species = null;
            String location = null;
            long acquired = 0L;
            String photo = null;
            Long ledProfileId = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "id":
                        Long plantId = readNullableLong(reader);
                        id = plantId != null ? plantId : 0L;
                        break;
                    case "name":
                        name = readOptionalString(reader);
                        break;
                    case "description":
                        description = readOptionalString(reader);
                        break;
                    case "species":
                        species = readOptionalString(reader);
                        break;
                    case "locationHint":
                        location = readOptionalString(reader);
                        break;
                    case "acquiredAtEpoch":
                        Long acquiredValue = readNullableLong(reader);
                        acquired = acquiredValue != null ? acquiredValue : 0L;
                        break;
                    case "photo":
                        photo = readOptionalString(reader);
                        break;
                    case "ledProfileId":
                        ledProfileId = readNullableLong(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            List<String> parts = new ArrayList<>(8);
            parts.add(Long.toString(id));
            parts.add(name != null ? name : "");
            parts.add(description != null ? description : "");
            parts.add(species != null ? species : "");
            parts.add(location != null ? location : "");
            parts.add(Long.toString(acquired));
            parts.add(photo != null ? photo : "");
            parts.add(ledProfileId != null ? Long.toString(ledProfileId) : "");
            if (parsePlantRow(parts, mode, baseDir, plantIdMap, ledProfileIdMap, warnings, index, restoredUris, db)) {
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private ParseResult parseJsonPlantPhotosArray(JsonReader reader, Mode mode, File baseDir,
                                              Map<Long, Long> plantIdMap, List<ImportWarning> warnings,
                                              List<Uri> restoredUris, PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            long id = 0L;
            long plantId = 0L;
            String fileName = null;
            long createdAt = 0L;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "id":
                        Long value = readNullableLong(reader);
                        id = value != null ? value : 0L;
                        break;
                    case "plantId":
                        Long pValue = readNullableLong(reader);
                        plantId = pValue != null ? pValue : 0L;
                        break;
                    case "fileName":
                        fileName = readOptionalString(reader);
                        break;
                    case "createdAt":
                        Long createdValue = readNullableLong(reader);
                        createdAt = createdValue != null ? createdValue : 0L;
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            List<String> parts = new ArrayList<>(4);
            parts.add(Long.toString(id));
            parts.add(Long.toString(plantId));
            parts.add(fileName != null ? fileName : "");
            parts.add(Long.toString(createdAt));
            if (insertPlantPhotoRow(parts, mode, baseDir, plantIdMap, warnings, index, restoredUris, db)) {
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private ParseResult parseJsonLedProfilesArray(JsonReader reader, Mode mode,
                                                  Map<Long, Long> ledProfileIdMap,
                                                  List<ImportWarning> warnings,
                                                  PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            long id = 0L;
            String name = null;
            String type = null;
            Float distance = null;
            Map<String, Float> factors = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "id":
                        Long value = readNullableLong(reader);
                        id = value != null ? value : 0L;
                        break;
                    case "name":
                        name = readOptionalString(reader);
                        break;
                    case "type":
                        type = readOptionalString(reader);
                        break;
                    case "mountingDistanceCm":
                        distance = readNullableFloat(reader);
                        break;
                    case "calibrationFactors":
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull();
                        } else {
                            reader.beginObject();
                            while (reader.hasNext()) {
                                String key = reader.nextName();
                                Float value = readNullableFloat(reader);
                                if (key != null && value != null) {
                                    factors.put(key, value);
                                }
                            }
                            reader.endObject();
                        }
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            List<String> parts = new ArrayList<>(6);
            parts.add(Long.toString(id));
            parts.add(name != null ? name : "");
            parts.add(type != null ? type : "");
            parts.add(distance != null ? Float.toString(distance) : "");
            Float ambient = factors.get(LedProfile.CALIBRATION_KEY_AMBIENT);
            Float camera = factors.get(LedProfile.CALIBRATION_KEY_CAMERA);
            parts.add(ambient != null ? Float.toString(ambient) : "");
            parts.add(camera != null ? Float.toString(camera) : "");
            if (insertLedProfileRow(parts, mode, ledProfileIdMap, warnings, index, db)) {
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private ParseResult parseJsonMeasurementsArray(JsonReader reader, Mode mode,
                                                   Map<Long, Long> plantIdMap,
                                                   List<ImportWarning> warnings,
                                                   NumberFormat nf, PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            long id = 0L;
            long plantId = 0L;
            long time = 0L;
            Float lux = null;
            Float ppfd = null;
            Float dli = null;
            String note = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "id":
                        Long idValue = readNullableLong(reader);
                        id = idValue != null ? idValue : 0L;
                        break;
                    case "plantId":
                        Long plantValue = readNullableLong(reader);
                        plantId = plantValue != null ? plantValue : 0L;
                        break;
                    case "timeEpoch":
                        Long timeValue = readNullableLong(reader);
                        time = timeValue != null ? timeValue : 0L;
                        break;
                    case "luxAvg":
                        lux = readNullableFloat(reader);
                        break;
                    case "ppfd":
                        ppfd = readNullableFloat(reader);
                        break;
                    case "dli":
                        dli = readNullableFloat(reader);
                        break;
                    case "note":
                        note = readOptionalString(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            List<String> parts = new ArrayList<>(7);
            parts.add(Long.toString(id));
            parts.add(Long.toString(plantId));
            parts.add(Long.toString(time));
            parts.add(lux != null ? Float.toString(lux) : "0");
            parts.add(ppfd != null ? Float.toString(ppfd) : "");
            parts.add(dli != null ? Float.toString(dli) : "");
            parts.add(note != null ? note : "");
            if (insertMeasurementRow(parts, mode, plantIdMap, warnings, index, nf, db)) {
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private ParseResult parseJsonEnvironmentEntriesArray(JsonReader reader, Mode mode, File baseDir,
                                                         Map<Long, Long> plantIdMap,
                                                         List<ImportWarning> warnings,
                                                         List<Uri> restoredUris, NumberFormat nf,
                                                         PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            long id = 0L;
            long plantId = 0L;
            long timestamp = 0L;
            Float temperature = null;
            Float humidity = null;
            Float soilMoisture = null;
            Float height = null;
            Float width = null;
            Float naturalDli = null;
            String notes = null;
            String photo = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "id":
                        Long idValue = readNullableLong(reader);
                        id = idValue != null ? idValue : 0L;
                        break;
                    case "plantId":
                        Long plantValue = readNullableLong(reader);
                        plantId = plantValue != null ? plantValue : 0L;
                        break;
                    case "timestamp":
                        Long tsValue = readNullableLong(reader);
                        timestamp = tsValue != null ? tsValue : 0L;
                        break;
                    case "temperature":
                        temperature = readNullableFloat(reader);
                        break;
                    case "humidity":
                        humidity = readNullableFloat(reader);
                        break;
                    case "soilMoisture":
                        soilMoisture = readNullableFloat(reader);
                        break;
                    case "height":
                        height = readNullableFloat(reader);
                        break;
                    case "width":
                        width = readNullableFloat(reader);
                        break;
                    case "naturalDli":
                        naturalDli = readNullableFloat(reader);
                        break;
                    case "notes":
                        notes = readOptionalString(reader);
                        break;
                    case "photo":
                        photo = readOptionalString(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            List<String> parts = new ArrayList<>(11);
            parts.add(Long.toString(id));
            parts.add(Long.toString(plantId));
            parts.add(Long.toString(timestamp));
            parts.add(temperature != null ? Float.toString(temperature) : "");
            parts.add(humidity != null ? Float.toString(humidity) : "");
            parts.add(soilMoisture != null ? Float.toString(soilMoisture) : "");
            parts.add(height != null ? Float.toString(height) : "");
            parts.add(width != null ? Float.toString(width) : "");
            parts.add(notes != null ? notes : "");
            parts.add(photo != null ? photo : "");
            if (insertEnvironmentEntryRow(parts, mode, baseDir, plantIdMap, warnings, index,
                restoredUris, nf, db)) {
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private ParseResult parseJsonDiaryEntriesArray(JsonReader reader, Mode mode, File baseDir,
                                               Map<Long, Long> plantIdMap,
                                               List<ImportWarning> warnings,
                                               List<Uri> restoredUris, PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            long id = 0L;
            long plantId = 0L;
            long time = 0L;
            String type = null;
            String note = null;
            String photo = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "id":
                        Long idValue = readNullableLong(reader);
                        id = idValue != null ? idValue : 0L;
                        break;
                    case "plantId":
                        Long plantValue = readNullableLong(reader);
                        plantId = plantValue != null ? plantValue : 0L;
                        break;
                    case "timeEpoch":
                        Long timeValue = readNullableLong(reader);
                        time = timeValue != null ? timeValue : 0L;
                        break;
                    case "type":
                        type = readOptionalString(reader);
                        break;
                    case "note":
                        note = readOptionalString(reader);
                        break;
                    case "photo":
                        photo = readOptionalString(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            List<String> parts = new ArrayList<>(6);
            parts.add(Long.toString(id));
            parts.add(Long.toString(plantId));
            parts.add(Long.toString(time));
            parts.add(type != null ? type : "");
            parts.add(note != null ? note : "");
            parts.add(photo != null ? photo : "");
            if (insertDiaryRow(parts, mode, baseDir, plantIdMap, warnings, index, restoredUris, db)) {
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private ParseResult parseJsonRemindersArray(JsonReader reader, Mode mode,
                                            Map<Long, Long> plantIdMap,
                                            List<ImportWarning> warnings,
                                            PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            long id = 0L;
            long plantId = 0L;
            long triggerAt = 0L;
            String message = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "id":
                        Long idValue = readNullableLong(reader);
                        id = idValue != null ? idValue : 0L;
                        break;
                    case "plantId":
                        Long plantValue = readNullableLong(reader);
                        plantId = plantValue != null ? plantValue : 0L;
                        break;
                    case "triggerAt":
                        Long triggerValue = readNullableLong(reader);
                        triggerAt = triggerValue != null ? triggerValue : 0L;
                        break;
                    case "message":
                        message = readOptionalString(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            List<String> parts = new ArrayList<>(4);
            parts.add(Long.toString(id));
            parts.add(Long.toString(plantId));
            parts.add(Long.toString(triggerAt));
            parts.add(message != null ? message : "");
            if (insertReminderRow(parts, mode, plantIdMap, warnings, index, db)) {
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private ParseResult parseJsonReminderSuggestionsArray(JsonReader reader, Mode mode,
                                                          Map<Long, Long> plantIdMap,
                                                          List<ImportWarning> warnings,
                                                          PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            long plantId = 0L;
            Integer interval = null;
            Long evaluated = null;
            Float confidence = null;
            String explanation = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "plantId":
                        Long plantValue = readNullableLong(reader);
                        plantId = plantValue != null ? plantValue : 0L;
                        break;
                    case "suggestedIntervalDays":
                        Long intervalValue = readNullableLong(reader);
                        interval = intervalValue != null ? intervalValue.intValue() : null;
                        break;
                    case "lastEvaluatedAt":
                        evaluated = readNullableLong(reader);
                        break;
                    case "confidenceScore":
                        confidence = readNullableFloat(reader);
                        break;
                    case "explanation":
                        explanation = readOptionalString(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            List<String> parts = new ArrayList<>(5);
            parts.add(Long.toString(plantId));
            parts.add(interval != null ? Integer.toString(interval) : "0");
            parts.add(Long.toString(evaluated != null ? evaluated : 0L));
            parts.add(confidence != null ? Float.toString(confidence) : "");
            parts.add(explanation != null ? explanation : "");
            if (insertReminderSuggestionRow(parts, mode, plantIdMap, warnings, index, db)) {
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private ParseResult parseJsonSpeciesTargetsArray(JsonReader reader, List<ImportWarning> warnings,
                                                 PlantDatabase db) throws IOException {
        boolean imported = false;
        reader.beginArray();
        int index = 1;
        while (reader.hasNext()) {
            String speciesKey = null;
            String commonName = null;
            String scientificName = null;
            String categoryRaw = null;
            SpeciesTarget.StageTarget seedling = null;
            SpeciesTarget.StageTarget vegetative = null;
            SpeciesTarget.StageTarget flower = null;
            SpeciesTarget.WateringInfo watering = null;
            String tolerance = null;
            SpeciesTarget.FloatRange temperature = null;
            SpeciesTarget.FloatRange humidity = null;
            String growthHabit = null;
            Boolean toxicToPets = null;
            List<String> careTips = null;
            List<String> sources = null;
            String legacySource = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "speciesKey":
                        speciesKey = readOptionalString(reader);
                        break;
                    case "commonName":
                        commonName = readOptionalString(reader);
                        break;
                    case "scientificName":
                        scientificName = readOptionalString(reader);
                        break;
                    case "category":
                        categoryRaw = readOptionalString(reader);
                        break;
                    case "seedling":
                        seedling = readStage(reader);
                        break;
                    case "vegetative":
                        vegetative = readStage(reader);
                        break;
                    case "flower":
                        flower = readStage(reader);
                        break;
                    case "watering":
                        watering = readWatering(reader);
                        break;
                    case "tolerance":
                        tolerance = readOptionalString(reader);
                        break;
                    case "temperature":
                        temperature = readRange(reader);
                        break;
                    case "humidity":
                        humidity = readRange(reader);
                        break;
                    case "growthHabit":
                        growthHabit = readOptionalString(reader);
                        break;
                    case "toxicToPets":
                        toxicToPets = readNullableBoolean(reader);
                        break;
                    case "careTips":
                        careTips = readStringArray(reader);
                        break;
                    case "sources":
                        sources = readStringArray(reader);
                        break;
                    case "source":
                        legacySource = readOptionalString(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            String normalizedKey = speciesKey != null ? speciesKey.trim() : null;
            if (normalizedKey == null || normalizedKey.isEmpty()) {
                warnings.add(new ImportWarning("species targets", index, "malformed row"));
            } else {
                SpeciesTarget.Category category = parseCategory(categoryRaw);
                SpeciesTarget.WateringInfo wateringInfo = mergeWateringTolerance(watering, tolerance);
                List<String> mergedSources = mergeSources(sources, legacySource);
                SpeciesTarget target = new SpeciesTarget(normalizedKey,
                    commonName,
                    scientificName,
                    category,
                    seedling,
                    vegetative,
                    flower,
                    wateringInfo,
                    temperature,
                    humidity,
                    growthHabit,
                    toxicToPets,
                    careTips,
                    mergedSources);
                SpeciesTarget entity = PlantProfile.fromTarget(target);
                db.speciesTargetDao().insert(entity);
                imported = true;
            }
            index++;
        }
        reader.endArray();
        return new ParseResult(imported, index - 1);
    }

    private SpeciesTarget.StageTarget readStage(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        Float ppfdMin = null;
        Float ppfdMax = null;
        Float dliMin = null;
        Float dliMax = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            switch (field) {
                case "ppfdMin":
                    ppfdMin = readNullableFloat(reader);
                    break;
                case "ppfdMax":
                    ppfdMax = readNullableFloat(reader);
                    break;
                case "dliMin":
                    dliMin = readNullableFloat(reader);
                    break;
                case "dliMax":
                    dliMax = readNullableFloat(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        if (ppfdMin == null && ppfdMax == null && dliMin == null && dliMax == null) {
            return null;
        }
        return new SpeciesTarget.StageTarget(ppfdMin, ppfdMax, dliMin, dliMax);
    }

    private String readOptionalString(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return reader.nextString();
    }

    private boolean isNullOrEmpty(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private SpeciesTarget.WateringInfo readWatering(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        String schedule = null;
        String soil = null;
        String frequency = null;
        String soilType = null;
        String tolerance = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            switch (field) {
                case "schedule":
                    schedule = readOptionalString(reader);
                    break;
                case "frequency":
                    frequency = readOptionalString(reader);
                    break;
                case "soil":
                    soil = readOptionalString(reader);
                    break;
                case "soilType":
                    soilType = readOptionalString(reader);
                    break;
                case "tolerance":
                    tolerance = readOptionalString(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        String resolvedFrequency = !isNullOrEmpty(frequency) ? frequency : schedule;
        String resolvedSoil = !isNullOrEmpty(soilType) ? soilType : soil;
        if ((resolvedFrequency == null || resolvedFrequency.isEmpty())
            && (resolvedSoil == null || resolvedSoil.isEmpty())
            && (tolerance == null || tolerance.isEmpty())) {
            return null;
        }
        return new SpeciesTarget.WateringInfo(resolvedFrequency, resolvedSoil, tolerance);
    }

    private SpeciesTarget.FloatRange readRange(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        Float min = null;
        Float max = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            switch (field) {
                case "min":
                    min = readNullableFloat(reader);
                    break;
                case "max":
                    max = readNullableFloat(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        if (min == null && max == null) {
            return null;
        }
        return new SpeciesTarget.FloatRange(min, max);
    }

    private List<String> readStringArray(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            String value = readOptionalString(reader);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        }
        reader.endArray();
        return values;
    }

    private Long readNullableLong(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return reader.nextLong();
    }

    private Float readNullableFloat(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return (float) reader.nextDouble();
    }

    private Boolean readNullableBoolean(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case NULL:
                reader.nextNull();
                return null;
            case BOOLEAN:
                return reader.nextBoolean();
            case STRING: {
                String value = reader.nextString();
                if (value == null) {
                    return null;
                }
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    return null;
                }
                if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed) || "yes".equalsIgnoreCase(trimmed)) {
                    return Boolean.TRUE;
                }
                if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed) || "no".equalsIgnoreCase(trimmed)) {
                    return Boolean.FALSE;
                }
                return Boolean.parseBoolean(trimmed);
            }
            case NUMBER:
                return reader.nextDouble() != 0d;
            default:
                reader.skipValue();
                return null;
        }
    }

    private SpeciesTarget.Category parseCategory(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return SpeciesTarget.Category.OTHER;
        }
        String normalized = value.trim().toUpperCase(Locale.US);
        try {
            return SpeciesTarget.Category.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return SpeciesTarget.Category.OTHER;
        }
    }

    private SpeciesTarget.WateringInfo mergeWateringTolerance(@Nullable SpeciesTarget.WateringInfo info,
                                                              @Nullable String tolerance) {
        String trimmedTolerance = tolerance != null ? tolerance.trim() : null;
        if (trimmedTolerance == null || trimmedTolerance.isEmpty()) {
            return info;
        }
        if (info == null) {
            return new SpeciesTarget.WateringInfo(null, null, trimmedTolerance);
        }
        if (info.getTolerance() == null || info.getTolerance().isEmpty()) {
            info.setTolerance(trimmedTolerance);
        }
        return info;
    }

    private List<String> mergeSources(@Nullable List<String> sources, @Nullable String legacySource) {
        List<String> merged = new ArrayList<>();
        if (sources != null && !sources.isEmpty()) {
            merged.addAll(sources);
        }
        if (legacySource != null) {
            String trimmed = legacySource.trim();
            if (!trimmed.isEmpty()) {
                merged.add(trimmed);
            }
        }
        return merged.isEmpty() ? null : merged;
    }

    private void cleanupUris(List<Uri> uris) {
        for (Uri uri : uris) {
            PhotoManager.deletePhoto(context, uri);
        }
    }

    private ProcessResult processDataFile(File dataFile, boolean isJson, File baseDir,
                                          Mode mode, List<ImportWarning> warnings,
                                          @Nullable ProgressCallback progressCallback,
                                          AtomicInteger progress, AtomicInteger totalSteps) {
        PlantDatabase db = PlantDatabase.getDatabase(context);
        final boolean[] successHolder = {false};
        final ImportError[] errorHolder = {null};
        final List<Runnable> cleanupTasks = new ArrayList<>();
        if (mode == Mode.REPLACE) {
            BulkReadDao bulk = db.bulkDao();
            try {
                for (Plant plant : bulk.getAllPlants()) {
                    final Uri plantPhoto = plant.getPhotoUri();
                    if (plantPhoto != null) {
                        cleanupTasks.add(() -> PhotoManager.deletePhoto(context, plantPhoto));
                    }
                }
                for (DiaryEntry diaryEntry : bulk.getAllDiaryEntries()) {
                    final String diaryPhoto = diaryEntry.getPhotoUri();
                    if (diaryPhoto != null && !diaryPhoto.isEmpty()) {
                        cleanupTasks.add(() -> PhotoManager.deletePhoto(context, diaryPhoto));
                    }
                }
                for (PlantPhoto photo : bulk.getAllPlantPhotos()) {
                    final String galleryPhoto = photo.getUri();
                    if (!galleryPhoto.isEmpty()) {
                        cleanupTasks.add(() -> PhotoManager.deletePhoto(context, galleryPhoto));
                    }
                }
                for (Reminder reminder : bulk.getAllReminders()) {
                    final long reminderId = reminder.getId();
                    cleanupTasks.add(() -> ReminderScheduler.cancelReminder(context, reminderId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to collect cleanup targets", e);
                errorHolder[0] = ImportError.IO_ERROR;
            }
        }
        if (errorHolder[0] == null) {
            try {
                db.runInTransaction(() -> {
                    if (mode == Mode.REPLACE) {
                        try {
                            db.clearAllTables();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to clear database", e);
                            errorHolder[0] = ImportError.IO_ERROR;
                            throw new RuntimeException(e);
                        }
                    }
                    if (errorHolder[0] == null) {
                        try {
                            if (isJson) {
                                try (JsonReader reader = new JsonReader(new InputStreamReader(
                                    new FileInputStream(dataFile), StandardCharsets.UTF_8))) {
                                    ImportError parseResult = parseAndInsertJson(reader, baseDir,
                                        mode, warnings, progressCallback, progress, totalSteps);
                                    if (parseResult != null) {
                                        errorHolder[0] = parseResult;
                                        throw new RuntimeException();
                                    }
                                    successHolder[0] = true;
                                }
                            } else {
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                    new FileInputStream(dataFile), StandardCharsets.UTF_8))) {
                                    ImportError parseResult = parseAndInsert(reader, baseDir, mode,
                                        warnings, progressCallback, progress, totalSteps);
                                    if (parseResult != null) {
                                        errorHolder[0] = parseResult;
                                        throw new RuntimeException();
                                    }
                                    successHolder[0] = true;
                                }
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to parse import file", e);
                            errorHolder[0] = ImportError.IO_ERROR;
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (RuntimeException e) {
                // Transaction failure; errorHolder already set
            }
        }
        if (mode == Mode.REPLACE && successHolder[0] && !cleanupTasks.isEmpty()) {
            List<Runnable> tasks = new ArrayList<>(cleanupTasks);
            Runnable cleanupRunnable = () -> {
                for (Runnable task : tasks) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        Log.w(TAG, "Cleanup task failed", e);
                    }
                }
            };
            try {
                executor.execute(cleanupRunnable);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to schedule cleanup tasks", e);
                cleanupRunnable.run();
            }
        }
        ImportError finalError = errorHolder[0];
        boolean finalSuccess = successHolder[0];
        if (finalError != null) {
            finalSuccess = false;
        }
        return new ProcessResult(finalSuccess, finalError);
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
            Log.e(TAG, "Failed to restore image", e);
            return null;
        }
    }

    /**
     * Import mode determining how incoming data is applied.
     */
    public enum Mode {MERGE, REPLACE}

    /**
     * Possible high-level errors during import.
     */
    public enum ImportError {
        MISSING_VERSION,
        INVALID_VERSION,
        UNSUPPORTED_VERSION,
        NO_DATA,
        IO_ERROR
    }

    /**
     * Sections within an exported CSV file.
     */
    public enum Section {
        NONE(""),
        LED_PROFILES("LedProfiles"),
        PLANTS("Plants"),
        PLANT_PHOTOS("PlantPhotos"),
        SPECIES_TARGETS("SpeciesTargets"),
        MEASUREMENTS("Measurements"),
        ENVIRONMENT_ENTRIES("EnvironmentEntries"),
        DIARY_ENTRIES("DiaryEntries"),
        REMINDERS("Reminders"),
        REMINDER_SUGGESTIONS("ReminderSuggestions");

        private final String header;

        Section(String header) {
            this.header = header;
        }

        @Nullable
        static Section fromHeader(@NonNull String header) {
            for (Section section : values()) {
                if (section.header.equals(header)) {
                    return section;
                }
            }
            return null;
        }

        @NonNull
        String getHeader() {
            return header;
        }
    }

    /**
     * Callback used to signal completion of the import operation.
     */
    public interface Callback {
        void onComplete(boolean success, @Nullable ImportError error,
                        List<ImportWarning> warnings, @NonNull String message);
    }

    /**
     * Callback used to report incremental progress.
     */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }

    private enum ArchiveKind {
        ZIP,
        JSON_ZIP,
        JSON_STREAM
    }

    private static final class ProcessResult {
        final boolean success;
        @Nullable
        final ImportError error;

        ProcessResult(boolean success, @Nullable ImportError error) {
            this.success = success;
            this.error = error;
        }
    }

    @VisibleForTesting
    public interface SectionParser {
        @NonNull
        Section getSection();

        default int estimateWorkUnits(@NonNull SectionChunk chunk) {
            return chunk.getRowCount();
        }

        boolean parseSection(@NonNull SectionChunk chunk,
                             @NonNull SectionContext context) throws IOException;
    }

    /**
     * Information about a skipped row during import.
     */
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

    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        CountingInputStream(@NonNull InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result != -1) {
                count++;
            }
            return result;
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
            int result = super.read(buffer, offset, length);
            if (result > 0) {
                count += result;
            }
            return result;
        }

        long getCount() {
            return count;
        }
    }

    @VisibleForTesting
    public static class SectionContext {
        final ImportManager manager;
        final Mode mode;
        final File baseDir;
        final Map<Long, Long> plantIdMap;
        final Map<Long, Long> ledProfileIdMap;
        final List<ImportWarning> warnings;
        final List<Uri> restoredUris;
        final PlantDatabase db;
        final NumberFormat numberFormat;
        final int version;
        final AtomicBoolean cancelled;

        public SectionContext(@NonNull ImportManager manager,
                              @NonNull Mode mode,
                              @NonNull File baseDir,
                              @NonNull Map<Long, Long> plantIdMap,
                              @NonNull Map<Long, Long> ledProfileIdMap,
                              @NonNull List<ImportWarning> warnings,
                              @NonNull List<Uri> restoredUris,
                              @NonNull PlantDatabase db,
                              @NonNull NumberFormat numberFormat,
                              int version,
                              @NonNull AtomicBoolean cancelled) {
            this.manager = manager;
            this.mode = mode;
            this.baseDir = baseDir;
            this.plantIdMap = plantIdMap;
            this.ledProfileIdMap = ledProfileIdMap;
            this.warnings = warnings;
            this.restoredUris = restoredUris;
            this.db = db;
            this.numberFormat = numberFormat;
            this.version = version;
            this.cancelled = cancelled;
        }
    }

    @VisibleForTesting
    static class SectionRow {
        final String line;
        final int lineNumber;

        SectionRow(@NonNull String line, int lineNumber) {
            this.line = line;
            this.lineNumber = lineNumber;
        }
    }

    @VisibleForTesting
    static class SectionChunk {
        private final Section section;
        private final String header;
        private final List<SectionRow> rows;

        SectionChunk(@NonNull Section section, @NonNull String header,
                     @NonNull List<SectionRow> rows) {
            this.section = section;
            this.header = header;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }

        @NonNull
        Section getSection() {
            return section;
        }

        @NonNull
        String getHeader() {
            return header;
        }

        @NonNull
        List<SectionRow> getRows() {
            return rows;
        }

        int getRowCount() {
            return rows.size();
        }
    }

    @VisibleForTesting
    public static class SectionReader {
        private final BufferedReader reader;
        private final AtomicInteger lineNumber;
        private String pendingHeader;

        public SectionReader(@NonNull BufferedReader reader, @NonNull AtomicInteger lineNumber) {
            this.reader = reader;
            this.lineNumber = lineNumber;
        }

        @Nullable
        public SectionChunk nextSectionChunk(@NonNull ImportManager manager) throws IOException {
            String headerLine;
            if (pendingHeader != null) {
                headerLine = pendingHeader;
                pendingHeader = null;
            } else {
                while ((headerLine = reader.readLine()) != null) {
                    lineNumber.incrementAndGet();
                    if (headerLine.trim().isEmpty()) {
                        continue;
                    }
                    break;
                }
                if (headerLine == null) {
                    return null;
                }
            }
            Section next = manager.readSection(headerLine);
            String headerRow = reader.readLine();
            if (headerRow == null) {
                throw new IOException("Missing header row for section " + headerLine);
            }
            lineNumber.incrementAndGet();
            List<SectionRow> rows = new ArrayList<>();
            SectionRow row;
            while ((row = nextRowInternal()) != null) {
                rows.add(row);
            }
            return new SectionChunk(next, headerLine, rows);
        }

        @Nullable
        private SectionRow nextRowInternal() throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                int currentLine = lineNumber.incrementAndGet();
                if (line.trim().isEmpty()) {
                    continue;
                }
                Section possible = Section.fromHeader(line);
                if (possible != null && possible != Section.NONE) {
                    pendingHeader = line;
                    return null;
                }
                return new SectionRow(line, currentLine);
            }
            return null;
        }
    }

    private final class ArchiveProgressTracker {
        private final long totalBytes;
        private final int archiveSteps;
        private final AtomicInteger overallProgress;
        private final AtomicInteger totalSteps;
        @Nullable
        private final ProgressCallback callback;
        private int lastReported;

        ArchiveProgressTracker(long totalBytes, int archiveSteps, AtomicInteger overallProgress,
                               AtomicInteger totalSteps, @Nullable ProgressCallback callback) {
            this.totalBytes = totalBytes;
            this.archiveSteps = archiveSteps;
            this.overallProgress = overallProgress;
            this.totalSteps = totalSteps;
            this.callback = callback;
            this.lastReported = 0;
        }

        void update(long countedBytes) {
            if (archiveSteps <= 0) {
                return;
            }
            int target;
            if (totalBytes > 0) {
                double ratio = (double) countedBytes / (double) totalBytes;
                if (ratio < 0d) {
                    ratio = 0d;
                } else if (ratio > 1d) {
                    ratio = 1d;
                }
                target = (int) Math.floor(ratio * archiveSteps);
            } else {
                long capped = Math.min(countedBytes, archiveSteps);
                if (capped < 0L) {
                    capped = 0L;
                }
                target = (int) capped;
            }
            publish(target);
        }

        void complete() {
            publish(archiveSteps);
        }

        private void publish(int target) {
            if (target <= lastReported) {
                return;
            }
            int delta = target - lastReported;
            lastReported = target;
            stepProgress(overallProgress, callback, totalSteps.get(), delta);
        }
    }
}
