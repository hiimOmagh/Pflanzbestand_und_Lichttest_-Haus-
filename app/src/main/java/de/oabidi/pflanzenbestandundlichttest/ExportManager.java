package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    public ExportManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.repository = ((PlantApp) this.context).getRepository();
    }

    /**
     * Exports all measurements and diary entries to the given destination URI.
     *
     * @param uri      destination chosen by the user
     * @param callback invoked on the main thread with the result
     */
    public void export(@NonNull Uri uri, @NonNull Callback callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            boolean success = false;
            File tempDir = new File(context.getCacheDir(), "export_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                tempDir = null;
            }
            if (tempDir != null) {
                File csvFile = new File(tempDir, "data.csv");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
                    List<Plant> plants = repository.getAllPlantsSync();
                    List<SpeciesTarget> targets = repository.getAllSpeciesTargetsSync();
                    List<Measurement> measurements = repository.getAllMeasurementsSync();
                    List<DiaryEntry> diaryEntries = repository.getAllDiaryEntriesSync();
                    List<Reminder> reminders = repository.getAllRemindersSync();

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

                    writer.write("\nSpeciesTargets\n");
                    writer.write("speciesKey,ppfdMin,ppfdMax\n");
                    for (SpeciesTarget t : targets) {
                        writer.write(String.format(Locale.US, "%s,%f,%f\n",
                            escape(t.getSpeciesKey()),
                            t.getPpfdMin(),
                            t.getPpfdMax()));
                    }

                    writer.write("\nMeasurements\n");
                    writer.write("id,plantId,timeEpoch,luxAvg,ppfd,dli\n");
                    for (Measurement m : measurements) {
                        writer.write(String.format(Locale.US, "%d,%d,%d,%f,%f,%f\n",
                            m.getId(),
                            m.getPlantId(),
                            m.getTimeEpoch(),
                            m.getLuxAvg(),
                            m.getPpfd(),
                            m.getDli()));
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
                } catch (IOException e) {
                    success = false;
                }

                if (!success) {
                    deleteRecursive(tempDir);
                    final boolean result = false;
                    mainHandler.post(() -> callback.onComplete(result));
                    return;
                }

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
                    success = true;
                } catch (IOException e) {
                    success = false;
                } finally {
                    deleteRecursive(tempDir);
                }
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
        return name != null ? name : "image";
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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n")) {
            escaped = "\"" + escaped + "\"";
        }
        return escaped;
    }
}
