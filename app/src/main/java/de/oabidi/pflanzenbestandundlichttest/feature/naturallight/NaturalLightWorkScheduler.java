package de.oabidi.pflanzenbestandundlichttest.feature.naturallight;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Schedules the {@link NaturalLightWorker} to run once per day.
 */
public final class NaturalLightWorkScheduler {
    private static final String WORK_NAME = "daily-natural-light";

    private NaturalLightWorkScheduler() {
    }

    public static void ensureScheduled(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(NaturalLightWorker.class,
            1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
