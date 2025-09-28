package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

import de.oabidi.pflanzenbestandundlichttest.core.data.db.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;

/**
 * Instrumentation test verifying reminder deletion can be undone via Snackbar.
 */
@RunWith(AndroidJUnit4.class)
public class ReminderUndoInstrumentedTest {

    @Test
    public void swipeReminderShowsUndoAndRestoresItem() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        // Ensure a plant and reminder exist in the database
        PlantDatabase db = PlantDatabase.getDatabase(context);
        Plant plant = new Plant("Undo Plant", null, null, null, 0, null);
        final long[] plantIdHolder = new long[1];
        CountDownLatch plantLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            plantIdHolder[0] = db.plantDao().insert(plant);
            plantLatch.countDown();
        });
        assertTrue(plantLatch.await(5, TimeUnit.SECONDS));
        long plantId = plantIdHolder[0];
        Reminder reminder = new Reminder(System.currentTimeMillis() + 60000,
            "Undo me", plantId);
        final long[] reminderIdHolder = new long[1];
        CountDownLatch reminderLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            reminderIdHolder[0] = db.reminderDao().insert(reminder);
            reminderLatch.countDown();
        });
        assertTrue(reminderLatch.await(5, TimeUnit.SECONDS));
        reminder.setId(reminderIdHolder[0]);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Navigate to the reminder list
            onView(withId(R.id.nav_reminders)).perform(click());
            SystemClock.sleep(500);

            // Swipe away the reminder to trigger deletion
            onView(withText(containsString("Undo me"))).perform(swipeLeft());
            SystemClock.sleep(500);

            // Tap the undo action in the Snackbar
            onView(withText(R.string.action_undo)).perform(click());
            SystemClock.sleep(500);

            // Verify the reminder is shown again in the list
            onView(withText(containsString("Undo me"))).check(matches(isDisplayed()));
        }
    }
}
