package de.oabidi.pflanzenbestandundlichttest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import de.oabidi.pflanzenbestandundlichttest.feature.settings.SettingsFragment;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

/**
 * Implementation of {@link MainPresenter} handling main screen interactions such as
 * import/export, permission requests and navigation decisions.
 */
public class MainPresenterImpl implements MainPresenter {
    private final MainView view;
    private final Context context;
    private ExportManager exportManager;
    private ImportManager importManager;

    public MainPresenterImpl(MainView view, Context context) {
        this.view = view;
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, Intent intent) {
        PlantRepository repository = ((PlantApp) context).getRepository();
        exportManager = new ExportManager(context, repository);
        importManager = new ImportManager(context);

        if (Build.VERSION.SDK_INT >= 33
            && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            view.requestNotificationPermission(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (savedInstanceState == null) {
            SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
            boolean hasOnboarded = prefs.getBoolean(SettingsKeys.KEY_HAS_ONBOARDED, false);
            if (!hasOnboarded) {
                view.navigateToFragment(new OnboardingFragment(), false);
            } else if (intent.getBooleanExtra(MainActivity.EXTRA_NAVIGATE_MEASURE, false)) {
                view.selectNavigationItem(R.id.nav_measure);
            } else {
                view.selectNavigationItem(R.id.nav_plants);
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent.getBooleanExtra(MainActivity.EXTRA_NAVIGATE_MEASURE, false)) {
            view.selectNavigationItem(R.id.nav_measure);
        }
    }

    @Override
    public void onNotificationPermissionResult(boolean granted) {
        if (!granted) {
            view.showLongToast(R.string.notification_permission_denied);
        }
    }

    @Override
    public void handleExportResult(@Nullable Uri uri) {
        if (uri != null) {
            exportManager.export(uri, success -> {
                int msg = success ? R.string.export_success : R.string.export_failure;
                view.showToast(msg);
            }, view::showExportProgress);
        } else {
            view.showToast(R.string.export_failure);
        }
    }

    @Override
    public void handleImportResult(@Nullable Uri uri) {
        if (uri != null) {
            importManager.importData(uri, ImportManager.Mode.MERGE,
                (success, error, warnings, message) -> {
                    view.showToast(message);
                    if (success && !warnings.isEmpty()) {
                        String warningMsg = ImportManager.summarizeWarnings(warnings);
                        view.showImportWarnings(warningMsg);
                    }
                });
        } else {
            view.showToast(R.string.import_failure);
        }
    }

    @Override
    public boolean onOptionsItemSelected(int id) {
        if (id == R.id.action_help) {
            view.navigateToFragment(new OnboardingFragment(), true);
            return true;
        } else if (id == R.id.action_export_data) {
            view.launchExport(context.getString(R.string.export_file_name));
            return true;
        } else if (id == R.id.action_import_data) {
            view.launchImport(new String[]{"text/csv"});
            return true;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_plants) {
            fragment = new PlantListFragment();
        } else if (itemId == R.id.nav_measure) {
            fragment = new LightMeasurementFragment();
        } else if (itemId == R.id.nav_diary) {
            fragment = new DiaryFragment();
        } else if (itemId == R.id.nav_reminders) {
            fragment = new ReminderListFragment();
        } else if (itemId == R.id.nav_stats) {
            fragment = new StatsFragment();
        } else {
            fragment = new SettingsFragment();
        }
        view.navigateToFragment(fragment, false);
        return true;
    }
}
