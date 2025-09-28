package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.TestExecutorApp;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntryDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.LightSummary;
import de.oabidi.pflanzenbestandundlichttest.repository.ArtificialLightEstimateSource;
import de.oabidi.pflanzenbestandundlichttest.repository.CareRecommendationDelegate;
import de.oabidi.pflanzenbestandundlichttest.repository.EnvironmentRepository;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit tests covering the EnvironmentLogPresenter chart aggregation logic.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class EnvironmentLogPresenterTest {

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

    @Test
    public void loadEntries_reportsLatestNaturalDli() {
        Context context = ApplicationProvider.getApplicationContext();
        EnvironmentEntry older = buildEntry(1L, 1_000L, null, null, null, null);
        older.setNaturalDli(8.4f);
        EnvironmentEntry newer = buildEntry(1L, 2_000L, null, null, null, null);
        newer.setNaturalDli(12.1f);
        EnvironmentEntry without = buildEntry(1L, 3_000L, null, null, null, null);
        StubRepository repository = new StubRepository(context, List.of(older, newer, without));
        StubView view = new StubView();
        EnvironmentLogPresenter presenter = new EnvironmentLogPresenter(view, repository, 1L, context);

        presenter.loadEntries();

        assertNotNull(view.lightSummary);
        assertEquals(Float.valueOf(12.1f), view.lightSummary.getNaturalDli());
        assertEquals(Long.valueOf(2_000L), view.lightSummary.getNaturalTimestamp());
        assertNull(view.lightSummary.getArtificialDli());
    }

    @Test
    public void loadEntries_reportsArtificialDliWhenNaturalMissing() {
        Context context = ApplicationProvider.getApplicationContext();
        EnvironmentEntry natural = buildEntry(1L, 1_000L, null, null, null, null);
        natural.setNaturalDli(5.5f);
        EnvironmentEntry artificial = buildEntry(1L, 3_000L, null, null, null, null);
        artificial.setArtificialDli(9.3f);
        EnvironmentEntry hours = buildEntry(1L, 4_000L, null, null, null, null);
        hours.setArtificialHours(11.5f);
        hours.setArtificialDli(6.2f);
        StubRepository repository = new StubRepository(context, List.of(natural, artificial, hours));
        StubView view = new StubView();
        EnvironmentLogPresenter presenter = new EnvironmentLogPresenter(view, repository, 1L, context);

        presenter.loadEntries();

        assertNotNull(view.lightSummary);
        assertEquals(Float.valueOf(5.5f), view.lightSummary.getNaturalDli());
        assertEquals(Long.valueOf(1_000L), view.lightSummary.getNaturalTimestamp());
        assertEquals(Float.valueOf(9.3f), view.lightSummary.getArtificialDli());
        assertEquals(Long.valueOf(3_000L), view.lightSummary.getArtificialTimestamp());
    }

    @Test
    public void onSubmit_withOnlyPhotoPersistsEntry() {
        Context context = ApplicationProvider.getApplicationContext();
        EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);
        EnvironmentLogView view = Mockito.mock(EnvironmentLogView.class);
        EnvironmentLogPresenter presenter = new EnvironmentLogPresenter(view, repository, 5L, context);

        doAnswer(invocation -> {
            EnvironmentEntry entry = invocation.getArgument(0);
            Runnable callback = invocation.getArgument(1);
            entry.setId(42L);
            callback.run();
            return null;
        }).when(repository).insertEnvironmentEntry(any(EnvironmentEntry.class), any(Runnable.class), any());

        presenter.onPhotoSelected("file:///tmp/photo.jpg");
        presenter.onSubmit(new EnvironmentLogFormData(null, null, null, null, null, null, null));

        ArgumentCaptor<EnvironmentEntry> captor = ArgumentCaptor.forClass(EnvironmentEntry.class);
        verify(repository).insertEnvironmentEntry(captor.capture(), any(Runnable.class), any());
        EnvironmentEntry inserted = captor.getValue();
        assertEquals("file:///tmp/photo.jpg", inserted.getPhotoUri());
        verify(view, never()).showEmptyFormError();
        verify(view).clearForm();
    }

    private static class StubRepository extends EnvironmentRepository {
        private final List<EnvironmentEntry> entries;

        StubRepository(Context context, List<EnvironmentEntry> entries) {
            super(context, new Handler(Looper.getMainLooper()), new DirectExecutorService(),
                new NoOpEnvironmentEntryDao(), new NoOpCareDelegate(),
                plantId -> ArtificialLightEstimateSource.ArtificialLightEstimate.empty());
            this.entries = entries;
        }

        @Override
        public void environmentEntriesForPlant(long plantId, Consumer<List<EnvironmentEntry>> callback,
                                               @Nullable Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(entries);
            }
        }
    }

    private static class NoOpEnvironmentEntryDao implements EnvironmentEntryDao {
        @Override
        public List<EnvironmentEntry> getForPlantOrdered(long plantId) {
            return Collections.emptyList();
        }

        @Override
        public List<EnvironmentEntry> getRecentForPlant(long plantId, int limit) {
            return Collections.emptyList();
        }

        @Override
        public long insert(EnvironmentEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(EnvironmentEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(EnvironmentEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EnvironmentEntry getLatestWithLight(long plantId) {
            return null;
        }
    }

    private static class NoOpCareDelegate implements CareRecommendationDelegate {
        @Override
        public Runnable refreshCareRecommendationsAsync(long plantId) {
            return () -> {
            };
        }
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            if (command != null) {
                command.run();
            }
        }
    }

    private static class StubView implements EnvironmentLogView {
        EnvironmentLogPresenter.ChartData growthData;
        EnvironmentLogPresenter.ChartData climateData;
        List<EnvironmentLogPresenter.PhotoHighlight> photoHighlights;
        LightSummary lightSummary;

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
        public void showPhotoHighlights(List<EnvironmentLogPresenter.PhotoHighlight> highlights) {
            photoHighlights = highlights;
        }

        @Override
        public void showLightSummary(@NonNull LightSummary summary) {
            lightSummary = summary;
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
        public void showPhotoPreview(@Nullable String photoUri) {
        }

        @Override
        public void notifyLogEvent(@NonNull String event, long entryId) {
        }
    }
}
