package de.oabidi.pflanzenbestandundlichttest;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Database;
import androidx.room.OnConflictStrategy;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

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
        PlantFts.class,
        DiaryEntryFts.class,
        PlantPhoto.class,
        PlantCalibration.class,
        EnvironmentEntry.class
    },
    version = 15
)
@TypeConverters({Converters.class})
public abstract class PlantDatabase extends RoomDatabase {
    private static volatile PlantDatabase INSTANCE;

    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
        Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE DiaryEntry ADD COLUMN photoUri TEXT");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS Reminder (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, triggerAt INTEGER NOT NULL, message TEXT NOT NULL)");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Reminder ADD COLUMN plantId INTEGER NOT NULL DEFAULT -1");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Measurement ADD COLUMN note TEXT");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Measurement ADD COLUMN dli REAL");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS PlantFts USING FTS4(name, species, locationHint, description)");
            database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS DiaryEntryFts USING FTS4(note, type)");
            database.execSQL("INSERT INTO PlantFts(rowid, name, species, locationHint, description) " +
                "SELECT id, name, IFNULL(species, ''), IFNULL(locationHint, ''), IFNULL(description, '') FROM Plant");
            database.execSQL("INSERT INTO DiaryEntryFts(rowid, note, type) SELECT id, IFNULL(note, ''), type FROM DiaryEntry");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS DiaryEntryFts");
            database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS DiaryEntryFts USING FTS4(note, type)");
            database.execSQL("INSERT INTO DiaryEntryFts(rowid, note, type) SELECT id, IFNULL(note, ''), type FROM DiaryEntry");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS PlantFts");
            database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS PlantFts USING FTS4(name, species, locationHint, description)");
            database.execSQL("INSERT INTO PlantFts(rowid, name, species, locationHint, description) " +
                "SELECT id, name, IFNULL(species, ''), IFNULL(locationHint, ''), IFNULL(description, '') FROM Plant");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS PlantPhoto (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "plantId INTEGER NOT NULL, " +
                    "uri TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(plantId) REFERENCES Plant(id) ON DELETE CASCADE)"
            );
            database.execSQL("CREATE INDEX IF NOT EXISTS index_PlantPhoto_plantId ON PlantPhoto(plantId)");
        }
    };

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS PlantCalibration (" +
                    "plantId INTEGER PRIMARY KEY NOT NULL, " +
                    "ambientFactor REAL NOT NULL, " +
                    "cameraFactor REAL NOT NULL, " +
                    "FOREIGN KEY(plantId) REFERENCES Plant(id) ON DELETE CASCADE)"
            );
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_PlantCalibration_plantId ON PlantCalibration(plantId)");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS SpeciesTarget_new (" +
                    "speciesKey TEXT NOT NULL PRIMARY KEY, " +
                    "seedling_ppfdMin REAL, " +
                    "seedling_ppfdMax REAL, " +
                    "seedling_dliMin REAL, " +
                    "seedling_dliMax REAL, " +
                    "vegetative_ppfdMin REAL, " +
                    "vegetative_ppfdMax REAL, " +
                    "vegetative_dliMin REAL, " +
                    "vegetative_dliMax REAL, " +
                    "flower_ppfdMin REAL, " +
                    "flower_ppfdMax REAL, " +
                    "flower_dliMin REAL, " +
                    "flower_dliMax REAL, " +
                    "tolerance TEXT, " +
                    "source TEXT)"
            );
            database.execSQL(
                "INSERT INTO SpeciesTarget_new (" +
                    "speciesKey, seedling_ppfdMin, seedling_ppfdMax, seedling_dliMin, seedling_dliMax, " +
                    "vegetative_ppfdMin, vegetative_ppfdMax, vegetative_dliMin, vegetative_dliMax, " +
                    "flower_ppfdMin, flower_ppfdMax, flower_dliMin, flower_dliMax, tolerance, source" +
                    ") SELECT speciesKey, ppfdMin, ppfdMax, ppfdMin * 0.0432, ppfdMax * 0.0432, " +
                    "ppfdMin, ppfdMax, ppfdMin * 0.0432, ppfdMax * 0.0432, ppfdMin, ppfdMax, ppfdMin * 0.0432, ppfdMax * 0.0432, tolerance, source " +
                    "FROM SpeciesTarget"
            );
            database.execSQL("DROP TABLE IF EXISTS SpeciesTarget");
            database.execSQL("ALTER TABLE SpeciesTarget_new RENAME TO SpeciesTarget");
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS SpeciesTarget_new (" +
                    "speciesKey TEXT NOT NULL PRIMARY KEY, " +
                    "commonName TEXT, " +
                    "scientificName TEXT, " +
                    "category TEXT NOT NULL DEFAULT 'OTHER', " +
                    "seedling_ppfd_min REAL, " +
                    "seedling_ppfd_max REAL, " +
                    "seedling_dli_min REAL, " +
                    "seedling_dli_max REAL, " +
                    "vegetative_ppfd_min REAL, " +
                    "vegetative_ppfd_max REAL, " +
                    "vegetative_dli_min REAL, " +
                    "vegetative_dli_max REAL, " +
                    "flower_ppfd_min REAL, " +
                    "flower_ppfd_max REAL, " +
                    "flower_dli_min REAL, " +
                    "flower_dli_max REAL, " +
                    "watering_schedule TEXT, " +
                    "watering_soil TEXT, " +
                    "watering_tolerance TEXT, " +
                    "temperature_min REAL, " +
                    "temperature_max REAL, " +
                    "humidity_min REAL, " +
                    "humidity_max REAL, " +
                    "growthHabit TEXT, " +
                    "toxicToPets INTEGER, " +
                    "careTips TEXT, " +
                    "sources TEXT)"
            );

            Cursor cursor = database.query(
                "SELECT speciesKey, " +
                    "seedling_ppfdMin, seedling_ppfdMax, seedling_dliMin, seedling_dliMax, " +
                    "vegetative_ppfdMin, vegetative_ppfdMax, vegetative_dliMin, vegetative_dliMax, " +
                    "flower_ppfdMin, flower_ppfdMax, flower_dliMin, flower_dliMax, " +
                    "tolerance, source FROM SpeciesTarget"
            );
            try {
                int speciesKeyIndex = cursor.getColumnIndexOrThrow("speciesKey");
                int seedlingPpfdMinIndex = cursor.getColumnIndex("seedling_ppfdMin");
                int seedlingPpfdMaxIndex = cursor.getColumnIndex("seedling_ppfdMax");
                int seedlingDliMinIndex = cursor.getColumnIndex("seedling_dliMin");
                int seedlingDliMaxIndex = cursor.getColumnIndex("seedling_dliMax");
                int vegetativePpfdMinIndex = cursor.getColumnIndex("vegetative_ppfdMin");
                int vegetativePpfdMaxIndex = cursor.getColumnIndex("vegetative_ppfdMax");
                int vegetativeDliMinIndex = cursor.getColumnIndex("vegetative_dliMin");
                int vegetativeDliMaxIndex = cursor.getColumnIndex("vegetative_dliMax");
                int flowerPpfdMinIndex = cursor.getColumnIndex("flower_ppfdMin");
                int flowerPpfdMaxIndex = cursor.getColumnIndex("flower_ppfdMax");
                int flowerDliMinIndex = cursor.getColumnIndex("flower_dliMin");
                int flowerDliMaxIndex = cursor.getColumnIndex("flower_dliMax");
                int toleranceIndex = cursor.getColumnIndex("tolerance");
                int sourceIndex = cursor.getColumnIndex("source");

                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("speciesKey", cursor.getString(speciesKeyIndex));
                    values.put("category", SpeciesTarget.Category.OTHER.name());

                    copyFloat(cursor, values, seedlingPpfdMinIndex, "seedling_ppfd_min");
                    copyFloat(cursor, values, seedlingPpfdMaxIndex, "seedling_ppfd_max");
                    copyFloat(cursor, values, seedlingDliMinIndex, "seedling_dli_min");
                    copyFloat(cursor, values, seedlingDliMaxIndex, "seedling_dli_max");
                    copyFloat(cursor, values, vegetativePpfdMinIndex, "vegetative_ppfd_min");
                    copyFloat(cursor, values, vegetativePpfdMaxIndex, "vegetative_ppfd_max");
                    copyFloat(cursor, values, vegetativeDliMinIndex, "vegetative_dli_min");
                    copyFloat(cursor, values, vegetativeDliMaxIndex, "vegetative_dli_max");
                    copyFloat(cursor, values, flowerPpfdMinIndex, "flower_ppfd_min");
                    copyFloat(cursor, values, flowerPpfdMaxIndex, "flower_ppfd_max");
                    copyFloat(cursor, values, flowerDliMinIndex, "flower_dli_min");
                    copyFloat(cursor, values, flowerDliMaxIndex, "flower_dli_max");

                    if (toleranceIndex >= 0 && !cursor.isNull(toleranceIndex)) {
                        values.put("watering_tolerance", cursor.getString(toleranceIndex));
                    }

                    if (sourceIndex >= 0 && !cursor.isNull(sourceIndex)) {
                        String source = cursor.getString(sourceIndex);
                        if (source != null && !source.trim().isEmpty()) {
                            JSONArray array = new JSONArray();
                            array.put(source);
                            values.put("sources", array.toString());
                        }
                    }

                    database.insert("SpeciesTarget_new", OnConflictStrategy.REPLACE, values);
                }
            } finally {
                cursor.close();
            }

            database.execSQL("DROP TABLE IF EXISTS SpeciesTarget");
            database.execSQL("ALTER TABLE SpeciesTarget_new RENAME TO SpeciesTarget");
        }

        private void copyFloat(Cursor cursor, ContentValues values, int columnIndex, String columnName) {
            if (columnIndex >= 0 && !cursor.isNull(columnIndex)) {
                values.put(columnName, cursor.getFloat(columnIndex));
            }
        }
    };

    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS EnvironmentEntry (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "plantId INTEGER NOT NULL, " +
                    "timestamp INTEGER NOT NULL, " +
                    "temperature REAL, " +
                    "humidity REAL, " +
                    "soilMoisture REAL, " +
                    "height REAL, " +
                    "width REAL, " +
                    "notes TEXT, " +
                    "photoUri TEXT, " +
                    "FOREIGN KEY(plantId) REFERENCES Plant(id) ON DELETE CASCADE)"
            );
            database.execSQL("CREATE INDEX IF NOT EXISTS index_EnvironmentEntry_plantId ON EnvironmentEntry(plantId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_EnvironmentEntry_timestamp ON EnvironmentEntry(timestamp)");
        }
    };

    public abstract PlantDao plantDao();

    /** Provides access to stored light measurements. */
    public abstract MeasurementDao measurementDao();

    public abstract DiaryDao diaryDao();

    public abstract SpeciesTargetDao speciesTargetDao();

    public abstract ReminderDao reminderDao();

    /** Provides bulk read access for export and import operations. */
    public abstract BulkReadDao bulkDao();

    public abstract PlantPhotoDao plantPhotoDao();

    public abstract PlantCalibrationDao plantCalibrationDao();

    public abstract EnvironmentEntryDao environmentEntryDao();

    public static PlantDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (PlantDatabase.class) {
                if (INSTANCE == null) {
                    Context appContext = context.getApplicationContext();
                    INSTANCE = Room.databaseBuilder(appContext,
                            PlantDatabase.class, "plant_database")
                        // Migrations must be supplied for all future schema changes
                        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                            MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                            MIGRATION_14_15)
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
