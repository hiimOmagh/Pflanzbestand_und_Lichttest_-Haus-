package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;

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
            onView(withId(R.id.edit_seedling_ppfd_min)).perform(scrollTo(), replaceText("80"), closeSoftKeyboard());
            onView(withId(R.id.edit_seedling_ppfd_max)).perform(scrollTo(), replaceText("120"), closeSoftKeyboard());
            onView(withId(R.id.edit_seedling_dli_min)).perform(scrollTo(), replaceText("3"), closeSoftKeyboard());
            onView(withId(R.id.edit_seedling_dli_max)).perform(scrollTo(), replaceText("5"), closeSoftKeyboard());
            onView(withId(R.id.edit_vegetative_ppfd_min)).perform(scrollTo(), replaceText("100"), closeSoftKeyboard());
            onView(withId(R.id.edit_vegetative_ppfd_max)).perform(scrollTo(), replaceText("200"), closeSoftKeyboard());
            onView(withId(R.id.edit_vegetative_dli_min)).perform(scrollTo(), replaceText("6"), closeSoftKeyboard());
            onView(withId(R.id.edit_vegetative_dli_max)).perform(scrollTo(), replaceText("8"), closeSoftKeyboard());
            onView(withId(R.id.edit_flower_ppfd_min)).perform(scrollTo(), replaceText("140"), closeSoftKeyboard());
            onView(withId(R.id.edit_flower_ppfd_max)).perform(scrollTo(), replaceText("220"), closeSoftKeyboard());
            onView(withId(R.id.edit_flower_dli_min)).perform(scrollTo(), replaceText("7"), closeSoftKeyboard());
            onView(withId(R.id.edit_flower_dli_max)).perform(scrollTo(), replaceText("9"), closeSoftKeyboard());
            onView(withId(R.id.edit_tolerance)).perform(scrollTo(), replaceText("medium"), closeSoftKeyboard());
            onView(withId(R.id.edit_source)).perform(scrollTo(), replaceText("instrumented"), closeSoftKeyboard());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);

            String toleranceText = context.getString(R.string.format_tolerance, "medium");
            String sourceText = context.getString(R.string.format_source, "instrumented");
            String seedlingLabel = context.getString(R.string.label_stage_seedling);
            String vegetativeLabel = context.getString(R.string.label_stage_vegetative);
            String flowerLabel = context.getString(R.string.label_stage_flower);
            String seedlingText = context.getString(R.string.format_stage_range,
                seedlingLabel,
                String.format(Locale.US, "%.1f", 80f),
                String.format(Locale.US, "%.1f", 120f),
                String.format(Locale.US, "%.1f", 3f),
                String.format(Locale.US, "%.1f", 5f));
            String vegetativeText = context.getString(R.string.format_stage_range,
                vegetativeLabel,
                String.format(Locale.US, "%.1f", 100f),
                String.format(Locale.US, "%.1f", 200f),
                String.format(Locale.US, "%.1f", 6f),
                String.format(Locale.US, "%.1f", 8f));
            String flowerText = context.getString(R.string.format_stage_range,
                flowerLabel,
                String.format(Locale.US, "%.1f", 140f),
                String.format(Locale.US, "%.1f", 220f),
                String.format(Locale.US, "%.1f", 7f),
                String.format(Locale.US, "%.1f", 9f));

            onView(withText(toleranceText)).check(matches(isDisplayed()));
            onView(withText(sourceText)).check(matches(isDisplayed()));
            onView(withText(seedlingText)).check(matches(isDisplayed()));
            onView(withText(vegetativeText)).check(matches(isDisplayed()));
            onView(withText(flowerText)).check(matches(isDisplayed()));

            // Edit the species target
            onView(withText("Tolerance: medium")).perform(click());
            onView(withId(R.id.edit_vegetative_ppfd_min)).perform(scrollTo(), replaceText("150"), closeSoftKeyboard());
            onView(withId(R.id.edit_vegetative_ppfd_max)).perform(scrollTo(), replaceText("250"), closeSoftKeyboard());
            onView(withId(R.id.edit_vegetative_dli_min)).perform(scrollTo(), replaceText("7"), closeSoftKeyboard());
            onView(withId(R.id.edit_vegetative_dli_max)).perform(scrollTo(), replaceText("10"), closeSoftKeyboard());
            onView(withId(R.id.edit_flower_ppfd_min)).perform(scrollTo(), replaceText("190"), closeSoftKeyboard());
            onView(withId(R.id.edit_flower_ppfd_max)).perform(scrollTo(), replaceText("260"), closeSoftKeyboard());
            onView(withId(R.id.edit_flower_dli_min)).perform(scrollTo(), replaceText("9"), closeSoftKeyboard());
            onView(withId(R.id.edit_flower_dli_max)).perform(scrollTo(), replaceText("12"), closeSoftKeyboard());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);
            String vegetativeEditedText = context.getString(R.string.format_stage_range,
                vegetativeLabel,
                String.format(Locale.US, "%.1f", 150f),
                String.format(Locale.US, "%.1f", 250f),
                String.format(Locale.US, "%.1f", 7f),
                String.format(Locale.US, "%.1f", 10f));
            String flowerEditedText = context.getString(R.string.format_stage_range,
                flowerLabel,
                String.format(Locale.US, "%.1f", 190f),
                String.format(Locale.US, "%.1f", 260f),
                String.format(Locale.US, "%.1f", 9f),
                String.format(Locale.US, "%.1f", 12f));
            onView(withText(vegetativeEditedText)).check(matches(isDisplayed()));
            onView(withText(flowerEditedText)).check(matches(isDisplayed()));

            // Delete the species target
            onView(withText(flowerEditedText)).perform(longClick());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);
            onView(withText(flowerEditedText)).check(doesNotExist());
        }
    }
}
