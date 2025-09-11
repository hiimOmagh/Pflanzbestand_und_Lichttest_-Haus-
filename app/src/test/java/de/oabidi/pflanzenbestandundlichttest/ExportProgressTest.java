package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that export progress updates are forwarded to the view.
 */
@RunWith(RobolectricTestRunner.class)
public class ExportProgressTest {
    private Context context;

    private static class FakeView implements MainView {
        final List<int[]> progress = new ArrayList<>();
        String lastExportFileName;
        @Override public void navigateToFragment(androidx.fragment.app.Fragment fragment, boolean addToBackStack) { }
        @Override public void showToast(int messageResId) { }
        @Override public void showLongToast(int messageResId) { }
        @Override public void showExportProgress(int current, int total) { progress.add(new int[]{current, total}); }
        @Override public void selectNavigationItem(int itemId) { }
        @Override public void requestNotificationPermission(String permission) { }
        @Override public void launchExport(String fileName) { lastExportFileName = fileName; }
        @Override public void launchImport(String[] mimeTypes) { }
        @Override public void showImportWarnings(String message) { }
    }

    private static class FakeExportManager extends ExportManager {
        FakeExportManager(Context context) { super(context); }
        @Override
        public void export(Uri uri, Callback callback, ProgressCallback progressCallback) {
            progressCallback.onProgress(1, 3);
            progressCallback.onProgress(3, 3);
            callback.onComplete(true);
        }
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void exportFlow_reportsProgress() throws Exception {
        FakeView view = new FakeView();
        MainPresenterImpl presenter = new MainPresenterImpl(view, context);
        Field f = MainPresenterImpl.class.getDeclaredField("exportManager");
        f.setAccessible(true);
        f.set(presenter, new FakeExportManager(context));

        boolean handled = presenter.onOptionsItemSelected(R.id.action_export_data);
        assertTrue(handled);
        assertEquals(context.getString(R.string.export_file_name), view.lastExportFileName);

        presenter.handleExportResult(Uri.parse("content://test"));
        assertEquals(2, view.progress.size());
        assertArrayEquals(new int[]{1,3}, view.progress.get(0));
        assertArrayEquals(new int[]{3,3}, view.progress.get(1));
    }
}
