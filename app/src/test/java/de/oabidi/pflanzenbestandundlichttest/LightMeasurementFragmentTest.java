package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfile;

/**
 * Tests for LightMeasurementFragment ensuring preferences are validated.
 */
@RunWith(RobolectricTestRunner.class)
public class LightMeasurementFragmentTest {

    @Test
    public void fragmentStartsWithZeroSampleSizePreference() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // Write invalid sample size to preferences
        context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(SettingsKeys.KEY_SAMPLE_SIZE, "0").apply();

        // Start fragment inside an activity
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        LightMeasurementFragment fragment = LightMeasurementFragment.newInstance(
            new PlantRepository(context, TestExecutors.newImmediateExecutor()));
        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        // Verify sample size was clamped to at least 1
        Field sampleSizeField = LightMeasurementFragment.class.getDeclaredField("sampleSize");
        sampleSizeField.setAccessible(true);
        assertEquals(1, sampleSizeField.getInt(fragment));
    }

    @Test
    public void fragmentLoadsCalibrationFromRepository() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository repo = new PlantRepository(context, TestExecutors.newImmediateExecutor());

        Plant plant = new Plant();
        plant.setName("Test");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repo.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        LedProfile profile = new LedProfile();
        profile.setName("FragmentProfile");
        profile.setCalibrationFactors(new HashMap<>());

        final LedProfile[] stored = new LedProfile[1];
        CountDownLatch profileLatch = new CountDownLatch(1);
        repo.createLedProfile(profile, created -> {
            stored[0] = created;
            profileLatch.countDown();
        }, e -> fail("Profile creation failed"));
        awaitLatch(profileLatch);
        assertNotNull(stored[0]);

        CountDownLatch assignLatch = new CountDownLatch(1);
        repo.assignLedProfileToPlant(plant.getId(), stored[0].getId(), assignLatch::countDown,
            e -> fail("Assignment failed"));
        awaitLatch(assignLatch);

        CountDownLatch saveLatch = new CountDownLatch(1);
        repo.saveLedCalibrationForPlant(plant.getId(), 0.05f, 0.07f, saveLatch::countDown);
        awaitLatch(saveLatch);

        context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(SettingsKeys.KEY_SELECTED_PLANT, plant.getId()).apply();

        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        LightMeasurementFragment fragment = LightMeasurementFragment.newInstance(repo);
        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        // Allow asynchronous callbacks to execute
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Field presenterField = LightMeasurementFragment.class.getDeclaredField("presenter");
        presenterField.setAccessible(true);
        LightMeasurementPresenter presenter = (LightMeasurementPresenter) presenterField.get(fragment);

        Field ambientField = LightMeasurementPresenter.class.getDeclaredField("calibrationFactor");
        ambientField.setAccessible(true);
        Field cameraField = LightMeasurementPresenter.class.getDeclaredField("cameraCalibrationFactor");
        cameraField.setAccessible(true);

        assertEquals(0.05f, ambientField.getFloat(presenter), 0.0001f);
        assertEquals(0.07f, cameraField.getFloat(presenter), 0.0001f);
    }

    private void awaitLatch(CountDownLatch latch) throws InterruptedException {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (latch.await(100, TimeUnit.MILLISECONDS)) {
                return;
            }
        }
        fail("Callback not invoked");
    }
}
