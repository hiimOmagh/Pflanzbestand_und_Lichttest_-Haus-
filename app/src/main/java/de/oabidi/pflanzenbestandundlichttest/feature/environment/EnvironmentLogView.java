package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.LightSummary;

/**
 * Contract implemented by the environment log UI.
 */
public interface EnvironmentLogView {
    /** Displays the list of environment log items. */
    void showEntries(List<EnvironmentLogPresenter.EnvironmentLogItem> items);

    /** Toggles a loading indicator while background work is running. */
    void showLoading(boolean loading);

    /** Shows or hides the empty state for the list. */
    void showEmptyState(boolean isEmpty);

    /** Updates the growth chart with the provided data, hiding it when {@code null}. */
    void showGrowthChart(@Nullable EnvironmentLogPresenter.ChartData data);

    /** Updates the climate chart with the provided data, hiding it when {@code null}. */
    void showClimateChart(@Nullable EnvironmentLogPresenter.ChartData data);

    /** Displays photo highlights aligned with the chart timeline. */
    void showPhotoHighlights(List<EnvironmentLogPresenter.PhotoHighlight> highlights);

    /** Displays the latest light summary if available. */
    void showLightSummary(@NonNull LightSummary summary);

    /** Updates the currently selected photo preview in the form. */
    void showPhotoPreview(@Nullable String photoUri);

    /** Presents a transient message to the user. */
    void showMessage(String message);

    /** Presents an error message to the user. */
    void showError(String message);

    /** Highlights that the form submission is missing required information. */
    void showEmptyFormError();

    /** Clears all form inputs. */
    void clearForm();

    /** Populates the form with the values from the provided entry. */
    void populateForm(EnvironmentEntry entry);

    /** Toggles the editing state, updating affordances such as button labels. */
    void showEditingState(boolean editing);

    /** Notifies the host that the log changed. */
    void notifyLogEvent(@NonNull String event, long entryId);
}
