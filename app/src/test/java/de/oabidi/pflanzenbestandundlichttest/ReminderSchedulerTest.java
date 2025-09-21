package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestDriver;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

/**
 * Tests for {@link ReminderScheduler} verifying the WorkManager and
 * AlarmManager scheduling paths.
 */
@RunWith(RobolectricTestRunner.class)
public class ReminderSchedulerTest {
    private Context context;
    private Application application;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        application = (Application) context;
        Shadows.shadowOf(application).getBroadcastIntents().clear();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.S)
    public void scheduleReminder_usesWorkManagerOnAndroid12Plus() throws Exception {
        Configuration configuration = new Configuration.Builder()
            .setExecutor(new SynchronousExecutor())
            .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration);

        long reminderId = 42L;
        long plantId = 99L;
        long triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
        ReminderScheduler.scheduleReminderAt(context, triggerAt, "Water", reminderId, plantId);

        ShadowApplication shadowApp = Shadows.shadowOf(application);
        List<Intent> broadcasts = shadowApp.getBroadcastIntents();
        assertTrue("Widget update broadcast sent",
            broadcasts.stream().anyMatch(intent ->
                AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())));

        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager
            .getWorkInfosForUniqueWork(ReminderWorkManager.workName(reminderId))
            .get();
        assertEquals(1, workInfos.size());
        WorkInfo workInfo = workInfos.get(0);
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.getState());

        Data input = workInfo.getInputData();
        assertEquals("Water", input.getString(ReminderWorker.KEY_MESSAGE));
        assertEquals(reminderId, input.getLong(ReminderWorker.KEY_ID, -1));
        assertEquals(plantId, input.getLong(ReminderWorker.KEY_PLANT_ID, -1));

        TestDriver testDriver = WorkManagerTestInitHelper.getTestDriver(context);
        assertNotNull(testDriver);
        int beforeCount = broadcasts.size();
        testDriver.setInitialDelayMet(workInfo.getId());

        List<Intent> afterBroadcasts = shadowApp.getBroadcastIntents();
        assertEquals(beforeCount + 1, afterBroadcasts.size());
        Intent reminderIntent = afterBroadcasts.get(afterBroadcasts.size() - 1);
        assertEquals(ReminderScheduler.ACTION_SHOW_REMINDER, reminderIntent.getAction());
        assertEquals("Water", reminderIntent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE));
        assertEquals(reminderId, reminderIntent.getLongExtra(ReminderScheduler.EXTRA_ID, -1));
        assertEquals(plantId, reminderIntent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.S)
    public void cancelReminder_cancelsWorkRequestOnAndroid12Plus() throws Exception {
        Configuration configuration = new Configuration.Builder()
            .setExecutor(new SynchronousExecutor())
            .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration);

        long reminderId = 7L;
        long triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        ReminderScheduler.scheduleReminderAt(context, triggerAt, "Fertilize", reminderId, 3L);

        ReminderScheduler.cancelReminder(context, reminderId);

        WorkManager workManager = WorkManager.getInstance(context);
        List<WorkInfo> workInfos = workManager
            .getWorkInfosForUniqueWork(ReminderWorkManager.workName(reminderId))
            .get();
        assertTrue(workInfos.isEmpty() ||
            workInfos.stream().allMatch(info -> info.getState() == WorkInfo.State.CANCELLED));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void scheduleReminder_fallsBackToAlarmManagerBelowAndroid12() {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ShadowAlarmManager.reset();

        long reminderId = 11L;
        long triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
        ReminderScheduler.scheduleReminderAt(context, triggerAt, "Repot", reminderId, 1L);

        assertNotNull("Alarm scheduled", shadowAlarmManager.getNextScheduledAlarm());

        ReminderScheduler.cancelReminder(context, reminderId);
        assertNull("Alarm cancelled", shadowAlarmManager.getNextScheduledAlarm());
    }
}
