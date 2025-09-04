package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
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
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * Espresso test covering add, edit, and delete operations for species targets.
 */
@RunWith(AndroidJUnit4.class)
public class SpeciesTargetInstrumentedTest {
    @Test
    public void testAddEditDeleteSpeciesTarget() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Context context = ApplicationProvider.getApplicationContext();

            // Navigate to species targets screen
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Species Targets")).perform(click());

            // Add a species target
            onView(withId(R.id.fab_add_target)).perform(click());
            onView(withId(R.id.edit_species_key)).perform(replaceText("test"), closeSoftKeyboard());
            onView(withId(R.id.edit_ppfd_min)).perform(replaceText("100"), closeSoftKeyboard());
            onView(withId(R.id.edit_ppfd_max)).perform(replaceText("200"), closeSoftKeyboard());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);
            onView(withText("test: 100.0 - 200.0")).check(matches(isDisplayed()));

            // Edit the species target
            onView(withText("test: 100.0 - 200.0")).perform(click());
            onView(withId(R.id.edit_ppfd_min)).perform(replaceText("150"), closeSoftKeyboard());
            onView(withId(R.id.edit_ppfd_max)).perform(replaceText("250"), closeSoftKeyboard());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);
            onView(withText("test: 150.0 - 250.0")).check(matches(isDisplayed()));

            // Delete the species target
            onView(withText("test: 150.0 - 250.0")).perform(longClick());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);
            onView(withText("test: 150.0 - 250.0")).check(doesNotExist());
        }
    }
}
