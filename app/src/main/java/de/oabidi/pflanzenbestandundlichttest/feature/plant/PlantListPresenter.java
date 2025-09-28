package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import de.oabidi.pflanzenbestandundlichttest.R;

import android.content.Context;
import android.net.Uri;

import java.util.List;
import java.util.concurrent.ExecutorService;

import androidx.annotation.Nullable;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.core.data.util.ImportManager;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExecutorProvider;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExportManager;

/**
 * Presenter responsible for loading and modifying the list of plants.
 */
public class PlantListPresenter {
    private final View view;
    private final PlantRepository repository;
    private final Context context;
    private final ExportManager exportManager;
    private final ImportManager importManager;
    public PlantListPresenter(View view, PlantRepository repository, Context context) {
        this(view, repository, ensureAppContext(context),
            createExportManager(context, repository),
            createImportManager(context));
    }

    public PlantListPresenter(View view, PlantRepository repository, Context context,
                              ExportManager exportManager, ImportManager importManager) {
        this.view = view;
        this.repository = repository;
        this.context = context.getApplicationContext();
        this.exportManager = exportManager;
        this.importManager = importManager;
    }

    private static Context ensureAppContext(Context context) {
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        return appContext;
    }

    private static ExecutorService getIoExecutor(Context appContext) {
        return ((ExecutorProvider) appContext).getIoExecutor();
    }

    private static ExportManager createExportManager(Context context, PlantRepository repository) {
        Context appContext = ensureAppContext(context);
        return new ExportManager(appContext, repository, getIoExecutor(appContext));
    }

    private static ImportManager createImportManager(Context context) {
        Context appContext = ensureAppContext(context);
        return new ImportManager(appContext, getIoExecutor(appContext));
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

    public void requestExport() {
        view.requestExport(context.getString(R.string.export_file_name));
    }

    public void requestImport() {
        view.requestImport();
    }

    public void startExport(Uri uri) {
        view.showProgress();
        exportManager.export(uri, success -> {
            view.hideProgress();
            view.onExportResult(success, uri);
        }, (c, t) -> view.onExportProgress(c, t));
    }

    public void startImport(Uri uri, ImportManager.Mode mode) {
        view.showProgress();
        importManager.importData(uri, mode, (success, error, warnings, message) -> {
            view.hideProgress();
            view.onImportResult(success, error, warnings, message);
            if (success) {
                refreshPlants();
            }
        }, (c, t) -> view.onImportProgress(c, t));
    }

    public void filterPlants(String query) {
        if (query == null || query.trim().isEmpty()) {
            refreshPlants();
        } else {
            repository.searchPlants(query, view::showSearchResults,
                e -> view.showError(context.getString(R.string.error_database)));
        }
    }

    public interface View {
        void showPlants(List<Plant> plants);

        void showSearchResults(List<Plant> plants);

        void showError(String message);

        void showProgress();

        void hideProgress();

        void requestExport(String fileName);

        void requestImport();

        void onExportProgress(int current, int total);

        void onExportResult(boolean success, Uri uri);

        void onImportProgress(int current, int total);

        void onImportResult(boolean success, @Nullable ImportManager.ImportError error,
                            List<ImportManager.ImportWarning> warnings,
                            @Nullable String message);
    }
}
