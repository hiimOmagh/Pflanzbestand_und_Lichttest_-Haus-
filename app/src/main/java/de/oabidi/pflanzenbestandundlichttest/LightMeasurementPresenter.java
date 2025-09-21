package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Presenter handling light sensor measurements and related calculations.
 */
public class LightMeasurementPresenter implements LightSensorHelper.OnLuxChangedListener {
    public interface View {
        void showLightData(@Nullable LightReading ambient, @Nullable LightReading camera);
        void showRangeStatus(String status);
        void showPlants(List<Plant> plants);
        void showError(String message);
    }

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
            return;
        }
        String speciesKey = plants.get(index).getSpecies();
        if (speciesKey != null && !speciesKey.isEmpty()) {
            plantRepository.getSpeciesTarget(speciesKey, target -> speciesTarget = target,
                e -> view.showError(context.getString(R.string.error_database)));
        } else {
            speciesTarget = null;
        }
    }

    @Override
    public void onLuxChanged(float rawLux, float lux) {
        float ppfd = LightMath.ppfdFromLux(lux, calibrationFactor);
        float dli = LightMath.dliFromPpfd(ppfd, lightHours);
        String statusText = context.getString(R.string.unknown);
        if (speciesTarget != null) {
            LightMath.RangeStatus status = LightMath.rangeCheck(ppfd, speciesTarget.getPpfdMin(), speciesTarget.getPpfdMax());
            switch (status) {
                case LOW:
                    statusText = context.getString(R.string.range_low);
                    break;
                case HIGH:
                    statusText = context.getString(R.string.range_high);
                    break;
                case OK:
                default:
                    statusText = context.getString(R.string.range_ok);
                    break;
            }
        }
        ambientReading = new LightReading(LightReading.Source.AMBIENT, rawLux, lux, ppfd, dli);
        dispatchReadings();
        view.showRangeStatus(statusText);
    }

    public void onCameraLumaChanged(float rawLuma, float smoothedLuma) {
        float ppfd = LightMath.ppfdFromLux(smoothedLuma, cameraCalibrationFactor);
        float dli = LightMath.dliFromPpfd(ppfd, lightHours);
        cameraReading = new LightReading(LightReading.Source.CAMERA, rawLuma, smoothedLuma, ppfd, dli);
        dispatchReadings();
    }

    private void dispatchReadings() {
        view.showLightData(ambientReading, cameraReading);
    }
}
