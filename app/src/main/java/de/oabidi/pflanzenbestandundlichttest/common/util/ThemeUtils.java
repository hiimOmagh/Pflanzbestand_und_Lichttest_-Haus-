package de.oabidi.pflanzenbestandundlichttest.common.util;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Utility helpers related to theming and night mode.
 */
public final class ThemeUtils {

    private ThemeUtils() {
        // Utility class
    }

    /**
     * Applies the requested night mode and returns the mapped {@link AppCompatDelegate} constant.
     *
     * @param value The stored preference value. May be {@code null} or unrecognized.
     * @return The mapped {@link AppCompatDelegate} night mode constant.
     */
    public static int applyNightMode(String value) {
        int mode;
        if ("light".equals(value)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(value)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
        return mode;
    }
}
