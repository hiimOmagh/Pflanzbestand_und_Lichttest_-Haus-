package de.oabidi.pflanzenbestandundlichttest;

/**
 * View interface for displaying plant details.
 */
public interface PlantDetailView {
    /** Notifies the user that the export completed successfully. */
    void showExportSuccess();

    /** Notifies the user that the export failed. */
    void showExportFailure();

    /** Launches a document creation flow for exporting plant data. */
    void launchExport();

    /** Navigates to the diary for the given plant. */
    void navigateToDiary(long plantId);

    /** Provides a placeholder for empty text fields. */
    String getPlaceholderDash();

    /** Provides the text for an unknown date. */
    String getUnknownDateText();
}
