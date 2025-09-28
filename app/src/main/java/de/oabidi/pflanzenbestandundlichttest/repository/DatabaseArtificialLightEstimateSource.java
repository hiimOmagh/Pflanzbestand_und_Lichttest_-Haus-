package de.oabidi.pflanzenbestandundlichttest.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDao;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfileDao;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibrationDao;
import de.oabidi.pflanzenbestandundlichttest.feature.lighting.LedProfileUtils;

/**
 * Database-backed implementation resolving artificial light estimates for plants.
 */
public class DatabaseArtificialLightEstimateSource implements ArtificialLightEstimateSource {

    private final PlantDao plantDao;
    private final LedProfileDao ledProfileDao;
    private final PlantCalibrationDao plantCalibrationDao;

    public DatabaseArtificialLightEstimateSource(@NonNull PlantDao plantDao,
                                                 @NonNull LedProfileDao ledProfileDao,
                                                 @NonNull PlantCalibrationDao plantCalibrationDao) {
        this.plantDao = plantDao;
        this.ledProfileDao = ledProfileDao;
        this.plantCalibrationDao = plantCalibrationDao;
    }

    @NonNull
    @Override
    public ArtificialLightEstimate estimate(long plantId) {
        Plant plant = plantDao.findById(plantId);
        if (plant == null) {
            return ArtificialLightEstimateSource.ArtificialLightEstimate.empty();
        }
        LedProfile profile = null;
        Long profileId = plant.getLedProfileId();
        if (profileId != null) {
            profile = ledProfileDao.findById(profileId);
        }
        PlantCalibration legacy = plantCalibrationDao.getForPlant(plantId);
        Map<String, Float> factors = resolveCalibration(profile, legacy);
        List<LedProfile.ScheduleEntry> schedule = profile != null ? profile.getSchedule() : null;
        LedProfileUtils.ArtificialLightEstimate estimate =
            LedProfileUtils.estimateArtificialLight(schedule, factors);
        float photonHours = estimate.getPhotonHours();
        float dli = estimate.getAmbientDli();
        if (photonHours <= 0f && dli <= 0f) {
            return ArtificialLightEstimateSource.ArtificialLightEstimate.empty();
        }
        return new ArtificialLightEstimateSource.ArtificialLightEstimate(dli, photonHours);
    }

    @Nullable
    private Map<String, Float> resolveCalibration(@Nullable LedProfile profile,
                                                  @Nullable PlantCalibration legacy) {
        Map<String, Float> resolved = null;
        if (profile != null && profile.getCalibrationFactors() != null
            && !profile.getCalibrationFactors().isEmpty()) {
            resolved = new HashMap<>(profile.getCalibrationFactors());
        }
        if ((resolved == null || resolved.isEmpty()) && legacy != null) {
            resolved = new HashMap<>();
            resolved.put(LedProfile.CALIBRATION_KEY_AMBIENT, legacy.getAmbientFactor());
            resolved.put(LedProfile.CALIBRATION_KEY_CAMERA, legacy.getCameraFactor());
        }
        return resolved;
    }
}
