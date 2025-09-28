package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryEntryFts;
import de.oabidi.pflanzenbestandundlichttest.repository.DiaryRepository;

/**
 * Tests for {@link DiaryPresenter} verifying repository injection.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class DiaryPresenterTest {
    @Test
    public void loadEntriesUsesRepository() {
        Context context = ApplicationProvider.getApplicationContext();
        List<DiaryEntry> entries = List.of(new DiaryEntry(1, 0L, DiaryEntry.TYPE_WATER, null));
        StubDiaryRepository repo = new StubDiaryRepository(context, entries);
        StubView view = new StubView();
        DiaryPresenter presenter = new DiaryPresenter(view, repo, 1L, context);
        presenter.loadEntries("water");
        assertEquals(entries, view.shown);
    }

    @Test
    public void loadEntriesErrorShowsMessage() {
        Context context = ApplicationProvider.getApplicationContext();
        FailingDiaryRepository repo = new FailingDiaryRepository(context);
        StubView view = new StubView();
        DiaryPresenter presenter = new DiaryPresenter(view, repo, 1L, context);
        presenter.loadEntries("test");
        assertEquals(context.getString(R.string.error_database), view.error);
    }

    @Test
    public void diaryFragmentNewInstanceRetainsProvidedRepository() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DiaryRepository repository = new DiaryRepository(context, new Handler(Looper.getMainLooper()),
            TestExecutors.newImmediateExecutor(), new NoOpDiaryDao());
        DiaryFragment fragment = DiaryFragment.newInstance(repository, 42L);

        Field repositoryField = DiaryFragment.class.getDeclaredField("repository");
        repositoryField.setAccessible(true);
        assertSame(repository, repositoryField.get(fragment));
    }

    @Test
    public void diaryFragmentResolvesRepositoryWhenNotInjected() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        DiaryFragment fragment = new DiaryFragment();
        Bundle args = new Bundle();
        args.putLong("plantId", 1L);
        fragment.setArguments(args);

        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        Field repositoryField = DiaryFragment.class.getDeclaredField("repository");
        repositoryField.setAccessible(true);
        DiaryRepository fragmentRepository = (DiaryRepository) repositoryField.get(fragment);

        DiaryRepository expectedRepository = ((RepositoryProvider) context).getDiaryRepository();
        assertSame(expectedRepository, fragmentRepository);
    }

    private static final class StubDiaryRepository extends DiaryRepository {
        private final List<DiaryEntry> entries;

        StubDiaryRepository(Context context, List<DiaryEntry> entries) {
            super(context, new Handler(Looper.getMainLooper()), TestExecutors.newImmediateExecutor(), new NoOpDiaryDao());
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

    private static final class FailingDiaryRepository extends DiaryRepository {
        FailingDiaryRepository(Context context) {
            super(context, new Handler(Looper.getMainLooper()), TestExecutors.newImmediateExecutor(), new NoOpDiaryDao());
        }

        @Override
        public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback,
                                       Consumer<Exception> errorCallback) {
            if (errorCallback != null) {
                errorCallback.accept(new RuntimeException("fail"));
            }
        }
    }

    private static final class NoOpDiaryDao implements DiaryDao {
        @Override
        public long insertInternal(DiaryEntry entry) {
            throw new UnsupportedOperationException("insertInternal not supported in tests");
        }

        @Override
        public void insertFts(DiaryEntryFts entryFts) {
            throw new UnsupportedOperationException("insertFts not supported in tests");
        }

        @Override
        public void deleteInternal(DiaryEntry entry) {
            throw new UnsupportedOperationException("deleteInternal not supported in tests");
        }

        @Override
        public void deleteFts(long rowid) {
            throw new UnsupportedOperationException("deleteFts not supported in tests");
        }

        @Override
        public void updateInternal(DiaryEntry entry) {
            throw new UnsupportedOperationException("updateInternal not supported in tests");
        }

        @Override
        public List<DiaryEntry> entriesForPlant(long plantId) {
            throw new UnsupportedOperationException("entriesForPlant not supported in tests");
        }

        @Override
        public DiaryEntry latestForPlant(long plantId) {
            throw new UnsupportedOperationException("latestForPlant not supported in tests");
        }

        @Override
        public List<DiaryEntry> searchDiaryEntries(long plantId, String query) {
            throw new UnsupportedOperationException("searchDiaryEntries not supported in tests");
        }

        @Override
        public List<DiaryEntry> getAll() {
            throw new UnsupportedOperationException("getAll not supported in tests");
        }
    }

    private static class StubView implements DiaryPresenter.View {
        List<DiaryEntry> shown;
        String error;

        @Override
        public void showEntries(List<DiaryEntry> entries) {
            shown = entries;
        }

        @Override
        public void showError(String message) {
            error = message;
        }
    }
}
