package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * Instrumented test verifying that the statistics fragment shows correct DLI and chart information.
 */
@RunWith(AndroidJUnit4.class)
public class StatsFragmentInstrumentedTest {

    @Test
    public void testDliAndChartReflectMeasurements() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository repository = ((PlantApp) context).getRepository();

        // Insert a plant
        Plant plant = new Plant();
        plant.setName("Stats Test Plant");
        repository.insert(plant, null).get();

        // Insert measurements on two different days with known PPFD and DLI values
        long now = System.currentTimeMillis();
        float ppfd = 100f;
        float dli1 = 12f;
        float dli2 = 6f;
        Measurement measurement1 = new Measurement(plant.getId(), now, 0f, ppfd, dli1, null);
        Measurement measurement2 = new Measurement(plant.getId(), now - 86400000L, 0f, 50f, dli2, null);
        repository.insertMeasurement(measurement1, null).get();
        repository.insertMeasurement(measurement2, null).get();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Navigate to stats screen
            onView(withId(R.id.nav_stats)).perform(click());
            SystemClock.sleep(500);

            float expectedAvg = (dli1 + dli2) / 2f;
            String expectedDli = context.getString(R.string.format_dli, expectedAvg);
            onView(withId(R.id.stats_dli)).check(matches(withText(expectedDli)));

            String expectedDesc = context.getString(R.string.format_stats_chart_content_description, ppfd);
            onView(withId(R.id.stats_chart)).check(matches(withContentDescription(expectedDesc)));
        }
    }
}
