package de.oabidi.pflanzenbestandundlichttest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.function.Consumer;

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

    /** Extra containing the associated plant identifier. */
    public static final String EXTRA_PLANT_ID = "extra_plant_id";

    private ReminderScheduler() {
        // no instances
    }

    /**
     * Schedule a reminder to be shown after the specified number of days.
     *
     * @param context       context used to access system services
     * @param days          number of days until the reminder should trigger
     * @param message       message displayed in the reminder notification
     * @param plantId       identifier of the related plant
     * @param errorCallback callback invoked when scheduling the reminder fails
     * @return {@code true} if the reminder was scheduled, {@code false} if the input was invalid
     */
    public static boolean scheduleReminder(Context context, int days, String message, long plantId,
                                           Consumer<Exception> errorCallback) {
        if (days <= 0) {
            Log.w("ReminderScheduler", "Days must be positive");
            return false;
        }
        long triggerAt = System.currentTimeMillis() + days * AlarmManager.INTERVAL_DAY;
        PlantRepository repository = ((PlantApp) context.getApplicationContext()).getRepository();
        Reminder reminder = new Reminder(triggerAt, message, plantId);
        repository.insertReminder(reminder,
            () -> scheduleReminderAt(context, triggerAt, message, reminder.getId(), plantId),
            errorCallback);
        return true;
    }

    public static void scheduleReminderAt(Context context, long triggerAt, String message, long id, long plantId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_SHOW_REMINDER);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_PLANT_ID, plantId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            (int) id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        sendWidgetUpdateBroadcast(context);
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
        sendWidgetUpdateBroadcast(context);
    }

    private static void sendWidgetUpdateBroadcast(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(context, ReminderWidgetProvider.class));
        Intent update = new Intent(context, ReminderWidgetProvider.class);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(update);
    }
}
