package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Bundle;

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

/**
 * Tests for {@link DiaryPresenter} verifying repository injection.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
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

    @Test
    public void diaryFragmentNewInstanceRetainsProvidedRepository() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository repository = new PlantRepository(context, TestExecutors.newImmediateExecutor());
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
        PlantRepository fragmentRepository = (PlantRepository) repositoryField.get(fragment);

        PlantRepository expectedRepository = ((RepositoryProvider) context).getRepository();
        assertSame(expectedRepository, fragmentRepository);
    }
}
