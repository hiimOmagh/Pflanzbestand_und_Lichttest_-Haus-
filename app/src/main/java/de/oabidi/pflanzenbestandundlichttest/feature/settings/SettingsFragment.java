package de.oabidi.pflanzenbestandundlichttest.feature.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Fragment displaying application settings.
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String PREFS_NAME = "settings";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        setPreferencesFromResource(R.xml.settings_preferences, rootKey);

        EditTextPreference calibrationPreference = findPreference("calibration_factor");
        if (calibrationPreference != null) {
            calibrationPreference.setOnPreferenceChangeListener(this::validatePositiveFloat);
        }

        EditTextPreference sampleSizePreference = findPreference("sample_size");
        if (sampleSizePreference != null) {
            sampleSizePreference.setOnPreferenceChangeListener(this::validateSampleSize);
        }

        EditTextPreference lightHoursPreference = findPreference("light_hours");
        if (lightHoursPreference != null) {
            lightHoursPreference.setOnPreferenceChangeListener(this::validatePositiveFloat);
        }
    }

    private boolean validatePositiveFloat(Preference preference, Object newValue) {
        try {
            if (Float.parseFloat((String) newValue) > 0f) {
                return true;
            }
        } catch (NumberFormatException e) {
            // fall through to show error
        }
        Toast.makeText(requireContext(), R.string.error_positive_number, Toast.LENGTH_SHORT).show();
        return false;
    }

    private boolean validateSampleSize(Preference preference, Object newValue) {
        try {
            if (Integer.parseInt((String) newValue) >= 1) {
                return true;
            }
        } catch (NumberFormatException e) {
            // fall through to show error
        }
        Toast.makeText(requireContext(), R.string.error_positive_number, Toast.LENGTH_SHORT).show();
        return false;
    }
}
