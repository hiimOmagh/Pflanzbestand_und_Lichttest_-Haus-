package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.LightMath;
import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.MeasurementDao;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDao;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.Reminder;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderDao;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderScheduler;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTargetDao;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Repository providing access to {@link Measurement} persistence APIs.
 */
public class MeasurementRepository extends BaseRepository {
    private final MeasurementDao measurementDao;
    private final PlantDao plantDao;
    private final SpeciesTargetDao speciesTargetDao;
    private final ReminderDao reminderDao;

    public MeasurementRepository(Context context, Handler mainHandler, ExecutorService ioExecutor,
                                 MeasurementDao measurementDao, PlantDao plantDao,
                                 SpeciesTargetDao speciesTargetDao, ReminderDao reminderDao) {
        super(context, mainHandler, ioExecutor);
        this.measurementDao = Objects.requireNonNull(measurementDao, "measurementDao");
        this.plantDao = Objects.requireNonNull(plantDao, "plantDao");
        this.speciesTargetDao = Objects.requireNonNull(speciesTargetDao, "speciesTargetDao");
        this.reminderDao = Objects.requireNonNull(reminderDao, "reminderDao");
    }

    public void insertMeasurement(Measurement measurement, Runnable callback,
                                  @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(measurement, "measurement");
        AtomicReference<Runnable> postActionRef = new AtomicReference<>();
        runAsync(() -> {
            measurementDao.insert(measurement);
            postActionRef.set(checkDliAlerts(measurement.getPlantId()));
        }, postActionRef::get, callback, errorCallback);
    }

    public void insertMeasurement(Measurement measurement, Runnable callback) {
        insertMeasurement(measurement, callback, null);
    }

    public void updateMeasurement(Measurement measurement, Runnable callback,
                                  @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(measurement, "measurement");
        runAsync(() -> measurementDao.update(measurement), callback, errorCallback);
    }

    public void updateMeasurement(Measurement measurement, Runnable callback) {
        updateMeasurement(measurement, callback, null);
    }

    public void deleteMeasurement(Measurement measurement, Runnable callback,
                                  @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(measurement, "measurement");
        runAsync(() -> measurementDao.delete(measurement), callback, errorCallback);
    }

    public void deleteMeasurement(Measurement measurement, Runnable callback) {
        deleteMeasurement(measurement, callback, null);
    }

    public void getMeasurementsForPlant(long plantId, Consumer<List<Measurement>> callback,
                                        @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> measurementDao.getAllForPlant(plantId), callback, errorCallback);
    }

    public void getMeasurementsForPlant(long plantId, Consumer<List<Measurement>> callback) {
        getMeasurementsForPlant(plantId, callback, null);
    }

    public void recentMeasurementsForPlant(long plantId, int limit, Consumer<List<Measurement>> callback,
                                           @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> measurementDao.recentForPlant(plantId, limit), callback, errorCallback);
    }

    public void recentMeasurementsForPlant(long plantId, int limit, Consumer<List<Measurement>> callback) {
        recentMeasurementsForPlant(plantId, limit, callback, null);
    }

    public void measurementsForPlantSince(long plantId, long since, Consumer<List<Measurement>> callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> measurementDao.getForPlantSince(plantId, since), callback, errorCallback);
    }

    public void measurementsForPlantSince(long plantId, long since, Consumer<List<Measurement>> callback) {
        measurementsForPlantSince(plantId, since, callback, null);
    }

    public void measurementsForPlantInRange(long plantId, long start, long end,
                                            Consumer<List<Measurement>> callback,
                                            @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> measurementDao.getForPlantInRange(plantId, start, end), callback, errorCallback);
    }

    public void measurementsForPlantInRange(long plantId, long start, long end,
                                            Consumer<List<Measurement>> callback) {
        measurementsForPlantInRange(plantId, start, end, callback, null);
    }

    public void sumPpfdForRange(long plantId, long start, long end, Consumer<Float> callback,
                                @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> {
            Float value = measurementDao.sumPpfdForRange(plantId, start, end);
            return value != null ? value : 0f;
        }, callback, errorCallback);
    }

    public void sumPpfdForRange(long plantId, long start, long end, Consumer<Float> callback) {
        sumPpfdForRange(plantId, start, end, callback, null);
    }

    public void countDaysWithData(long plantId, long start, long end, Consumer<Integer> callback,
                                  @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> measurementDao.countDaysWithData(plantId, start, end), callback, errorCallback);
    }

    public void countDaysWithData(long plantId, long start, long end, Consumer<Integer> callback) {
        countDaysWithData(plantId, start, end, callback, null);
    }

    public void sumPpfdAndCountDays(long plantId, long start, long end,
                                    Consumer<MeasurementDao.SumAndDays> callback,
                                    @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> {
            MeasurementDao.SumAndDays result = measurementDao.sumPpfdAndCountDays(plantId, start, end);
            if (result == null) {
                result = new MeasurementDao.SumAndDays();
            }
            if (result.sum == null) {
                result.sum = 0f;
            }
            return result;
        }, callback, errorCallback);
    }

    public void sumPpfdAndCountDays(long plantId, long start, long end,
                                    Consumer<MeasurementDao.SumAndDays> callback) {
        sumPpfdAndCountDays(plantId, start, end, callback, null);
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

        SpeciesTarget.GrowthStage stage = target.getDefaultStage();
        SpeciesTarget.StageTarget stageTarget = target.getStage(stage);
        Float minDli = resolveDli(stageTarget != null ? stageTarget.getDliMin() : null,
            stageTarget != null ? stageTarget.getPpfdMin() : null, lightHours);
        Float maxDli = resolveDli(stageTarget != null ? stageTarget.getDliMax() : null,
            stageTarget != null ? stageTarget.getPpfdMax() : null, lightHours);
        if (minDli == null || maxDli == null) {
            return null;
        }

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

    @Nullable
    private Float resolveDli(@Nullable Float dli, @Nullable Float ppfd, float lightHours) {
        if (dli != null) {
            return dli;
        }
        if (ppfd != null) {
            return LightMath.dliFromPpfd(ppfd, lightHours);
        }
        return null;
    }
}
