package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;

import java.util.List;
import java.util.Arrays;

import de.oabidi.pflanzenbestandundlichttest.BulkReadDao;
import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;

/**
 * Unit tests for {@link PlantDetailPresenter} verifying export handling and
 * text formatting.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class PlantDetailPresenterTest {
    private FakeView view;
    private Context context;
    private PlantRepository repository;
    private final Uri dummyUri = Uri.parse("file://test");

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        repository = mock(PlantRepository.class);
        when(repository.bulkDao()).thenReturn(mock(BulkReadDao.class));
        view = new FakeView();
    }

    @Test
    public void onExportUriSelected_success_notifiesView() {
        FakeExportManager manager = new FakeExportManager(context, repository, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 7L, manager, repository);
        presenter.onExportUriSelected(dummyUri);
        assertTrue(view.success);
        assertFalse(view.failure);
        assertEquals(dummyUri, manager.uri);
        assertEquals(7L, manager.plantId);
    }

    @Test
    public void onExportUriSelected_failure_notifiesView() {
        FakeExportManager manager = new FakeExportManager(context, repository, false);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 1L, manager, repository);
        presenter.onExportUriSelected(dummyUri);
        assertTrue(view.failure);
        assertFalse(view.success);
    }

    @Test
    public void getTextOrFallback_nullOrEmpty_returnsPlaceholder() {
        FakeExportManager manager = new FakeExportManager(context, repository, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 0L, manager, repository);
        assertEquals("-", presenter.getTextOrFallback(null));
        assertEquals("-", presenter.getTextOrFallback(""));
        assertEquals("abc", presenter.getTextOrFallback("abc"));
    }

    @Test
    public void loadSpeciesMetadata_withNullKey_showsFallback() {
        FakeExportManager manager = new FakeExportManager(context, repository, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 1L, manager, repository);
        presenter.loadSpeciesMetadata(null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("no-metadata", view.metadataFallback);
    }

    @Test
    public void loadSpeciesMetadata_withValidTarget_mapsMetadata() {
        FakeExportManager manager = new FakeExportManager(context, repository, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 1L, manager, repository);
        SpeciesTarget target = new SpeciesTarget("pothos",
            new SpeciesTarget.StageTarget(),
            new SpeciesTarget.StageTarget(),
            new SpeciesTarget.StageTarget(),
            null,
            null);
        target.setWateringInfo(new SpeciesTarget.WateringInfo("Weekly", "Loamy", "Moderate"));
        target.setTemperatureRange(new SpeciesTarget.FloatRange(18f, 24f));
        target.setHumidityRange(new SpeciesTarget.FloatRange(40f, 60f));
        target.setToxicToPets(Boolean.TRUE);
        target.setCareTips(Arrays.asList("Rotate plant", "Mist leaves"));

        doAnswer(invocation -> {
            java.util.function.Consumer<SpeciesTarget> callback = invocation.getArgument(1);
            callback.accept(target);
            return null;
        }).when(repository).getSpeciesTarget(eq("pothos"), any(), any());

        presenter.loadSpeciesMetadata("pothos");
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(view.lastMetadata);
        assertEquals("Weekly", view.lastMetadata.getWateringInfo().getFrequency());
        assertEquals(Float.valueOf(18f), view.lastMetadata.getTemperatureRange().getMin());
        assertEquals(Boolean.TRUE, view.lastMetadata.getToxicToPets());
        assertEquals(2, view.lastMetadata.getCareTips().size());
        assertNull(view.metadataFallback);
    }

    @Test
    public void loadSpeciesMetadata_missingTarget_showsFallback() {
        FakeExportManager manager = new FakeExportManager(context, repository, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 1L, manager, repository);
        doAnswer(invocation -> {
            java.util.function.Consumer<SpeciesTarget> callback = invocation.getArgument(1);
            callback.accept(null);
            return null;
        }).when(repository).getSpeciesTarget(eq("unknown"), any(), any());

        presenter.loadSpeciesMetadata("unknown");
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("no-metadata", view.metadataFallback);
    }

    @Test
    public void loadLatestNaturalDli_withValue_updatesView() {
        FakeExportManager manager = new FakeExportManager(context, repository, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 5L, manager, repository);
        EnvironmentEntry entry = new EnvironmentEntry();
        entry.setNaturalDli(12.3f);
        entry.setTimestamp(12345L);
        doAnswer(invocation -> {
            java.util.function.Consumer<EnvironmentEntry> callback = invocation.getArgument(1);
            callback.accept(entry);
            return null;
        }).when(repository).latestNaturalDliForPlant(eq(5L), any(), any());

        presenter.loadLatestNaturalDli();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(Float.valueOf(12.3f), view.lastDli);
        assertEquals(Long.valueOf(12345L), view.lastDliTimestamp);
        assertEquals(1, view.naturalDliCallCount);
    }

    @Test
    public void loadLatestNaturalDli_withoutValue_hidesView() {
        FakeExportManager manager = new FakeExportManager(context, repository, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 6L, manager, repository);
        doAnswer(invocation -> {
            java.util.function.Consumer<EnvironmentEntry> callback = invocation.getArgument(1);
            callback.accept(null);
            return null;
        }).when(repository).latestNaturalDliForPlant(eq(6L), any(), any());

        presenter.loadLatestNaturalDli();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertNull(view.lastDli);
        assertNull(view.lastDliTimestamp);
        assertEquals(1, view.naturalDliCallCount);
    }

    private static class FakeView implements PlantDetailView {
        boolean success;
        boolean failure;
        PlantMetadataViewModel lastMetadata;
        String metadataFallback;
        Float lastDli;
        Long lastDliTimestamp;
        int naturalDliCallCount;

        @Override
        public void showExportSuccess() {
            success = true;
        }

        @Override
        public void showExportFailure() {
            failure = true;
        }

        @Override
        public void launchExport() {
        }

        @Override
        public void navigateToDiary(long plantId) {
        }

        @Override
        public void navigateToEnvironmentLog(long plantId) {
        }

        @Override
        public void showCareRecommendations(List<CareRecommendation> items) {
        }

        @Override
        public void showCareRecommendationsEmpty() {
        }

        @Override
        public void showCareRecommendationError() {
        }

        @Override
        public void setCareRecommendationsLoading(boolean loading) {
        }

        @Override
        public void onCareRecommendationDismissed(String recommendationId) {
        }

        @Override
        public String getPlaceholderDash() {
            return "-";
        }

        @Override
        public String getUnknownDateText() {
            return "unknown";
        }

        @Override
        public void showSpeciesMetadata(PlantMetadataViewModel metadata) {
            lastMetadata = metadata;
        }

        @Override
        public void showSpeciesMetadataUnavailable(String message) {
            metadataFallback = message;
        }

        @Override
        public String getSpeciesMetadataUnavailableText() {
            return "no-metadata";
        }

        @Override
        public void showNaturalDli(Float dli, Long timestamp) {
            naturalDliCallCount++;
            lastDli = dli;
            lastDliTimestamp = timestamp;
        }
    }

    private static class FakeExportManager extends ExportManager {
        final boolean result;
        Uri uri;
        long plantId;

        FakeExportManager(Context context, PlantRepository repository, boolean result) {
            super(context, repository, TestExecutors.newImmediateExecutor());
            this.result = result;
        }

        @Override
        public void export(@NonNull Uri uri, long plantId, @NonNull Callback cb) {
            this.uri = uri;
            this.plantId = plantId;
            cb.onComplete(result);
        }
    }
}
