package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.repository.SpeciesRepository;

/**
 * Default implementation of {@link SpeciesSearchPresenter} that proxies search queries to the repository.
 */
public class SpeciesSearchPresenterImpl implements SpeciesSearchPresenter {
    private static final long DEFAULT_DEBOUNCE_MS = 300L;

    private final SpeciesSearchView view;
    private final SpeciesRepository repository;
    private final Context context;
    private final Handler handler;
    private final long debounceMs;
    private final Runnable searchRunnable;

    private String pendingQuery = "";
    private boolean destroyed;

    public SpeciesSearchPresenterImpl(@NonNull SpeciesSearchView view,
                                      @NonNull SpeciesRepository repository,
                                      @NonNull Context context) {
        this(view, repository, context, new Handler(Looper.getMainLooper()), DEFAULT_DEBOUNCE_MS);
    }

    @VisibleForTesting
    SpeciesSearchPresenterImpl(@NonNull SpeciesSearchView view,
                               @NonNull SpeciesRepository repository,
                               @NonNull Context context,
                               @NonNull Handler handler,
                               long debounceMs) {
        this.view = Objects.requireNonNull(view, "view");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.context = context.getApplicationContext();
        this.handler = Objects.requireNonNull(handler, "handler");
        this.debounceMs = debounceMs;
        this.searchRunnable = this::executeSearch;
    }

    @Override
    public void onSearchQueryChanged(String query) {
        if (destroyed) {
            return;
        }
        String normalized = query == null ? "" : query.trim();
        handler.removeCallbacks(searchRunnable);
        pendingQuery = normalized;
        if (normalized.isEmpty()) {
            view.hideLoading();
            view.showEmptyState();
            return;
        }
        view.showLoading();
        handler.postDelayed(searchRunnable, debounceMs);
    }

    private void executeSearch() {
        if (destroyed) {
            return;
        }
        final String query = pendingQuery;
        if (TextUtils.isEmpty(query)) {
            view.hideLoading();
            view.showEmptyState();
            return;
        }
        repository.searchSpecies(query,
            results -> handleResults(query, results),
            error -> handleError(query, error));
    }

    private void handleResults(String query, List<SpeciesTarget> results) {
        if (destroyed) {
            return;
        }
        if (!TextUtils.equals(query, pendingQuery)) {
            return;
        }
        view.hideLoading();
        if (results == null || results.isEmpty()) {
            view.showEmptyState();
        } else {
            view.showResults(results);
        }
    }

    private void handleError(String query, Exception error) {
        if (destroyed) {
            return;
        }
        if (!TextUtils.equals(query, pendingQuery)) {
            return;
        }
        view.hideLoading();
        view.showError(context.getString(R.string.error_database));
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
    }
}
