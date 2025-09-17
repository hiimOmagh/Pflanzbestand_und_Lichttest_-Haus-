package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.content.Context;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.util.Collections;
import java.lang.reflect.Field;

/**
 * Tests for {@link PlantListFragment}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class PlantListFragmentTest {

    @Test
    public void onImportResult_displaysProvidedMessage() {
        Context context = ApplicationProvider.getApplicationContext();
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        PlantRepository repository = new PlantRepository(context, TestExecutors.newImmediateExecutor());
        PlantListFragment fragment = PlantListFragment.newInstance(repository);
        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        String message = "custom message";
        fragment.onImportResult(false, null, Collections.emptyList(), message);

        assertEquals(message, ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void fragmentWithoutInjectedRepository_resolvesFromApplication() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        PlantListFragment fragment = new PlantListFragment();

        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        Field repositoryField = PlantListFragment.class.getDeclaredField("repository");
        repositoryField.setAccessible(true);
        PlantRepository fragmentRepository = (PlantRepository) repositoryField.get(fragment);

        PlantRepository expectedRepository = ((RepositoryProvider) context).getRepository();
        assertSame(expectedRepository, fragmentRepository);
    }
}
