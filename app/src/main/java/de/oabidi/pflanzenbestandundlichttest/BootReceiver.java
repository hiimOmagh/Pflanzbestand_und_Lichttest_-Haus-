package de.oabidi.pflanzenbestandundlichttest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Broadcast receiver that reschedules pending reminders after a device reboot.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            PlantDatabase.databaseWriteExecutor.execute(() -> {
                PlantDatabase db = PlantDatabase.getDatabase(context);
                List<Reminder> reminders = db.reminderDao().getAll();
                long now = System.currentTimeMillis();
                for (Reminder reminder : reminders) {
                    if (reminder.getTriggerAt() > now) {
                        ReminderScheduler.scheduleReminderAt(context, reminder.getTriggerAt(), reminder.getMessage(), reminder.getId());
                    } else {
                        db.reminderDao().deleteById(reminder.getId());
                    }
                }
            });
        }
    }
}
