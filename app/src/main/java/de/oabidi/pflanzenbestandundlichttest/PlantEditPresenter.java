package de.oabidi.pflanzenbestandundlichttest;

/**
 * Presenter contract handling validation and persistence of plant edits.
 */
public interface PlantEditPresenter {
    /** Validate inputs and save the plant. */
    void savePlant();
}
