package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntryDao;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibrationDao;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhotoDao;
import de.oabidi.pflanzenbestandundlichttest.data.ProactiveAlertDao;
import de.oabidi.pflanzenbestandundlichttest.data.ProactiveAlertLog;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestionDao;

/**
 * Singleton Room database for storing {@link Plant} data.
 *
 * <p>The database disallows main-thread queries. Database read/write work
 * should be executed on {@link #databaseWriteExecutor}, a fixed thread pool
 * used to run operations asynchronously.</p>
 */
@Database(
    entities = {
        Plant.class,
        Measurement.class,
        DiaryEntry.class,
        SpeciesTarget.class,
        Reminder.class,
        ReminderSuggestion.class,
        PlantFts.class,
        DiaryEntryFts.class,
        PlantPhoto.class,
        PlantCalibration.class,
        EnvironmentEntry.class,
        ProactiveAlertLog.class
    },
    version = 1
)
@TypeConverters({Converters.class})
public abstract class PlantDatabase extends RoomDatabase {
    private static volatile PlantDatabase INSTANCE;

    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
        Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public abstract PlantDao plantDao();

    /** Provides access to stored light measurements. */
    public abstract MeasurementDao measurementDao();

    public abstract DiaryDao diaryDao();

    public abstract SpeciesTargetDao speciesTargetDao();

    public abstract ReminderDao reminderDao();

    /** Provides access to stored reminder suggestions. */
    public abstract ReminderSuggestionDao reminderSuggestionDao();

    /** Provides bulk read access for export and import operations. */
    public abstract BulkReadDao bulkDao();

    public abstract PlantPhotoDao plantPhotoDao();

    public abstract PlantCalibrationDao plantCalibrationDao();

    public abstract EnvironmentEntryDao environmentEntryDao();

    public abstract ProactiveAlertDao proactiveAlertDao();
    
