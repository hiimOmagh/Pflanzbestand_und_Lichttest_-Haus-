package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;

/**
 * Contract for UI components showing species search results.
 */
public interface SpeciesSearchView {
    /**
     * Display a loading indicator while a search is running.
     */
    void showLoading();

    /**
     * Hide any loading indicator once a search completes.
     */
    void hideLoading();

    /**
     * Render the given search results.
     */
    void showResults(List<SpeciesTarget> results);

    /**
     * Display an empty state when no query is entered or no results were found.
     */
    void showEmptyState();

    /**
     * Surface an error message to the user.
     */
    void showError(String message);
}
