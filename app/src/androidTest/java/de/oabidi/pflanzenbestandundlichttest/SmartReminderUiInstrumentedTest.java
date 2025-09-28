package de.oabidi.pflanzenbestandundlichttest;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.db.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;

/**
 * Instrumented test verifying smart reminder suggestions appear in the diary dialog.
 */
@RunWith(AndroidJUnit4.class)
public class SmartReminderUiInstrumentedTest {
    private long plantId;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase db = PlantDatabase.getDatabase(context);
        Plant plant = new Plant("Smart Plant", null, "sansevieria-trifasciata", null,
            System.currentTimeMillis(), null);
        CountDownLatch latch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            plantId = db.plantDao().insert(plant);
            EnvironmentEntry entry = new EnvironmentEntry();
            entry.setPlantId(plantId);
            entry.setTimestamp(System.currentTimeMillis());
            entry.setSoilMoisture(82f);
            db.environmentEntryDao().insert(entry);
            latch.countDown();
        });
        latch.await(2, TimeUnit.SECONDS);
    }

    @Test
    public void smartSuggestionVisibleInReminderDialog() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            SystemClock.sleep(500);
            onView(withText("Smart Plant")).perform(click());
            onView(withId(R.id.detail_diary)).perform(click());
            onView(withId(R.id.fab_add_entry)).perform(click());
            SystemClock.sleep(1000);
            onView(withId(R.id.diary_entry_suggestion)).check(matches(isDisplayed()));
            onView(withId(R.id.diary_entry_suggestion))
                .check(matches(withText(containsString("Suggested"))));
            onView(withId(R.id.diary_entry_remind_days))
                .check(matches(withText(containsString("34"))));
        }
    }
}
