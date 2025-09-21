package de.oabidi.pflanzenbestandundlichttest;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Helper that routes reminder scheduling through {@link WorkManager} on
 * Android 12 (API level 31) and higher. Older devices continue to fall back to
 * {@link android.app.AlarmManager}.
 */
final class ReminderWorkManager {
    static final String WORK_NAME_PREFIX = "reminder-";

    private ReminderWorkManager() {
        // no instances
    }

    static void schedule(@NonNull Context context, long triggerAt, @NonNull String message,
                         long id, long plantId) {
        WorkManager workManager = WorkManager.getInstance(context);
        Data input = new Data.Builder()
            .putString(ReminderWorker.KEY_MESSAGE, message)
            .putLong(ReminderWorker.KEY_ID, id)
            .putLong(ReminderWorker.KEY_PLANT_ID, plantId)
            .build();
        long delay = Math.max(0L, triggerAt - System.currentTimeMillis());
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(ReminderWorker.class)
            .setInputData(input)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(workName(id));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (canScheduleExactAlarms(context)) {
                setScheduleRequestedAt(builder, triggerAt);
            } else if (delay <= 0L) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
            }
        }
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, builder.build());
    }

    static void cancel(@NonNull Context context, long id) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id));
    }

    static String workName(long id) {
        return WORK_NAME_PREFIX + id;
    }

    private static boolean canScheduleExactAlarms(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName("androidx.work.ExactAlarmPermission");
            Method method = clazz.getMethod("canScheduleExactAlarms", Context.class);
            Object result = method.invoke(null, context);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to checking the AlarmManager directly below.
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    @SuppressLint("RestrictedApi")
    private static void setScheduleRequestedAt(@NonNull OneTimeWorkRequest.Builder builder,
                                               long triggerAt) {
        builder.setScheduleRequestedAt(triggerAt, TimeUnit.MILLISECONDS);
    }
}
