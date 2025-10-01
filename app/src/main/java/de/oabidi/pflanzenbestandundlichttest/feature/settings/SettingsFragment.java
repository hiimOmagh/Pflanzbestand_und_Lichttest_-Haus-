package de.oabidi.pflanzenbestandundlichttest.feature.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.common.util.LocaleHelper;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.common.util.ThemeUtils;
import de.oabidi.pflanzenbestandundlichttest.core.system.BackupScheduler;
import de.oabidi.pflanzenbestandundlichttest.feature.alerts.AlertHistoryDialogFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.alerts.ProactiveAlertWorkScheduler;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;
import de.oabidi.pflanzenbestandundlichttest.feature.onboarding.OnboardingActivity;

/**
 * Fragment displaying application settings.
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String KEY_SHOW_TUTORIAL = "show_tutorial";

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
                boolean enabled = Boolean.TRUE.equals(newValue);
                if (enabled) {
                    BackupScheduler.schedule(requireContext());
                } else {
                    BackupScheduler.cancel(requireContext());
                }
                return true;
            });
        }

        SwitchPreferenceCompat alertsPref = findPreference(SettingsKeys.KEY_PROACTIVE_ALERTS_ENABLED);
        if (alertsPref != null) {
            alertsPref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                ProactiveAlertWorkScheduler.ensureScheduled(requireContext(), enabled);
                return true;
            });
        }

        Preference historyPref = findPreference("alert_history");
        if (historyPref != null) {
            historyPref.setOnPreferenceClickListener(pref -> {
                AlertHistoryDialogFragment.newInstance()
                    .show(getParentFragmentManager(), "alert_history");
                return true;
            });
        }

        Preference tutorialPref = findPreference(KEY_SHOW_TUTORIAL);
        if (tutorialPref != null) {
            tutorialPref.setOnPreferenceClickListener(pref -> {
                SharedPreferences sharedPreferences =
                    requireContext().getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
                sharedPreferences.edit()
                    .putBoolean(SettingsKeys.KEY_ONBOARDING_DONE, false)
                    .putBoolean(SettingsKeys.KEY_ONBOARDING_COMPLETE, false)
                    .putBoolean(SettingsKeys.KEY_HAS_ONBOARDED, false)
                    .apply();
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).launchOnboarding();
                } else {
                    Intent onboardingIntent = new Intent(requireContext(), OnboardingActivity.class);
                    startActivity(onboardingIntent);
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

        ListPreference languagePref = findPreference(SettingsKeys.KEY_LANGUAGE);
        if (languagePref != null) {
            languagePref.setOnPreferenceChangeListener((pref, newValue) -> {
                String languageCode = (String) newValue;
                int entryIndex = languagePref.findIndexOfValue(languageCode);
                CharSequence languageLabel = entryIndex >= 0
                    ? languagePref.getEntries()[entryIndex]
                    : languageCode;
                LocaleHelper.applyLocale(requireContext(), languageCode);
                Toast.makeText(
                    requireContext(),
                    getString(R.string.language_changed_to, languageLabel),
                    Toast.LENGTH_SHORT
                ).show();
                LocaleHelper.recreateActivity(requireActivity());
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
