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
     * Inserts a plant into the database asynchronously.
     *
     * @param plant    the {@link Plant} to add
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> insert(Plant plant, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            plantDao.insert(plant);
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
}
