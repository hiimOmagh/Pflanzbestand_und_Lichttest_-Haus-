package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Publishes proactive alerts as user notifications.
 */
public class ProactiveAlertNotifier {
    public static final String CHANNEL_ID = "proactive_care";
    private static volatile Callback callback;

    private final Context context;

    public ProactiveAlertNotifier(Context context) {
        this.context = context.getApplicationContext();
    }

    public static void setCallback(@Nullable Callback callback) {
        ProactiveAlertNotifier.callback = callback;
    }

    public void dispatch(List<ProactiveAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return;
        }
        for (ProactiveAlert alert : alerts) {
            if (alert.getSeverity() == ProactiveAlert.Severity.CRITICAL) {
                postNotification(alert);
            }
        }
    }

    private void postNotification(ProactiveAlert alert) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ensureChannel();
        Plant plant = alert.getPlant();
        Intent intent = MainActivity.createPlantDetailIntent(context, plant);
        PendingIntent contentIntent = PendingIntent.getActivity(context,
            (int) (plant.getId() & 0x7FFFFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action viewAction = new NotificationCompat.Action(0,
            context.getString(R.string.alert_view_details), contentIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.alert_notification_title, plant.getName()))
            .setContentText(alert.getMessage())
            .setStyle(new NotificationCompat.BigTextStyle().bigText(alert.getMessage()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(viewAction);

        int notificationId = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        Callback activeCallback = callback;
        if (activeCallback != null) {
            activeCallback.onNotificationDispatched(alert);
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH);
        channel.enableLights(true);
        channel.setLightColor(Color.RED);
        manager.createNotificationChannel(channel);
    }

    /**
     * Callback used for instrumentation tests.
     */
    public interface Callback {
        void onNotificationDispatched(ProactiveAlert alert);
    }
}
