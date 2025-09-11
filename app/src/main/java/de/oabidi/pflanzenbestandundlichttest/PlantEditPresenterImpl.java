package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;

/**
 * Implementation of {@link PlantEditPresenter} performing validation and persistence.
 */
public class PlantEditPresenterImpl implements PlantEditPresenter {
    private final PlantEditView view;
    private final PlantRepository repository;
    private final Context context;

    public PlantEditPresenterImpl(PlantEditView view, PlantRepository repository, Context context) {
        this.view = view;
        this.repository = repository;
        this.context = context.getApplicationContext();
    }

    @Override
    public void savePlant() {
        String name = view.getName();
        if (name == null || name.trim().isEmpty()) {
            view.showNameError();
            return;
        }
        String species = emptyToNull(view.getSpecies());
        String location = emptyToNull(view.getLocation());
        String notes = emptyToNull(view.getNotes());
        long acquired = view.getAcquiredEpoch();
        Uri photo = view.getPhotoUri();

        Plant plant = new Plant(name, notes, species, location, acquired, photo);
        long id = view.getPlantId();
        plant.setId(id);
        Runnable afterSave = () -> view.finishWithResult(plant);
        if (id == 0) {
            repository.insert(plant, afterSave,
                e -> view.showError(context.getString(R.string.error_database)));
        } else {
            repository.update(plant, afterSave,
                e -> view.showError(context.getString(R.string.error_database)));
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
