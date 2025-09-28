package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.core.data.PlantZone;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;

/**
 * View contract for the plant edit screen.
 */
public interface PlantEditView {
    /**
     * @return current input for the plant name.
     */
    String getName();

    /**
     * @return current input for the species.
     */
    String getSpecies();

    /**
     * @return current input for the location hint.
     */
    String getLocation();

    /**
     * @return current input for the notes/description.
     */
    String getNotes();

    /**
     * @return epoch time when the plant was acquired.
     */
    long getAcquiredEpoch();

    /**
     * @return selected photo URI or {@code null}.
     */
    Uri getPhotoUri();

    /**
     * @return identifier of the selected LED profile or {@code null}.
     */
    @Nullable
    Long getSelectedLedProfileId();

    /**
     * @return ID of the plant being edited or 0 for a new plant.
     */
    long getPlantId();

    /**
     * @return selected zone orientation code or {@code null} if not set.
     */
    @Nullable
    String getZoneOrientation();

    /**
     * @return free-form notes for the plant zone or {@code null}.
     */
    @Nullable
    String getZoneNotes();

    /**
     * Display the stored plant zone information for editing.
     */
    void setPlantZone(@Nullable PlantZone zone);

    /**
     * Display available LED profiles and preselect the current assignment.
     */
    void showLedProfiles(List<LedProfile> profiles, @Nullable Long selectedProfileId);

    /**
     * Show an error indicating that the name field is required.
     */
    void showNameError();

    /**
     * Finish editing and return the resulting plant to the caller.
     */
    void finishWithResult(Plant plant);

    /**
     * Display a generic error message.
     */
    void showError(String message);
}
