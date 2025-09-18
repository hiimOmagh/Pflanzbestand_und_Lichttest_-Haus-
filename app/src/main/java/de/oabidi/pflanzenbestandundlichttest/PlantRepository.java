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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
    private final Context context; // This will be the application context
    private final ExecutorService ioExecutor;
    private static final Pattern UNSUPPORTED_CHARS = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern RESERVED_FTS = Pattern.compile("\\b(?:AND|OR|NOT|NEAR)\\b", Pattern.CASE_INSENSITIVE);

    // Helper method to get ExecutorService and perform checks
    private static ExecutorService getExecutorFromProvider(Context originalContext) {
        Context appContext = originalContext.getApplicationContext();
        if (!(appContext instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        return ((ExecutorProvider) appContext).getIoExecutor();
    }

    /**
     * Creates a new repository instance.
     * <p>
     * The instance can be created on any thread, but its methods must be
     * executed on a background thread.
     *
     * @param context application context used to obtain the database
     */
    public PlantRepository(Context context) {
        this(context.getApplicationContext(), getExecutorFromProvider(context));
    }

    @VisibleForTesting
    PlantRepository(Context appContext, ExecutorService ioExecutor) {
        this.context = appContext; // appContext is already the application context
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
        runAsync(action, (Supplier<Runnable>) null, callback, errorCallback);
    }

    private void runAsync(Runnable action, Runnable postAction, Runnable callback,
                          Consumer<Exception> errorCallback) {
        runAsync(action, postAction == null ? null : () -> postAction, callback, errorCallback);
    }

    private void runAsync(Runnable action, Supplier<Runnable> postActionSupplier, Runnable callback,
                          Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                action.run();
                Runnable suppliedPostAction = null;
                if (postActionSupplier != null) {
                    suppliedPostAction = postActionSupplier.get();
                }
                if (suppliedPostAction == null) {
                    if (callback != null) {
                        mainHandler.post(callback);
                    }
                } else {
                    final Runnable postAction = suppliedPostAction;
                    ioExecutor.execute(() -> {
                        try {
                            postAction.run();
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
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    // ... (rest of the class remains the same) ...

    public void getAllPlants(Consumer<List<Plant>> callback) {
        getAllPlants(callback, null);
    }

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

    public void getPlant(long plantId, Consumer<Plant> callback) {
        getPlant(plantId, callback, null);
    }

    public void getPlant(long plantId, Consumer<Plant> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Plant plant = plantDao.findById(plantId);
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(plant));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    public void insert(Plant plant, Runnable callback) {
        insert(plant, callback, null);
    }

    public void insert(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> {
            final long id = plantDao.insert(plant);
            plant.setId(id);
        }, callback, errorCallback);
    }

    public void update(Plant plant, Runnable callback) {
        update(plant, callback, null);
    }

    public void update(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> plantDao.update(plant), callback, errorCallback);
    }

    public void delete(Plant plant, Runnable callback) {
        delete(plant, callback, null);
    }

    public void delete(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        AtomicReference<List<Reminder>> remindersRef = new AtomicReference<>(Collections.emptyList());
        runAsync(() -> {
            List<Reminder> reminders = reminderDao.getForPlant(plant.getId());
            remindersRef.set(reminders);
            plantDao.delete(plant);
            SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getLong(SettingsKeys.KEY_SELECTED_PLANT, -1) == plant.getId()) {
                prefs.edit().remove(SettingsKeys.KEY_SELECTED_PLANT).apply();
            }
        }, () -> {
            PhotoManager.deletePhoto(context, plant.getPhotoUri());
            for (Reminder reminder : remindersRef.get()) {
                ReminderScheduler.cancelReminder(context, reminder.getId());
            }
        }, callback, errorCallback);
    }

    public void insertMeasurement(Measurement measurement, Runnable callback) {
        insertMeasurement(measurement, callback, null);
    }

    public void insertMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        AtomicReference<Runnable> postActionRef = new AtomicReference<>();
        runAsync(() -> {
            measurementDao.insert(measurement);
            postActionRef.set(checkDliAlerts(measurement.getPlantId()));
        }, postActionRef::get, callback, errorCallback);
    }

    public void updateMeasurement(Measurement measurement, Runnable callback) {
        updateMeasurement(measurement, callback, null);
    }

    public void updateMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> measurementDao.update(measurement), callback, errorCallback);
    }

    public void deleteMeasurement(Measurement measurement, Runnable callback) {
        deleteMeasurement(measurement, callback, null);
    }

    public void deleteMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> measurementDao.delete(measurement), callback, errorCallback);
    }

    private Runnable checkDliAlerts(long plantId) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(SettingsKeys.KEY_DLI_ALERTS_ENABLED, false)) {
            return null;
        }
        String thresholdStr = prefs.getString(SettingsKeys.KEY_DLI_ALERT_THRESHOLD, "3");
        int threshold;
        try {
            threshold = Integer.parseInt(thresholdStr);
        } catch (NumberFormatException e) {
            threshold = 3;
        }
        if (threshold <= 0) {
            return null;
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
            return null;
        }
        SpeciesTarget target = speciesTargetDao.findBySpeciesKey(plant.getSpecies());
        if (target == null) {
            return null;
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
                    return null;
                }
            }
            long triggerAt = System.currentTimeMillis();
            Reminder reminder = new Reminder(triggerAt, message, plantId);
            long id = reminderDao.insert(reminder);
            final long finalTriggerAt = triggerAt;
            final String finalMessage = message;
            final long finalId = id;
            final long finalPlantId = plantId;
            return () -> ReminderScheduler.scheduleReminderAt(context, finalTriggerAt, finalMessage, finalId, finalPlantId);
        }
        return null;
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

    public void insertDiaryEntry(DiaryEntry entry, Runnable callback) {
        insertDiaryEntry(entry, callback, null);
    }

    public void insertDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> diaryDao.insert(entry), callback, errorCallback);
    }

    public void updateDiaryEntry(DiaryEntry entry, Runnable callback) {
        updateDiaryEntry(entry, callback, null);
    }

    public void updateDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> diaryDao.update(entry), callback, errorCallback);
    }

    public void deleteDiaryEntry(DiaryEntry entry, Runnable callback) {
        deleteDiaryEntry(entry, callback, null);
    }

    public void deleteDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        final String photoUri = entry.getPhotoUri();
        runAsync(() -> diaryDao.delete(entry), () -> PhotoManager.deletePhoto(context, photoUri),
            callback, errorCallback);
    }

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

    public void insertReminder(Reminder reminder, Runnable callback) {
        insertReminder(reminder, callback, null);
    }

    public void insertReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> {
            long id = reminderDao.insert(reminder);
            reminder.setId(id);
        }, callback, errorCallback);
    }

    public void updateReminder(Reminder reminder, Runnable callback) {
        updateReminder(reminder, callback, null);
    }

    public void updateReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> reminderDao.update(reminder), callback, errorCallback);
    }

    public void deleteReminderById(long id, Runnable callback) {
        deleteReminderById(id, callback, null);
    }

    public void deleteReminderById(long id, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> reminderDao.deleteById(id), callback, errorCallback);
    }

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

    public void insertSpeciesTarget(SpeciesTarget target, Runnable callback) {
        insertSpeciesTarget(target, callback, null);
    }

    public void insertSpeciesTarget(SpeciesTarget target, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> speciesTargetDao.insert(target), callback, errorCallback);
    }

    public void deleteSpeciesTarget(String speciesKey, Runnable callback) {
        deleteSpeciesTarget(speciesKey, callback, null);
    }

    public void deleteSpeciesTarget(String speciesKey, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> speciesTargetDao.deleteBySpeciesKey(speciesKey), callback, errorCallback);
    }

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
