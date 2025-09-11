package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import java.util.List;

/**
 * Presenter responsible for loading and modifying the list of plants.
 */
public class PlantListPresenter {
    public interface View {
        void showPlants(List<Plant> plants);
        void showError(String message);
    }

    private final View view;
    private final PlantRepository repository;
    private final Context context;

    public PlantListPresenter(View view, Context context) {
        this.view = view;
        this.context = context.getApplicationContext();
        this.repository = ((PlantApp) this.context).getRepository();
    }

    public void refreshPlants() {
        repository.getAllPlants(view::showPlants,
            e -> view.showError(context.getString(R.string.error_database)));
    }

    public void insertPlant(Plant plant) {
        repository.insert(plant, this::refreshPlants, e ->
            view.showError(context.getString(R.string.error_database)));
    }

    public void updatePlant(Plant plant) {
        repository.update(plant, this::refreshPlants, e ->
            view.showError(context.getString(R.string.error_database)));
    }

    public void deletePlant(Plant plant) {
        deletePlant(plant, null);
    }

    public void deletePlant(Plant plant, Runnable afterDelete) {
        repository.delete(plant, () -> {
            refreshPlants();
            if (afterDelete != null) {
                afterDelete.run();
            }
        }, e -> view.showError(context.getString(R.string.error_database)));
    }
}
