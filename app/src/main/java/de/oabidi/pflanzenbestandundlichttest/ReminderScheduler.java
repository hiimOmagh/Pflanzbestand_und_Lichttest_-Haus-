package de.oabidi.pflanzenbestandundlichttest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Schedules reminder alarms using the {@link AlarmManager}.
 */
public class ReminderScheduler {
    /** Action used for reminder broadcasts. */
    public static final String ACTION_SHOW_REMINDER =
        "de.oabidi.pflanzenbestandundlichttest.action.SHOW_REMINDER";

    /** Extra containing the message to display in the notification. */
    public static final String EXTRA_MESSAGE = "extra_message";

    /** Extra containing the reminder database identifier. */
    public static final String EXTRA_ID = "extra_id";

    private ReminderScheduler() {
        // no instances
    }

    /**
     * Schedule a reminder to be shown after the specified number of days.
     *
     * @param context context used to access system services
     * @param days    number of days until the reminder should trigger
     * @param message message displayed in the reminder notification
     */
    public static void scheduleReminder(Context context, int days, String message) {
        long triggerAt = System.currentTimeMillis() + days * AlarmManager.INTERVAL_DAY;
        PlantRepository repository = new PlantRepository(context.getApplicationContext());
        Reminder reminder = new Reminder(triggerAt, message);
        repository.insertReminder(reminder,
            () -> scheduleReminderAt(context, triggerAt, message, reminder.getId()));
    }

    public static void scheduleReminderAt(Context context, long triggerAt, String message, long id) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_SHOW_REMINDER);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_ID, id);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            (int) id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    /**
     * Cancels a previously scheduled reminder alarm.
     *
     * @param context context used to access system services
     * @param id      identifier of the reminder to cancel
     */
    public static void cancelReminder(Context context, long id) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_SHOW_REMINDER);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            (int) id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
    }
}
