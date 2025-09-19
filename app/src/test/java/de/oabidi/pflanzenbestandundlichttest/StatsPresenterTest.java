package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import androidx.test.core.app.ApplicationProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tests for {@link StatsPresenter} verifying that the injected repository is used.
 */
@RunWith(RobolectricTestRunner.class)
public class StatsPresenterTest {
    @Test
    public void loadPlantsUsesRepository() {
        Context context = ApplicationProvider.getApplicationContext();
        List<Plant> plants = List.of(new Plant("Aloe", null, null, null, 0L, null));
        StubRepository repo = new StubRepository(context, plants);
        StubView view = new StubView();
        StatsPresenter presenter = new StatsPresenter(view, repo, context);
        presenter.loadPlants();
        assertEquals(plants, view.shown);
    }

    @Test
    public void loadDataErrorShowsMessage() {
        Context context = ApplicationProvider.getApplicationContext();
        FailingRepository repo = new FailingRepository(context);
        StubView view = new StubView();
        StatsPresenter presenter = new StatsPresenter(view, repo, context);
        presenter.loadDataForPlants(Arrays.asList(1L, 2L));
        assertEquals(context.getString(R.string.error_database), view.error);
    }

    private static class StubRepository extends PlantRepository {
        private final List<Plant> plants;

        StubRepository(Context context, List<Plant> plants) {
            super(context);
            this.plants = plants;
        }

        @Override
        public void getAllPlants(Consumer<List<Plant>> callback, Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(plants);
            }
        }
    }

    private static class StubView implements StatsPresenter.View {
        List<Plant> shown;
        String error;

        @Override
        public void showPlants(List<Plant> plants) {
            shown = plants;
        }

        @Override
        public void showMeasurements(Map<Long, List<Measurement>> data) {
        }

        @Override
        public void showDiaryCounts(String text) {
        }

        @Override
        public void showDli(String text) {
        }

        @Override
        public void showError(String message) {
            error = message;
        }
    }

    private static class FailingRepository extends PlantRepository {
        FailingRepository(Context context) {
            super(context);
        }

        @Override
        public void recentMeasurementsForPlant(long plantId, int limit,
                                               Consumer<List<Measurement>> callback,
                                               Consumer<Exception> errorCallback) {
            if (errorCallback != null) {
                errorCallback.accept(new RuntimeException("fail"));
            }
        }
    }
}
