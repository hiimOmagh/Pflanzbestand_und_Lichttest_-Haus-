package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        PlantPhoto.class
    },
    version = 11,
    exportSchema = true
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

    public abstract PlantDao plantDao();

    /** Provides access to stored light measurements. */
    public abstract MeasurementDao measurementDao();

    public abstract DiaryDao diaryDao();

    public abstract SpeciesTargetDao speciesTargetDao();

    public abstract ReminderDao reminderDao();

    /** Provides bulk read access for export and import operations. */
    public abstract BulkReadDao bulkDao();

    public abstract PlantPhotoDao plantPhotoDao();

    public static PlantDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (PlantDatabase.class) {
                if (INSTANCE == null) {
                    Context appContext = context.getApplicationContext();
                    INSTANCE = Room.databaseBuilder(appContext,
                            PlantDatabase.class, "plant_database")
                        // Migrations must be supplied for all future schema changes
                        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
                float min = (float) obj.getDouble("ppfdMin");
                float max = (float) obj.getDouble("ppfdMax");
                dao.insert(new SpeciesTarget(key, min, max));
            }
        } catch (IOException | JSONException e) {
            Log.e("PlantDatabase", "Failed to seed species targets", e);
        }
    }
}
