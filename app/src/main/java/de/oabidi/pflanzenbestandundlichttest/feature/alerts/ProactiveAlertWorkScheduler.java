package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Helper utilities for scheduling proactive alert evaluations via WorkManager.
 */
public final class ProactiveAlertWorkScheduler {
    private static final String WORK_NAME = "proactive-care-evaluation";
    private static final long REPEAT_INTERVAL_HOURS = 6L;

    private ProactiveAlertWorkScheduler() {
        // no instances
    }

    public static void ensureScheduled(@NonNull Context context, boolean enabled) {
        if (enabled) {
            schedule(context);
        } else {
            cancel(context);
        }
    }

    public static void schedule(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ProactiveAlertWorker.class,
            REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build();
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(@NonNull Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
