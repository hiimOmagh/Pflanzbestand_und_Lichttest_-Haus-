package de.oabidi.pflanzenbestandundlichttest;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowNotificationManager;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Tests DLI alert notifications are triggered when measurements stay
 * outside the species target range for the configured streak threshold.
 */
@RunWith(RobolectricTestRunner.class)
public class DliAlertInstrumentedTest {
    private static void awaitDb(Runnable task) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                task.run();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Before
    public void setUp() {
        ShadowAlarmManager.reset();
        ShadowNotificationManager.reset();
        ShadowSystemClock.reset();
        ShadowAlarmManager.setAutoSchedule(true);
    }

    @Test
    public void lowDliStreakPostsNotification() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository repository = new PlantRepository(context);

        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(SettingsKeys.KEY_DLI_ALERTS_ENABLED, true)
            .putString(SettingsKeys.KEY_DLI_ALERT_THRESHOLD, "2")
            .putString(SettingsKeys.KEY_LIGHT_HOURS, "12")
            .apply();

        SpeciesTarget target = new SpeciesTarget("AlertSpecies", 100f, 200f);
        awaitDb(() -> PlantDatabase.getDatabase(context).speciesTargetDao().insert(target));

        Plant plant = new Plant("AlertPlant", null, "AlertSpecies", null, 0L, null);
        awaitDb(() -> {
            long id = PlantDatabase.getDatabase(context).plantDao().insert(plant);
            plant.setId(id);
        });

        long now = System.currentTimeMillis();
        long yesterday = now - 86400000L;

        Measurement first = new Measurement(plant.getId(), yesterday, 0f, 50f, null, null);
        awaitDb(() -> PlantDatabase.getDatabase(context).measurementDao().insert(first));

        Measurement second = new Measurement(plant.getId(), now, 0f, 50f, null, null);
        awaitDb(() -> PlantDatabase.getDatabase(context).measurementDao().insert(first));

        ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
        CountDownLatch latch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);
        assertEquals("Notification posted", 1, shadowNm.size());
    }
}
