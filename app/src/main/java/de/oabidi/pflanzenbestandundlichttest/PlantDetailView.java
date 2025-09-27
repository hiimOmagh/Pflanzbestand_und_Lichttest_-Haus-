package de.oabidi.pflanzenbestandundlichttest;

import java.util.List;

import androidx.annotation.Nullable;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;

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

    /** Navigates to the environment log for the given plant. */
    void navigateToEnvironmentLog(long plantId);

    /** Displays the supplied care recommendations. */
    void showCareRecommendations(List<CareRecommendation> items);

    /** Shows an empty state for the care recommendation list. */
    void showCareRecommendationsEmpty();

    /** Indicates that loading care recommendations failed. */
    void showCareRecommendationError();

    /** Toggles the loading indicator for care recommendations. */
    void setCareRecommendationsLoading(boolean loading);

    /** Notifies that the supplied recommendation was dismissed. */
    void onCareRecommendationDismissed(String recommendationId);

    /** Provides a placeholder for empty text fields. */
    String getPlaceholderDash();

    /** Provides the text for an unknown date. */
    String getUnknownDateText();

    /** Displays species metadata such as watering guidance and care tips. */
    void showSpeciesMetadata(PlantMetadataViewModel metadata);

    /** Displays a fallback message when no species metadata is available. */
    void showSpeciesMetadataUnavailable(String message);

    /** Provides the localized fallback text when metadata cannot be resolved. */
    String getSpeciesMetadataUnavailableText();

    /** Displays the latest natural DLI estimate for the plant. */
    void showNaturalDli(@Nullable Float dli, @Nullable Long timestamp);
}
