package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;

import java.text.DateFormat;
import java.util.Date;

/**
 * Presenter for {@link PlantDetailView} handling non-UI logic such as export,
 * text formatting and diary navigation.
 */
public class PlantDetailPresenter {
    private final PlantDetailView view;
    private final ExportManager exportManager;
    private final long plantId;
    private final DateFormat dateFormat = DateFormat.getDateInstance();

    public PlantDetailPresenter(PlantDetailView view, long plantId, ExportManager exportManager) {
        this.view = view;
        this.plantId = plantId;
        this.exportManager = exportManager;
    }

    /**
     * Returns the given text or a placeholder when {@code null} or empty.
     */
    public String getTextOrFallback(String text) {
        return (text == null || text.isEmpty()) ? view.getPlaceholderDash() : text;
    }

    /**
     * Formats the acquisition epoch using the current locale or an unknown date string.
     */
    public String formatAcquiredAt(long epoch) {
        return epoch == 0 ? view.getUnknownDateText() : dateFormat.format(new Date(epoch));
    }

    /** Handles diary button clicks. */
    public void onDiaryClicked() {
        view.navigateToDiary(plantId);
    }

    /** Handles environment log button clicks. */
    public void onEnvironmentLogClicked() {
        view.navigateToEnvironmentLog(plantId);
    }

    /** Initiates the export flow. */
    public void onExportRequested() {
        view.launchExport();
    }

    /**
     * Handles the URI returned from the export document picker.
     *
     * @param uri the target URI or {@code null} when user cancelled
     */
    public void onExportUriSelected(Uri uri) {
        if (uri == null) {
            view.showExportFailure();
            return;
        }
        exportManager.export(uri, plantId, success -> {
            if (success) {
                view.showExportSuccess();
            } else {
                view.showExportFailure();
            }
        });
    }
}
