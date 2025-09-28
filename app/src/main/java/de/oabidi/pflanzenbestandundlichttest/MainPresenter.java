package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import de.oabidi.pflanzenbestandundlichttest.core.system.ExportManager;

/**
 * Presenter contract for main screen orchestration.
 */
public interface MainPresenter {
    /** Initialise the presenter. */
    void onCreate(@Nullable Bundle savedInstanceState, Intent intent);

    /** Handle a new intent delivered to the activity. */
    void onNewIntent(Intent intent);

    /** Callback for notification permission results. */
    void onNotificationPermissionResult(boolean granted);

    /** Handle export result from the document picker. */
    void handleExportResult(@Nullable Uri uri);

    /** Handle import result from the document picker. */
    void handleImportResult(@Nullable Uri uri);

    /** Respond to options menu item selection. */
    boolean onOptionsItemSelected(int id);

    /** Respond to bottom navigation item selections. */
    boolean onNavigationItemSelected(int itemId);

    /** Persist the user's export format choice and start the export flow. */
    void onExportFormatChosen(ExportManager.Format format);
}
