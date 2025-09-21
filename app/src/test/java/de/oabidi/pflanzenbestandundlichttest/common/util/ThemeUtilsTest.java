package de.oabidi.pflanzenbestandundlichttest.common.util;

import static org.junit.Assert.assertEquals;

import androidx.appcompat.app.AppCompatDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ThemeUtilsTest {

    @Before
    public void setUp() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Test
    public void applyNightMode_light() {
        int mode = ThemeUtils.applyNightMode("light");

        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, mode);
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void applyNightMode_dark() {
        int mode = ThemeUtils.applyNightMode("dark");

        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, mode);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void applyNightMode_unknownDefaultsToSystem() {
        int mode = ThemeUtils.applyNightMode("unexpected");

        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, mode);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void applyNightMode_nullDefaultsToSystem() {
        int mode = ThemeUtils.applyNightMode(null);

        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, mode);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
    }
}
