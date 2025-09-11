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

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

/**
 * Presenter handling main screen interactions such as import/export,
 * permission requests and initial navigation decisions.
 */
public class MainPresenter {
    public interface View {
        void navigateToFragment(Fragment fragment, boolean addToBackStack);
        void showToast(int messageResId);
        void showLongToast(int messageResId);
        void selectNavigationItem(int itemId);
        void requestNotificationPermission(String permission);
        void launchExport(String fileName);
        void launchImport(String[] mimeTypes);
    }

    private final View view;
    private final Context context;
    private ExportManager exportManager;
    private ImportManager importManager;

    public MainPresenter(View view, Context context) {
        this.view = view;
        this.context = context.getApplicationContext();
    }

    public void onCreate(@Nullable Bundle savedInstanceState, Intent intent) {
        exportManager = new ExportManager(context);
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
                view.navigateToFragment(new PlantListFragment(), false);
            }
        }
    }

    public void onNewIntent(Intent intent) {
        if (intent.getBooleanExtra(MainActivity.EXTRA_NAVIGATE_MEASURE, false)) {
            view.selectNavigationItem(R.id.nav_measure);
        }
    }

    public void onNotificationPermissionResult(boolean granted) {
        if (!granted) {
            view.showLongToast(R.string.notification_permission_denied);
        }
    }

    public void handleExportResult(@Nullable Uri uri) {
        if (uri != null) {
            exportManager.export(uri, success -> {
                int msg = success ? R.string.export_success : R.string.export_failure;
                view.showToast(msg);
            });
        } else {
            view.showToast(R.string.export_failure);
        }
    }

    public void handleImportResult(@Nullable Uri uri) {
        if (uri != null) {
            importManager.importData(uri, ImportManager.Mode.MERGE, (success, hadWarnings) -> {
                int msg = success ? R.string.import_success : R.string.import_failure;
                view.showToast(msg);
            });
        } else {
            view.showToast(R.string.import_failure);
        }
    }

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
}
