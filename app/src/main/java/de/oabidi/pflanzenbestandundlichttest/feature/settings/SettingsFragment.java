package de.oabidi.pflanzenbestandundlichttest.feature.settings;

import android.os.Bundle;

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
    }
}
