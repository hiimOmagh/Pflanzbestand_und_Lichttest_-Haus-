package de.oabidi.pflanzenbestandundlichttest;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.ExecutionException;

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
    private final PlantRepository repository;

    public ReminderReceiver() {
        this(null);
    }

    public ReminderReceiver(PlantRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        PlantRepository repo = repository != null ? repository : new PlantRepository(context.getApplicationContext());
        if (ACTION_MARK_DONE.equals(action)) {
            int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
            long reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1);
            NotificationManagerCompat.from(context).cancel(id);
            repo.deleteReminderById(reminderId, null);
            return;
        } else if (ACTION_SNOOZE.equals(action)) {
            String message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE);
            long plantId = intent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1);
            boolean scheduled = ReminderScheduler.scheduleReminder(
                context,
                repo,
                1,
                message,
                plantId,
                e -> Log.w("ReminderReceiver", "Failed to reschedule reminder for snooze", e)
            );
            if (!scheduled) {
                Log.w("ReminderReceiver", "Failed to reschedule reminder for snooze");
            }
            int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
            long reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1);
            NotificationManagerCompat.from(context).cancel(id);
            repo.deleteReminderById(reminderId, null);
            return;
        }

        String message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE);
        long reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1);
        long plantId = intent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1);
        createChannel(context);
        int notificationId = (int) System.currentTimeMillis();

        PlantDatabase.databaseWriteExecutor.execute(() -> {
            repo.deleteReminderById(reminderId, null);
            Plant plant = null;
            try {
                plant = repo.getPlant(plantId).get();
            } catch (ExecutionException | InterruptedException e) {
                // Ignore and proceed without plant details
            }

            Intent doneIntent = new Intent(context, ReminderReceiver.class);
            doneIntent.setAction(ACTION_MARK_DONE);
            doneIntent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
            doneIntent.putExtra(ReminderScheduler.EXTRA_ID, reminderId);
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
            snoozeIntent.putExtra(ReminderScheduler.EXTRA_ID, reminderId);
            snoozeIntent.putExtra(ReminderScheduler.EXTRA_PLANT_ID, plantId);
            PendingIntent snoozePending = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Intent contentIntent;
            if (plant != null) {
                contentIntent = MainActivity.createPlantDetailIntent(context, plant);
            } else {
                contentIntent = new Intent(context, PlantDetailActivity.class);
            }
            PendingIntent contentPending = PendingIntent.getActivity(
                context,
                notificationId + 2,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(contentPending)
                .addAction(0, context.getString(R.string.reminder_mark_done), donePending)
                .addAction(0, context.getString(R.string.reminder_snooze), snoozePending);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        });
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
