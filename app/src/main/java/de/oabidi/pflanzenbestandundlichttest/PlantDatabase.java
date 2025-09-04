package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton Room database for storing {@link Plant} data.
 *
 * <p>The database disallows main-thread queries. Database read/write work
 * should be executed on {@link #databaseWriteExecutor}, a fixed thread pool
 * used to run operations asynchronously.</p>
 */
@Database(entities = {Plant.class, Measurement.class, DiaryEntry.class, SpeciesTarget.class}, version = 2)
@TypeConverters({Converters.class})
public abstract class PlantDatabase extends RoomDatabase {
    private static volatile PlantDatabase INSTANCE;

    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
        Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public abstract PlantDao plantDao();

    public abstract MeasurementDao measurementDao();

    public abstract DiaryDao diaryDao();

    public abstract SpeciesTargetDao speciesTargetDao();

    public static PlantDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (PlantDatabase.class) {
                if (INSTANCE == null) {
                    Context appContext = context.getApplicationContext();
                    INSTANCE = Room.databaseBuilder(appContext,
                            PlantDatabase.class, "plant_database")
                        // During development we simply reset the database whenever the
                        // schema version changes. If data preservation becomes
                        // important, replace this with explicit Migration objects for
                        // each version step.
                        .fallbackToDestructiveMigration()
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
