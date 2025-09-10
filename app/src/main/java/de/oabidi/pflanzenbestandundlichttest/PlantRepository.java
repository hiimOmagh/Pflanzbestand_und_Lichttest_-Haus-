package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

import de.oabidi.pflanzenbestandundlichttest.data.util.PhotoManager;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Repository providing asynchronous access to {@link Plant} entities.
 * <p>
 * All database operations are executed on {@link PlantDatabase#databaseWriteExecutor}
 * and results or completion callbacks are delivered on the Android main thread,
 * allowing callers to update the UI directly from these callbacks.
 */
public class PlantRepository {
    private final PlantDao plantDao;
    private final MeasurementDao measurementDao;
    private final DiaryDao diaryDao;
    private final SpeciesTargetDao speciesTargetDao;
    private final ReminderDao reminderDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    /**
     * Creates a new repository instance.
     * <p>
     * The instance can be created on any thread, but its methods must be
     * executed on a background thread.
     *
     * @param context application context used to obtain the database
     */
    public PlantRepository(Context context) {
        this.context = context.getApplicationContext();
        PlantDatabase db = PlantDatabase.getDatabase(this.context);
        plantDao = db.plantDao();
        measurementDao = db.measurementDao();
        diaryDao = db.diaryDao();
        speciesTargetDao = db.speciesTargetDao();
        reminderDao = db.reminderDao();
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
     * Searches plants matching the given query asynchronously and delivers
     * results on the main thread.
     *
     * @param query    text to match against name or notes
     * @param callback invoked with the resulting list on the main thread
     */
    public void searchPlants(String query, Consumer<List<Plant>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Plant> result;
            if (query == null || query.isEmpty()) {
                result = plantDao.getAll();
            } else {
                String q = query + "*";
                result = plantDao.search(q);
            }
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
            final long id = plantDao.insert(plant);
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
            PhotoManager.deletePhoto(context, plant.getPhotoUri());
            List<Reminder> reminders = reminderDao.getForPlant(plant.getId());
            for (Reminder reminder : reminders) {
                ReminderScheduler.cancelReminder(context, reminder.getId());
            }
            plantDao.delete(plant);
            SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getLong(SettingsKeys.KEY_SELECTED_PLANT, -1) == plant.getId()) {
                prefs.edit().remove(SettingsKeys.KEY_SELECTED_PLANT).apply();
            }
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
     * Updates an existing measurement asynchronously.
     *
     * @param measurement the {@link Measurement} to update
     * @param callback    optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> updateMeasurement(Measurement measurement, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            measurementDao.update(measurement);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Deletes a measurement from the database asynchronously.
     *
     * @param measurement the {@link Measurement} to remove
     * @param callback    optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> deleteMeasurement(Measurement measurement, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            measurementDao.delete(measurement);
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
     * Updates an existing diary entry asynchronously.
     *
     * @param entry    the {@link DiaryEntry} to update
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> updateDiaryEntry(DiaryEntry entry, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            diaryDao.update(entry);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Deletes a diary entry from the database asynchronously.
     *
     * @param entry    the {@link DiaryEntry} to remove
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> deleteDiaryEntry(DiaryEntry entry, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            PhotoManager.deletePhoto(context, entry.getPhotoUri());
            diaryDao.delete(entry);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Retrieves all reminders asynchronously and delivers them on the main thread.
     *
     * @param callback invoked with the resulting list on the main thread
     */
    public void getAllReminders(Consumer<List<Reminder>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Reminder> result = reminderDao.getAll();
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }

    /**
     * Inserts a reminder into the database asynchronously and updates the entity
     * with the generated primary key.
     *
     * @param reminder the {@link Reminder} to add
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> insertReminder(Reminder reminder, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            long id = reminderDao.insert(reminder);
            reminder.setId(id);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Updates an existing reminder asynchronously.
     *
     * @param reminder the {@link Reminder} to update
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> updateReminder(Reminder reminder, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            reminderDao.update(reminder);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Deletes the reminder identified by the given id asynchronously.
     *
     * @param id       identifier of the reminder to remove
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> deleteReminderById(long id, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            reminderDao.deleteById(id);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Retrieves the PPFD target range for the given species key asynchronously.
     *
     * @param speciesKey identifier of the species
     * @param callback   invoked with the resulting target on the main thread
     */
    public void getSpeciesTarget(String speciesKey, Consumer<SpeciesTarget> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            SpeciesTarget result = speciesTargetDao.findBySpeciesKey(speciesKey);
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }

    /**
     * Retrieves all species targets asynchronously.
     *
     * @param callback invoked with the resulting list on the main thread
     */
    public void getAllSpeciesTargets(Consumer<List<SpeciesTarget>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<SpeciesTarget> result = speciesTargetDao.getAll();
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }

    /**
     * Inserts or updates a species target asynchronously.
     *
     * @param target   the {@link SpeciesTarget} to persist
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> insertSpeciesTarget(SpeciesTarget target, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            speciesTargetDao.insert(target);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Deletes the species target identified by the given key asynchronously.
     *
     * @param speciesKey key of the species target to delete
     * @param callback   optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> deleteSpeciesTarget(String speciesKey, Runnable callback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            speciesTargetDao.deleteBySpeciesKey(speciesKey);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    /**
     * Returns all plants stored in the database.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @return list of all plants
     */
    @VisibleForTesting
    List<Plant> getAllPlantsSync() {
        return plantDao.getAll();
    }

    /**
     * Returns all species targets stored in the database.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @return list of all species targets
     */
    @VisibleForTesting
    List<SpeciesTarget> getAllSpeciesTargetsSync() {
        return speciesTargetDao.getAll();
    }

    /**
     * Returns all measurements stored in the database.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @return list of all measurements
     */
    @VisibleForTesting
    List<Measurement> getAllMeasurementsSync() {
        return measurementDao.getAll();
    }

    /**
     * Returns all diary entries stored in the database.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @return list of all diary entries
     */
    @VisibleForTesting
    List<DiaryEntry> getAllDiaryEntriesSync() {
        return diaryDao.getAll();
    }

    /**
     * Returns all reminders stored in the database.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @return list of all reminders
     */
    @VisibleForTesting
    List<Reminder> getAllRemindersSync() {
        return reminderDao.getAll();
    }

    /**
     * Returns a plant by its identifier.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @param id database identifier
     * @return the matching plant or {@code null} if not found
     */
    @VisibleForTesting
    Plant getPlantSync(long id) {
        return plantDao.findById(id);
    }

    /**
     * Returns all measurements for the given plant.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @param plantId identifier of the plant
     * @return list of measurements associated with the plant
     */
    @VisibleForTesting
    List<Measurement> getMeasurementsForPlantSync(long plantId) {
        return measurementDao.getAllForPlant(plantId);
    }

    /**
     * Retrieves all measurements for a plant asynchronously and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param callback invoked with the resulting list on the main thread
     */
    public void getMeasurementsForPlant(long plantId, Consumer<List<Measurement>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Measurement> result = measurementDao.getAllForPlant(plantId);
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }

    /**
     * Returns all diary entries for the given plant.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @param plantId identifier of the plant
     * @return list of diary entries associated with the plant
     */
    @VisibleForTesting
    List<DiaryEntry> getDiaryEntriesForPlantSync(long plantId) {
        return diaryDao.entriesForPlant(plantId);
    }

    /**
     * Returns all reminders for the given plant.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @param plantId identifier of the plant
     * @return list of reminders associated with the plant
     */
    @VisibleForTesting
    List<Reminder> getRemindersForPlantSync(long plantId) {
        return reminderDao.getForPlant(plantId);
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
     * Retrieves measurements for a plant recorded since the given timestamp and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param since    minimum timestamp (inclusive) of measurements to return
     * @param callback invoked with the resulting list on the main thread
     */
    public void measurementsForPlantSince(long plantId, long since, Consumer<List<Measurement>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Measurement> result = measurementDao.getForPlantSince(plantId, since);
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }

    /**
     * Retrieves measurements for a plant within the specified time range and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param start    start of the time range (inclusive)
     * @param end      end of the time range (exclusive)
     * @param callback invoked with the resulting list on the main thread
     */
    public void measurementsForPlantInRange(long plantId, long start, long end, Consumer<List<Measurement>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Measurement> result = measurementDao.getForPlantInRange(plantId, start, end);
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }

    /**
     * Calculates the summed PPFD for a plant within the specified time range and delivers
     * the result on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param start    start of the time range (inclusive)
     * @param end      end of the time range (exclusive)
     * @param callback invoked with the summed PPFD on the main thread
     */
    public void sumPpfdForRange(long plantId, long start, long end, Consumer<Float> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            Float result = measurementDao.sumPpfdForRange(plantId, start, end);
            float value = result != null ? result : 0f;
            if (callback != null) {
                mainHandler.post(() -> callback.accept(value));
            }
        });
    }

    /**
     * Counts the number of days with measurement data greater than zero for a plant
     * within the specified time range and delivers the result on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param start    start of the time range (inclusive)
     * @param end      end of the time range (inclusive)
     * @param callback invoked with the day count on the main thread
     */
    public void countDaysWithData(long plantId, long start, long end, Consumer<Integer> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            int result = measurementDao.countDaysWithData(plantId, start, end);
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

    /**
     * Retrieves diary entries for a plant matching the given query.
     * Results are delivered on the main thread.
     *
     * @param plantId identifier of the plant
     * @param query   text to search in notes; empty string returns all entries
     * @param callback invoked with the resulting list on the main thread
     */
    public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<DiaryEntry> result;
            if (query == null || query.isEmpty()) {
                result = diaryDao.entriesForPlant(plantId);
            } else {
                String q = query + "*";
                result = diaryDao.searchDiaryEntries(plantId, q);
            }
            if (callback != null) {
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }
}
