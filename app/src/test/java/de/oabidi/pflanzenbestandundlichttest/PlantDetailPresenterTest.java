package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.BulkReadDao;
import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;

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

    private static class FakeView implements PlantDetailView {
        boolean success;
        boolean failure;

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