    public static PlantDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (PlantDatabase.class) {
                if (INSTANCE == null) {
                    Context appContext = context.getApplicationContext();
                    INSTANCE = Room.databaseBuilder(appContext,
                            PlantDatabase.class, "plant_database")
                        // Schema migrations are not maintained; always recreate the database
                        // on upgrade or downgrade to keep the schema in sync with assets.
                        .fallbackToDestructiveMigration()
                        .fallbackToDestructiveMigrationOnDowngrade()
                        .addCallback(new RoomDatabase.Callback() {
                            @Override
                            public void onCreate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                                super.onCreate(db);
                                databaseWriteExecutor.execute(() -> seedDatabase(appContext));
                            }
                        })
                        .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Clears the singleton database instance to allow fresh databases to be created during tests.
     */
    @VisibleForTesting
    public static void resetInstanceForTesting(@NonNull Context context) {
        synchronized (PlantDatabase.class) {
            if (INSTANCE != null) {
                INSTANCE.close();
                INSTANCE = null;
            }
            context.getApplicationContext().deleteDatabase("plant_database");
        }
    }

    private static void seedDatabase(Context context) {
        try (InputStream is = context.getAssets().open("targets.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            JSONArray array = new JSONArray(builder.toString());
            SpeciesTargetDao dao = INSTANCE.speciesTargetDao();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String key = obj.getString("speciesKey");
                String commonName = optString(obj, "commonName");
                String scientificName = optString(obj, "scientificName");
                SpeciesTarget.Category category = parseCategory(optString(obj, "category"));
                SpeciesTarget.StageTarget seedling = parseStage(obj.optJSONObject("seedling"));
                SpeciesTarget.StageTarget vegetative = parseStage(obj.optJSONObject("vegetative"));
                SpeciesTarget.StageTarget flower = parseStage(obj.optJSONObject("flower"));
                SpeciesTarget.WateringInfo watering = parseWatering(obj.optJSONObject("watering"));
                String tolerance = optString(obj, "tolerance");
                if (tolerance != null && !tolerance.isEmpty()) {
                    if (watering == null) {
                        watering = new SpeciesTarget.WateringInfo(null, null, tolerance);
                    } else if (watering.getTolerance() == null || watering.getTolerance().isEmpty()) {
                        watering.setTolerance(tolerance);
                    }
                }
                SpeciesTarget.FloatRange temperature = parseRange(obj.optJSONObject("temperature"));
                SpeciesTarget.FloatRange humidity = parseRange(obj.optJSONObject("humidity"));
                String growthHabit = optString(obj, "growthHabit");
                Boolean toxicToPets = optBoolean(obj);
                List<String> careTips = parseStringArray(obj.optJSONArray("careTips"));
                List<String> sources = parseStringArray(obj.optJSONArray("sources"));
                String legacySource = optString(obj, "source");
                if (legacySource != null && !legacySource.isEmpty()) {
                    if (sources == null) {
                        sources = new ArrayList<>();
                    }
                    sources.add(legacySource);
                }
                SpeciesTarget target = new SpeciesTarget(key,
                    commonName,
                    scientificName,
                    category,
                    seedling,
                    vegetative,
                    flower,
                    watering,
                    temperature,
                    humidity,
                    growthHabit,
                    toxicToPets,
                    careTips,
                    sources);
                SpeciesTarget entity = PlantProfile.fromTarget(target);
                dao.insert(entity);
            }
        } catch (IOException | JSONException e) {
            Log.e("PlantDatabase", "Failed to seed species targets", e);
        }
    }

    private static SpeciesTarget.StageTarget parseStage(@Nullable JSONObject object) throws JSONException {
        if (object == null) {
            return new SpeciesTarget.StageTarget();
        }
        Float ppfdMin = optFloat(object, "ppfdMin");
        Float ppfdMax = optFloat(object, "ppfdMax");
        Float dliMin = optFloat(object, "dliMin");
        Float dliMax = optFloat(object, "dliMax");
        return new SpeciesTarget.StageTarget(ppfdMin, ppfdMax, dliMin, dliMax);
    }

    @Nullable
    private static SpeciesTarget.FloatRange parseRange(@Nullable JSONObject object) throws JSONException {
        if (object == null) {
            return null;
        }
        Float min = optFloat(object, "min");
        Float max = optFloat(object, "max");
        if (min == null && max == null) {
            return null;
        }
        return new SpeciesTarget.FloatRange(min, max);
    }

    @Nullable
    private static SpeciesTarget.WateringInfo parseWatering(@Nullable JSONObject object) throws JSONException {
        if (object == null) {
            return null;
        }
        String schedule = optString(object, "schedule");
        String soil = optString(object, "soil");
        String frequency = optString(object, "frequency");
        String soilType = optString(object, "soilType");
        String tolerance = optString(object, "tolerance");
        String resolvedFrequency = frequency != null ? frequency : schedule;
        String resolvedSoil = soilType != null ? soilType : soil;
        if ((resolvedFrequency == null || resolvedFrequency.isEmpty())
            && (resolvedSoil == null || resolvedSoil.isEmpty())
            && (tolerance == null || tolerance.isEmpty())) {
            return null;
        }
        return new SpeciesTarget.WateringInfo(resolvedFrequency, resolvedSoil, tolerance);
    }

    @Nullable
    private static List<String> parseStringArray(@Nullable JSONArray array) throws JSONException {
        if (array == null) {
            return null;
        }
        List<String> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            if (!array.isNull(i)) {
                String value = array.optString(i, null);
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        values.add(trimmed);
                    }
                }
            }
        }
        return values.isEmpty() ? null : values;
    }

    private static Float optFloat(JSONObject object, String key) throws JSONException {
        if (object == null || !object.has(key)) {
            return null;
        }
        double value = object.optDouble(key, Double.NaN);
        return Double.isNaN(value) ? null : (float) value;
    }

    private static String optString(JSONObject object, String key) {
        if (object != null && object.has(key)) {
            String value = object.optString(key, null);
            if (!value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static Boolean optBoolean(@Nullable JSONObject object) {
        if (object == null || !object.has("toxicToPets")) {
            return null;
        }
        Object raw = object.opt("toxicToPets");
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            String value = ((String) raw).trim();
            if (value.isEmpty()) {
                return null;
            }
            if ("1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) {
                return Boolean.TRUE;
            }
            if ("0".equals(value) || "false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private static SpeciesTarget.Category parseCategory(@Nullable String value) {
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
}
