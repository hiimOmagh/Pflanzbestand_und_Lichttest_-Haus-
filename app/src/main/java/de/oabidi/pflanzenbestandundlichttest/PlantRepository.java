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
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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
    private final ExecutorService ioExecutor;
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
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        this(appContext, ((ExecutorProvider) appContext).getIoExecutor());
    }

    @VisibleForTesting
    PlantRepository(Context context, ExecutorService ioExecutor) {
        this.context = context.getApplicationContext();
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
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

    private void runAsync(Runnable action, Runnable callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
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

    private void runBlockingOnIo(Runnable task) {
        Future<?> future = ioExecutor.submit(task);
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("IO task interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("IO task failed", cause);
        }
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
        searchPlants(query, callback, null);
    }

    public void searchPlants(String query, Consumer<List<Plant>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
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
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    /**
     * Inserts a plant into the database asynchronously and updates the provided
     * entity with the generated primary key.
     *
     * @param plant    the {@link Plant} to add
     * @param callback optional callback invoked on the main thread when done
     */
    public void insert(Plant plant, Runnable callback) {
        return insert(plant, callback, null);
    }

    public void insert(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> {
            final long id = plantDao.insert(plant);
            plant.setId(id);
        }, callback, errorCallback);
    }

    /**
     * Updates an existing plant asynchronously.
     *
     * @param plant    the {@link Plant} to update
     * @param callback optional callback invoked on the main thread when done
     */
    public void update(Plant plant, Runnable callback) {
        update(plant, callback, null);
    }

    public void update(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> plantDao.update(plant), callback, errorCallback);
    }

    /**
     * Removes a plant from the database asynchronously.
     *
     * @param plant    the {@link Plant} to remove
     * @param callback optional callback invoked on the main thread when done
     */
    public void delete(Plant plant, Runnable callback) {
        delete(plant, callback, null);
    }

    public void delete(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> {
            List<Reminder> reminders = reminderDao.getForPlant(plant.getId());
            plantDao.delete(plant);
            SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getLong(SettingsKeys.KEY_SELECTED_PLANT, -1) == plant.getId()) {
                prefs.edit().remove(SettingsKeys.KEY_SELECTED_PLANT).apply();
            }
            runBlockingOnIo(() -> {
                PhotoManager.deletePhoto(context, plant.getPhotoUri());
                for (Reminder reminder : reminders) {
                    ReminderScheduler.cancelReminder(context, reminder.getId());
                }
            });
            }, callback, errorCallback);
    }

    /**
     * Inserts a measurement into the database asynchronously.
     *
     * @param measurement the {@link Measurement} to add
     * @param callback    optional callback invoked on the main thread when done
     */
    public void insertMeasurement(Measurement measurement, Runnable callback) {
        insertMeasurement(measurement, callback, null);
    }

    public void insertMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> {
            measurementDao.insert(measurement);
            checkDliAlerts(measurement.getPlantId());
        }, callback, errorCallback);
    }

    /**
     * Updates an existing measurement asynchronously.
     *
     * @param measurement the {@link Measurement} to update
     * @param callback    optional callback invoked on the main thread when done
     */
    public void updateMeasurement(Measurement measurement, Runnable callback) {
        updateMeasurement(measurement, callback, null);
    }

    public void updateMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> measurementDao.update(measurement), callback, errorCallback);
    }

    /**
     * Deletes a measurement from the database asynchronously.
     *
     * @param measurement the {@link Measurement} to remove
     * @param callback    optional callback invoked on the main thread when done
     */
    public void deleteMeasurement(Measurement measurement, Runnable callback) {
        deleteMeasurement(measurement, callback, null);
    }

    public void deleteMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> measurementDao.delete(measurement), callback, errorCallback);
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
            runBlockingOnIo(() ->
                ReminderScheduler.scheduleReminderAt(context, triggerAt, message, id, plantId)
            );
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
     */
    public void insertDiaryEntry(DiaryEntry entry, Runnable callback) {
        insertDiaryEntry(entry, callback, null);
    }

    public void insertDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> diaryDao.insert(entry), callback, errorCallback);
    }

    /**
     * Updates an existing diary entry asynchronously.
     *
     * @param entry    the {@link DiaryEntry} to update
     * @param callback optional callback invoked on the main thread when done
     */
    public void updateDiaryEntry(DiaryEntry entry, Runnable callback) {
        updateDiaryEntry(entry, callback, null);
    }

    public void updateDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> diaryDao.update(entry), callback, errorCallback);
    }

    /**
     * Deletes a diary entry from the database asynchronously.
     *
     * @param entry    the {@link DiaryEntry} to remove
     * @param callback optional callback invoked on the main thread when done
     */
    public void deleteDiaryEntry(DiaryEntry entry, Runnable callback) {
        deleteDiaryEntry(entry, callback, null);
    }

    public void deleteDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> {
            diaryDao.delete(entry);
            runBlockingOnIo(() -> PhotoManager.deletePhoto(context, entry.getPhotoUri()));
        }, callback, errorCallback);
    }

    /**
     * Retrieves all reminders asynchronously and delivers them on the main thread.
     *
     * @param callback invoked with the resulting list on the main thread
     */
    public void getAllReminders(Consumer<List<Reminder>> callback) {
        getAllReminders(callback, null);
    }

    public void getAllReminders(Consumer<List<Reminder>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Reminder> result = reminderDao.getAll();
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
     * Inserts a reminder into the database asynchronously and updates the entity
     * with the generated primary key.
     *
     * @param reminder the {@link Reminder} to add
     * @param callback optional callback invoked on the main thread when done
     */
    public void insertReminder(Reminder reminder, Runnable callback) {
        insertReminder(reminder, callback, null);
    }

    public void insertReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> {
            long id = reminderDao.insert(reminder);
            reminder.setId(id);
        }, callback, errorCallback);
    }

    /**
     * Updates an existing reminder asynchronously.
     *
     * @param reminder the {@link Reminder} to update
     * @param callback optional callback invoked on the main thread when done
     */
    public void updateReminder(Reminder reminder, Runnable callback) {
        updateReminder(reminder, callback, null);
    }

    public void updateReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> reminderDao.update(reminder), callback, errorCallback);
    }

    /**
     * Deletes the reminder identified by the given id asynchronously.
     *
     * @param id       identifier of the reminder to remove
     * @param callback optional callback invoked on the main thread when done
     */
    public void deleteReminderById(long id, Runnable callback) {
        deleteReminderById(id, callback, null);
    }

    public void deleteReminderById(long id, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> reminderDao.deleteById(id), callback, errorCallback);
    }

    /**
     * Retrieves the PPFD target range for the given species key asynchronously.
     *
     * @param speciesKey identifier of the species
     * @param callback   invoked with the resulting target on the main thread
     */
    public void getSpeciesTarget(String speciesKey, Consumer<SpeciesTarget> callback) {
        getSpeciesTarget(speciesKey, callback, null);
    }

    public void getSpeciesTarget(String speciesKey, Consumer<SpeciesTarget> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                SpeciesTarget result = speciesTargetDao.findBySpeciesKey(speciesKey);
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
     * Retrieves all species targets asynchronously.
     *
     * @param callback invoked with the resulting list on the main thread
     */
    public void getAllSpeciesTargets(Consumer<List<SpeciesTarget>> callback) {
        getAllSpeciesTargets(callback, null);
    }

    public void getAllSpeciesTargets(Consumer<List<SpeciesTarget>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<SpeciesTarget> result = speciesTargetDao.getAll();
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
     * Inserts or updates a species target asynchronously.
     *
     * @param target   the {@link SpeciesTarget} to persist
     * @param callback optional callback invoked on the main thread when done
     */
    public void insertSpeciesTarget(SpeciesTarget target, Runnable callback) {
        insertSpeciesTarget(target, callback, null);
    }

    public void insertSpeciesTarget(SpeciesTarget target, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> speciesTargetDao.insert(target), callback, errorCallback);
    }

    /**
     * Deletes the species target identified by the given key asynchronously.
     *
     * @param speciesKey key of the species target to delete
     * @param callback   optional callback invoked on the main thread when done
     */
    public void deleteSpeciesTarget(String speciesKey, Runnable callback) {
        deleteSpeciesTarget(speciesKey, callback, null);
    }

    public void deleteSpeciesTarget(String speciesKey, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> speciesTargetDao.deleteBySpeciesKey(speciesKey), callback, errorCallback);
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
     * Retrieves all measurements for a plant asynchronously and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param callback invoked with the resulting list on the main thread
     */
    public void getMeasurementsForPlant(long plantId, Consumer<List<Measurement>> callback) {
        getMeasurementsForPlant(plantId, callback, null);
    }

    public void getMeasurementsForPlant(long plantId, Consumer<List<Measurement>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Measurement> result = measurementDao.getAllForPlant(plantId);
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
     * Retrieves recent measurements for a plant asynchronously and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param limit    maximum number of measurements to return
     * @param callback invoked with the resulting list on the main thread
     */
    public void recentMeasurementsForPlant(long plantId, int limit, Consumer<List<Measurement>> callback) {
        recentMeasurementsForPlant(plantId, limit, callback, null);
    }

    public void recentMeasurementsForPlant(long plantId, int limit, Consumer<List<Measurement>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Measurement> result = measurementDao.recentForPlant(plantId, limit);
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
     * Retrieves measurements for a plant recorded since the given timestamp and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param since    minimum timestamp (inclusive) of measurements to return
     * @param callback invoked with the resulting list on the main thread
     */
    public void measurementsForPlantSince(long plantId, long since, Consumer<List<Measurement>> callback) {
        measurementsForPlantSince(plantId, since, callback, null);
    }

    public void measurementsForPlantSince(long plantId, long since, Consumer<List<Measurement>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Measurement> result = measurementDao.getForPlantSince(plantId, since);
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
     * Retrieves measurements for a plant within the specified time range and delivers them on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param start    start of the time range (inclusive)
     * @param end      end of the time range (exclusive)
     * @param callback invoked with the resulting list on the main thread
     */
    public void measurementsForPlantInRange(long plantId, long start, long end, Consumer<List<Measurement>> callback) {
        measurementsForPlantInRange(plantId, start, end, callback, null);
    }

    public void measurementsForPlantInRange(long plantId, long start, long end, Consumer<List<Measurement>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Measurement> result = measurementDao.getForPlantInRange(plantId, start, end);
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
     * Calculates the summed PPFD for a plant within the specified time range and delivers
     * the result on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param start    start of the time range (inclusive)
     * @param end      end of the time range (exclusive)
     * @param callback invoked with the summed PPFD on the main thread
     */
    public void sumPpfdForRange(long plantId, long start, long end, Consumer<Float> callback) {
        sumPpfdForRange(plantId, start, end, callback, null);
    }

    public void sumPpfdForRange(long plantId, long start, long end, Consumer<Float> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Float result = measurementDao.sumPpfdForRange(plantId, start, end);
                float value = result != null ? result : 0f;
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(value));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
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
        countDaysWithData(plantId, start, end, callback, null);
    }

    public void countDaysWithData(long plantId, long start, long end, Consumer<Integer> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                int result = measurementDao.countDaysWithData(plantId, start, end);
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
     * Calculates the summed PPFD and day count for a plant within the specified time range
     * and delivers the result on the main thread.
     *
     * @param plantId  identifier of the plant
     * @param start    start of the time range (inclusive)
     * @param end      end of the time range (inclusive)
     * @param callback invoked with the aggregated result on the main thread
     */
    public void sumPpfdAndCountDays(long plantId, long start, long end, Consumer<MeasurementDao.SumAndDays> callback) {
        sumPpfdAndCountDays(plantId, start, end, callback, null);
    }

    public void sumPpfdAndCountDays(long plantId, long start, long end, Consumer<MeasurementDao.SumAndDays> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
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
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
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
        diaryEntriesForPlant(plantId, callback, null);
    }

    public void diaryEntriesForPlant(long plantId, Consumer<List<DiaryEntry>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<DiaryEntry> result = diaryDao.entriesForPlant(plantId);
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
     * Retrieves diary entries for a plant matching the given query.
     * Results are delivered on the main thread.
     *
     * @param plantId identifier of the plant
     * @param query   text to search in notes; empty string returns all entries
     * @param callback invoked with the resulting list on the main thread
     */
    public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback) {
        searchDiaryEntries(plantId, query, callback, null);
    }

    public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
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
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }
}
