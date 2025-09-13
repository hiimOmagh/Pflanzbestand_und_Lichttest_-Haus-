package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Tests for LightMeasurementFragment ensuring preferences are validated.
 */
@RunWith(RobolectricTestRunner.class)
public class LightMeasurementFragmentTest {

    @Test
    public void fragmentStartsWithZeroSampleSizePreference() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // Write invalid sample size to preferences
        context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(SettingsKeys.KEY_SAMPLE_SIZE, "0").apply();

        // Start fragment inside an activity
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        LightMeasurementFragment fragment = LightMeasurementFragment.newInstance(new PlantRepository(context));
        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        // Verify sample size was clamped to at least 1
        Field sampleSizeField = LightMeasurementFragment.class.getDeclaredField("sampleSize");
        sampleSizeField.setAccessible(true);
        assertEquals(1, sampleSizeField.getInt(fragment));
    }

    @Test
    public void fragmentUsesDefaultWhenCalibrationPreferenceMalformed() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // Write malformed calibration factor to preferences
        context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(SettingsKeys.KEY_CALIBRATION, "invalid").apply();

        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        LightMeasurementFragment fragment = LightMeasurementFragment.newInstance(new PlantRepository(context));
        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        // Verify default calibration factor was used
        Field calibrationField = LightMeasurementFragment.class.getDeclaredField("calibrationFactor");
        calibrationField.setAccessible(true);
        assertEquals(0.0185f, calibrationField.getFloat(fragment), 0.0001f);

        // Inject another malformed value and trigger onResume()
        context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(SettingsKeys.KEY_CALIBRATION, "NaN").apply();
        fragment.onPause();
        fragment.onResume();
        assertEquals(0.0185f, calibrationField.getFloat(fragment), 0.0001f);
    }
}
