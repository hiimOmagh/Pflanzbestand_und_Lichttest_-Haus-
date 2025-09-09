package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manager responsible for exporting measurements and diary entries to a CSV file.
 */
public class ExportManager {
    private final Context context;
    private final PlantRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Callback used to signal completion of the export operation. */
    public interface Callback {
        void onComplete(boolean success);
    }

    /** Callback used to report incremental progress. */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }

    public ExportManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.repository = ((PlantApp) this.context).getRepository();
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
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            boolean success = false; // Overall success flag
            File tempDir = new File(context.getCacheDir(), "export_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                tempDir = null;
            }

            final int[] progress = {0}; // Moved to outer scope
            final int[] totalStepsHolder = {0}; // Holder for totalSteps in outer scope

            if (tempDir != null) {
                File csvFile = new File(tempDir, "data.csv");
                boolean csvPhaseOk = false; // Tracks success of CSV writing phase
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
                    List<Plant> plants;
                    List<Measurement> measurements;
                    List<DiaryEntry> diaryEntries;
                    List<Reminder> reminders;
                    if (plantId >= 0) {
                        Plant p = repository.getPlantSync(plantId);
                        plants = p != null ? Collections.singletonList(p) : Collections.emptyList();
                        measurements = repository.getMeasurementsForPlantSync(plantId);
                        diaryEntries = repository.getDiaryEntriesForPlantSync(plantId);
                        reminders = repository.getRemindersForPlantSync(plantId);
                    } else {
                        plants = repository.getAllPlantsSync();
                        measurements = repository.getAllMeasurementsSync();
                        diaryEntries = repository.getAllDiaryEntriesSync();
                        reminders = repository.getAllRemindersSync();
                    }
                    List<SpeciesTarget> targets = repository.getAllSpeciesTargetsSync();

                    int photoCount = 0;
                    for (Plant p : plants) {
                        if (p.getPhotoUri() != null) {
                            photoCount++;
                        }
                    }
                    for (DiaryEntry d : diaryEntries) {
                        if (d.getPhotoUri() != null && !d.getPhotoUri().isEmpty()) {
                            photoCount++;
                        }
                    }
                    totalStepsHolder[0] = 5 + photoCount + 1; // Assign to holder

                    writer.write("Plants\n");
                    writer.write("id,name,description,species,locationHint,acquiredAtEpoch,photoUri\n");
                    for (Plant p : plants) {
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
                    progress[0]++;
                    if (progressCallback != null) {
                        mainHandler.post(() -> progressCallback.onProgress(progress[0], totalStepsHolder[0]));
                    }

                    writer.write("\nSpeciesTargets\n");
                    writer.write("speciesKey,ppfdMin,ppfdMax\n");
                    for (SpeciesTarget t : targets) {
                        writer.write(String.format(Locale.US, "%s,%f,%f\n",
                            escape(t.getSpeciesKey()),
                            t.getPpfdMin(),
                            t.getPpfdMax()));
                    }
                    progress[0]++;
                    if (progressCallback != null) {
                        mainHandler.post(() -> progressCallback.onProgress(progress[0], totalStepsHolder[0]));
                    }

                    writer.write("\nMeasurements\n");
                    writer.write("id,plantId,timeEpoch,luxAvg,ppfd\n"); // Corrected header
                    for (Measurement m : measurements) {
                        writer.write(String.format(Locale.US, "%d,%d,%d,%f,%s\n",
                            m.getId(),
                            m.getPlantId(),
                            m.getTimeEpoch(),
                            m.getLuxAvg(),
                            m.getPpfd() != null ? Float.toString(m.getPpfd()) : ""
                        ));
                    }
                    progress[0]++;
                    if (progressCallback != null) {
                        mainHandler.post(() -> progressCallback.onProgress(progress[0], totalStepsHolder[0]));
                    }

                    writer.write("\nDiaryEntries\n");
                    writer.write("id,plantId,timeEpoch,type,note,photoUri\n");
                    for (DiaryEntry d : diaryEntries) {
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
                    progress[0]++;
                    if (progressCallback != null) {
                        mainHandler.post(() -> progressCallback.onProgress(progress[0], totalStepsHolder[0]));
                    }

                    writer.write("\nReminders\n");
                    writer.write("id,plantId,triggerAt,message\n");
                    for (Reminder r : reminders) {
                        writer.write(String.format(Locale.US, "%d,%d,%d,%s\n",
                            r.getId(),
                            r.getPlantId(),
                            r.getTriggerAt(),
                            escape(r.getMessage())));
                    }
                    writer.flush();
                    progress[0]++;
                    if (progressCallback != null) {
                        mainHandler.post(() -> progressCallback.onProgress(progress[0], totalStepsHolder[0]));
                    }
                    csvPhaseOk = true; // CSV writing successful
                } catch (IOException e) {
                    // csvPhaseOk remains false, error logged if necessary
                }

                if (csvPhaseOk) {
                    boolean zipPhaseOk = false; // Tracks success of zipping phase
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
                                progress[0]++; // Now in scope
                                if (progressCallback != null) {
                                    mainHandler.post(() -> progressCallback.onProgress(progress[0], totalStepsHolder[0])); // Now in scope
                                }
                            }
                        }
                        zipPhaseOk = true; // Zipping successful
                    } catch (IOException e) {
                        // zipPhaseOk remains false, error logged if necessary
                    } finally {
                        deleteRecursive(tempDir); // Delete tempDir after zipping attempt
                    }
                    success = zipPhaseOk; // Overall success depends on zipping success
                } else {
                    // CSV phase failed
                    if (tempDir.exists()) { // tempDir might exist even if CSV writing failed partway
                        deleteRecursive(tempDir);
                    }
                    success = false; // Ensure overall success is false
                }
            } else { // tempDir creation failed
                success = false;
            }
            final boolean result = success;
            mainHandler.post(() -> callback.onComplete(result));
        });
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
        // Basic sanitization, consider more robust solution if needed for security
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
}
