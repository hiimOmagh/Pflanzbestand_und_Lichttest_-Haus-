package de.oabidi.pflanzenbestandundlichttest.feature.plant;

/**
 * Presenter contract handling validation and persistence of plant edits.
 */
public interface PlantEditPresenter {
    /**
     * Validate inputs and save the plant.
     */
    void savePlant();

    /**
     * Load existing plant zone information for editing.
     */
    void loadPlantZone();

    /**
     * Load available LED profiles for selection.
     */
    void loadLedProfiles();
}
