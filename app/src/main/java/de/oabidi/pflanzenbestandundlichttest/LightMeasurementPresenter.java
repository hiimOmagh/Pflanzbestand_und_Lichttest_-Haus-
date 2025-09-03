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
    private final LightSensorHelper lightSensorHelper;
    private final PlantRepository plantRepository;
    private List<Plant> plants;
    private SpeciesTarget speciesTarget;
    private float calibrationFactor;
    private float lightHours;

    public LightMeasurementPresenter(View view, Context context, float calibrationFactor, float lightHours) {
        this.view = view;
        this.calibrationFactor = calibrationFactor;
        this.lightHours = lightHours;
        lightSensorHelper = new LightSensorHelper(context, this, 10);
        plantRepository = new PlantRepository(context);
    }

    public boolean hasLightSensor() {
        return lightSensorHelper.hasLightSensor();
    }

    public void start() {
        if (hasLightSensor()) {
            lightSensorHelper.start();
        }
    }

    public void stop() {
        lightSensorHelper.stop();
    }

    public void setCalibrationFactor(float calibrationFactor) {
        this.calibrationFactor = calibrationFactor;
    }

    public void setLightHours(float lightHours) {
        this.lightHours = lightHours;
    }

    public void saveMeasurement(long plantId, float lux, float ppfd) {
        if (plantId < 0) {
            return;
        }
        Measurement measurement = new Measurement(plantId, System.currentTimeMillis(), lux, ppfd);
        plantRepository.insertMeasurement(measurement, null);
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
        String status = "Unknown";
        if (speciesTarget != null) {
            status = LightMath.rangeCheck(ppfd, speciesTarget.getPpfdMin(), speciesTarget.getPpfdMax());
        }
        view.showLightData(rawLux, lux, ppfd, dli);
        view.showRangeStatus(status);
    }
}
