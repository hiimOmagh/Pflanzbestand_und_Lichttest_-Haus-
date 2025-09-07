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

/**
 * Tests for LightMeasurementFragment ensuring sample size preference is clamped.
 */
@RunWith(RobolectricTestRunner.class)
public class LightMeasurementFragmentTest {

    @Test
    public void fragmentStartsWithZeroSampleSizePreference() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // Write invalid sample size to preferences
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("sample_size", "0").apply();

        // Start fragment inside an activity
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        LightMeasurementFragment fragment = new LightMeasurementFragment();
        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        // Verify sample size was clamped to at least 1
        Field sampleSizeField = LightMeasurementFragment.class.getDeclaredField("sampleSize");
        sampleSizeField.setAccessible(true);
        assertEquals(1, sampleSizeField.getInt(fragment));
    }
}
