package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import java.util.List;

import androidx.annotation.NonNull;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;

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

    /** Updates the placeholder messaging for the upcoming charts. */
    void updateChartPlaceholder(int entryCount);

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
