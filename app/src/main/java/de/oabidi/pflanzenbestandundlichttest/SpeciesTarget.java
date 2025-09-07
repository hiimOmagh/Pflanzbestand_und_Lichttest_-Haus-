package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Room entity defining the acceptable PPFD range for a plant species.
 */
@Entity
public class SpeciesTarget {
    @PrimaryKey
    @NonNull
    private String speciesKey;
    private float ppfdMin;
    private float ppfdMax;

    /**
     * Convenience constructor to create fully initialised instances.
     */
    public SpeciesTarget(@NonNull String speciesKey, float ppfdMin, float ppfdMax) {
        this.speciesKey = speciesKey;
        this.ppfdMin = ppfdMin;
        this.ppfdMax = ppfdMax;
    }

    /**
     * @return identifier for the plant species.
     */
    @NonNull
    public String getSpeciesKey() {
        return speciesKey;
    }

    /**
     * Updates the species identifier.
     */
    public void setSpeciesKey(@NonNull String speciesKey) {
        this.speciesKey = speciesKey;
    }

    /**
     * @return minimum target PPFD.
     */
    public float getPpfdMin() {
        return ppfdMin;
    }

    /**
     * Defines the lower PPFD bound.
     */
    public void setPpfdMin(float ppfdMin) {
        this.ppfdMin = ppfdMin;
    }

    /**
     * @return maximum target PPFD.
     */
    public float getPpfdMax() {
        return ppfdMax;
    }

    /**
     * Defines the upper PPFD bound.
     */
    public void setPpfdMax(float ppfdMax) {
        this.ppfdMax = ppfdMax;
    }
}
