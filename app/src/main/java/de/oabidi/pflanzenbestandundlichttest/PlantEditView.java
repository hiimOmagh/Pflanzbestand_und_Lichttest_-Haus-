package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;

/**
 * View contract for the plant edit screen.
 */
public interface PlantEditView {
    /** @return current input for the plant name. */
    String getName();

    /** @return current input for the species. */
    String getSpecies();

    /** @return current input for the location hint. */
    String getLocation();

    /** @return current input for the notes/description. */
    String getNotes();

    /** @return epoch time when the plant was acquired. */
    long getAcquiredEpoch();

    /** @return selected photo URI or {@code null}. */
    Uri getPhotoUri();

    /** @return ID of the plant being edited or 0 for a new plant. */
    long getPlantId();

    /** Show an error indicating that the name field is required. */
    void showNameError();

    /** Finish editing and return the resulting plant to the caller. */
    void finishWithResult(Plant plant);

    /** Display a generic error message. */
    void showError(String message);
}
