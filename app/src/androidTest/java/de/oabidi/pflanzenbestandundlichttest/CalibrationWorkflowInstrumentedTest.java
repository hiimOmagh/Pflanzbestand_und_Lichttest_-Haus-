package de.oabidi.pflanzenbestandundlichttest;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfileAssociation;

/**
 * Espresso test covering the end-to-end calibration workflow.
 */
@RunWith(AndroidJUnit4.class)
public class CalibrationWorkflowInstrumentedTest {

    @Test
    public void calibrationRoundTripPersistsValues() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase db = PlantDatabase.getDatabase(context);

        CountDownLatch clearLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            db.clearAllTables();
            clearLatch.countDown();
        });
        assertTrue(clearLatch.await(2, TimeUnit.SECONDS));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Create a plant that we can calibrate
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText(context.getString(R.string.menu_add_plant))).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText("Calibration Plant"), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            SystemClock.sleep(600);

            CountDownLatch profileLatch = new CountDownLatch(1);
            PlantDatabase.databaseWriteExecutor.execute(() -> {
                List<Plant> plants = db.plantDao().getAll();
                if (!plants.isEmpty()) {
                    Plant inserted = plants.get(0);
                    LedProfile profile = new LedProfile();
                    profile.setName("WorkflowProfile");
                    profile.setCalibrationFactors(new HashMap<>());
                    long profileId = db.ledProfileDao().insert(profile);
                    inserted.setLedProfileId(profileId);
                    db.plantDao().update(inserted);
                    db.ledProfileAssociationDao().upsert(new LedProfileAssociation(inserted.getId(), profileId));
                }
                profileLatch.countDown();
            });
            assertTrue(profileLatch.await(2, TimeUnit.SECONDS));

            // Navigate to measurement screen and open calibration
            onView(withId(R.id.nav_measure)).perform(click());
            SystemClock.sleep(600);
            onView(withId(R.id.measurement_calibrate_button)).perform(click());
            SystemClock.sleep(400);

            float ambient = 0.0246f;
            float camera = 0.0312f;
            String ambientText = String.format(Locale.US, "%.4f", ambient);
            String cameraText = String.format(Locale.US, "%.4f", camera);

            onView(withId(R.id.calibration_ambient_input)).perform(replaceText(ambientText), closeSoftKeyboard());
            onView(withId(R.id.calibration_camera_input)).perform(replaceText(cameraText), closeSoftKeyboard());
            onView(withId(R.id.calibration_confirm_button)).perform(click());
            SystemClock.sleep(800);

            // Back on the measurement screen – reopening the dialog should show the saved values
            onView(withId(R.id.measurement_calibrate_button)).check(matches(isDisplayed())).perform(click());
            SystemClock.sleep(500);
            onView(withId(R.id.calibration_ambient_input)).check(matches(withText(ambientText)));
            onView(withId(R.id.calibration_camera_input)).check(matches(withText(cameraText)));

            // Verify the calibration was persisted in Room
            final long[] plantIdHolder = new long[1];
            CountDownLatch plantLatch = new CountDownLatch(1);
            PlantDatabase.databaseWriteExecutor.execute(() -> {
                List<Plant> plants = db.plantDao().getAll();
                plantIdHolder[0] = plants.isEmpty() ? -1 : plants.get(0).getId();
                plantLatch.countDown();
            });
            assertTrue(plantLatch.await(2, TimeUnit.SECONDS));
            long plantId = plantIdHolder[0];
            assertTrue(plantId > 0);

            final LedProfile[] profileHolder = new LedProfile[1];
            CountDownLatch calibrationLatch = new CountDownLatch(1);
            PlantDatabase.databaseWriteExecutor.execute(() -> {
                Plant stored = db.plantDao().findById(plantId);
                if (stored != null && stored.getLedProfileId() != null) {
                    profileHolder[0] = db.ledProfileDao().findById(stored.getLedProfileId());
                }
                calibrationLatch.countDown();
            });
            assertTrue(calibrationLatch.await(2, TimeUnit.SECONDS));
            LedProfile profile = profileHolder[0];
            assertNotNull(profile);
            Map<String, Float> factors = profile.getCalibrationFactors();
            assertEquals(ambient, factors.get(LedProfile.CALIBRATION_KEY_AMBIENT), 0.0001f);
            assertEquals(camera, factors.get(LedProfile.CALIBRATION_KEY_CAMERA), 0.0001f);
        }
    }
}
