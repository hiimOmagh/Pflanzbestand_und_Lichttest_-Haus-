package de.oabidi.pflanzenbestandundlichttest.data.util;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicBoolean;

import de.oabidi.pflanzenbestandundlichttest.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.Reminder;
import de.oabidi.pflanzenbestandundlichttest.ReminderScheduler;

/**
 * Manager responsible for importing measurements and diary entries from a CSV file.
 */
public class ImportManager {
    private static final String TAG = "ImportManager";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Callback used to signal completion of the import operation. */
    public interface Callback {
        void onComplete(boolean success, boolean hadWarnings);
    }

    /** Import mode determining how incoming data is applied. */
    public enum Mode { MERGE, REPLACE }

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
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            boolean success = false;
            AtomicBoolean warning = new AtomicBoolean(false);
            File tempDir = new File(context.getCacheDir(), "import_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                tempDir = null;
            }
            if (tempDir != null) {
                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    ZipEntry entry;
                    File csvFile = null;
                    byte[] buffer = new byte[8192];
                    while ((entry = zis.getNextEntry()) != null) {
                        File outFile = new File(tempDir, entry.getName());
                        try (OutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        if (entry.getName().endsWith(".csv")) {
                            csvFile = outFile;
                        }
                        zis.closeEntry();
                    }
                    if (csvFile != null) {
                        if (mode == Mode.REPLACE) {
                            PlantDatabase.getDatabase(context).clearAllTables();
                        }
                        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                            success = parseAndInsert(reader, tempDir, mode, warning);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to open import file", e);
                    success = false;
                } finally {
                    deleteRecursive(tempDir);
                }
            }
            final boolean result = success;
            final boolean hadWarnings = warning.get();
            mainHandler.post(() -> callback.onComplete(result, hadWarnings));
        });
    }

    private boolean parseAndInsert(BufferedReader reader, File baseDir, Mode mode,
                                   AtomicBoolean warning) throws IOException {
        PlantDatabase db = PlantDatabase.getDatabase(context);
        Map<Long, Long> plantIdMap = new HashMap<>();
        final boolean[] importedAny = {false};
        final NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        try {
            db.runInTransaction(() -> {
                try {
                    String line;
                    Section section = Section.NONE;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        switch (line) {
                            case "Plants":
                                section = Section.PLANTS;
                                reader.readLine(); // skip header
                                continue;
                            case "SpeciesTargets":
                                section = Section.SPECIES_TARGETS;
                                reader.readLine(); // skip header
                                continue;
                            case "Measurements":
                                section = Section.MEASUREMENTS;
                                reader.readLine(); // skip header
                                continue;
                            case "DiaryEntries":
                                section = Section.DIARY;
                                reader.readLine(); // skip header
                                continue;
                            case "Reminders":
                                section = Section.REMINDERS;
                                reader.readLine(); // skip header
                                continue;
                        }
                        List<String> parts = parseCsv(line);
                        if (section == Section.PLANTS) {
                            if (parts.size() >= 7) {
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
                                    } else {
                                        warning.set(true);
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
                                importedAny[0] = true;
                            } else {
                                Log.e(TAG, "Malformed plant row: " + line);
                                throw new RuntimeException("Malformed plant row");
                            }
                        } else if (section == Section.SPECIES_TARGETS) {
                            if (parts.size() >= 3) {
                                String speciesKey = parts.get(0);
                                try {
                                    float ppfdMin = nf.parse(parts.get(1)).floatValue();
                                    float ppfdMax = nf.parse(parts.get(2)).floatValue();
                                    SpeciesTarget t = new SpeciesTarget(speciesKey, ppfdMin, ppfdMax);
                                    db.speciesTargetDao().insert(t);
                                    importedAny[0] = true;
                                } catch (ParseException e) {
                                    Log.e(TAG, "Malformed species target row: " + line, e);
                                    throw new RuntimeException("Malformed species target row", e);
                                }
                            } else {
                                Log.e(TAG, "Malformed species target row: " + line);
                                throw new RuntimeException("Malformed species target row");
                            }
                        } else if (section == Section.MEASUREMENTS) {
                            if (parts.size() >= 6) {
                                long plantId = Long.parseLong(parts.get(1));
                                if (mode == Mode.MERGE) {
                                    Long mappedId = plantIdMap.get(plantId);
                                    if (mappedId == null) {
                                        Log.w(TAG, "Skipping measurement for missing plant " + plantId);
                                        continue;
                                    }
                                    plantId = mappedId;
                                }
                                long timeEpoch = Long.parseLong(parts.get(2));
                                try {
                                    float luxAvg = nf.parse(parts.get(3)).floatValue();
                                    float ppfd = nf.parse(parts.get(4)).floatValue();
                                    float dli = nf.parse(parts.get(5)).floatValue();
                                    Measurement m = new Measurement(plantId, timeEpoch, luxAvg, ppfd, dli);
                                    db.measurementDao().insert(m);
                                    importedAny[0] = true;
                                } catch (ParseException e) {
                                    Log.e(TAG, "Malformed measurement row: " + line, e);
                                    throw new RuntimeException("Malformed measurement row", e);
                                }
                            } else {
                                Log.e(TAG, "Malformed measurement row: " + line);
                                throw new RuntimeException("Malformed measurement row");
                            }
                        } else if (section == Section.DIARY) {
                            if (parts.size() >= 6) {
                                long plantId = Long.parseLong(parts.get(1));
                                if (mode == Mode.MERGE) {
                                    Long mappedId = plantIdMap.get(plantId);
                                    if (mappedId == null) {
                                        Log.w(TAG, "Skipping diary entry for missing plant " + plantId);
                                        continue;
                                    }
                                    plantId = mappedId;
                                }
                                long timeEpoch = Long.parseLong(parts.get(2));
                                String type = parts.get(3);
                                String note = parts.get(4);
                                String photoUri = parts.get(5);
                                DiaryEntry d = new DiaryEntry(plantId, timeEpoch, type, note);
                                if (!photoUri.isEmpty()) {
                                    Uri restored = restoreImage(new File(baseDir, photoUri));
                                    if (restored != null) {
                                        d.setPhotoUri(restored.toString());
                                    } else {
                                        warning.set(true);
                                    }
                                }
                                db.diaryDao().insert(d);
                                importedAny[0] = true;
                            } else {
                                Log.e(TAG, "Malformed diary row: " + line);
                                throw new RuntimeException("Malformed diary row");
                            }
                        } else if (section == Section.REMINDERS) {
                            if (parts.size() >= 3) {
                                long id = Long.parseLong(parts.get(0));
                                long triggerAt = Long.parseLong(parts.get(1));
                                String message = parts.get(2);
                                Reminder r = new Reminder(triggerAt, message);
                                long reminderId;
                                if (mode == Mode.MERGE) {
                                    reminderId = db.reminderDao().insert(r);
                                } else {
                                    r.setId(id);
                                    db.reminderDao().insert(r);
                                    reminderId = id;
                                }
                                ReminderScheduler.scheduleReminderAt(context, triggerAt, message, reminderId);
                                importedAny[0] = true;
                            } else {
                                Log.e(TAG, "Malformed reminder row: " + line);
                                throw new RuntimeException("Malformed reminder row");
                            }
                        }
                    }
                } catch (IOException | ParseException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            Log.e(TAG, "Failed to parse import", e);
            return false;
        }
        return importedAny[0];
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

    private Uri restoreImage(File exportedImage) {
        try {
            File imagesDir = new File(context.getFilesDir(), "imported_images");
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                throw new IOException("Unable to create destination directory");
            }
            File destFile = new File(imagesDir,
                "imported_" + System.currentTimeMillis() + "_" + exportedImage.getName());
            try (InputStream in = new FileInputStream(exportedImage);
                 OutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            return Uri.fromFile(destFile);
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
    
    private enum Section {
        NONE,
        PLANTS,
        SPECIES_TARGETS,
        MEASUREMENTS,
        DIARY,
        REMINDERS
    }
}
