package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manager responsible for exporting measurements and diary entries to a CSV file.
 */
public class ExportManager {
    private static final String TAG = "ExportManager";
    private final Context context;
    private final BulkReadDao bulkDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    /** Callback used to signal completion of the export operation. */
    public interface Callback {
        void onComplete(boolean success);
    }

    /** Callback used to report incremental progress. */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }

    public ExportManager(@NonNull Context context, @NonNull PlantRepository repository) {
        this.context = context.getApplicationContext();
        this.bulkDao = repository.bulkDao();
    }

    public void export(@NonNull Uri uri, @NonNull Callback callback) {
        exportInternal(uri, -1, callback, null);
    }

    public void export(@NonNull Uri uri, long plantId, @NonNull Callback callback) {
        exportInternal(uri, plantId, callback, null);
    }

    public void export(@NonNull Uri uri, @NonNull Callback callback,
                       @Nullable ProgressCallback progressCallback) {
        exportInternal(uri, -1, callback, progressCallback);
    }

    public void export(@NonNull Uri uri, long plantId, @NonNull Callback callback,
                       @Nullable ProgressCallback progressCallback) {
        exportInternal(uri, plantId, callback, progressCallback);
    }

    private void exportInternal(@NonNull Uri uri, long plantId, @NonNull Callback callback,
                                @Nullable ProgressCallback progressCallback) {
        ioExecutor.execute(() -> {
            ExportData data;
            try {
                data = loadData(plantId);
            } catch (IOException e) {
                Log.e(TAG, "Export failed", e);
                mainHandler.post(() -> callback.onComplete(false));
                return;
            }

            boolean success = false;
            File tempDir = new File(context.getCacheDir(), "export_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                tempDir = null;
            }

            int totalSteps = 3;
            int[] progress = {0};

            if (tempDir != null) {
                try {
                    notifyProgress(progressCallback, progress, totalSteps);

                    writeCsv(tempDir, data);
                    notifyProgress(progressCallback, progress, totalSteps);

                    zipFiles(uri, tempDir);
                    notifyProgress(progressCallback, progress, totalSteps);

                    success = true;
                } catch (IOException e) {
                    Log.e(TAG, "Export failed", e);
                }
            }

            boolean result = success;
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    private ExportData loadData(long plantId) throws IOException {
        try {
            List<Plant> plants;
            List<Measurement> measurements;
            List<DiaryEntry> diaryEntries;
            List<Reminder> reminders;
            if (plantId >= 0) {
                Plant p = bulkDao.getPlant(plantId);
                plants = p != null ? Collections.singletonList(p) : Collections.emptyList();
                measurements = bulkDao.getMeasurementsForPlant(plantId);
                diaryEntries = bulkDao.getDiaryEntriesForPlant(plantId);
                reminders = bulkDao.getRemindersForPlant(plantId);
            } else {
                plants = bulkDao.getAllPlants();
                measurements = bulkDao.getAllMeasurements();
                diaryEntries = bulkDao.getAllDiaryEntries();
                reminders = bulkDao.getAllReminders();
            }
            List<SpeciesTarget> targets = bulkDao.getAllSpeciesTargets();
            return new ExportData(plants, measurements, diaryEntries, reminders, targets);
        } catch (Exception e) {
            Log.e(TAG, "Error loading data", e);
            throw new IOException("Failed to load data", e);
        }
    }

    private File writeCsv(File tempDir, ExportData data) throws IOException {
        File csvFile = new File(tempDir, "data.csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            writer.write("Version,1\n\n");
            writer.write("Plants\n");
            writer.write("id,name,description,species,locationHint,acquiredAtEpoch,photoUri\n");
            for (Plant p : data.plants) {
                String photoName = "";
                if (p.getPhotoUri() != null) {
                    photoName = "plant_" + p.getId() + "_" + getFileName(p.getPhotoUri());
                    copyUriToFile(p.getPhotoUri(), new File(tempDir, photoName));
                }
                writer.write(String.format(Locale.US,
                    "%d,%s,%s,%s,%s,%d,%s\n",
                    p.getId(),
                    escape(p.getName()),
                    escape(p.getDescription()),
                    escape(p.getSpecies()),
                    escape(p.getLocationHint()),
                    p.getAcquiredAtEpoch(),
                    escape(photoName)));
            }

            writer.write("\nSpeciesTargets\n");
            writer.write("speciesKey,ppfdMin,ppfdMax\n");
            for (SpeciesTarget t : data.targets) {
                writer.write(String.format(Locale.US, "%s,%f,%f\n",
                    escape(t.getSpeciesKey()),
                    t.getPpfdMin(),
                    t.getPpfdMax()));
            }

            writer.write("\nMeasurements\n");
            writer.write("id,plantId,timeEpoch,luxAvg,ppfd\n");
            for (Measurement m : data.measurements) {
                writer.write(String.format(Locale.US, "%d,%d,%d,%f,%s\n",
                    m.getId(),
                    m.getPlantId(),
                    m.getTimeEpoch(),
                    m.getLuxAvg(),
                    m.getPpfd() != null ? Float.toString(m.getPpfd()) : ""));
            }

            writer.write("\nDiaryEntries\n");
            writer.write("id,plantId,timeEpoch,type,note,photoUri\n");
            for (DiaryEntry d : data.diaryEntries) {
                String photoName = "";
                if (d.getPhotoUri() != null && !d.getPhotoUri().isEmpty()) {
                    Uri dUri = Uri.parse(d.getPhotoUri());
                    photoName = "diary_" + d.getId() + "_" + getFileName(dUri);
                    copyUriToFile(dUri, new File(tempDir, photoName));
                }
                writer.write(String.format(Locale.US, "%d,%d,%d,%s,%s,%s\n",
                    d.getId(),
                    d.getPlantId(),
                    d.getTimeEpoch(),
                    escape(d.getType()),
                    escape(d.getNote()),
                    escape(photoName)));
            }

            writer.write("\nReminders\n");
            writer.write("id,plantId,triggerAt,message\n");
            for (Reminder r : data.reminders) {
                writer.write(String.format(Locale.US, "%d,%d,%d,%s\n",
                    r.getId(),
                    r.getPlantId(),
                    r.getTriggerAt(),
                    escape(r.getMessage())));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing CSV", e);
            throw e;
        }
        return csvFile;
    }

    private void zipFiles(Uri uri, File tempDir) throws IOException {
        try (OutputStream os = context.getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                byte[] buffer = new byte[8192];
                for (File f : files) {
                    try (InputStream fis = new FileInputStream(f)) {
                        zos.putNextEntry(new ZipEntry(f.getName()));
                        int len;
                        while ((len = fis.read(buffer)) != -1) {
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating ZIP", e);
            throw e;
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private void notifyProgress(@Nullable ProgressCallback progressCallback, int[] progress, int totalSteps) {
        progress[0]++;
        if (progressCallback != null) {
            mainHandler.post(() -> progressCallback.onProgress(progress[0], totalSteps));
        }
    }

    private void copyUriToFile(Uri source, File dest) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IOException("Cannot open source URI: " + source);
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }

    private String getFileName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null) {
            name = name.replaceAll("[/:*?\"<>|]", "_");
        }
        return name != null ? name : "image";
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        fileOrDirectory.delete();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            escaped = "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static class ExportData {
        final List<Plant> plants;
        final List<Measurement> measurements;
        final List<DiaryEntry> diaryEntries;
        final List<Reminder> reminders;
        final List<SpeciesTarget> targets;

        ExportData(List<Plant> plants, List<Measurement> measurements, List<DiaryEntry> diaryEntries,
                   List<Reminder> reminders, List<SpeciesTarget> targets) {
            this.plants = plants;
            this.measurements = measurements;
            this.diaryEntries = diaryEntries;
            this.reminders = reminders;
            this.targets = targets;
        }
    }
}
