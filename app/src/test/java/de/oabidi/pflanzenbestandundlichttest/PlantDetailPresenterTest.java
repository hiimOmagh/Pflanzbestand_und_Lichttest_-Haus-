package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.net.Uri;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit tests for {@link PlantDetailPresenter} verifying export handling and
 * text formatting.
 */
@RunWith(RobolectricTestRunner.class)
public class PlantDetailPresenterTest {
    private FakeView view;
    private Uri dummyUri = Uri.parse("file://test");

    @Before
    public void setUp() {
        view = new FakeView();
    }

    @Test
    public void onExportUriSelected_success_notifiesView() {
        Context ctx = ApplicationProvider.getApplicationContext();
        FakeExportManager manager = new FakeExportManager(ctx, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 7L, manager);
        presenter.onExportUriSelected(dummyUri);
        assertTrue(view.success);
        assertFalse(view.failure);
        assertEquals(dummyUri, manager.uri);
        assertEquals(7L, manager.plantId);
    }

    @Test
    public void onExportUriSelected_failure_notifiesView() {
        Context ctx = ApplicationProvider.getApplicationContext();
        FakeExportManager manager = new FakeExportManager(ctx, false);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 1L, manager);
        presenter.onExportUriSelected(dummyUri);
        assertTrue(view.failure);
        assertFalse(view.success);
    }

    @Test
    public void getTextOrFallback_nullOrEmpty_returnsPlaceholder() {
        Context ctx = ApplicationProvider.getApplicationContext();
        FakeExportManager manager = new FakeExportManager(ctx, true);
        PlantDetailPresenter presenter = new PlantDetailPresenter(view, 0L, manager);
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
        public String getPlaceholderDash() {
            return "-";
        }

        @Override
        public String getUnknownDateText() {
            return "unknown";
        }
    }

    private static class FakeExportManager extends ExportManager {
        boolean result;
        Uri uri;
        long plantId;

        FakeExportManager(Context context, boolean result) {
            PlantRepository fakeRepo = new PlantRepository(context, TestExecutors.newImmediateExecutor());
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
