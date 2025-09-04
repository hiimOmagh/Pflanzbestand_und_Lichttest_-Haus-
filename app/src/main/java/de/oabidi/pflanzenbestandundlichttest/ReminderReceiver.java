package de.oabidi.pflanzenbestandundlichttest;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Receives reminder alarms and displays notifications with action buttons.
 */
public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "reminders";
    public static final String ACTION_MARK_DONE =
        "de.oabidi.pflanzenbestandundlichttest.action.MARK_DONE";
    public static final String ACTION_SNOOZE =
        "de.oabidi.pflanzenbestandundlichttest.action.SNOOZE";
    private static final String EXTRA_NOTIFICATION_ID = "notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_MARK_DONE.equals(action)) {
            int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
            NotificationManagerCompat.from(context).cancel(id);
            return;
        } else if (ACTION_SNOOZE.equals(action)) {
            String message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE);
            ReminderScheduler.scheduleReminder(context, 1, message);
            int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
            NotificationManagerCompat.from(context).cancel(id);
            return;
        }

        String message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE);
        createChannel(context);
        int notificationId = (int) System.currentTimeMillis();

        Intent doneIntent = new Intent(context, ReminderReceiver.class);
        doneIntent.setAction(ACTION_MARK_DONE);
        doneIntent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        PendingIntent donePending = PendingIntent.getBroadcast(
            context,
            notificationId,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        snoozeIntent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        snoozeIntent.putExtra(ReminderScheduler.EXTRA_MESSAGE, message);
        PendingIntent snoozePending = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.reminder_mark_done), donePending)
            .addAction(0, context.getString(R.string.reminder_snooze), snoozePending);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.enableLights(true);
                channel.setLightColor(Color.GREEN);
                manager.createNotificationChannel(channel);
            }
        }
    }
}
