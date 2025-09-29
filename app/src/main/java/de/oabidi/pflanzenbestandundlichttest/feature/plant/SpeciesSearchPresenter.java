package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import androidx.annotation.Nullable;

/**
 * Presenter contract coordinating species search interactions between the repository and the view.
 */
public interface SpeciesSearchPresenter {
    /**
     * Trigger a new search for the provided query. Passing {@code null} or an empty string clears the results.
     */
    void onSearchQueryChanged(@Nullable String query);

    /**
     * Clean up any resources when the presenter is no longer needed.
     */
    void onDestroy();
}
