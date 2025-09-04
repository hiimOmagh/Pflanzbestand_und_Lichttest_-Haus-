package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;

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
import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;

/**
 * Instrumented tests covering basic plant CRUD operations and diary entries.
 */
@RunWith(AndroidJUnit4.class)
public class PlantDiaryInstrumentedTest {

    @Test
    public void testAddEditDeletePlant() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Context context = ApplicationProvider.getApplicationContext();

            // Add a plant
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Add Plant")).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText("Test Plant"), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            SystemClock.sleep(500);
            onView(withText("Test Plant")).check(matches(isDisplayed()));

            // Edit the plant
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Update First Plant")).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText("Updated Plant"), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            SystemClock.sleep(500);
            onView(withText("Updated Plant")).check(matches(isDisplayed()));

            // Delete the plant
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Delete First Plant")).perform(click());
            SystemClock.sleep(500);
            onView(withText("Updated Plant")).check(doesNotExist());
        }
    }

    @Test
    public void testCreateDiaryEntry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Context context = ApplicationProvider.getApplicationContext();

            // Ensure a plant exists
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Add Plant")).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText("Diary Plant"), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            SystemClock.sleep(500);

            // Open plant detail
            onView(withText("Diary Plant")).perform(click());

            // Navigate to diary
            onView(withId(R.id.detail_diary)).perform(click());

            // Add diary entry
            onView(withId(R.id.fab_add_entry)).perform(click());
            onView(withId(R.id.diary_entry_note)).perform(replaceText("Test note"), closeSoftKeyboard());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);

            // Verify entry appears in list
            onView(allOf(withId(R.id.diary_entry_text), withText(containsString("Test note"))))
                .check(matches(isDisplayed()));
        }
    }
}
