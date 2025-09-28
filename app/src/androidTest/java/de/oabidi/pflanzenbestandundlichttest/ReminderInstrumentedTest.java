package de.oabidi.pflanzenbestandundlichttest;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowNotificationManager;
import org.robolectric.shadows.ShadowPendingIntent;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

import de.oabidi.pflanzenbestandundlichttest.core.data.db.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;

/**
 * Instrumented tests verifying reminder scheduling and rescheduling behavior.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class ReminderInstrumentedTest {

    @Before
    public void setUp() {
        ShadowAlarmManager.reset();
        ShadowNotificationManager.reset();
        ShadowSystemClock.reset();
        ShadowAlarmManager.setAutoSchedule(true);
    }

    /**
     * Schedule a reminder and advance time to ensure a notification is posted.
     */
    @Test
    public void reminderTriggersNotification() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase db = PlantDatabase.getDatabase(context);
        long triggerAt = System.currentTimeMillis() + 1000;

        Plant plant = new Plant("Alarm Plant", null, null, null, System.currentTimeMillis(), null);
        long[] plantIdHolder = new long[1];
        CountDownLatch plantLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            plantIdHolder[0] = db.plantDao().insert(plant);
            plantLatch.countDown();
        });
        assertTrue("Plant inserted", plantLatch.await(2, TimeUnit.SECONDS));
        long plantId = plantIdHolder[0];

        Reminder reminder = new Reminder(triggerAt, "Water test plant", plantId);
        long[] reminderIdHolder = new long[1];
        CountDownLatch reminderLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            reminderIdHolder[0] = db.reminderDao().insert(reminder);
            reminderLatch.countDown();
        });
        assertTrue("Reminder inserted", reminderLatch.await(2, TimeUnit.SECONDS));
        long reminderId = reminderIdHolder[0];

        ReminderScheduler.scheduleReminderAt(context, triggerAt, reminder.getMessage(), reminderId, plantId);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAm = Shadows.shadowOf(am);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAm.getNextScheduledAlarm();
        assertNotNull("Alarm scheduled", alarm);

        ShadowPendingIntent pending = Shadows.shadowOf(alarm.operation);
        Intent scheduledIntent = pending.getSavedIntent();
        assertNotNull("Scheduled intent", scheduledIntent);

        Reminder[] restoredHolder = new Reminder[1];
        CountDownLatch loadLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Reminder> reminders = db.reminderDao().getAll();
            restoredHolder[0] = reminders.isEmpty() ? null : reminders.get(0);
            loadLatch.countDown();
        });
        assertTrue("Reminder restored", loadLatch.await(2, TimeUnit.SECONDS));
        Reminder restoredReminder = restoredHolder[0];
        assertNotNull("Reminder retrieved", restoredReminder);

        assertEquals(restoredReminder.getMessage(), scheduledIntent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE));
        assertEquals(restoredReminder.getId(), scheduledIntent.getLongExtra(ReminderScheduler.EXTRA_ID, -1));
        assertEquals(restoredReminder.getPlantId(), scheduledIntent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1));

        ShadowSystemClock.advanceBy(Duration.ofSeconds(2));
        alarm.operation.send();

        CountDownLatch completionLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(completionLatch::countDown);
        assertTrue("Receiver completed", completionLatch.await(2, TimeUnit.SECONDS));

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);
        assertEquals("Notification posted", 1, shadowNm.size());
    }

    /**
     * Simulate a boot event and ensure the reminder is rescheduled.
     */
    @Test
    public void bootReceiverReschedulesReminder() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        long triggerAt = System.currentTimeMillis() + 60000;
        Reminder reminder = new Reminder(triggerAt, "Water later", 1);

        CountDownLatch insertLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            PlantDatabase.getDatabase(context).reminderDao().insert(reminder);
            insertLatch.countDown();
        });
        assertTrue("Reminder inserted", insertLatch.await(2, TimeUnit.SECONDS));

        Context appContext = context.getApplicationContext();
        BootReceiver receiver = new BootReceiver(
            RepositoryProvider.getReminderRepository(appContext));
        receiver.onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));

        CountDownLatch latch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAm = Shadows.shadowOf(am);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAm.getNextScheduledAlarm();
        assertNotNull("Alarm scheduled", alarm);
        assertEquals("Trigger time preserved", triggerAt, alarm.getTriggerAtMs());
        ShadowPendingIntent pending = Shadows.shadowOf(alarm.operation);
        Intent scheduledIntent = pending.getSavedIntent();
        assertEquals("Message preserved", "Water later", scheduledIntent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE));
        assertEquals("PlantId preserved", 1, scheduledIntent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1));
    }
}
