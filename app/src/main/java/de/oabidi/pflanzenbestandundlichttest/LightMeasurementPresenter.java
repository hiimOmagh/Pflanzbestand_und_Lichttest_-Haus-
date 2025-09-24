package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

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
    private List<Plant> plants;
    private SpeciesTarget speciesTarget;
    private SpeciesTarget.GrowthStage activeStage = SpeciesTarget.GrowthStage.VEGETATIVE;
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
        this.view = view;
        this.plantRepository = plantRepository;
        this.context = context.getApplicationContext();
        this.calibrationFactor = calibrationFactor;
        this.cameraCalibrationFactor = calibrationFactor;
        this.sampleSize = sampleSize;
        lightSensorHelper = new LightSensorHelper(this.context, this, sampleSize);
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

    public void refreshPlants() {
        plantRepository.getAllPlants(plants -> {
            this.plants = plants;
            view.showPlants(plants);
            selectPlant(0);
        }, e -> view.showError(context.getString(R.string.error_database)));
    }

    public void saveMeasurement(Measurement measurement, Runnable afterSave) {
        plantRepository.insertMeasurement(measurement, afterSave,
            e -> view.showError(context.getString(R.string.error_database)));
    }

    public void selectPlant(int index) {
        if (plants == null || index < 0 || index >= plants.size()) {
            speciesTarget = null;
            applyDefaultCalibration();
            view.showSelectedStage(activeStage);
            return;
        }
        String speciesKey = plants.get(index).getSpecies();
        if (speciesKey != null && !speciesKey.isEmpty()) {
            plantRepository.getSpeciesTarget(speciesKey, target -> {
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
        plantRepository.getPlantCalibration(plantId, calibration -> {
            if (calibration != null) {
                setCalibrationFactor(calibration.getAmbientFactor());
                setCameraCalibrationFactor(calibration.getCameraFactor());
            } else {
                applyDefaultCalibration();
            }
        }, e -> {
            applyDefaultCalibration();
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
    }
}
