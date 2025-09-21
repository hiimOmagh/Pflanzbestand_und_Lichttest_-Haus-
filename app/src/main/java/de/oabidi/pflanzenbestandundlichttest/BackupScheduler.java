package de.oabidi.pflanzenbestandundlichttest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.util.Arrays;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Schedules periodic exports of the database to the app's external files directory.
 */
public class BackupScheduler extends BroadcastReceiver {
    private static final int REQUEST_CODE = 42;
    private static final long WEEK_INTERVAL = AlarmManager.INTERVAL_DAY * 7;
    private static final int RETENTION_COUNT = 5;
    private final PlantRepository repository;

    public BackupScheduler() {
        this(null);
    }

    public BackupScheduler(PlantRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        File dir = context.getExternalFilesDir(null);
        if (dir != null) {
            cleanupOldBackups(dir);
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date());
            File out = new File(dir, "backup-" + timestamp + ".json.zip");
            Uri uri = Uri.fromFile(out);
            PlantRepository repo = repository != null
                ? repository
                : RepositoryProvider.getRepository(context);
            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof ExecutorProvider)) {
                throw new IllegalStateException("Application context does not implement ExecutorProvider");
            }
            ExecutorService executor = ((ExecutorProvider) appContext).getIoExecutor();
            new ExportManager(context, repo, executor).exportJson(uri, success -> result.finish());
        } else {
            result.finish();
        }
    }

    private static void cleanupOldBackups(File dir) {
        File[] backups = dir.listFiles((d, name) -> name.startsWith("backup-") && name.endsWith(".zip"));
        if (backups == null) return;
        Arrays.sort(backups, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        if (backups.length <= RETENTION_COUNT) return;
        for (int i = RETENTION_COUNT; i < backups.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            backups[i].delete();
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
