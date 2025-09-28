package de.oabidi.pflanzenbestandundlichttest.core.system;

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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import java.nio.charset.StandardCharsets;

import de.oabidi.pflanzenbestandundlichttest.core.data.db.BulkReadDao;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.core.data.db.Converters;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Measurement;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.data.util.FileUtils;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.Reminder;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderSuggestion;

/**
 * Manager responsible for exporting measurements and diary entries to a CSV file.
 */
public class ExportManager {
    private static final String TAG = "ExportManager";
    private static final int EXPORT_VERSION = 4;
    private static final String ENVIRONMENT_PHOTO_PREFIX = "environment_";
    private static final String ENVIRONMENT_CSV_HEADER =
        "id,plantId,timestamp,temperature,humidity,soilMoisture,height,width,naturalDli,artificialDli,artificialHours,notes,photo";
    private final Context context;
    private final BulkReadDao bulkDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor;

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

    private static String formatBoolean(@Nullable Boolean value) {
        return value != null ? Boolean.toString(value) : "";
    }

    private static boolean isNullOrEmpty(@Nullable String value) {
        return value == null || value.trim().isEmpty();
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
            List<EnvironmentEntry> environmentEntries;
            List<ReminderSuggestion> reminderSuggestions;
            List<LedProfile> ledProfiles;
            if (plantId >= 0) {
                Plant p = bulkDao.getPlant(plantId);
                plants = p != null ? Collections.singletonList(p) : Collections.emptyList();
                measurements = bulkDao.getMeasurementsForPlant(plantId);
                diaryEntries = bulkDao.getDiaryEntriesForPlant(plantId);
                List<PlantPhoto> photos = bulkDao.getPlantPhotosForPlant(plantId);
                reminders = bulkDao.getRemindersForPlant(plantId);
                environmentEntries = bulkDao.getEnvironmentEntriesForPlant(plantId);
                ReminderSuggestion suggestion = bulkDao.getReminderSuggestionForPlant(plantId);
                reminderSuggestions = suggestion != null
                    ? Collections.singletonList(suggestion)
                    : Collections.emptyList();
                LedProfile profile = null;
                if (p != null && p.getLedProfileId() != null) {
                    profile = bulkDao.getLedProfile(p.getLedProfileId());
                }
                ledProfiles = profile != null
                    ? Collections.singletonList(profile)
                    : Collections.emptyList();
                return new ExportData(plants, measurements, diaryEntries, reminders,
                    bulkDao.getAllSpeciesTargets(), photos != null ? photos : Collections.emptyList(), ledProfiles,
                    environmentEntries != null ? environmentEntries : Collections.emptyList(), reminderSuggestions);
            } else {
                plants = bulkDao.getAllPlants();
                measurements = bulkDao.getAllMeasurements();
                diaryEntries = bulkDao.getAllDiaryEntries();
                List<PlantPhoto> plantPhotos = bulkDao.getAllPlantPhotos();
                reminders = bulkDao.getAllReminders();
                reminderSuggestions = bulkDao.getAllReminderSuggestions();
                ledProfiles = bulkDao.getAllLedProfiles();
                return new ExportData(plants, measurements, diaryEntries, reminders,
                    bulkDao.getAllSpeciesTargets(), plantPhotos, ledProfiles,
                    bulkDao.getAllEnvironmentEntries(), reminderSuggestions);
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
            writer.write("LedProfiles\n");
            writer.write("id,name,type,mountingDistanceCm,ambientFactor,cameraFactor\n");
            for (LedProfile profile : data.ledProfiles) {
                Map<String, Float> factors = profile.getCalibrationFactors();
                Float ambient = factors.get(LedProfile.CALIBRATION_KEY_AMBIENT);
                Float camera = factors.get(LedProfile.CALIBRATION_KEY_CAMERA);
                writer.write(String.format(Locale.US, "%d,%s,%s,%s,%s,%s\n",
                    profile.getId(),
                    escape(profile.getName()),
                    escape(profile.getType()),
                    formatFloat(profile.getMountingDistanceCm()),
                    formatFloat(ambient),
                    formatFloat(camera)));
            }

            writer.write("\nPlants\n");
            writer.write("id,name,description,species,locationHint,acquiredAtEpoch,photoUri,ledProfileId\n");
            for (Plant p : data.plants) {
                String photoName = copyPhotoIfPresent(tempDir, "plant_", p.getId(), p.getPhotoUri());
                Long ledProfileId = p.getLedProfileId();
                writer.write(String.format(Locale.US,
                    "%d,%s,%s,%s,%s,%d,%s,%s\n",
                    p.getId(),
                    escape(p.getName()),
                    escape(p.getDescription()),
                    escape(p.getSpecies()),
                    escape(p.getLocationHint()),
                    p.getAcquiredAtEpoch(),
                    escape(photoName),
                    ledProfileId != null ? ledProfileId.toString() : ""));
            }

            writer.write("\nPlantPhotos\n");
            writer.write("id,plantId,uri,createdAt\n");
            for (PlantPhoto photo : data.plantPhotos) {
                String photoName = "";
                String uriString = photo.getUri();
                if (!uriString.isEmpty()) {
                    Uri photoUri = Uri.parse(uriString);
                    photoName = copyPhotoIfPresent(tempDir, "plant_photo_", photo.getId(), photoUri);
                }
                writer.write(String.format(Locale.US, "%d,%d,%s,%d\n",
                    photo.getId(),
                    photo.getPlantId(),
                    escape(photoName),
                    photo.getCreatedAt()));
            }

            writer.write("\nSpeciesTargets\n");
            writer.write("speciesKey,commonName,scientificName,category,seedlingPpfdMin,seedlingPpfdMax,seedlingDliMin,seedlingDliMax,"
                + "vegetativePpfdMin,vegetativePpfdMax,vegetativeDliMin,vegetativeDliMax,"
                + "flowerPpfdMin,flowerPpfdMax,flowerDliMin,flowerDliMax,"
                + "wateringFrequency,wateringSoilType,wateringTolerance,"
                + "temperatureMin,temperatureMax,humidityMin,humidityMax,growthHabit,toxicToPets,careTips,sources\n");
            for (SpeciesTarget t : data.targets) {
                SpeciesTarget.StageTarget seedling = t.getSeedlingStage();
                SpeciesTarget.StageTarget vegetative = t.getVegetativeStage();
                SpeciesTarget.StageTarget flower = t.getFlowerStage();
                SpeciesTarget.WateringInfo watering = t.getWateringInfo();
                SpeciesTarget.FloatRange temperature = t.getTemperatureRange();
                SpeciesTarget.FloatRange humidity = t.getHumidityRange();
                String[] columns = new String[]{
                    escape(t.getSpeciesKey()),
                    escape(t.getCommonName()),
                    escape(t.getScientificName()),
                    escape(t.getCategory().name()),
                    escape(formatFloat(seedling != null ? seedling.getPpfdMin() : null)),
                    escape(formatFloat(seedling != null ? seedling.getPpfdMax() : null)),
                    escape(formatFloat(seedling != null ? seedling.getDliMin() : null)),
                    escape(formatFloat(seedling != null ? seedling.getDliMax() : null)),
                    escape(formatFloat(vegetative != null ? vegetative.getPpfdMin() : null)),
                    escape(formatFloat(vegetative != null ? vegetative.getPpfdMax() : null)),
                    escape(formatFloat(vegetative != null ? vegetative.getDliMin() : null)),
                    escape(formatFloat(vegetative != null ? vegetative.getDliMax() : null)),
                    escape(formatFloat(flower != null ? flower.getPpfdMin() : null)),
                    escape(formatFloat(flower != null ? flower.getPpfdMax() : null)),
                    escape(formatFloat(flower != null ? flower.getDliMin() : null)),
                    escape(formatFloat(flower != null ? flower.getDliMax() : null)),
                    escape(watering != null ? watering.getFrequency() : null),
                    escape(watering != null ? watering.getSoilType() : null),
                    escape(watering != null ? watering.getTolerance() : null),
                    escape(formatFloat(temperature != null ? temperature.getMin() : null)),
                    escape(formatFloat(temperature != null ? temperature.getMax() : null)),
                    escape(formatFloat(humidity != null ? humidity.getMin() : null)),
                    escape(formatFloat(humidity != null ? humidity.getMax() : null)),
                    escape(t.getGrowthHabit()),
                    escape(formatBoolean(t.getToxicToPets())),
                    escape(Converters.fromStringListToJson(t.getCareTips())),
                    escape(Converters.fromStringListToJson(t.getSources()))
                };
                writer.write(String.join(",", columns));
                writer.write("\n");
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

            writer.write("\nEnvironmentEntries\n");
            writer.write(ENVIRONMENT_CSV_HEADER);
            writer.write("\n");
            for (EnvironmentEntry entry : data.environmentEntries) {
                writer.write(buildEnvironmentEntryCsvRow(tempDir, entry));
                writer.write("\n");
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

            writer.write("\nReminderSuggestions\n");
            writer.write("plantId,suggestedIntervalDays,lastEvaluatedAt,confidenceScore,explanation\n");
            for (ReminderSuggestion suggestion : data.reminderSuggestions) {
                writer.write(String.format(Locale.US, "%d,%d,%d,%s,%s\n",
                    suggestion.getPlantId(),
                    suggestion.getSuggestedIntervalDays(),
                    suggestion.getLastEvaluatedAt(),
                    escape(formatFloat(suggestion.getConfidenceScore())),
                    escape(suggestion.getExplanation())));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing CSV", e);
            throw e;
        }
        return csvFile;
    }

    private void writeJson(File tempDir, ExportData data) throws IOException {
        File jsonFile = new File(tempDir, "data.json");
        try (OutputStream os = new FileOutputStream(jsonFile);
             OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             JsonWriter writer = new JsonWriter(osw)) {
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("version").value(EXPORT_VERSION);

            writer.name("ledProfiles");
            writer.beginArray();
            for (LedProfile profile : data.ledProfiles) {
                writer.beginObject();
                writer.name("id").value(profile.getId());
                writeString(writer, "name", profile.getName());
                writeOptionalString(writer, "type", profile.getType());
                writeOptionalFloat(writer, "mountingDistanceCm", profile.getMountingDistanceCm());
                writer.name("calibrationFactors");
                writer.beginObject();
                for (Map.Entry<String, Float> entry : profile.getCalibrationFactors().entrySet()) {
                    String key = entry.getKey();
                    Float value = entry.getValue();
                    if (key != null && value != null) {
                        writer.name(key).value(value);
                    }
                }
                writer.endObject();
                writer.endObject();
            }
            writer.endArray();

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
                writeOptionalLong(writer, "ledProfileId", plant.getLedProfileId());
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
                if (!uriString.isEmpty()) {
                    fileName = copyPhotoIfPresent(tempDir, "plant_photo_", photo.getId(), Uri.parse(uriString));
                }
                writeOptionalString(writer, "fileName", fileName);
                writer.name("createdAt").value(photo.getCreatedAt());
                writer.endObject();
            }
            writer.endArray();

            writer.name("speciesTargets");
            writer.beginArray();
            for (SpeciesTarget target : data.targets) {
                writer.beginObject();
                writeString(writer, "speciesKey", target.getSpeciesKey());
                writeString(writer, "commonName", target.getCommonName());
                writeString(writer, "scientificName", target.getScientificName());
                writer.name("category").value(target.getCategory().name());
                writeStage(writer, "seedling", target.getSeedlingStage());
                writeStage(writer, "vegetative", target.getVegetativeStage());
                writeStage(writer, "flower", target.getFlowerStage());
                writeWatering(writer, target.getWateringInfo());
                writeRange(writer, "temperature", target.getTemperatureRange());
                writeRange(writer, "humidity", target.getHumidityRange());
                writeString(writer, "growthHabit", target.getGrowthHabit());
                writeOptionalBoolean(writer, target.getToxicToPets());
                writeStringArray(writer, "careTips", target.getCareTips());
                writeStringArray(writer, "sources", target.getSources());
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

            writer.name("environmentEntries");
            writer.beginArray();
            for (EnvironmentEntry entry : data.environmentEntries) {
                writeEnvironmentEntry(writer, tempDir, entry);
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

            writer.name("reminderSuggestions");
            writer.beginArray();
            for (ReminderSuggestion suggestion : data.reminderSuggestions) {
                writer.beginObject();
                writer.name("plantId").value(suggestion.getPlantId());
                writer.name("suggestedIntervalDays").value(suggestion.getSuggestedIntervalDays());
                writer.name("lastEvaluatedAt").value(suggestion.getLastEvaluatedAt());
                writer.name("confidenceScore").value(suggestion.getConfidenceScore());
                writeOptionalString(writer, "explanation", suggestion.getExplanation());
                writer.endObject();
            }
            writer.endArray();

            writer.endObject();
        } catch (IOException e) {
            Log.e(TAG, "Error writing JSON", e);
            throw e;
        }
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

    private String buildEnvironmentEntryCsvRow(File tempDir, EnvironmentEntry entry) throws IOException {
        String photoName = copyEnvironmentPhoto(tempDir, entry);
        String[] columns = new String[]{
            Long.toString(entry.getId()),
            Long.toString(entry.getPlantId()),
            Long.toString(entry.getTimestamp()),
            formatFloat(entry.getTemperature()),
            formatFloat(entry.getHumidity()),
            formatFloat(entry.getSoilMoisture()),
            formatFloat(entry.getHeight()),
            formatFloat(entry.getWidth()),
            formatFloat(entry.getNaturalDli()),
            formatFloat(entry.getArtificialDli()),
            formatFloat(entry.getArtificialHours()),
            escape(entry.getNotes()),
            escape(photoName)
        };
        return String.join(",", columns);
    }

    private void writeEnvironmentEntry(JsonWriter writer, File tempDir, EnvironmentEntry entry) throws IOException {
        writer.beginObject();
        writer.name("id").value(entry.getId());
        writer.name("plantId").value(entry.getPlantId());
        writer.name("timestamp").value(entry.getTimestamp());
        writeOptionalFloat(writer, "temperature", entry.getTemperature());
        writeOptionalFloat(writer, "humidity", entry.getHumidity());
        writeOptionalFloat(writer, "soilMoisture", entry.getSoilMoisture());
        writeOptionalFloat(writer, "height", entry.getHeight());
        writeOptionalFloat(writer, "width", entry.getWidth());
        writeOptionalFloat(writer, "naturalDli", entry.getNaturalDli());
        writeOptionalFloat(writer, "artificialDli", entry.getArtificialDli());
        writeOptionalFloat(writer, "artificialHours", entry.getArtificialHours());
        writeString(writer, "notes", entry.getNotes());
        writeOptionalString(writer, "photo", copyEnvironmentPhoto(tempDir, entry));
        writer.endObject();
    }

    private String copyEnvironmentPhoto(File tempDir, EnvironmentEntry entry) throws IOException {
        String uriString = entry.getPhotoUri();
        if (uriString == null || uriString.isEmpty()) {
            return "";
        }
        return copyPhotoIfPresent(tempDir, ENVIRONMENT_PHOTO_PREFIX, entry.getId(), Uri.parse(uriString));
    }

    private void writeWatering(JsonWriter writer, @Nullable SpeciesTarget.WateringInfo info)
        throws IOException {
        writer.name("watering");
        if (info == null || (isNullOrEmpty(info.getFrequency())
            && isNullOrEmpty(info.getSoilType())
            && isNullOrEmpty(info.getTolerance()))) {
            writer.nullValue();
            return;
        }
        writer.beginObject();
        writeString(writer, "frequency", isNullOrEmpty(info.getFrequency()) ? null : info.getFrequency());
        writeString(writer, "soilType", isNullOrEmpty(info.getSoilType()) ? null : info.getSoilType());
        writeString(writer, "tolerance", isNullOrEmpty(info.getTolerance()) ? null : info.getTolerance());
        if (!isNullOrEmpty(info.getSchedule())) {
            writeString(writer, "schedule", info.getSchedule());
        }
        if (!isNullOrEmpty(info.getSoil())) {
            writeString(writer, "soil", info.getSoil());
        }
        writer.endObject();
    }

    private void writeRange(JsonWriter writer, String name, @Nullable SpeciesTarget.FloatRange range)
        throws IOException {
        writer.name(name);
        if (range == null || !range.hasValues()) {
            writer.nullValue();
            return;
        }
        writer.beginObject();
        writeOptionalFloat(writer, "min", range.getMin());
        writeOptionalFloat(writer, "max", range.getMax());
        writer.endObject();
    }

    private void writeStringArray(JsonWriter writer, String name, @Nullable List<String> values)
        throws IOException {
        writer.name(name);
        if (values == null || values.isEmpty()) {
            writer.nullValue();
            return;
        }
        writer.beginArray();
        for (String value : values) {
            if (value == null) {
                writer.nullValue();
            } else {
                writer.value(value);
            }
        }
        writer.endArray();
    }

    private void writeOptionalBoolean(JsonWriter writer, @Nullable Boolean value)
        throws IOException {
        writer.name("toxicToPets");
        if (value == null) {
            writer.nullValue();
        } else {
            writer.value(value);
        }
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

    private void writeOptionalLong(JsonWriter writer, String name, @Nullable Long value)
        throws IOException {
        if (value == null) {
            writer.name(name).nullValue();
        } else {
            writer.name(name).value(value);
        }
    }

    /**
     * Supported export formats.
     */
    public enum Format {
        CSV,
        JSON
    }

    /**
     * Callback used to signal completion of the export operation.
     */
    public interface Callback {
        void onComplete(boolean success);
    }

    /**
     * Callback used to report incremental progress.
     */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }

    private static class ExportData {
        final List<Plant> plants;
        final List<Measurement> measurements;
        final List<DiaryEntry> diaryEntries;
        final List<Reminder> reminders;
        final List<SpeciesTarget> targets;
        final List<PlantPhoto> plantPhotos;
        final List<LedProfile> ledProfiles;
        final List<EnvironmentEntry> environmentEntries;
        final List<ReminderSuggestion> reminderSuggestions;

        ExportData(List<Plant> plants, List<Measurement> measurements, List<DiaryEntry> diaryEntries,
                   List<Reminder> reminders, List<SpeciesTarget> targets, List<PlantPhoto> plantPhotos,
                   List<LedProfile> ledProfiles, List<EnvironmentEntry> environmentEntries,
                   List<ReminderSuggestion> reminderSuggestions) {
            this.plants = plants;
            this.measurements = measurements;
            this.diaryEntries = diaryEntries;
            this.reminders = reminders;
            this.targets = targets;
            this.plantPhotos = plantPhotos;
            this.ledProfiles = ledProfiles;
            this.environmentEntries = environmentEntries;
            this.reminderSuggestions = reminderSuggestions;
        }
    }
}
