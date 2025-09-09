package de.oabidi.pflanzenbestandundlichttest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Schedules periodic exports of the database to the app's external files directory.
 */
public class BackupScheduler extends BroadcastReceiver {
    private static final int REQUEST_CODE = 42;
    private static final long WEEK_INTERVAL = AlarmManager.INTERVAL_DAY * 7;

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        File dir = context.getExternalFilesDir(null);
        if (dir != null) {
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date());
            File out = new File(dir, "backup-" + timestamp + ".zip");
            Uri uri = Uri.fromFile(out);
            new ExportManager(context).export(uri, success -> result.finish());
        } else {
            result.finish();
        }
    }

    /**
     * Enable weekly backups.
     */
    public static void schedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        long triggerAt = System.currentTimeMillis() + WEEK_INTERVAL;
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            WEEK_INTERVAL,
            getPendingIntent(context)
        );
    }

    /**
     * Disable scheduled backups.
     */
    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(getPendingIntent(context));
        }
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, BackupScheduler.class);
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
