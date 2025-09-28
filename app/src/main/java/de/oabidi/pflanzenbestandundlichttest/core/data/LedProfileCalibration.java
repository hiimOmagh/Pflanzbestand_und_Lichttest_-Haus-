package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.annotation.Nullable;

/**
 * Lightweight DTO exposing the calibration factors available to a plant via its LED profile.
 */
public final class LedProfileCalibration {

    @Nullable
    private final Long profileId;

    @Nullable
    private final String profileName;

    @Nullable
    private final Float ambientFactor;

    @Nullable
    private final Float cameraFactor;

    private final boolean legacyFallback;

    private LedProfileCalibration(@Nullable Long profileId, @Nullable String profileName,
                                  @Nullable Float ambientFactor, @Nullable Float cameraFactor,
                                  boolean legacyFallback) {
        this.profileId = profileId;
        this.profileName = profileName;
        this.ambientFactor = ambientFactor;
        this.cameraFactor = cameraFactor;
        this.legacyFallback = legacyFallback;
    }

    /**
     * Returns a DTO populated from the provided LED profile and optional legacy calibration.
     */
    public static LedProfileCalibration forProfile(@Nullable LedProfile profile,
                                                   @Nullable PlantCalibration legacy) {
        if (profile == null) {
            return fromLegacy(legacy);
        }
        Float ambient = null;
        Float camera = null;
        if (profile.getCalibrationFactors() != null) {
            ambient = profile.getCalibrationFactors().get(LedProfile.CALIBRATION_KEY_AMBIENT);
            camera = profile.getCalibrationFactors().get(LedProfile.CALIBRATION_KEY_CAMERA);
        }
        if (ambient != null && camera != null) {
            return new LedProfileCalibration(profile.getId(), profile.getName(), ambient, camera, false);
        }
        if (legacy != null) {
            return new LedProfileCalibration(profile.getId(), profile.getName(),
                legacy.getAmbientFactor(), legacy.getCameraFactor(), true);
        }
        return new LedProfileCalibration(profile.getId(), profile.getName(), null, null, false);
    }

    /**
     * Returns a DTO representing calibration data from a legacy per-plant row.
     */
    public static LedProfileCalibration fromLegacy(@Nullable PlantCalibration legacy) {
        if (legacy == null) {
            return empty();
        }
        return new LedProfileCalibration(null, null, legacy.getAmbientFactor(), legacy.getCameraFactor(), true);
    }

    /**
     * Returns an empty DTO with no calibration data.
     */
    public static LedProfileCalibration empty() {
        return new LedProfileCalibration(null, null, null, null, false);
    }

    /**
     * Returns the identifier of the LED profile providing calibration values, if any.
     */
    @Nullable
    public Long getProfileId() {
        return profileId;
    }

    /**
     * Returns the human-readable LED profile name, if any.
     */
    @Nullable
    public String getProfileName() {
        return profileName;
    }

    /**
     * Returns the calibration factor for ambient light, if available.
     */
    @Nullable
    public Float getAmbientFactor() {
        return ambientFactor;
    }

    /**
     * Returns the calibration factor for camera-derived readings, if available.
     */
    @Nullable
    public Float getCameraFactor() {
        return cameraFactor;
    }

    /**
     * Returns whether the values originate from a legacy per-plant calibration row.
     */
    public boolean isLegacyFallback() {
        return legacyFallback;
    }

    /**
     * Returns whether a LED profile is assigned to the plant.
     */
    public boolean hasAssignedProfile() {
        return profileId != null;
    }

    /**
     * Returns true when both calibration factors are populated.
     */
    public boolean hasCalibrationValues() {
        return ambientFactor != null && cameraFactor != null;
    }
}
