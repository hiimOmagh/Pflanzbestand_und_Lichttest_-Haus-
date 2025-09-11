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

/**
 * Unit tests for {@link MainPresenterImpl} verifying navigation and onboarding logic.
 */
@RunWith(RobolectricTestRunner.class)
public class MainPresenterTest {
    private Context context;
    private FakeView view;
    private MainPresenter presenter;

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
        public void showToast(int messageResId) { }

        @Override
        public void showLongToast(int messageResId) { }

        @Override
        public void selectNavigationItem(int itemId) {
            lastSelectedId = itemId;
        }

        @Override
        public void requestNotificationPermission(String permission) { }

        @Override
        public void launchExport(String fileName) { }

        @Override
        public void launchImport(String[] mimeTypes) { }
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        view = new FakeView();
        presenter = new MainPresenterImpl(view, context);
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
}
