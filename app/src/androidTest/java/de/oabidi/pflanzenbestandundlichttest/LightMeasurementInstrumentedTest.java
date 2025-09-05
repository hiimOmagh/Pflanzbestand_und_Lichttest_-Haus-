package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.SystemClock;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;

/**
 * Instrumented test ensuring a light measurement is saved and appears in stats.
 */
@RunWith(AndroidJUnit4.class)
public class LightMeasurementInstrumentedTest {

    @Test
    public void testMeasurementSavedAndShownInStats() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Context context = ApplicationProvider.getApplicationContext();

            // Add a plant to measure
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Add Plant")).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText("Light Plant"), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            SystemClock.sleep(500);

            // Open light measurement screen
            onView(withId(R.id.nav_measure)).perform(click());
            SystemClock.sleep(500);

            // Simulate a sensor value via presenter hook
            scenario.onActivity(activity -> {
                Fragment navHost = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                LightMeasurementFragment fragment = (LightMeasurementFragment) navHost.getChildFragmentManager().getFragments().get(0);
                try {
                    Field presenterField = LightMeasurementFragment.class.getDeclaredField("presenter");
                    presenterField.setAccessible(true);
                    LightMeasurementPresenter presenter = (LightMeasurementPresenter) presenterField.get(fragment);
                    presenter.onLuxChanged(500f, 500f);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });

            // Save the measurement
            onView(withId(R.id.measurement_save_button)).perform(click());
            SystemClock.sleep(500);

            // Navigate to stats and open measurements list
            onView(withId(R.id.nav_stats)).perform(click());
            SystemClock.sleep(500);
            onView(withId(R.id.stats_view_measurements)).perform(click());
            SystemClock.sleep(500);

            // Expected formatted values
            float ppfd = 500f * 0.0185f;
            float dli = ppfd * 12f * 0.0036f;
            String expectedPpfd = context.getString(R.string.format_ppfd, ppfd);
            String expectedDli = context.getString(R.string.format_dli, dli);

            // Verify measurement entry is displayed
            onView(allOf(withId(R.id.measurement_ppfd), withText(expectedPpfd)))
                .check(matches(isDisplayed()));
            onView(allOf(withId(R.id.measurement_dli), withText(expectedDli)))
                .check(matches(isDisplayed()));
        }
    }
}
