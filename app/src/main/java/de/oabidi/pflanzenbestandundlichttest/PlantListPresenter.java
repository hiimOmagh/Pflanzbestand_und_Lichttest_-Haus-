package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

/**
 * Presenter responsible for loading and modifying the list of plants.
 */
public class PlantListPresenter {
    public interface View {
        void showPlants(List<Plant> plants);
        void showSearchResults(List<Plant> plants);
        void showError(String message);
        void onExportProgress(int current, int total);
        void onExportResult(boolean success, Uri uri);
        void onImportProgress(int current, int total);
        void onImportResult(boolean success, List<ImportManager.ImportWarning> warnings);
    }

    private final View view;
    private final PlantRepository repository;
    private final Context context;
    private final ExportManager exportManager;
    private final ImportManager importManager;

    public PlantListPresenter(View view, Context context, ExportManager exportManager,
                              ImportManager importManager) {
        this.view = view;
        this.context = context.getApplicationContext();
        this.repository = ((PlantApp) this.context).getRepository();
        this.exportManager = exportManager;
        this.importManager = importManager;
    }

    public PlantListPresenter(View view, Context context) {
        this(view, context.getApplicationContext(),
            new ExportManager(context.getApplicationContext()),
            new ImportManager(context.getApplicationContext()));
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

    public void exportData(Uri uri) {
        exportManager.export(uri, success -> view.onExportResult(success, uri),
            (c, t) -> view.onExportProgress(c, t));
    }

    public void importData(Uri uri, ImportManager.Mode mode) {
        importManager.importData(uri, mode, (success, warnings) -> {
            view.onImportResult(success, warnings);
            if (success) {
                refreshPlants();
            }
        }, (c, t) -> view.onImportProgress(c, t));
    }

    public void searchPlants(String query) {
        if (query == null || query.trim().isEmpty()) {
            refreshPlants();
        } else {
            repository.searchPlants(query, view::showSearchResults);
        }
    }
}
