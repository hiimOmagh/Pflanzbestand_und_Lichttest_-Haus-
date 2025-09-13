package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

import de.oabidi.pflanzenbestandundlichttest.data.util.PhotoManager;

import java.util.List;
import java.util.Calendar;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
    private final BulkReadDao bulkDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    private static final Pattern UNSUPPORTED_CHARS = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern RESERVED_FTS = Pattern.compile("\\b(?:AND|OR|NOT|NEAR)\\b", Pattern.CASE_INSENSITIVE);

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
        bulkDao = db.bulkDao();
    }

    /** Exposes bulk read operations for export and import managers. */
    public BulkReadDao bulkDao() {
        return bulkDao;
    }

    private Future<?> runAsync(Runnable action, Runnable callback, Consumer<Exception> errorCallback) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> {
            try {
                action.run();
                if (callback != null) {
                    mainHandler.post(callback);
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    /**
     * Retrieves all stored plants asynchronously and delivers them on the main thread.
     *
     * @param callback      invoked with the resulting list on the main thread
     */
    public void getAllPlants(Consumer<List<Plant>> callback) {
        getAllPlants(callback, null);
    }

    /**
     * Retrieves all stored plants asynchronously and delivers them on the main thread.
     *
     * @param callback      invoked with the resulting list on the main thread
     * @param errorCallback invoked with any encountered exception on the main thread
     */
    public void getAllPlants(Consumer<List<Plant>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Plant> result = plantDao.getAll();
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(result));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    /**
     * Searches plants matching the given query asynchronously and delivers
     * results on the main thread.
     *
     * @param query    text to match against name, notes, species or location
     * @param callback invoked with the resulting list on the main thread
     */
    public void searchPlants(String query, Consumer<List<Plant>> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Plant> result;
            if (query == null || query.isEmpty()) {
                result = plantDao.getAll();
            } else {
                String normalized = UNSUPPORTED_CHARS.matcher(query).replaceAll(" ");
                normalized = RESERVED_FTS.matcher(normalized).replaceAll(" ");
                normalized = normalized.trim().replaceAll("\\s+", " ");
                if (normalized.isEmpty()) {
                    result = Collections.emptyList();
                } else {
                    String q = normalized + "*";
                    try {
                        result = plantDao.search(q);
                    } catch (SQLiteException e) {
                        result = Collections.emptyList();
                    }
                }
            }
            if (callback != null) {
                List<Plant> finalResult = result;
                mainHandler.post(() -> callback.accept(finalResult));
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
        return insert(plant, callback, null);
    }

    public Future<?> insert(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> {
            final long id = plantDao.insert(plant);
            plant.setId(id);
        }, callback, errorCallback);
    }

    /**
     * Updates an existing plant asynchronously.
     *
     * @param plant    the {@link Plant} to update
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> update(Plant plant, Runnable callback) {
        return update(plant, callback, null);
    }

    public Future<?> update(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> plantDao.update(plant), callback, errorCallback);
    }

    /**
     * Removes a plant from the database asynchronously.
     *
     * @param plant    the {@link Plant} to remove
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> delete(Plant plant, Runnable callback) {
        return delete(plant, callback, null);
    }

    public Future<?> delete(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> {
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
        }, callback, errorCallback);
    }

    /**
     * Inserts a measurement into the database asynchronously.
     *
     * @param measurement the {@link Measurement} to add
     * @param callback    optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> insertMeasurement(Measurement measurement, Runnable callback) {
        return insertMeasurement(measurement, callback, null);
    }

    public Future<?> insertMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> {
            measurementDao.insert(measurement);
            checkDliAlerts(measurement.getPlantId());
        }, callback, errorCallback);
    }

    /**
     * Updates an existing measurement asynchronously.
     *
     * @param measurement the {@link Measurement} to update
     * @param callback    optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> updateMeasurement(Measurement measurement, Runnable callback) {
        return updateMeasurement(measurement, callback, null);
    }

    public Future<?> updateMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> measurementDao.update(measurement), callback, errorCallback);
    }

    /**
     * Deletes a measurement from the database asynchronously.
     *
     * @param measurement the {@link Measurement} to remove
     * @param callback    optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> deleteMeasurement(Measurement measurement, Runnable callback) {
        return deleteMeasurement(measurement, callback, null);
    }

    public Future<?> deleteMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> measurementDao.delete(measurement), callback, errorCallback);
    }

    private void checkDliAlerts(long plantId) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(SettingsKeys.KEY_DLI_ALERTS_ENABLED, false)) {
            return;
        }
        String thresholdStr = prefs.getString(SettingsKeys.KEY_DLI_ALERT_THRESHOLD, "3");
        int threshold;
        try {
            threshold = Integer.parseInt(thresholdStr);
        } catch (NumberFormatException e) {
            threshold = 3;
        }
        if (threshold <= 0) {
            return;
        }
        String hoursStr = prefs.getString(SettingsKeys.KEY_LIGHT_HOURS, "12");
        float lightHours;
        try {
            lightHours = Float.parseFloat(hoursStr);
        } catch (NumberFormatException e) {
            lightHours = 12f;
        }

        Plant plant = plantDao.findById(plantId);
        if (plant == null || plant.getSpecies() == null || plant.getSpecies().isEmpty()) {
            return;
        }
        SpeciesTarget target = speciesTargetDao.findBySpeciesKey(plant.getSpecies());
        if (target == null) {
            return;
        }

        float minDli = LightMath.dliFromPpfd(target.getPpfdMin(), lightHours);
        float maxDli = LightMath.dliFromPpfd(target.getPpfdMax(), lightHours);

        long todayStart = startOfDay(System.currentTimeMillis());
        int lowStreak = 0;
        int highStreak = 0;
        for (int i = 0; i < threshold; i++) {
            long start = todayStart - i * 86400000L;
            long end = start + 86400000L;
            List<Measurement> list = measurementDao.getForPlantInRange(plantId, start, end);
            if (list.isEmpty()) {
                break;
            }
            Measurement m = list.get(0);
            Float dli = m.getDli();
            if (dli == null) {
                Float ppfd = m.getPpfd();
                if (ppfd != null) {
                    dli = LightMath.dliFromPpfd(ppfd, lightHours);
                }
            }
            if (dli == null) {
                break;
            }
            if (dli < minDli) {
                lowStreak++;
                highStreak = 0;
            } else if (dli > maxDli) {
                highStreak++;
                lowStreak = 0;
            } else {
                break;
            }
        }

        if (lowStreak >= threshold || highStreak >= threshold) {
            String message;
            if (lowStreak >= threshold) {
                message = context.getString(R.string.reminder_dli_low, plant.getName(), threshold);
            } else {
                message = context.getString(R.string.reminder_dli_high, plant.getName(), threshold);
            }
            List<Reminder> reminders = reminderDao.getForPlant(plantId);
            for (Reminder r : reminders) {
                if (message.equals(r.getMessage())) {
                    return;
                }
            }
            long triggerAt = System.currentTimeMillis();
            Reminder reminder = new Reminder(triggerAt, message, plantId);
            long id = reminderDao.insert(reminder);
            ReminderScheduler.scheduleReminderAt(context, triggerAt, message, id, plantId);
        }
    }

    private long startOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Inserts a diary entry into the database asynchronously.
     *
     * @param entry    the {@link DiaryEntry} to add
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> insertDiaryEntry(DiaryEntry entry, Runnable callback) {
        return insertDiaryEntry(entry, callback, null);
    }

    public Future<?> insertDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> diaryDao.insert(entry), callback, errorCallback);
    }

    /**
     * Updates an existing diary entry asynchronously.
     *
     * @param entry    the {@link DiaryEntry} to update
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> updateDiaryEntry(DiaryEntry entry, Runnable callback) {
        return updateDiaryEntry(entry, callback, null);
    }

    public Future<?> updateDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> diaryDao.update(entry), callback, errorCallback);
    }

    /**
     * Deletes a diary entry from the database asynchronously.
     *
     * @param entry    the {@link DiaryEntry} to remove
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> deleteDiaryEntry(DiaryEntry entry, Runnable callback) {
        return deleteDiaryEntry(entry, callback, null);
    }

    public Future<?> deleteDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> {
            PhotoManager.deletePhoto(context, entry.getPhotoUri());
            diaryDao.delete(entry);
        }, callback, errorCallback);
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
        return insertReminder(reminder, callback, null);
    }

    public Future<?> insertReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> {
            long id = reminderDao.insert(reminder);
            reminder.setId(id);
        }, callback, errorCallback);
    }

    /**
     * Updates an existing reminder asynchronously.
     *
     * @param reminder the {@link Reminder} to update
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> updateReminder(Reminder reminder, Runnable callback) {
        return updateReminder(reminder, callback, null);
    }

    public Future<?> updateReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> reminderDao.update(reminder), callback, errorCallback);
    }

    /**
     * Deletes the reminder identified by the given id asynchronously.
     *
     * @param id       identifier of the reminder to remove
     * @param callback optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> deleteReminderById(long id, Runnable callback) {
        return deleteReminderById(id, callback, null);
    }

    public Future<?> deleteReminderById(long id, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> reminderDao.deleteById(id), callback, errorCallback);
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
        return insertSpeciesTarget(target, callback, null);
    }

    public Future<?> insertSpeciesTarget(SpeciesTarget target, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> speciesTargetDao.insert(target), callback, errorCallback);
    }

    /**
     * Deletes the species target identified by the given key asynchronously.
     *
     * @param speciesKey key of the species target to delete
     * @param callback   optional callback invoked on the main thread when done
     * @return a {@link Future} representing the pending operation
     */
    public Future<?> deleteSpeciesTarget(String speciesKey, Runnable callback) {
        return deleteSpeciesTarget(speciesKey, callback, null);
    }

    public Future<?> deleteSpeciesTarget(String speciesKey, Runnable callback, Consumer<Exception> errorCallback) {
        return runAsync(() -> speciesTargetDao.deleteBySpeciesKey(speciesKey), callback, errorCallback);
    }

    /**
     * Retrieves all plants asynchronously.
     *
     * @return {@link Future} delivering the list of plants
     */
    public Future<List<Plant>> getAllPlants() {
        return PlantDatabase.databaseWriteExecutor.submit(plantDao::getAll);
    }

    /**
     * Retrieves all species targets asynchronously.
     *
     * @return {@link Future} delivering the list of species targets
     */
    public Future<List<SpeciesTarget>> getAllSpeciesTargets() {
        return PlantDatabase.databaseWriteExecutor.submit(speciesTargetDao::getAll);
    }

    /**
     * Retrieves all measurements asynchronously.
     *
     * @return {@link Future} delivering the list of measurements
     */
    public Future<List<Measurement>> getAllMeasurements() {
        return PlantDatabase.databaseWriteExecutor.submit(measurementDao::getAll);
    }

    /**
     * Retrieves all diary entries asynchronously.
     *
     * @return {@link Future} delivering the list of diary entries
     */
    public Future<List<DiaryEntry>> getAllDiaryEntries() {
        return PlantDatabase.databaseWriteExecutor.submit(diaryDao::getAll);
    }

    /**
     * Retrieves all reminders asynchronously.
     *
     * @return {@link Future} delivering the list of reminders
     */
    public Future<List<Reminder>> getAllReminders() {
        return PlantDatabase.databaseWriteExecutor.submit(reminderDao::getAll);
    }

    /**
     * Retrieves a plant by its identifier asynchronously.
     *
     * @param id database identifier
     * @return {@link Future} delivering the matching plant or {@code null} if not found
     */
    public Future<Plant> getPlant(long id) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> plantDao.findById(id));
    }

    /**
     * Retrieves all measurements for the given plant asynchronously.
     *
     * @param plantId identifier of the plant
     * @return {@link Future} delivering the list of measurements for the plant
     */
    public Future<List<Measurement>> getMeasurementsForPlant(long plantId) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> measurementDao.getAllForPlant(plantId));
    }

    /**
     * Retrieves all diary entries for the given plant asynchronously.
     *
     * @param plantId identifier of the plant
     * @return {@link Future} delivering the list of diary entries for the plant
     */
    public Future<List<DiaryEntry>> diaryEntriesForPlant(long plantId) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> diaryDao.entriesForPlant(plantId));
    }

    /**
     * Retrieves all reminders for the given plant asynchronously.
     *
     * @param plantId identifier of the plant
     * @return {@link Future} delivering the list of reminders for the plant
     */
    public Future<List<Reminder>> getRemindersForPlant(long plantId) {
        return PlantDatabase.databaseWriteExecutor.submit(() -> reminderDao.getForPlant(plantId));
    }

    /**
     * Returns all plants stored in the database.
     * <p>
     * This method must be invoked on a background thread.
     *
     * @return list of all plants
     */
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
     * Calculates the summed PPFD and day count for a plant within the specified time range
     * and delivers the result on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param start    start of the time range (inclusive)
     * @param end      end of the time range (inclusive)
     * @param callback invoked with the aggregated result on the main thread
     */
    public void sumPpfdAndCountDays(long plantId, long start, long end, Consumer<MeasurementDao.SumAndDays> callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            MeasurementDao.SumAndDays result = measurementDao.sumPpfdAndCountDays(plantId, start, end);
            if (result == null) {
                result = new MeasurementDao.SumAndDays();
            }
            if (result.sum == null) {
                result.sum = 0f;
            }
            MeasurementDao.SumAndDays finalResult = result;
            if (callback != null) {
                mainHandler.post(() -> callback.accept(finalResult));
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
