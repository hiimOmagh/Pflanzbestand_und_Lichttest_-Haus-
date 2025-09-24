package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * Instrumented test verifying environment logging and care recommendations integration.
 */
@RunWith(AndroidJUnit4.class)
public class EnvironmentLogInstrumentedTest {

    @Test
    public void logEntryDisplaysAndShowsCareTip() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Context context = ApplicationProvider.getApplicationContext();

            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Add Plant")).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText("Env Plant"), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            SystemClock.sleep(500);

            onView(withText("Env Plant")).perform(click());
            SystemClock.sleep(500);

            onView(withId(R.id.detail_environment_log)).perform(click());
            SystemClock.sleep(500);

            onView(withId(R.id.environment_temperature_input)).perform(replaceText("16"), closeSoftKeyboard());
            onView(withId(R.id.environment_humidity_input)).perform(replaceText("70"), closeSoftKeyboard());
            onView(withId(R.id.environment_soil_input)).perform(replaceText("10"), closeSoftKeyboard());
            onView(withId(R.id.environment_notes_input)).perform(replaceText("dry day"), closeSoftKeyboard());
            onView(withId(R.id.environment_save_button)).perform(click());
            SystemClock.sleep(1000);

            String tempText = context.getString(R.string.environment_log_metric_temperature, 16f);
            String humidityText = context.getString(R.string.environment_log_metric_humidity, 70f);
            String soilText = context.getString(R.string.environment_log_metric_soil_moisture, 10f);

            onView(withId(R.id.environment_log_list)).check(matches(isDisplayed()));
            onView(allOf(withId(R.id.environment_log_item_metrics),
                withText(Matchers.containsString(tempText)))).check(matches(isDisplayed()));
            onView(allOf(withId(R.id.environment_log_item_metrics),
                withText(Matchers.containsString(humidityText)))).check(matches(isDisplayed()));
            onView(allOf(withId(R.id.environment_log_item_metrics),
                withText(Matchers.containsString(soilText)))).check(matches(isDisplayed()));
            onView(allOf(withId(R.id.environment_log_item_notes), withText("dry day")))
                .check(matches(isDisplayed()));

            pressBack();
            SystemClock.sleep(1000);

            String soilWarning = context.getString(R.string.care_soil_dry, 10f);
            onView(withId(R.id.detail_care_list)).check(matches(isDisplayed()));
            onView(withText(soilWarning)).check(matches(isDisplayed()));
        }
    }
}
