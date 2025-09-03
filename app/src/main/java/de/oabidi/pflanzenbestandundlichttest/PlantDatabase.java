package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton Room database for storing {@link Plant} data.
 *
 * <p>The database disallows main-thread queries. Database read/write work
 * should be executed on {@link #databaseWriteExecutor}, a fixed thread pool
 * used to run operations asynchronously.</p>
 */
@Database(entities = {Plant.class, Measurement.class, DiaryEntry.class, SpeciesTarget.class}, version = 4)
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
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            PlantDatabase.class, "plant_database")
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
