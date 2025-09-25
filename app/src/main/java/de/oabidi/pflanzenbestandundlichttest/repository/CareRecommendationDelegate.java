package de.oabidi.pflanzenbestandundlichttest.repository;

/**
 * Delegate providing hooks to refresh care recommendations after data changes.
 */
public interface CareRecommendationDelegate {
    Runnable refreshCareRecommendationsAsync(long plantId);
}
