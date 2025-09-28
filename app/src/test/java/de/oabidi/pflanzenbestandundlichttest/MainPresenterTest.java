package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.fragment.app.Fragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.feature.light.measurement.LightMeasurementFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainPresenter;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainPresenterImpl;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainView;

/**
 * Unit tests for {@link MainPresenterImpl} verifying navigation and onboarding logic.
 */
@RunWith(RobolectricTestRunner.class)
public class MainPresenterTest {
    private Context context;
    private FakeView view;
    private MainPresenter presenter;
    private PlantRepository repository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        view = new FakeView();
        repository = new FakeRepository(context);
        presenter = new MainPresenterImpl(view, context, repository);
    }

    /**
     * When the user has not completed onboarding, the onboarding fragment should be shown.
     */
    @Test
    public void onCreate_notOnboarded_showsOnboarding() {
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(SettingsKeys.KEY_HAS_ONBOARDED, false).apply();
        presenter.onCreate(null, new Intent());
        assertTrue(view.lastFragment instanceof OnboardingFragment);
        assertFalse(view.lastAddToBack);
    }

    /**
     * Selecting the measurement navigation item should display the LightMeasurementFragment.
     */
    @Test
    public void navigation_measure_showsLightMeasurement() {
        presenter.onNavigationItemSelected(R.id.nav_measure);
        assertTrue(view.lastFragment instanceof LightMeasurementFragment);
    }

    @Test
    public void navigation_plants_usesProvidedRepository() throws Exception {
        presenter.onNavigationItemSelected(R.id.nav_plants);
        assertTrue(view.lastFragment instanceof PlantListFragment);
        java.lang.reflect.Field f = PlantListFragment.class.getDeclaredField("repository");
        f.setAccessible(true);
        assertSame(repository, f.get(view.lastFragment));
    }

    @Test
    public void navigation_diary_usesProvidedRepository() throws Exception {
        presenter.onNavigationItemSelected(R.id.nav_diary);
        assertTrue(view.lastFragment instanceof DiaryFragment);
        java.lang.reflect.Field f = DiaryFragment.class.getDeclaredField("repository");
        f.setAccessible(true);
        assertSame(repository, f.get(view.lastFragment));
    }

    /**
     * If the activity receives an intent requesting measurement navigation, the presenter selects
     * the measurement tab.
     */
    @Test
    public void onCreate_withNavigateMeasureExtra_selectsMeasure() {
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(SettingsKeys.KEY_HAS_ONBOARDED, true).apply();
        Intent intent = new Intent();
        intent.putExtra(MainActivity.EXTRA_NAVIGATE_MEASURE, true);
        presenter.onCreate(null, intent);
        assertEquals(Integer.valueOf(R.id.nav_measure), view.lastSelectedId);
    }

    private static class FakeRepository extends PlantRepository {
        FakeRepository(Context context) {
            super(context);
        }
    }

    private static class FakeView implements MainView {
        Fragment lastFragment;
        boolean lastAddToBack;
        Integer lastSelectedId;

        @Override
        public void navigateToFragment(Fragment fragment, boolean addToBackStack) {
            lastFragment = fragment;
            lastAddToBack = addToBackStack;
        }

        @Override
        public void showToast(int messageResId) {
        }

        @Override
        public void showToast(String message) {
        }

        @Override
        public void showLongToast(int messageResId) {
        }

        @Override
        public void showExportProgress(int current, int total) {
        }

        @Override
        public void showProgressBar() {
        }

        @Override
        public void hideProgressBar() {
        }

        @Override
        public void selectNavigationItem(int itemId) {
            lastSelectedId = itemId;
        }

        @Override
        public void requestNotificationPermission(String permission) {
        }

        @Override
        public void launchExport(String fileName) {
        }

        @Override
        public void showExportFormatChooser(ExportManager.Format currentFormat) {
        }

        @Override
        public void launchImport(String[] mimeTypes) {
        }

        @Override
        public void showImportWarnings(String message) {
        }
    }
}
