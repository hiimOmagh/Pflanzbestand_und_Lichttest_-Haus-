package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import java.util.List;

/**
 * Presenter handling light sensor measurements and related calculations.
 */
public class LightMeasurementPresenter implements LightSensorHelper.OnLuxChangedListener {
    public interface View {
        void showLightData(float rawLux, float lux, float ppfd, float dli);
        void showRangeStatus(String status);
        void showPlants(List<Plant> plants);
    }

    private final View view;
    private final Context context;
    private LightSensorHelper lightSensorHelper;
    private final PlantRepository plantRepository;
    private List<Plant> plants;
    private SpeciesTarget speciesTarget;
    private float calibrationFactor;
    private int sampleSize;
    private boolean sensing = false;

    private float lightHours = 12f;

    public LightMeasurementPresenter(View view, Context context, float calibrationFactor, int sampleSize) {
        this.view = view;
        this.context = context.getApplicationContext();
        this.calibrationFactor = calibrationFactor;
        this.sampleSize = sampleSize;
        lightSensorHelper = new LightSensorHelper(this.context, this, sampleSize);
        plantRepository = ((PlantApp) this.context).getRepository();
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
        });
    }

    public void selectPlant(int index) {
        if (plants == null || index < 0 || index >= plants.size()) {
            speciesTarget = null;
            return;
        }
        String speciesKey = plants.get(index).getSpecies();
        if (speciesKey != null && !speciesKey.isEmpty()) {
            plantRepository.getSpeciesTarget(speciesKey, target -> speciesTarget = target);
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
        view.showLightData(rawLux, lux, ppfd, dli);
        view.showRangeStatus(statusText);
    }
}
