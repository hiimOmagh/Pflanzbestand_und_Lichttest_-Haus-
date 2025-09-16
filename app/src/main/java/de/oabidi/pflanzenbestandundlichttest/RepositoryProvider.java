package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

/**
 * Interface providing access to a {@link PlantRepository} instance.
 */
public interface RepositoryProvider {
    /**
     * Returns the shared {@link PlantRepository} instance for the application.
     */
    PlantRepository getRepository();

    /**
     * Convenience method returning the repository associated with the given context.
     *
     * @param context any context whose application implements {@link RepositoryProvider}
     * @return the shared {@link PlantRepository} instance
     * @throws IllegalStateException if the application context is not a {@link RepositoryProvider}
     */
    static PlantRepository getRepository(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof RepositoryProvider) {
            return ((RepositoryProvider) appContext).getRepository();
        }
        throw new IllegalStateException("Application context does not implement RepositoryProvider");
    }
}
