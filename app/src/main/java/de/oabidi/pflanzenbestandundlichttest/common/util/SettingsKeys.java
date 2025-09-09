package de.oabidi.pflanzenbestandundlichttest.common.util;

/**
 * Shared preference keys used throughout the application.
 */
public final class SettingsKeys {
    private SettingsKeys() {
        // Utility class
    }

    /**
     * Name of the shared preferences file storing app settings.
     */
    public static final String PREFS_NAME = "settings";

    /**
     * Calibration factor for converting lux to PPFD.
     */
    public static final String KEY_CALIBRATION = "calibration_factor";

    /**
     * Number of samples taken when averaging light readings.
     */
    public static final String KEY_SAMPLE_SIZE = "sample_size";

    /**
     * Daily light hours used to compute DLI.
     */
    public static final String KEY_LIGHT_HOURS = "light_hours";

    /**
     * Identifier of the plant currently selected by the user.
     */
    public static final String KEY_SELECTED_PLANT = "selectedPlantId";

    /**
     * Flag indicating whether the user has completed the onboarding flow.
     */
    public static final String KEY_HAS_ONBOARDED = "has_onboarded";

    /**
     * Enables weekly automatic backups.
     */
    public static final String KEY_AUTO_BACKUP = "auto_backup";

    /**
     * Selected application theme (light, dark, or system default).
     */
    public static final String KEY_THEME = "theme";
}
