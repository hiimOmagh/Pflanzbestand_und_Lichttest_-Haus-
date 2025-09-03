package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Repository providing asynchronous access to {@link Plant} entities.
 * <p>
 * All database operations are executed on {@link PlantDatabase#databaseWriteExecutor}
 * and results or completion callbacks are delivered on the Android main thread.
 */
public class PlantRepository {
    private final PlantDao plantDao;
    private final MeasurementDao measurementDao;
    private final DiaryDao diaryDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Creates a new repository instance.
     * <p>
     * The instance can be created on any thread, but its methods must be
     * executed on a background thread.
     *
     * @param context application context used to obtain the database
     */
    public PlantRepository(Context context) {
        PlantDatabase db = PlantDatabase.getDatabase(context);
        plantDao = db.plantDao();
        measurementDao = db.measurementDao();
        diaryDao = db.diaryDao();
    }

    /**
     * Retrieves all stored plants asynchronously and delivers them on the main thread.
     *
     * @param callback invoked with the resulting list on the main thread
     */
    public void getAllPlants(Consumer<List<Plant>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Plant> result = plantDao.getAll();
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }

    /**
     * Inserts a plant into the database asynchronously and updates the provided
     * entity with the generated primary key.
     *
     * @param plant    the {@link Plant} to add
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> insert(Plant plant, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            long id = plantDao.insert(plant);
            plant.setId(id);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Updates an existing plant asynchronously.
     *
     * @param plant    the {@link Plant} to update
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> update(Plant plant, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            plantDao.update(plant);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Removes a plant from the database asynchronously.
     *
     * @param plant    the {@link Plant} to remove
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> delete(Plant plant, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            plantDao.delete(plant);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Inserts a measurement into the database asynchronously.
     *
     * @param measurement the {@link Measurement} to add
     * @param callback    optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> insertMeasurement(Measurement measurement, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            measurementDao.insert(measurement);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Inserts a diary entry into the database asynchronously.
     *
     * @param entry    the {@link DiaryEntry} to add
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> insertDiaryEntry(DiaryEntry entry, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            diaryDao.insert(entry);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Retrieves recent measurements for a plant asynchronously and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param limit    maximum number of measurements to return
     * @param callback invoked with the resulting list on the main thread
     */
    public void recentMeasurementsForPlant(long plantId, int limit, Consumer<List<Measurement>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Measurement> result = measurementDao.recentForPlant(plantId, limit);
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }

    /**
     * Retrieves all diary entries for a plant asynchronously and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param callback invoked with the resulting list on the main thread
     */
    public void diaryEntriesForPlant(long plantId, Consumer<List<DiaryEntry>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<DiaryEntry> result = diaryDao.entriesForPlant(plantId);
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }
}
