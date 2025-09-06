package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import java.util.List;

/**
 * Presenter responsible for loading and modifying the list of plants.
 */
public class PlantListPresenter {
    public interface View {
        void showPlants(List<Plant> plants);
    }

    private final View view;
    private final PlantRepository repository;

    public PlantListPresenter(View view, Context context) {
        this.view = view;
        this.repository = ((PlantApp) context.getApplicationContext()).getRepository();
    }

    public void refreshPlants() {
        repository.getAllPlants(view::showPlants);
    }

    public void insertPlant(Plant plant) {
        repository.insert(plant, this::refreshPlants);
    }

    public void updatePlant(Plant plant) {
        repository.update(plant, this::refreshPlants);
    }

    public void deletePlant(Plant plant) {
        repository.delete(plant, this::refreshPlants);
    }
}
