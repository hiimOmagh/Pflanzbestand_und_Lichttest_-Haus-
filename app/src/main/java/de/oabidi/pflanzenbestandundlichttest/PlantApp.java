package de.oabidi.pflanzenbestandundlichttest;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

/**
 * Application subclass providing a single {@link PlantRepository} instance.
 */
public class PlantApp extends Application {
    private PlantRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    /**
     * Returns the shared {@link PlantRepository} instance for the application.
     */
    public synchronized PlantRepository getRepository() {
        if (repository == null) {
            repository = new PlantRepository(this);
        }
        return repository;
    }
}
