package de.oabidi.pflanzenbestandundlichttest.common.util;

/**
 * Shared preference keys used throughout the application.
 */
public final class SettingsKeys {
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
     * Enables alerts when DLI stays outside the species target range.
     */
    public static final String KEY_DLI_ALERTS_ENABLED = "dli_alerts_enabled";
    /**
     * Consecutive days threshold before triggering DLI alerts.
     */
    public static final String KEY_DLI_ALERT_THRESHOLD = "dli_alert_threshold";
    /**
     * Identifier of the plant currently selected by the user.
     */
    public static final String KEY_SELECTED_PLANT = "selectedPlantId";
    /**
     * Active growth stage used in the measurement screen.
     */
    public static final String KEY_SELECTED_STAGE = "selectedStage";
    /**
     * Flag indicating whether the user has completed the onboarding flow.
     */
    public static final String KEY_HAS_ONBOARDED = "has_onboarded";
    /**
     * Enables weekly automatic backups.
     */
    public static final String KEY_AUTO_BACKUP = "auto_backup";
    /**
     * Preferred export file format.
     */
    public static final String KEY_EXPORT_FORMAT = "export_format";
    /**
     * Selected application theme (light, dark, or system default).
     */
    public static final String KEY_THEME = "theme";
    /**
     * Base key for storing dismissed care recommendation identifiers per plant.
     */
    public static final String KEY_DISMISSED_CARE_RECOMMENDATIONS = "dismissed_care_recommendations";
    /**
     * Enables periodic proactive care alerts.
     */
    public static final String KEY_PROACTIVE_ALERTS_ENABLED = "proactive_alerts_enabled";
    /**
     * Stores the last latitude reported by the user for natural-light estimation.
     */
    public static final String KEY_LAST_KNOWN_LATITUDE = "last_known_latitude";
    /**
     * Stores the last longitude reported by the user for natural-light estimation.
     */
    public static final String KEY_LAST_KNOWN_LONGITUDE = "last_known_longitude";
    /**
     * Accuracy of the last recorded location in metres.
     */
    public static final String KEY_LAST_KNOWN_LOCATION_ACCURACY = "last_known_location_accuracy";
    /**
     * Timestamp for the last recorded location fix.
     */
    public static final String KEY_LAST_KNOWN_LOCATION_TIME = "last_known_location_time";
    /**
     * Date (epoch day) of the last successful natural light fetch.
     */
    public static final String KEY_LAST_NATURAL_LIGHT_FETCH_DATE = "last_natural_light_fetch_date";
    /**
     * Latitude associated with the last successful natural light fetch.
     */
    public static final String KEY_LAST_NATURAL_LIGHT_FETCH_LATITUDE = "last_natural_light_fetch_latitude";
    /**
     * Longitude associated with the last successful natural light fetch.
     */
    public static final String KEY_LAST_NATURAL_LIGHT_FETCH_LONGITUDE = "last_natural_light_fetch_longitude";
    /**
     * Timestamp for the last successful natural light fetch.
     */
    public static final String KEY_LAST_NATURAL_LIGHT_FETCH_TIME = "last_natural_light_fetch_time";

    private SettingsKeys() {
        // Utility class
    }
}
