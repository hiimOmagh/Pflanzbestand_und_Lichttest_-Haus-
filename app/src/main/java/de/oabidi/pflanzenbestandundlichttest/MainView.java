package de.oabidi.pflanzenbestandundlichttest;

import androidx.fragment.app.Fragment;

/**
 * View contract for {@link MainPresenter} interactions with the main screen.
 */
public interface MainView {
    /** Navigate to the provided fragment.
     * @param fragment Fragment to display
     * @param addToBackStack whether to add the transaction to the back stack
     */
    void navigateToFragment(Fragment fragment, boolean addToBackStack);

    /** Show a short toast message. */
    void showToast(int messageResId);

    /** Show a longer toast message. */
    void showLongToast(int messageResId);

    /** Programmatically select an item in the bottom navigation. */
    void selectNavigationItem(int itemId);

    /** Request a runtime permission from the user. */
    void requestNotificationPermission(String permission);

    /** Launch an export document picker with the given file name. */
    void launchExport(String fileName);

    /** Launch an import document picker with the specified MIME types. */
    void launchImport(String[] mimeTypes);
}
