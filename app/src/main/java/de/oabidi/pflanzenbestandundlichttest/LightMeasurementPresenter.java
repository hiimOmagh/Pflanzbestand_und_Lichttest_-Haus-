package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.repository.MeasurementRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.SpeciesRepository;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileCalibration;
import de.oabidi.pflanzenbestandundlichttest.feature.lighting.LedProfileUtils;

/**
 * Presenter handling light sensor measurements and related calculations.
 */
public class LightMeasurementPresenter implements LightSensorHelper.OnLuxChangedListener {
    public interface View {
        void showLightData(@Nullable LightReading ambient, @Nullable LightReading camera);
        void showRangeStatus(String status);
        void showPlants(List<Plant> plants);
        void showError(String message);
        void showSelectedStage(SpeciesTarget.GrowthStage stage);
        void showArtificialLightProjection(ArtificialLightProjection projection);
    }

    private static final float DEFAULT_CALIBRATION = 0.0185f;

    public static final class LightReading {
        public enum Source {
            AMBIENT,
            CAMERA
        }

        private final Source source;
        private final float raw;
        private final float value;
        private final float ppfd;
        private final float dli;

        public LightReading(Source source, float raw, float value, float ppfd, float dli) {
            this.source = source;
            this.raw = raw;
            this.value = value;
            this.ppfd = ppfd;
            this.dli = dli;
        }

        public Source getSource() {
            return source;
        }

        public float getRaw() {
            return raw;
        }

        public float getValue() {
            return value;
        }

        public float getPpfd() {
            return ppfd;
        }

        public float getDli() {
            return dli;
        }
    }

    private final View view;
    private final Context context;
    private LightSensorHelper lightSensorHelper;
    private final PlantRepository plantRepository;
    private final MeasurementRepository measurementRepository;
    private final SpeciesRepository speciesRepository;
    private List<Plant> plants;
    private SpeciesTarget speciesTarget;
    private SpeciesTarget.GrowthStage activeStage = SpeciesTarget.GrowthStage.VEGETATIVE;
    private long activePlantId = -1L;
    @Nullable
    private LedProfile activeLedProfile;
    @Nullable
    private Float profileAmbientPpfd;
    @Nullable
    private Float profileCameraPpfd;
    private float calibrationFactor;
    private float cameraCalibrationFactor;
    private int sampleSize;
    private boolean sensing = false;

    private float lightHours = 12f;
    @Nullable
    private LightReading ambientReading;
    @Nullable
    private LightReading cameraReading;

    public LightMeasurementPresenter(View view, PlantRepository plantRepository, Context context,
                                     float calibrationFactor, int sampleSize) {
        this(view, plantRepository, plantRepository.measurementRepository(),
            plantRepository.speciesRepository(), context, calibrationFactor, sampleSize);
    }

    public LightMeasurementPresenter(View view, PlantRepository plantRepository,
                                     MeasurementRepository measurementRepository,
                                     SpeciesRepository speciesRepository,
                                     Context context, float calibrationFactor, int sampleSize) {
        this.view = view;
        this.plantRepository = plantRepository;
        this.measurementRepository = measurementRepository;
        this.speciesRepository = speciesRepository;
        this.context = context.getApplicationContext();
        this.calibrationFactor = calibrationFactor;
        this.cameraCalibrationFactor = calibrationFactor;
        this.sampleSize = sampleSize;
        lightSensorHelper = new LightSensorHelper(this.context, this, sampleSize);
        updateArtificialLightProjection();
    }

    public boolean hasLightSensor() {
        return lightSensorHelper.hasLightSensor();
    }

    public void start() {
        if (hasLightSensor()) {
            sensing = true;
            lightSensorHelper.start();
        }
    }

    public void stop() {
        sensing = false;
        lightSensorHelper.stop();
    }

    public void setCalibrationFactor(float calibrationFactor) {
        this.calibrationFactor = calibrationFactor;
        this.cameraCalibrationFactor = calibrationFactor;
    }

    public void setCameraCalibrationFactor(float cameraCalibrationFactor) {
        this.cameraCalibrationFactor = cameraCalibrationFactor;
    }

    public void setSampleSize(int sampleSize) {
        if (sampleSize < 1 || sampleSize == this.sampleSize) {
            return;
        }
        this.sampleSize = sampleSize;
        boolean wasSensing = sensing;
        lightSensorHelper.stop();
        lightSensorHelper = new LightSensorHelper(context, this, sampleSize);
        if (wasSensing && hasLightSensor()) {
            lightSensorHelper.start();
        }
    }

    public void setLightHours(float lightHours) {
        this.lightHours = lightHours;
    }

