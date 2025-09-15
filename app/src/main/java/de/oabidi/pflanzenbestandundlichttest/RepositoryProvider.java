package de.oabidi.pflanzenbestandundlichttest;

/**
 * Interface providing access to a {@link PlantRepository} instance.
 */
public interface RepositoryProvider {
    /**
     * Returns the shared {@link PlantRepository} instance for the application.
     */
    PlantRepository getRepository();
}
