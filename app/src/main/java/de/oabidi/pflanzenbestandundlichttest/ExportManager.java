package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonWriter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import java.nio.charset.StandardCharsets;

import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.data.util.FileUtils;

/**
 * Manager responsible for exporting measurements and diary entries to a CSV file.
 */
public class ExportManager {
    private static final String TAG = "ExportManager";
    private static final int EXPORT_VERSION = 2;
    private final Context context;
    private final BulkReadDao bulkDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor;

    /** Supported export formats. */
    public enum Format {
        CSV,
        JSON
    }

    /** Callback used to signal completion of the export operation. */
    public interface Callback {
        void onComplete(boolean success);
    }

    /** Callback used to report incremental progress. */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }

    public ExportManager(@NonNull Context context, @NonNull PlantRepository repository) {
        this(context, repository, requireExecutor(context));
    }

    public ExportManager(@NonNull Context context, @NonNull PlantRepository repository,
                         @NonNull ExecutorService executor) {
        this.context = context.getApplicationContext();
        this.bulkDao = repository.bulkDao();
        this.executor = executor;
    }

    private static ExecutorService requireExecutor(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        return ((ExecutorProvider) appContext).getIoExecutor();
    }

    public void export(@NonNull Uri uri, @NonNull Callback callback) {
        exportInternal(uri, -1, Format.CSV, callback, null);
    }

    public void export(@NonNull Uri uri, long plantId, @NonNull Callback callback) {
        exportInternal(uri, plantId, Format.CSV, callback, null);
    }

    public void export(@NonNull Uri uri, @NonNull Callback callback,
                       @Nullable ProgressCallback progressCallback) {
        exportInternal(uri, -1, Format.CSV, callback, progressCallback);
    }

    public void export(@NonNull Uri uri, long plantId, @NonNull Callback callback,
                       @Nullable ProgressCallback progressCallback) {
        exportInternal(uri, plantId, Format.CSV, callback, progressCallback);
    }

    public void exportJson(@NonNull Uri uri, @NonNull Callback callback) {
        exportInternal(uri, -1, Format.JSON, callback, null);
    }

    public void exportJson(@NonNull Uri uri, long plantId, @NonNull Callback callback) {
        exportInternal(uri, plantId, Format.JSON, callback, null);
    }

    public void exportJson(@NonNull Uri uri, @NonNull Callback callback,
                           @Nullable ProgressCallback progressCallback) {
        exportInternal(uri, -1, Format.JSON, callback, progressCallback);
    }

    public void exportJson(@NonNull Uri uri, long plantId, @NonNull Callback callback,
                           @Nullable ProgressCallback progressCallback) {
        exportInternal(uri, plantId, Format.JSON, callback, progressCallback);
    }

    private void exportInternal(@NonNull Uri uri, long plantId, @NonNull Format format,
                                @NonNull Callback callback,
                                @Nullable ProgressCallback progressCallback) {
        executor.execute(() -> {
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

                    if (format == Format.JSON) {
                        writeJson(tempDir, data);
                    } else {
                        writeCsv(tempDir, data);
                    }
                    notifyProgress(progressCallback, progress, totalSteps);

                    zipFiles(uri, tempDir);
                    notifyProgress(progressCallback, progress, totalSteps);

                    success = true;
                } catch (IOException e) {
                    Log.e(TAG, "Export failed", e);
                } finally {
                    FileUtils.deleteRecursive(tempDir);
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
                List<PlantPhoto> photos = bulkDao.getPlantPhotosForPlant(plantId);
                reminders = bulkDao.getRemindersForPlant(plantId);
                List<PlantCalibration> calibrations = bulkDao.getPlantCalibrationsForPlant(plantId);
                List<PlantPhoto> plantPhotos = photos != null ? photos : Collections.emptyList();
                List<PlantPhoto> finalPlantPhotos = plantPhotos;
                return new ExportData(plants, measurements, diaryEntries, reminders,
                    bulkDao.getAllSpeciesTargets(), finalPlantPhotos, calibrations);
            } else {
                plants = bulkDao.getAllPlants();
                measurements = bulkDao.getAllMeasurements();
                diaryEntries = bulkDao.getAllDiaryEntries();
                List<PlantPhoto> plantPhotos = bulkDao.getAllPlantPhotos();
                reminders = bulkDao.getAllReminders();
                return new ExportData(plants, measurements, diaryEntries, reminders,
                    bulkDao.getAllSpeciesTargets(), plantPhotos, bulkDao.getAllPlantCalibrations());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading data", e);
            throw new IOException("Failed to load data", e);
        }
    }

    private File writeCsv(File tempDir, ExportData data) throws IOException {
        File csvFile = new File(tempDir, "data.csv");
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8))) {
            writer.write("Version," + EXPORT_VERSION + "\n\n");
            writer.write("Plants\n");
            writer.write("id,name,description,species,locationHint,acquiredAtEpoch,photoUri\n");
            for (Plant p : data.plants) {
                String photoName = copyPhotoIfPresent(tempDir, "plant_", p.getId(), p.getPhotoUri());
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

            writer.write("\nPlantPhotos\n");
            writer.write("id,plantId,uri,createdAt\n");
            for (PlantPhoto photo : data.plantPhotos) {
                String photoName = "";
                String uriString = photo.getUri();
                if (uriString != null && !uriString.isEmpty()) {
                    Uri photoUri = Uri.parse(uriString);
                    photoName = copyPhotoIfPresent(tempDir, "plant_photo_", photo.getId(), photoUri);
                }
                writer.write(String.format(Locale.US, "%d,%d,%s,%d\n",
                    photo.getId(),
                    photo.getPlantId(),
                    escape(photoName),
                    photo.getCreatedAt()));
            }

            writer.write("\nPlantCalibrations\n");
            writer.write("plantId,ambientFactor,cameraFactor\n");
            for (PlantCalibration calibration : data.calibrations) {
                writer.write(String.format(Locale.US, "%d,%f,%f\n",
                    calibration.getPlantId(),
                    calibration.getAmbientFactor(),
                    calibration.getCameraFactor()));
            }

            writer.write("\nSpeciesTargets\n");
            writer.write("speciesKey,seedlingPpfdMin,seedlingPpfdMax,seedlingDliMin,seedlingDliMax,"
                + "vegetativePpfdMin,vegetativePpfdMax,vegetativeDliMin,vegetativeDliMax,"
                + "flowerPpfdMin,flowerPpfdMax,flowerDliMin,flowerDliMax,tolerance,source\n");
            for (SpeciesTarget t : data.targets) {
                SpeciesTarget.StageTarget seedling = t.getSeedlingStage();
                SpeciesTarget.StageTarget vegetative = t.getVegetativeStage();
                SpeciesTarget.StageTarget flower = t.getFlowerStage();
                writer.write(String.format(Locale.US, "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    escape(t.getSpeciesKey()),
                    formatFloat(seedling != null ? seedling.getPpfdMin() : null),
                    formatFloat(seedling != null ? seedling.getPpfdMax() : null),
                    formatFloat(seedling != null ? seedling.getDliMin() : null),
                    formatFloat(seedling != null ? seedling.getDliMax() : null),
                    formatFloat(vegetative != null ? vegetative.getPpfdMin() : null),
                    formatFloat(vegetative != null ? vegetative.getPpfdMax() : null),
                    formatFloat(vegetative != null ? vegetative.getDliMin() : null),
                    formatFloat(vegetative != null ? vegetative.getDliMax() : null),
                    formatFloat(flower != null ? flower.getPpfdMin() : null),
                    formatFloat(flower != null ? flower.getPpfdMax() : null),
                    formatFloat(flower != null ? flower.getDliMin() : null),
                    formatFloat(flower != null ? flower.getDliMax() : null),
                    escape(t.getTolerance()),
                    escape(t.getSource())));
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
                    photoName = copyPhotoIfPresent(tempDir, "diary_", d.getId(), dUri);
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

    private File writeJson(File tempDir, ExportData data) throws IOException {
        File jsonFile = new File(tempDir, "data.json");
        try (OutputStream os = new FileOutputStream(jsonFile);
             OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             JsonWriter writer = new JsonWriter(osw)) {
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("version").value(EXPORT_VERSION);

            writer.name("plants");
            writer.beginArray();
            for (Plant plant : data.plants) {
                writer.beginObject();
                writer.name("id").value(plant.getId());
                writeString(writer, "name", plant.getName());
                writeString(writer, "description", plant.getDescription());
                writeString(writer, "species", plant.getSpecies());
                writeString(writer, "locationHint", plant.getLocationHint());
                writer.name("acquiredAtEpoch").value(plant.getAcquiredAtEpoch());
                String photoName = copyPhotoIfPresent(tempDir, "plant_", plant.getId(), plant.getPhotoUri());
                writeOptionalString(writer, "photo", photoName);
                writer.endObject();
            }
            writer.endArray();

            writer.name("plantPhotos");
            writer.beginArray();
            for (PlantPhoto photo : data.plantPhotos) {
                writer.beginObject();
                writer.name("id").value(photo.getId());
                writer.name("plantId").value(photo.getPlantId());
                String fileName = "";
                String uriString = photo.getUri();
                if (uriString != null && !uriString.isEmpty()) {
                    fileName = copyPhotoIfPresent(tempDir, "plant_photo_", photo.getId(), Uri.parse(uriString));
                }
                writeOptionalString(writer, "fileName", fileName);
                writer.name("createdAt").value(photo.getCreatedAt());
                writer.endObject();
            }
            writer.endArray();

            writer.name("plantCalibrations");
            writer.beginArray();
            for (PlantCalibration calibration : data.calibrations) {
                writer.beginObject();
                writer.name("plantId").value(calibration.getPlantId());
                writer.name("ambientFactor").value(calibration.getAmbientFactor());
                writer.name("cameraFactor").value(calibration.getCameraFactor());
                writer.endObject();
            }
            writer.endArray();

            writer.name("speciesTargets");
            writer.beginArray();
            for (SpeciesTarget target : data.targets) {
                writer.beginObject();
                writeString(writer, "speciesKey", target.getSpeciesKey());
                writeStage(writer, "seedling", target.getSeedlingStage());
                writeStage(writer, "vegetative", target.getVegetativeStage());
                writeStage(writer, "flower", target.getFlowerStage());
                writeString(writer, "tolerance", target.getTolerance());
                writeString(writer, "source", target.getSource());
                writer.endObject();
            }
            writer.endArray();

            writer.name("measurements");
            writer.beginArray();
            for (Measurement measurement : data.measurements) {
                writer.beginObject();
                writer.name("id").value(measurement.getId());
                writer.name("plantId").value(measurement.getPlantId());
                writer.name("timeEpoch").value(measurement.getTimeEpoch());
                writer.name("luxAvg").value(measurement.getLuxAvg());
                writeOptionalFloat(writer, "ppfd", measurement.getPpfd());
                writeOptionalFloat(writer, "dli", measurement.getDli());
                writeString(writer, "note", measurement.getNote());
                writer.endObject();
            }
            writer.endArray();

            writer.name("diaryEntries");
            writer.beginArray();
            for (DiaryEntry entry : data.diaryEntries) {
                writer.beginObject();
                writer.name("id").value(entry.getId());
                writer.name("plantId").value(entry.getPlantId());
                writer.name("timeEpoch").value(entry.getTimeEpoch());
                writeString(writer, "type", entry.getType());
                writeString(writer, "note", entry.getNote());
                String photoName = "";
                String diaryUri = entry.getPhotoUri();
                if (diaryUri != null && !diaryUri.isEmpty()) {
                    photoName = copyPhotoIfPresent(tempDir, "diary_", entry.getId(), Uri.parse(diaryUri));
                }
                writeOptionalString(writer, "photo", photoName);
                writer.endObject();
            }
            writer.endArray();

            writer.name("reminders");
            writer.beginArray();
            for (Reminder reminder : data.reminders) {
                writer.beginObject();
                writer.name("id").value(reminder.getId());
                writer.name("plantId").value(reminder.getPlantId());
                writer.name("triggerAt").value(reminder.getTriggerAt());
                writeString(writer, "message", reminder.getMessage());
                writer.endObject();
            }
            writer.endArray();

            writer.endObject();
        } catch (IOException e) {
            Log.e(TAG, "Error writing JSON", e);
            throw e;
        }
        return jsonFile;
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

    private String copyPhotoIfPresent(File tempDir, String prefix, long id, @Nullable Uri uri)
        throws IOException {
        if (uri == null) {
            return "";
        }
        String fileName = prefix + id + "_" + getFileName(uri);
        copyUriToFile(uri, new File(tempDir, fileName));
        return fileName;
    }

    private void writeStage(JsonWriter writer, String name, @Nullable SpeciesTarget.StageTarget stage)
        throws IOException {
        writer.name(name);
        if (stage == null) {
            writer.nullValue();
            return;
        }
        writer.beginObject();
        writeOptionalFloat(writer, "ppfdMin", stage.getPpfdMin());
        writeOptionalFloat(writer, "ppfdMax", stage.getPpfdMax());
        writeOptionalFloat(writer, "dliMin", stage.getDliMin());
        writeOptionalFloat(writer, "dliMax", stage.getDliMax());
        writer.endObject();
    }

    private void writeString(JsonWriter writer, String name, @Nullable String value) throws IOException {
        writer.name(name);
        if (value == null) {
            writer.nullValue();
        } else {
            writer.value(value);
        }
    }

    private void writeOptionalString(JsonWriter writer, String name, @Nullable String value)
        throws IOException {
        if (value == null || value.isEmpty()) {
            writer.name(name).nullValue();
        } else {
            writer.name(name).value(value);
        }
    }

    private void writeOptionalFloat(JsonWriter writer, String name, @Nullable Float value)
        throws IOException {
        if (value == null) {
            writer.name(name).nullValue();
        } else {
            writer.name(name).value(value);
        }
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

    private static String formatFloat(@Nullable Float value) {
        return value != null ? Float.toString(value) : "";
    }

    private static class ExportData {
        final List<Plant> plants;
        final List<Measurement> measurements;
        final List<DiaryEntry> diaryEntries;
        final List<Reminder> reminders;
        final List<SpeciesTarget> targets;
        final List<PlantPhoto> plantPhotos;
        final List<PlantCalibration> calibrations;

        ExportData(List<Plant> plants, List<Measurement> measurements, List<DiaryEntry> diaryEntries,
                   List<Reminder> reminders, List<SpeciesTarget> targets, List<PlantPhoto> plantPhotos,
                   List<PlantCalibration> calibrations) {
            this.plants = plants;
            this.measurements = measurements;
            this.diaryEntries = diaryEntries;
            this.reminders = reminders;
            this.targets = targets;
            this.plantPhotos = plantPhotos;
            this.calibrations = calibrations;
        }
    }
}