    public void applyCalibration(@Nullable LedProfileCalibration calibration) {
        profileAmbientPpfd = null;
        profileCameraPpfd = null;
        if (calibration == null) {
            applyDefaultCalibration();
            return;
        }
        Float ambient = calibration.getAmbientFactor();
        if (ambient != null && ambient > 0f) {
            setCalibrationFactor(ambient);
            profileAmbientPpfd = ambient;
        } else {
            setCalibrationFactor(DEFAULT_CALIBRATION);
        }
        Float camera = calibration.getCameraFactor();
        if (camera != null && camera > 0f) {
            setCameraCalibrationFactor(camera);
            profileCameraPpfd = camera;
        } else if (ambient != null && ambient > 0f) {
            setCameraCalibrationFactor(ambient);
        } else {
            setCameraCalibrationFactor(DEFAULT_CALIBRATION);
        }
        updateArtificialLightProjection();
    }

    public void refreshActivePlantProfile() {
        if (activePlantId < 0) {
            activeLedProfile = null;
            updateArtificialLightProjection();
            return;
        }
        plantRepository.getLedProfileForPlant(activePlantId, profile -> {
            activeLedProfile = profile;
            updateArtificialLightProjection();
        }, e -> {
            activeLedProfile = null;
            updateArtificialLightProjection();
            view.showError(context.getString(R.string.error_database));
        });
    }

    public void refreshPlants() {
        plantRepository.getAllPlants(plants -> {
            this.plants = plants;
            view.showPlants(plants);
            selectPlant(0);
        }, e -> view.showError(context.getString(R.string.error_database)));
    }

    public void saveMeasurement(Measurement measurement, Runnable afterSave) {
        measurementRepository.insertMeasurement(measurement, afterSave,
            e -> view.showError(context.getString(R.string.error_database)));
    }

    public void selectPlant(int index) {
        if (plants == null || index < 0 || index >= plants.size()) {
            speciesTarget = null;
            activePlantId = -1L;
            activeLedProfile = null;
            profileAmbientPpfd = null;
            profileCameraPpfd = null;
            applyDefaultCalibration();
            view.showSelectedStage(activeStage);
            return;
        }
        String speciesKey = plants.get(index).getSpecies();
        if (speciesKey != null && !speciesKey.isEmpty()) {
            speciesRepository.getSpeciesTarget(speciesKey, target -> {
                speciesTarget = target;
                if (speciesTarget != null && !speciesTarget.hasStage(activeStage)) {
                    activeStage = speciesTarget.getDefaultStage();
                }
                view.showSelectedStage(activeStage);
            }, e -> view.showError(context.getString(R.string.error_database)));
        } else {
            speciesTarget = null;
            view.showSelectedStage(activeStage);
        }
        Plant selectedPlant = plants.get(index);
        long plantId = selectedPlant.getId();
        activePlantId = plantId;
        activeLedProfile = null;
        updateArtificialLightProjection();
        plantRepository.getLedCalibrationForPlant(plantId, calibration -> {
            applyCalibration(calibration);
        }, e -> {
            applyDefaultCalibration();
            view.showError(context.getString(R.string.error_database));
        });
        plantRepository.getLedProfileForPlant(plantId, profile -> {
            activeLedProfile = profile;
            updateArtificialLightProjection();
        }, e -> {
            activeLedProfile = null;
            updateArtificialLightProjection();
            view.showError(context.getString(R.string.error_database));
        });
    }

    @Override
    public void onLuxChanged(float rawLux, float lux) {
        float ppfd = LightMath.ppfdFromLux(lux, calibrationFactor);
        float dli = LightMath.dliFromPpfd(ppfd, lightHours);
        ambientReading = new LightReading(LightReading.Source.AMBIENT, rawLux, lux, ppfd, dli);
        dispatchReadings();
        view.showRangeStatus(buildRangeStatus(ppfd, dli));
    }

    public void onCameraLumaChanged(float rawLuma, float smoothedLuma) {
        float ppfd = LightMath.ppfdFromLux(smoothedLuma, cameraCalibrationFactor);
        float dli = LightMath.dliFromPpfd(ppfd, lightHours);
        cameraReading = new LightReading(LightReading.Source.CAMERA, rawLuma, smoothedLuma, ppfd, dli);
        dispatchReadings();
    }

    public void setActiveStage(SpeciesTarget.GrowthStage stage) {
        if (stage == null) {
            return;
        }
        activeStage = stage;
        if (speciesTarget != null && !speciesTarget.hasStage(activeStage)) {
            activeStage = speciesTarget.getDefaultStage();
        }
        view.showSelectedStage(activeStage);
        if (ambientReading != null) {
            view.showRangeStatus(buildRangeStatus(ambientReading.getPpfd(), ambientReading.getDli()));
        }
    }

    public SpeciesTarget.GrowthStage getActiveStage() {
        return activeStage;
    }

