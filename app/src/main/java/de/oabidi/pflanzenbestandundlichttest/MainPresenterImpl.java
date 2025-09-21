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

import java.util.concurrent.ExecutorService;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

/**
 * Implementation of {@link MainPresenter} handling main screen interactions such as
 * import/export, permission requests and navigation decisions.
 */
public class MainPresenterImpl implements MainPresenter {
    private final MainView view;
    private final Context context;
    private final PlantRepository repository;
    private ExportManager exportManager;
    private ImportManager importManager;
    private SharedPreferences preferences;
    private ExportManager.Format preferredFormat = ExportManager.Format.JSON;
    private ExportManager.Format currentExportFormat;

    public MainPresenterImpl(MainView view, Context context, PlantRepository repository) {
        this.view = view;
        this.context = context.getApplicationContext();
        this.repository = repository;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, Intent intent) {
        if (!(context instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        ExecutorService executor = ((ExecutorProvider) context).getIoExecutor();
        exportManager = new ExportManager(context, repository, executor);
        importManager = new ImportManager(context, executor);
        preferences = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        preferredFormat = parseFormat(preferences.getString(SettingsKeys.KEY_EXPORT_FORMAT,
            ExportManager.Format.JSON.name()));

        if (Build.VERSION.SDK_INT >= 33
            && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            view.requestNotificationPermission(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (savedInstanceState == null) {
            boolean hasOnboarded = preferences.getBoolean(SettingsKeys.KEY_HAS_ONBOARDED, false);
            if (!hasOnboarded) {
                view.navigateToFragment(OnboardingFragment.newInstance(repository), false);
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
        ExportManager.Format format = currentExportFormat != null ? currentExportFormat : preferredFormat;
        if (uri != null) {
            view.showProgressBar();
            ExportManager.ProgressCallback progressCallback = view::showExportProgress;
            ExportManager.Callback callback = success -> {
                int msg = success ? R.string.export_success : R.string.export_failure;
                view.hideProgressBar();
                view.showToast(msg);
                currentExportFormat = null;
            };
            if (format == ExportManager.Format.JSON) {
                exportManager.exportJson(uri, callback, progressCallback);
            } else {
                exportManager.export(uri, callback, progressCallback);
            }
        } else {
            view.showToast(R.string.export_failure);
            currentExportFormat = null;
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
            view.navigateToFragment(OnboardingFragment.newInstance(repository), true);
            return true;
        } else if (id == R.id.action_export_data) {
            view.showExportFormatChooser(preferredFormat);
            return true;
        } else if (id == R.id.action_import_data) {
            view.launchImport(new String[]{"application/zip", "application/json"});
            return true;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_plants) {
            fragment = PlantListFragment.newInstance(repository);
        } else if (itemId == R.id.nav_measure) {
            fragment = LightMeasurementFragment.newInstance(repository);
        } else if (itemId == R.id.nav_diary) {
            fragment = DiaryFragment.newInstance(repository, -1);
        } else if (itemId == R.id.nav_reminders) {
            fragment = ReminderListFragment.newInstance(repository);
        } else if (itemId == R.id.nav_stats) {
            fragment = StatsFragment.newInstance(repository);
        } else {
            fragment = new SettingsFragment();
        }
        view.navigateToFragment(fragment, false);
        return true;
    }

    @Override
    public void onExportFormatChosen(ExportManager.Format format) {
        preferredFormat = format;
        currentExportFormat = format;
        if (preferences != null) {
            preferences.edit().putString(SettingsKeys.KEY_EXPORT_FORMAT, format.name()).apply();
        }
        view.launchExport(getFileNameForFormat(format));
    }

    private ExportManager.Format parseFormat(@Nullable String stored) {
        if (stored != null) {
            try {
                return ExportManager.Format.valueOf(stored);
            } catch (IllegalArgumentException ignored) {
                // Fall back to default
            }
        }
        return ExportManager.Format.JSON;
    }

    private String getFileNameForFormat(ExportManager.Format format) {
        if (format == ExportManager.Format.JSON) {
            return context.getString(R.string.export_file_name_json);
        }
        return context.getString(R.string.export_file_name);
    }
}
