package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileAssociation;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileAssociationDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileCalibration;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantCalibrationDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.PlantDao;

/**
 * Coordinates LED calibration persistence for plants and LED profiles.
 */
public class CalibrationManager extends BaseRepository {
    private final PlantDao plantDao;
    private final PlantCalibrationDao plantCalibrationDao;
    private final LedProfileDao ledProfileDao;
    private final LedProfileAssociationDao ledProfileAssociationDao;

    public CalibrationManager(Context context,
                              Handler mainHandler,
                              ExecutorService ioExecutor,
                              PlantDao plantDao,
                              PlantCalibrationDao plantCalibrationDao,
                              LedProfileDao ledProfileDao,
                              LedProfileAssociationDao ledProfileAssociationDao) {
        super(context, mainHandler, ioExecutor);
        this.plantDao = Objects.requireNonNull(plantDao, "plantDao");
        this.plantCalibrationDao = Objects.requireNonNull(plantCalibrationDao, "plantCalibrationDao");
        this.ledProfileDao = Objects.requireNonNull(ledProfileDao, "ledProfileDao");
        this.ledProfileAssociationDao = Objects.requireNonNull(ledProfileAssociationDao, "ledProfileAssociationDao");
    }

    public void getLedCalibrationForPlant(long plantId, Consumer<LedProfileCalibration> callback) {
        getLedCalibrationForPlant(plantId, callback, null);
    }

    public void getLedCalibrationForPlant(long plantId,
                                          Consumer<LedProfileCalibration> callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> {
            Plant plant = plantDao.findById(plantId);
            LedProfileCalibration calibration = LedProfileCalibration.empty();
            if (plant != null) {
                PlantCalibration legacy = plantCalibrationDao.getForPlant(plantId);
                Long profileId = plant.getLedProfileId();
                if (profileId != null) {
                    LedProfile profile = ledProfileDao.findById(profileId);
                    calibration = LedProfileCalibration.forProfile(profile, legacy);
                } else if (legacy != null) {
                    calibration = LedProfileCalibration.fromLegacy(legacy);
                }
            }
            return calibration;
        }, callback, errorCallback);
    }

    public void saveLedCalibrationForPlant(long plantId,
                                           float ambientFactor,
                                           float cameraFactor,
                                           Runnable callback) {
        saveLedCalibrationForPlant(plantId, ambientFactor, cameraFactor, callback, null);
    }

    public void saveLedCalibrationForPlant(long plantId,
                                           float ambientFactor,
                                           float cameraFactor,
                                           @Nullable Runnable callback,
                                           @Nullable Consumer<Exception> errorCallback) {
        runAsync(() -> {
            Plant plant = plantDao.findById(plantId);
            if (plant == null) {
                throw new IllegalArgumentException("Plant not found: " + plantId);
            }
            Long profileId = plant.getLedProfileId();
            if (profileId != null) {
                LedProfile profile = ledProfileDao.findById(profileId);
                if (profile != null) {
                    Map<String, Float> factors = new HashMap<>(profile.getCalibrationFactors());
                    factors.put(LedProfile.CALIBRATION_KEY_AMBIENT, ambientFactor);
                    factors.put(LedProfile.CALIBRATION_KEY_CAMERA, cameraFactor);
                    profile.setCalibrationFactors(factors);
                    ledProfileDao.update(profile);
                    ledProfileAssociationDao.upsert(new LedProfileAssociation(plantId, profileId));
                    return;
                }
                ledProfileAssociationDao.deleteByPlantId(plantId);
                plant.setLedProfileId(null);
                plantDao.update(plant);
            }
            PlantCalibration calibration = new PlantCalibration(plantId, ambientFactor, cameraFactor);
            plantCalibrationDao.insertOrUpdate(calibration);
        }, callback, errorCallback);
    }

    /**
     * @deprecated Use {@link #getLedCalibrationForPlant(long, Consumer)} instead.
     */
    @Deprecated
    public void getPlantCalibration(long plantId, Consumer<PlantCalibration> callback) {
        getPlantCalibration(plantId, callback, null);
    }

    /**
     * @deprecated Use {@link #getLedCalibrationForPlant(long, Consumer, Consumer)} instead.
     */
    @Deprecated
    public void getPlantCalibration(long plantId,
                                    Consumer<PlantCalibration> callback,
                                    @Nullable Consumer<Exception> errorCallback) {
        getLedCalibrationForPlant(plantId, calibration -> {
            if (callback == null) {
                return;
            }
            PlantCalibration legacy = null;
            if (calibration != null && calibration.hasCalibrationValues()) {
                Float ambient = calibration.getAmbientFactor();
                Float camera = calibration.getCameraFactor();
                if (ambient != null && camera != null) {
                    legacy = new PlantCalibration(plantId, ambient, camera);
                }
            }
            callback.accept(legacy);
        }, errorCallback);
    }

    /**
     * @deprecated Use {@link #saveLedCalibrationForPlant(long, float, float, Runnable)} instead.
     */
    @Deprecated
    public void savePlantCalibration(long plantId,
                                     float ambientFactor,
                                     float cameraFactor,
                                     Runnable callback) {
        saveLedCalibrationForPlant(plantId, ambientFactor, cameraFactor, callback, null);
    }

    /**
     * @deprecated Use {@link #saveLedCalibrationForPlant(long, float, float, Runnable, Consumer)} instead.
     */
    @Deprecated
    public void savePlantCalibration(long plantId,
                                     float ambientFactor,
                                     float cameraFactor,
                                     Runnable callback,
                                     @Nullable Consumer<Exception> errorCallback) {
        saveLedCalibrationForPlant(plantId, ambientFactor, cameraFactor, callback, errorCallback);
    }
}
