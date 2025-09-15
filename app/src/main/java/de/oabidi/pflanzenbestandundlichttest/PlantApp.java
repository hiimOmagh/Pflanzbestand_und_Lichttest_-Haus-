package de.oabidi.pflanzenbestandundlichttest;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Application subclass providing a single {@link PlantRepository} instance.
 */
public class PlantApp extends Application implements RepositoryProvider {
    private PlantRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        SharedPreferences prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE);
        String theme = prefs.getString(SettingsKeys.KEY_THEME, "system");
        applyTheme(theme);
    }

    private void applyTheme(String value) {
        int mode;
        switch (value) {
            case "light":
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case "dark":
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /**
     * Returns the shared {@link PlantRepository} instance for the application.
     */
    @Override
    public synchronized PlantRepository getRepository() {
        if (repository == null) {
            repository = new PlantRepository(this);
        }
        return repository;
    }
}
