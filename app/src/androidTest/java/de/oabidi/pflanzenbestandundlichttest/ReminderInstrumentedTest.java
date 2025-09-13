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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests verifying reminder scheduling and rescheduling behavior.
 */
@RunWith(RobolectricTestRunner.class)
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
    public void reminderTriggersNotification() {
        Context context = ApplicationProvider.getApplicationContext();
        long triggerAt = System.currentTimeMillis() + 1000;

        assertEquals(restoredReminder.getPlantId(), scheduledIntent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1));

        ShadowSystemClock.advanceBy(Duration.ofSeconds(2));

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

        BootReceiver receiver = new BootReceiver(new PlantRepository(context));
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
