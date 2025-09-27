package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;

import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.data.PlantZone;

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
        final String zoneOrientation = PlantZone.normalizeOrientation(view.getZoneOrientation());
        final String zoneNotes = emptyToNull(view.getZoneNotes());
        Consumer<Exception> errorHandler = e -> view.showError(context.getString(R.string.error_database));

        Plant plant = new Plant(name, notes, species, location, acquired, photo);
        long id = view.getPlantId();
        plant.setId(id);
        Runnable afterSave = () -> repository.savePlantZone(plant.getId(), zoneOrientation, zoneNotes,
            () -> view.finishWithResult(plant), errorHandler);
        if (id == 0) {
            repository.insert(plant, afterSave, errorHandler);
        } else {
            repository.update(plant, afterSave, errorHandler);
        }
    }

    @Override
    public void loadPlantZone() {
        long plantId = view.getPlantId();
        if (plantId <= 0) {
            return;
        }
        repository.getPlantZone(plantId, view::setPlantZone,
            e -> view.showError(context.getString(R.string.error_database)));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
