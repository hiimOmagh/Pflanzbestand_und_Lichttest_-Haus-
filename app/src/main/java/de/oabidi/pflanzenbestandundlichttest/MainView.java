package de.oabidi.pflanzenbestandundlichttest;

import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.ExportManager;

/**
 * View contract for {@link MainPresenter} interactions with the main screen.
 */
public interface MainView {
    /** Navigate to the provided fragment.
     * @param fragment Fragment to display
     * @param addToBackStack whether to add the transaction to the back stack
     */
    void navigateToFragment(Fragment fragment, boolean addToBackStack);

    /** Show a short toast message using a string resource. */
    void showToast(int messageResId);

    /** Show a short toast message with a raw string. */
    void showToast(String message);

    /** Show a longer toast message. */
    void showLongToast(int messageResId);

    /** Display export progress to the user. */
    void showExportProgress(int current, int total);

    /** Show a progress bar for long running operations. */
    void showProgressBar();

    /** Hide the progress bar once operations complete. */
    void hideProgressBar();

    /** Programmatically select an item in the bottom navigation. */
    void selectNavigationItem(int itemId);

    /** Request a runtime permission from the user. */
    void requestNotificationPermission(String permission);

    /** Launch an export document picker with the given file name. */
    void launchExport(String fileName);

    /** Display a dialog allowing the user to choose an export format. */
    void showExportFormatChooser(ExportManager.Format currentFormat);

    /** Launch an import document picker with the specified MIME types. */
    void launchImport(String[] mimeTypes);

    /** Display summarized warnings after an import. */
    void showImportWarnings(String message);
}
