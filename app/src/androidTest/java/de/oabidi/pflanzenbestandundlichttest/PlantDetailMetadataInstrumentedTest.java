package de.oabidi.pflanzenbestandundlichttest;

import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;

/**
 * Instrumentation tests validating that species metadata is rendered on the detail screen.
 */
@RunWith(AndroidJUnit4.class)
public class PlantDetailMetadataInstrumentedTest {

    @Test
    public void plantDetail_displaysSpeciesMetadata() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Add a plant with a species that has catalog metadata
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
            onView(withText("Add Plant")).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText("Metadata Plant"), closeSoftKeyboard());
            onView(withId(R.id.input_species)).perform(replaceText("pothos-golden"), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            SystemClock.sleep(500);

            onView(withText("Metadata Plant")).perform(click());
            SystemClock.sleep(500);

            onView(withId(R.id.detail_species_metadata_card)).check(matches(isDisplayed()));
            onView(withId(R.id.detail_watering_frequency))
                .check(matches(withText(containsString("Allow top 2-3 cm"))));
            onView(withId(R.id.detail_temperature_range))
                .check(matches(withText(containsString("°C"))));
            onView(withId(R.id.detail_humidity_range))
                .check(matches(withText(containsString("%"))));
            onView(withId(R.id.detail_toxicity_text))
                .check(matches(withText(R.string.metadata_toxic_true)));
            onView(withId(R.id.detail_care_tips_text))
                .check(matches(withText(containsString("•"))));
        }
    }
}
