package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.testing.FragmentScenario;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Verifies that fragments reacquire the repository after being recreated.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = FragmentRepositoryRecreationTest.TestPlantApplication.class)
public class FragmentRepositoryRecreationTest {
    private static PlantRepository sharedRepository;

    @BeforeClass
    public static void setUpClass() {
        sharedRepository = Mockito.mock(PlantRepository.class, Mockito.withSettings().lenient());
        TestPlantApplication.setRepository(sharedRepository);
    }

    @AfterClass
    public static void tearDownClass() {
        TestPlantApplication.setRepository(null);
    }

    @Before
    public void resetRepository() {
        TestPlantApplication.setRepository(sharedRepository);
    }

    @Test
    public void plantListFragment_reacquiresRepositoryOnRecreation() {
        FragmentScenario<PlantListFragment> scenario = FragmentScenario.launchInContainer(
            PlantListFragment.class,
            null,
            R.style.Theme_Pflanzenbestand,
            null);

        scenario.onFragment(fragment -> assertSame(sharedRepository, getRepository(fragment)));

        scenario.recreate();

        scenario.onFragment(fragment -> assertSame(sharedRepository, getRepository(fragment)));
    }

    @Test
    public void diaryFragment_reacquiresRepositoryOnRecreation() {
        Bundle args = new Bundle();
        args.putLong("plantId", -1L);
        FragmentScenario<DiaryFragment> scenario = FragmentScenario.launchInContainer(
            DiaryFragment.class,
            args,
            R.style.Theme_Pflanzenbestand,
            null);

        scenario.onFragment(fragment -> assertSame(sharedRepository, getRepository(fragment)));

        scenario.recreate();

        scenario.onFragment(fragment -> assertSame(sharedRepository, getRepository(fragment)));
    }

    private static PlantRepository getRepository(Fragment fragment) {
        try {
            Field field = fragment.getClass().getDeclaredField("repository");
            field.setAccessible(true);
            PlantRepository repo = (PlantRepository) field.get(fragment);
            assertNotNull("Repository should be initialized", repo);
            return repo;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("Unable to access repository field", e);
        }
    }

    /**
     * Minimal application exposing a shared repository for tests.
     */
    public static class TestPlantApplication extends Application implements RepositoryProvider, ExecutorProvider {
        private static PlantRepository repository;
        private ExecutorService executor;

        static void setRepository(PlantRepository repo) {
            repository = repo;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            if (executor == null) {
                executor = Executors.newSingleThreadExecutor();
            }
        }

        @Override
        public PlantRepository getRepository() {
            return repository;
        }

        @Override
        public ExecutorService getIoExecutor() {
            if (executor == null || executor.isShutdown()) {
                executor = Executors.newSingleThreadExecutor();
            }
            return executor;
        }

        @Override
        public void onTerminate() {
            super.onTerminate();
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
    }
}
