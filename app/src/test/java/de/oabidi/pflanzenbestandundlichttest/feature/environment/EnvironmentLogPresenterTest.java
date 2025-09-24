package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.TestExecutorApp;
import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;

/**
 * Unit tests covering the EnvironmentLogPresenter chart aggregation logic.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class EnvironmentLogPresenterTest {

    @Test
    public void loadEntries_producesSortedChartSeries() {
        Context context = ApplicationProvider.getApplicationContext();
        EnvironmentEntry recent = buildEntry(1L, 3L, 21f, 60f, 15f, 6f);
        EnvironmentEntry middle = buildEntry(1L, 2L, 20f, null, null, 5f);
        EnvironmentEntry oldest = buildEntry(1L, 1L, 19f, 55f, 10f, 4f);
        StubRepository repository = new StubRepository(context, List.of(recent, middle, oldest));
        StubView view = new StubView();
        EnvironmentLogPresenter presenter = new EnvironmentLogPresenter(view, repository, 1L, context);

        presenter.loadEntries();

        assertNotNull(view.growthData);
        assertNotNull(view.climateData);

        String heightLabel = context.getString(R.string.environment_log_chart_label_height);
        String widthLabel = context.getString(R.string.environment_log_chart_label_width);
        EnvironmentLogPresenter.ChartSeries heightSeries = view.growthData.getSeries().stream()
            .filter(series -> heightLabel.equals(series.getLabel()))
            .findFirst()
            .orElseThrow();
        EnvironmentLogPresenter.ChartSeries widthSeries = view.growthData.getSeries().stream()
            .filter(series -> widthLabel.equals(series.getLabel()))
            .findFirst()
            .orElseThrow();

        assertEquals(2, heightSeries.getPoints().size());
        assertEquals(1L, heightSeries.getPoints().get(0).getTimestamp());
        assertEquals(10f, heightSeries.getPoints().get(0).getValue(), 0.001f);
        assertEquals(3L, heightSeries.getPoints().get(1).getTimestamp());
        assertEquals(15f, heightSeries.getPoints().get(1).getValue(), 0.001f);

        assertEquals(2, widthSeries.getPoints().size());
        assertEquals(2L, widthSeries.getPoints().get(0).getTimestamp());
        assertEquals(5f, widthSeries.getPoints().get(0).getValue(), 0.001f);
        assertEquals(3L, widthSeries.getPoints().get(1).getTimestamp());
        assertEquals(6f, widthSeries.getPoints().get(1).getValue(), 0.001f);

        String temperatureLabel = context.getString(R.string.environment_log_chart_label_temperature);
        EnvironmentLogPresenter.ChartSeries temperatureSeries = view.climateData.getSeries().stream()
            .filter(series -> temperatureLabel.equals(series.getLabel()))
            .findFirst()
            .orElseThrow();
        assertEquals(3, temperatureSeries.getPoints().size());
        assertEquals(1L, temperatureSeries.getPoints().get(0).getTimestamp());
        assertEquals(19f, temperatureSeries.getPoints().get(0).getValue(), 0.001f);
        assertEquals(2L, temperatureSeries.getPoints().get(1).getTimestamp());
        assertEquals(20f, temperatureSeries.getPoints().get(1).getValue(), 0.001f);
        assertEquals(3L, temperatureSeries.getPoints().get(2).getTimestamp());
        assertEquals(21f, temperatureSeries.getPoints().get(2).getValue(), 0.001f);
    }

    @Test
    public void loadEntries_withInsufficientDataHidesCharts() {
        Context context = ApplicationProvider.getApplicationContext();
        EnvironmentEntry single = buildEntry(2L, 10L, null, 60f, null, null);
        StubRepository repository = new StubRepository(context, List.of(single));
        StubView view = new StubView();
        EnvironmentLogPresenter presenter = new EnvironmentLogPresenter(view, repository, 2L, context);

        presenter.loadEntries();

        assertNull(view.growthData);
        assertNull(view.climateData);
    }

    private static EnvironmentEntry buildEntry(long plantId, long timestamp, Float temperature,
                                               Float humidity, Float height, Float width) {
        EnvironmentEntry entry = new EnvironmentEntry();
        entry.setPlantId(plantId);
        entry.setTimestamp(timestamp);
        entry.setTemperature(temperature);
        entry.setHumidity(humidity);
        entry.setHeight(height);
        entry.setWidth(width);
        return entry;
    }

    private static class StubRepository extends PlantRepository {
        private final List<EnvironmentEntry> entries;

        StubRepository(Context context, List<EnvironmentEntry> entries) {
            super(context);
            this.entries = entries;
        }

        @Override
        public void environmentEntriesForPlant(long plantId, java.util.function.Consumer<List<EnvironmentEntry>> callback,
                                               java.util.function.Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(entries);
            }
        }
    }

    private static class StubView implements EnvironmentLogView {
        EnvironmentLogPresenter.ChartData growthData;
        EnvironmentLogPresenter.ChartData climateData;

        @Override
        public void showEntries(List<EnvironmentLogPresenter.EnvironmentLogItem> items) {
        }

        @Override
        public void showLoading(boolean loading) {
        }

        @Override
        public void showEmptyState(boolean isEmpty) {
        }

        @Override
        public void showGrowthChart(EnvironmentLogPresenter.ChartData data) {
            growthData = data;
        }

        @Override
        public void showClimateChart(EnvironmentLogPresenter.ChartData data) {
            climateData = data;
        }

        @Override
        public void showMessage(String message) {
        }

        @Override
        public void showError(String message) {
        }

        @Override
        public void showEmptyFormError() {
        }

        @Override
        public void clearForm() {
        }

        @Override
        public void populateForm(EnvironmentEntry entry) {
        }

        @Override
        public void showEditingState(boolean editing) {
        }

        @Override
        public void notifyLogEvent(@NonNull String event, long entryId) {
        }
    }
}
