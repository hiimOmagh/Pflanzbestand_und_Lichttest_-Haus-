package de.oabidi.pflanzenbestandundlichttest.core.system.reminder;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Worker responsible for dispatching reminder broadcasts when WorkManager
 * alarms fire.
 */
public class ReminderWorker extends Worker {
    static final String KEY_MESSAGE = "key_message";
    static final String KEY_ID = "key_id";
    static final String KEY_PLANT_ID = "key_plant_id";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderScheduler.ACTION_SHOW_REMINDER);
        intent.putExtra(ReminderScheduler.EXTRA_MESSAGE, getInputData().getString(KEY_MESSAGE));
        intent.putExtra(ReminderScheduler.EXTRA_ID, getInputData().getLong(KEY_ID, -1));
        intent.putExtra(ReminderScheduler.EXTRA_PLANT_ID, getInputData().getLong(KEY_PLANT_ID, -1));
        context.sendBroadcast(intent);
        return Result.success();
    }
}
