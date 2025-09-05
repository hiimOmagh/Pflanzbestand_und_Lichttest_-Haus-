package de.oabidi.pflanzenbestandundlichttest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast receiver that reschedules pending reminders after a device reboot.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            PlantRepository repository = new PlantRepository(context.getApplicationContext());
            repository.getAllReminders(reminders -> {
                long now = System.currentTimeMillis();
                for (Reminder reminder : reminders) {
                    if (reminder.getTriggerAt() > now) {
                        ReminderScheduler.scheduleReminderAt(context, reminder.getTriggerAt(), reminder.getMessage(), reminder.getId());
                    } else {
                        repository.deleteReminderById(reminder.getId(), null);
                    }
                }
            });
        }
    }
}
