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
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

/**
 * Instrumentation test ensuring diary entries can be searched.
 */
@RunWith(AndroidJUnit4.class)
public class DiarySearchInstrumentedTest {

    @Test
    public void testSearchFiltersEntries() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Context context = ApplicationProvider.getApplicationContext();

            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Add Plant")).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText("Search Plant"), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            SystemClock.sleep(500);

            onView(withText("Search Plant")).perform(click());
            onView(withId(R.id.detail_diary)).perform(click());

            onView(withId(R.id.fab_add_entry)).perform(click());
            onView(withId(R.id.diary_entry_note)).perform(replaceText("First note"), closeSoftKeyboard());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);

            onView(withId(R.id.fab_add_entry)).perform(click());
            onView(withId(R.id.diary_entry_note)).perform(replaceText("Second note"), closeSoftKeyboard());
            onView(withText(android.R.string.ok)).perform(click());
            SystemClock.sleep(500);

            onView(withId(R.id.diary_search)).perform(click());
            onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("Second"), closeSoftKeyboard());
            SystemClock.sleep(500);

            onView(allOf(withId(R.id.diary_entry_text), withText(containsString("Second note"))))
                .check(matches(isDisplayed()));
            onView(withText(containsString("First note"))).check(doesNotExist());
        }
    }
}
