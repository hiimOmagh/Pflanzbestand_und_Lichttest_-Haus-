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
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import de.oabidi.pflanzenbestandundlichttest.core.data.db.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Measurement;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;

/**
 * Instrumented test verifying that the statistics fragment shows correct DLI and chart information.
 */
@RunWith(AndroidJUnit4.class)
public class StatsFragmentInstrumentedTest {
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

    @Test
    public void testDliAndChartReflectMeasurements() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository repository = ((RepositoryProvider) context).getRepository();

        // Insert a plant
        Plant plant = new Plant();
        plant.setName("Stats Test Plant");
        awaitDb(() -> {
            long id = PlantDatabase.getDatabase(context).plantDao().insert(plant);
            plant.setId(id);
        });

        // Insert measurements on two different days with known PPFD and DLI values
        long now = System.currentTimeMillis();
        float ppfd = 100f;
        Measurement measurement1 = new Measurement(plant.getId(), now, 0f, ppfd, ppfd * 0.0036f, null);
        Measurement measurement2 = new Measurement(plant.getId(), now - 86400000L, 0f, 50f, 50f * 0.0036f, null);
        awaitDb(() -> PlantDatabase.getDatabase(context).measurementDao().insert(measurement1));
        awaitDb(() -> PlantDatabase.getDatabase(context).measurementDao().insert(measurement2));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Navigate to stats screen
            onView(withId(R.id.nav_stats)).perform(click());
            SystemClock.sleep(500);

            float expectedAvg = ((ppfd + 50f) * 0.0036f) / 2f;
            String expectedDli = context.getString(R.string.format_dli, expectedAvg);
            onView(withId(R.id.stats_dli)).check(matches(withText(expectedDli)));

            String expectedDesc = context.getString(R.string.format_stats_chart_content_description, ppfd);
            onView(withId(R.id.stats_chart)).check(matches(withContentDescription(expectedDesc)));
        }
    }
}