    private String buildRangeStatus(float ppfd, float dli) {
        if (speciesTarget == null) {
            return context.getString(R.string.unknown);
        }
        SpeciesTarget.GrowthStage displayStage = speciesTarget.hasStage(activeStage)
            ? activeStage
            : speciesTarget.getDefaultStage();
        SpeciesTarget.StageTarget stageTarget = speciesTarget.getStageOrFallback(activeStage);
        if (stageTarget == null) {
            return context.getString(R.string.unknown);
        }

        LightMath.RangeStatus status = null;
        if (stageTarget.getPpfdMin() != null && stageTarget.getPpfdMax() != null) {
            status = LightMath.rangeCheck(ppfd, stageTarget.getPpfdMin(), stageTarget.getPpfdMax());
        } else if (stageTarget.getDliMin() != null && stageTarget.getDliMax() != null) {
            status = LightMath.rangeCheck(dli, stageTarget.getDliMin(), stageTarget.getDliMax());
        }
        if (status == null) {
            return context.getString(R.string.unknown);
        }
        int statusRes;
        switch (status) {
            case LOW:
                statusRes = R.string.range_low;
                break;
            case HIGH:
                statusRes = R.string.range_high;
                break;
            case OK:
            default:
                statusRes = R.string.range_ok;
                break;
        }
        return context.getString(R.string.format_stage_status,
            context.getString(getStageLabelRes(displayStage)),
            context.getString(statusRes));
    }

    private int getStageLabelRes(SpeciesTarget.GrowthStage stage) {
        switch (stage) {
            case SEEDLING:
                return R.string.label_stage_seedling;
            case FLOWER:
                return R.string.label_stage_flower;
            case VEGETATIVE:
            default:
                return R.string.label_stage_vegetative;
        }
    }

    private void dispatchReadings() {
        view.showLightData(ambientReading, cameraReading);
    }

    private void applyDefaultCalibration() {
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        float defaultValue = DEFAULT_CALIBRATION;
        String stored = prefs.getString(SettingsKeys.KEY_CALIBRATION, null);
        if (stored != null) {
            try {
                float parsed = Float.parseFloat(stored);
                if (parsed > 0f) {
                    Float.isNaN(parsed);
                    if (!Float.isInfinite(parsed)) {
                        defaultValue = parsed;
                    }
                }
            } catch (NumberFormatException ignored) {
                // Keep default
            }
        }
        setCalibrationFactor(defaultValue);
        setCameraCalibrationFactor(defaultValue);
        profileAmbientPpfd = null;
        profileCameraPpfd = null;
        updateArtificialLightProjection();
    }

    private void updateArtificialLightProjection() {
        Map<String, Float> factors = null;
        if (profileAmbientPpfd != null || profileCameraPpfd != null) {
            factors = new HashMap<>();
            if (profileAmbientPpfd != null) {
                factors.put(LedProfile.CALIBRATION_KEY_AMBIENT, profileAmbientPpfd);
            }
            if (profileCameraPpfd != null) {
                factors.put(LedProfile.CALIBRATION_KEY_CAMERA, profileCameraPpfd);
            }
        } else if (activeLedProfile != null) {
            Map<String, Float> profileFactors = activeLedProfile.getCalibrationFactors();
            if (profileFactors != null && !profileFactors.isEmpty()) {
                factors = new HashMap<>(profileFactors);
            }
        }
        List<LedProfile.ScheduleEntry> schedule = activeLedProfile != null
            ? activeLedProfile.getSchedule()
            : null;
        LedProfileUtils.ArtificialLightEstimate estimate = LedProfileUtils.estimateArtificialLight(schedule, factors);
        view.showArtificialLightProjection(new ArtificialLightProjection(
            estimate.getPhotonHours(),
            estimate.getAmbientDli(),
            estimate.isAmbientUsingFallback(),
            estimate.getCameraDli(),
            estimate.isCameraUsingFallback()
        ));
    }

    public static final class ArtificialLightProjection {
        private final float photonHours;
        private final float ambientDli;
        private final boolean ambientUsingFallback;
        private final float cameraDli;
        private final boolean cameraUsingFallback;

        public ArtificialLightProjection(float photonHours, float ambientDli,
                                         boolean ambientUsingFallback, float cameraDli,
                                         boolean cameraUsingFallback) {
            this.photonHours = photonHours;
            this.ambientDli = ambientDli;
            this.ambientUsingFallback = ambientUsingFallback;
            this.cameraDli = cameraDli;
            this.cameraUsingFallback = cameraUsingFallback;
        }

        public float getPhotonHours() {
            return photonHours;
        }

        public float getAmbientDli() {
            return ambientDli;
        }

        public boolean isAmbientUsingFallback() {
            return ambientUsingFallback;
        }

        public float getCameraDli() {
            return cameraDli;
        }

        public boolean isCameraUsingFallback() {
            return cameraUsingFallback;
        }
    }
}
