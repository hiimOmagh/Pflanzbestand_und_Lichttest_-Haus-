package de.oabidi.pflanzenbestandundlichttest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Broadcast receiver that reschedules pending reminders after a device reboot.
 */
public class BootReceiver extends BroadcastReceiver {
    private final PlantRepository repository;

    public BootReceiver() {
        this(null);
    }

    public BootReceiver(PlantRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getBoolean(SettingsKeys.KEY_AUTO_BACKUP, false)) {
                BackupScheduler.schedule(context);
            }
            PendingResult result = goAsync();
            PlantRepository repo = repository != null ? repository : new PlantRepository(context.getApplicationContext());
            repo.getAllReminders(reminders -> {
                long now = System.currentTimeMillis();
                for (Reminder reminder : reminders) {
                    if (reminder.getTriggerAt() > now) {
                        ReminderScheduler.scheduleReminderAt(context, reminder.getTriggerAt(), reminder.getMessage(), reminder.getId(), reminder.getPlantId());
                    } else {
                        repo.deleteReminderById(reminder.getId(), null);
                    }
                }
                result.finish();
            });
        }
    }
}
