package de.oabidi.pflanzenbestandundlichttest.feature.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.ListPreference;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.common.util.ThemeUtils;
import de.oabidi.pflanzenbestandundlichttest.BackupScheduler;

/**
 * Fragment displaying application settings.
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(SettingsKeys.PREFS_NAME);
        setPreferencesFromResource(R.xml.settings_preferences, rootKey);

        EditTextPreference calibrationPreference = findPreference(SettingsKeys.KEY_CALIBRATION);
        if (calibrationPreference != null) {
            calibrationPreference.setOnPreferenceChangeListener(this::validatePositiveFloat);
        }

        EditTextPreference sampleSizePreference = findPreference(SettingsKeys.KEY_SAMPLE_SIZE);
        if (sampleSizePreference != null) {
            sampleSizePreference.setOnPreferenceChangeListener(this::validateSampleSize);
        }

        EditTextPreference lightHoursPreference = findPreference(SettingsKeys.KEY_LIGHT_HOURS);
        if (lightHoursPreference != null) {
            lightHoursPreference.setOnPreferenceChangeListener(this::validatePositiveFloat);
        }

        EditTextPreference dliThresholdPreference = findPreference(SettingsKeys.KEY_DLI_ALERT_THRESHOLD);
        if (dliThresholdPreference != null) {
            dliThresholdPreference.setOnPreferenceChangeListener(this::validateSampleSize);
        }

        SwitchPreferenceCompat backupPref = findPreference(SettingsKeys.KEY_AUTO_BACKUP);
        if (backupPref != null) {
            backupPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if ((Boolean) newValue) {
                    BackupScheduler.schedule(requireContext());
                } else {
                    BackupScheduler.cancel(requireContext());
                }
                return true;
            });
        }

        ListPreference themePref = findPreference(SettingsKeys.KEY_THEME);
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((pref, newValue) -> {
                ThemeUtils.applyNightMode((String) newValue);
                return true;
            });
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
