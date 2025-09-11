package de.oabidi.pflanzenbestandundlichttest;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

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

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        PlantRepository repository = ((PlantApp) context.getApplicationContext()).getRepository();
        if (ACTION_MARK_DONE.equals(action)) {
            int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
            long reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1);
            NotificationManagerCompat.from(context).cancel(id);
            repository.deleteReminderById(reminderId, null);
            return;
        } else if (ACTION_SNOOZE.equals(action)) {
            String message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE);
            long plantId = intent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1);
            boolean scheduled = ReminderScheduler.scheduleReminder(context, 1, message, plantId);
            if (!scheduled) {
                Log.w("ReminderReceiver", "Failed to reschedule reminder for snooze");
            }
            int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
            long reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1);
            NotificationManagerCompat.from(context).cancel(id);
            repository.deleteReminderById(reminderId, null);
            return;
        }

        String message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE);
        long reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1);
        long plantId = intent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1);
        createChannel(context);
        int notificationId = (int) System.currentTimeMillis();

        PlantDatabase.databaseWriteExecutor.execute(() -> {
            repository.deleteReminderById(reminderId, null);
            Plant plant = null;
            try {
                plant = repository.getPlant(plantId).get();
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

            Intent contentIntent = new Intent(context, PlantDetailActivity.class);
            if (plant != null) {
                contentIntent.putExtra("plantId", plant.getId());
                contentIntent.putExtra("name", plant.getName());
                contentIntent.putExtra("description", plant.getDescription());
                contentIntent.putExtra("species", plant.getSpecies());
                contentIntent.putExtra("locationHint", plant.getLocationHint());
                contentIntent.putExtra("acquiredAtEpoch", plant.getAcquiredAtEpoch());
                String photo = plant.getPhotoUri() != null ? plant.getPhotoUri().toString() : "";
                contentIntent.putExtra("photoUri", photo);
            } else {
                contentIntent.putExtra("plantId", plantId);
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
