package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

/**
 * Tests for {@link PlantListPresenter} covering filtering, export and import behaviour.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = PlantListPresenterTest.TestApp.class)
public class PlantListPresenterTest {

    public static class TestApp extends Application implements RepositoryProvider, ExecutorProvider {
        private PlantRepository repository;
        private ExecutorService executor;
        @Override
        public synchronized PlantRepository getRepository() {
            return repository;
        }
        void setRepository(PlantRepository repo) {
            this.repository = repo;
        }
        @Override
        public synchronized ExecutorService getIoExecutor() {
            if (executor == null || executor.isShutdown()) {
                executor = TestExecutors.newImmediateExecutor();
            }
            return executor;
        }
    }

    private static class StubPlantRepository extends PlantRepository {
        private final List<Plant> data;
        StubPlantRepository(Context context, List<Plant> data) {
            super(context, TestExecutors.newImmediateExecutor());
            this.data = data;
        }
        @Override
        public void searchPlants(String query, java.util.function.Consumer<List<Plant>> cb,
                                 java.util.function.Consumer<Exception> errorCallback) {
            String lower = query.toLowerCase(Locale.ROOT);
            List<Plant> result = new ArrayList<>();
            for (Plant plant : data) {
                String name = plant.getName();
                String species = plant.getSpecies();
                String location = plant.getLocationHint();
                if ((name != null && name.toLowerCase(Locale.ROOT).contains(lower))
                    || (species != null && species.toLowerCase(Locale.ROOT).contains(lower))
                    || (location != null && location.toLowerCase(Locale.ROOT).contains(lower))) {
                    result.add(plant);
                }
            }
            if (cb != null) {
                cb.accept(result);
            }
        }
        @Override
        public void getAllPlants(java.util.function.Consumer<List<Plant>> callback,
                                 java.util.function.Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(new ArrayList<>(data));
            }
        }
    }

    private static class FailingRepository extends PlantRepository {
        FailingRepository(Context context) { super(context, TestExecutors.newImmediateExecutor()); }
        @Override
        public void searchPlants(String query, java.util.function.Consumer<List<Plant>> cb,
                                 java.util.function.Consumer<Exception> errorCallback) {
            if (errorCallback != null) {
                errorCallback.accept(new RuntimeException("fail"));
            }
        }
        @Override
        public void getAllPlants(java.util.function.Consumer<List<Plant>> callback,
                                 java.util.function.Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(new ArrayList<>());
            }
        }
    }

    private static class RecordingView implements PlantListPresenter.View {
        List<Plant> shown;
        boolean progressShown;
        boolean progressHidden;
        boolean exportRequested;
        boolean importRequested;
        boolean exportResult;
        Uri exportUri;
        boolean importResult;
        Uri importUri;
        ImportManager.Mode importMode;
        String lastError;
        @Override public void showPlants(List<Plant> plants) { shown = plants; }
        @Override public void showSearchResults(List<Plant> plants) { shown = plants; }
        @Override public void showError(String message) { lastError = message; }
        @Override public void showProgress() { progressShown = true; }
        @Override public void hideProgress() { progressHidden = true; }
        @Override public void requestExport(String fileName) { exportRequested = true; }
        @Override public void requestImport() { importRequested = true; }
        @Override public void onExportProgress(int current, int total) { }
        @Override public void onExportResult(boolean success, Uri uri) { exportResult = success; this.exportUri = uri; }
        @Override public void onImportProgress(int current, int total) { }
        @Override public void onImportResult(boolean success, ImportManager.ImportError error,
                                             List<ImportManager.ImportWarning> warnings,
                                             String message) { importResult = success; }
    }

    private static class FakeExportManager extends ExportManager {
        Uri lastUri;
        FakeExportManager(Context c) {
            super(c, new PlantRepository(c, TestExecutors.newImmediateExecutor()));
        }
        @Override
        public void export(@NonNull Uri uri, @NonNull Callback callback, ProgressCallback progressCallback) {
            lastUri = uri;
            if (progressCallback != null) {
                progressCallback.onProgress(1,1);
            }
            callback.onComplete(true);
        }
    }

    private static class FakeImportManager extends ImportManager {
        Uri lastUri; Mode lastMode;
        FakeImportManager(Context c) { super(c); }
        @Override
        public void importData(@NonNull Uri uri, @NonNull Mode mode, @NonNull Callback cb, ProgressCallback pc) {
            lastUri = uri; lastMode = mode;
            if (pc != null) { pc.onProgress(1,1); }
            cb.onComplete(true, null, Collections.emptyList(), "done");
        }
    }

    private PlantListPresenter createPresenter(List<Plant> data, RecordingView view,
                                               FakeExportManager em, FakeImportManager im) {
        Context context = ApplicationProvider.getApplicationContext();
        ((TestApp) context).setRepository(new StubPlantRepository(context, data));
        PlantRepository repo = ((TestApp) context).getRepository();
        return new PlantListPresenter(view, repo, context, em, im);
    }

    private PlantListPresenter createPresenterWithRepo(PlantRepository repo, RecordingView view,
                                                       FakeExportManager em, FakeImportManager im) {
        Context context = ApplicationProvider.getApplicationContext();
        ((TestApp) context).setRepository(repo);
        return new PlantListPresenter(view, repo, context, em, im);
    }

    @Test
    public void filterMatchesSpecies() {
        Plant p1 = new Plant("Aloe", null, "Aloe Vera", null, 0L, null);
        Plant p2 = new Plant("Cactus", null, "Echinocactus", null, 0L, null);
        RecordingView view = new RecordingView();
        PlantListPresenter presenter = createPresenter(Arrays.asList(p1, p2), view,
            new FakeExportManager(ApplicationProvider.getApplicationContext()),
            new FakeImportManager(ApplicationProvider.getApplicationContext()));
        presenter.filterPlants("vera");
        assertEquals(1, view.shown.size());
        assertSame(p1, view.shown.get(0));
    }

    @Test
    public void filterMatchesLocationHint() {
        Plant p1 = new Plant("Aloe", null, null, "Kitchen", 0L, null);
        Plant p2 = new Plant("Cactus", null, null, "Living Room", 0L, null);
        RecordingView view = new RecordingView();
        PlantListPresenter presenter = createPresenter(Arrays.asList(p1, p2), view,
            new FakeExportManager(ApplicationProvider.getApplicationContext()),
            new FakeImportManager(ApplicationProvider.getApplicationContext()));
        presenter.filterPlants("living");
        assertEquals(1, view.shown.size());
        assertSame(p2, view.shown.get(0));
    }

    @Test
    public void filterMatchesTurkishI() {
        Locale prev = Locale.getDefault();
        Locale.setDefault(new Locale("tr"));
        try {
            Plant p = new Plant("İnci", null, null, null, 0L, null);
            RecordingView view = new RecordingView();
            PlantListPresenter presenter = createPresenter(Collections.singletonList(p), view,
                new FakeExportManager(ApplicationProvider.getApplicationContext()),
                new FakeImportManager(ApplicationProvider.getApplicationContext()));
            presenter.filterPlants("I");
            assertEquals(1, view.shown.size());
            assertSame(p, view.shown.get(0));
        } finally {
            Locale.setDefault(prev);
        }
    }

    @Test
    public void startExportDelegatesToManager() {
        RecordingView view = new RecordingView();
        FakeExportManager em = new FakeExportManager(ApplicationProvider.getApplicationContext());
        FakeImportManager im = new FakeImportManager(ApplicationProvider.getApplicationContext());
        PlantListPresenter presenter = createPresenter(Collections.emptyList(), view, em, im);
        Uri uri = Uri.parse("file://test.zip");
        presenter.startExport(uri);
        assertTrue(view.progressShown);
        assertTrue(view.progressHidden);
        assertTrue(view.exportResult);
        assertEquals(uri, em.lastUri);
        assertEquals(uri, view.exportUri);
    }

    @Test
    public void startImportDelegatesToManager() {
        RecordingView view = new RecordingView();
        FakeExportManager em = new FakeExportManager(ApplicationProvider.getApplicationContext());
        FakeImportManager im = new FakeImportManager(ApplicationProvider.getApplicationContext());
        PlantListPresenter presenter = createPresenter(Collections.emptyList(), view, em, im);
        Uri uri = Uri.parse("file://import.zip");
        presenter.startImport(uri, ImportManager.Mode.MERGE);
        assertTrue(view.progressShown);
        assertTrue(view.progressHidden);
        assertTrue(view.importResult);
        assertEquals(uri, im.lastUri);
        assertEquals(ImportManager.Mode.MERGE, im.lastMode);
    }

    @Test
    public void filterPlantsErrorShowsMessage() {
        RecordingView view = new RecordingView();
        FakeExportManager em = new FakeExportManager(ApplicationProvider.getApplicationContext());
        FakeImportManager im = new FakeImportManager(ApplicationProvider.getApplicationContext());
        FailingRepository repo = new FailingRepository(ApplicationProvider.getApplicationContext());
        PlantListPresenter presenter = createPresenterWithRepo(repo, view, em, im);
        presenter.filterPlants("a");
        assertEquals(ApplicationProvider.getApplicationContext().getString(R.string.error_database), view.lastError);
    }
}
