package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tests for {@link DiaryPresenter} verifying repository injection.
 */
@RunWith(RobolectricTestRunner.class)
public class DiaryPresenterTest {
    private static class StubRepository extends PlantRepository {
        private final List<DiaryEntry> entries;
        StubRepository(Context context, List<DiaryEntry> entries) {
            super(context);
            this.entries = entries;
        }
        @Override
        public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback,
                                       Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(entries);
            }
        }
    }

    private static class FailingRepository extends PlantRepository {
        FailingRepository(Context context) { super(context); }
        @Override
        public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback,
                                       Consumer<Exception> errorCallback) {
            if (errorCallback != null) {
                errorCallback.accept(new RuntimeException("fail"));
            }
        }
    }

    private static class StubView implements DiaryPresenter.View {
        List<DiaryEntry> shown;
        String error;
        @Override public void showEntries(List<DiaryEntry> entries) { shown = entries; }
        @Override public void showError(String message) { error = message; }
    }

    @Test
    public void loadEntriesUsesRepository() {
        Context context = ApplicationProvider.getApplicationContext();
        List<DiaryEntry> entries = List.of(new DiaryEntry(1, 0L, DiaryEntry.TYPE_WATER, null));
        StubRepository repo = new StubRepository(context, entries);
        StubView view = new StubView();
        DiaryPresenter presenter = new DiaryPresenter(view, repo, 1L, context);
        presenter.loadEntries("water");
        assertEquals(entries, view.shown);
    }

    @Test
    public void loadEntriesErrorShowsMessage() {
        Context context = ApplicationProvider.getApplicationContext();
        FailingRepository repo = new FailingRepository(context);
        StubView view = new StubView();
        DiaryPresenter presenter = new DiaryPresenter(view, repo, 1L, context);
        presenter.loadEntries("test");
        assertEquals(context.getString(R.string.error_database), view.error);
    }
}
