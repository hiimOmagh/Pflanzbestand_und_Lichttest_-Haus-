package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import java.util.List;

/**
 * Presenter that mediates between {@link MainActivity}, {@link LightSensorHelper},
 * and {@link PlantRepository}. It handles background work such as sensor
 * management and database interaction and delivers results back to the view.
 */
public class MainPresenter implements LightSensorHelper.OnLuxChangedListener {
    /** View contract implemented by the activity. */
    public interface View {
        /** Display the latest light measurements. */
        void showLightData(float lux, float ppfd, float dli);

        /** Display the current list of plants. */
        void showPlants(List<Plant> plants);
    }

    private final View view;
    private final LightSensorHelper lightSensorHelper;
    private final PlantRepository plantRepository;
    private float calibrationFactor;
    private float lightHours;

    public MainPresenter(View view, Context context, float calibrationFactor, float lightHours) {
        this.view = view;
        this.calibrationFactor = calibrationFactor;
        this.lightHours = lightHours;
        lightSensorHelper = new LightSensorHelper(context, this, 10);
        plantRepository = new PlantRepository(context);
    }

    /** Whether a light sensor is available on the device. */
    public boolean hasLightSensor() {
        return lightSensorHelper.hasLightSensor();
    }

    /** Start listening for light sensor updates. */
    public void start() {
        if (hasLightSensor()) {
            lightSensorHelper.start();
        }
    }

    /** Stop listening for light sensor updates. */
    public void stop() {
        lightSensorHelper.stop();
    }

    /** Refresh the list of plants from the database. */
    public void refreshPlants() {
        plantRepository.getAllPlants(view::showPlants);
    }

    /** Update the calibration factor used for light calculations. */
    public void setCalibrationFactor(float calibrationFactor) {
        this.calibrationFactor = calibrationFactor;
    }

    /** Update the expected daily light hours used for DLI calculation. */
    public void setLightHours(float lightHours) {
        this.lightHours = lightHours;
    }

    /** Insert a new plant into the repository. */
    public void insertPlant(Plant plant) {
        plantRepository.insert(plant, this::refreshPlants);
    }

    /** Update an existing plant. */
    public void updatePlant(Plant plant) {
        plantRepository.update(plant, this::refreshPlants);
    }

    /** Delete a plant from the repository. */
    public void deletePlant(Plant plant) {
        plantRepository.delete(plant, this::refreshPlants);
    }

    @Override
    public void onLuxChanged(float lux) {
        float ppfd = LightMath.ppfdFromLux(lux, calibrationFactor);
        float dli = LightMath.dliFromPpfd(ppfd, lightHours);
        view.showLightData(lux, ppfd, dli);
    }
}
