package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import java.util.List;

/**
 * Repository providing synchronous access to {@link Plant} entities.
 * <p>
 * All methods are blocking and should be invoked on a background thread.
 * Use an {@link java.util.concurrent.ExecutorService} or similar mechanism
 * to avoid blocking the Android main thread.
 */
public class PlantRepository {
    private final PlantDao plantDao;

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
     * Returns all stored plants.
     * <p>
     * This call accesses the database directly and should run on a
     * background thread.
     *
     * @return list of all {@link Plant} entries
     */
    public List<Plant> getAllPlants() {
        return plantDao.getAll();
    }

    /**
     * Inserts a plant into the database.
     * <p>
     * Should be executed on a background thread to prevent blocking
     * the UI.
     *
     * @param plant the {@link Plant} to add
     */
    public void insert(Plant plant) {
        plantDao.insert(plant);
    }

    /**
     * Updates an existing plant.
     * <p>
     * Should be executed on a background thread to prevent blocking
     * the UI.
     *
     * @param plant the {@link Plant} to update
     */
    public void update(Plant plant) {
        plantDao.update(plant);
    }

    /**
     * Removes a plant from the database.
     * <p>
     * Should be executed on a background thread to prevent blocking
     * the UI.
     *
     * @param plant the {@link Plant} to remove
     */
    public void delete(Plant plant) {
        plantDao.delete(plant);
    }
}
