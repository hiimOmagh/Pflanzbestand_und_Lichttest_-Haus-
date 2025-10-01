package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine;
import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.PlantDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;

/**
 * Manages automatic care recommendation evaluation, caching and dismissal tracking.
 */
public class CareRecommendationService extends BaseRepository implements CareRecommendationDelegate {
    private static final String TAG = "CareRecommendationSvc";
    private static final int CARE_RECOMMENDATION_ENTRY_LIMIT = 30;

    private final PlantDao plantDao;
    private final SpeciesRepository speciesRepository;
    private final EnvironmentRepository environmentRepository;
    private final SharedPreferences sharedPreferences;
    private final CareRecommendationEngine careRecommendationEngine = new CareRecommendationEngine();
    private final ReminderSuggestionManager reminderSuggestionManager;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<CareRecommendationListener>> listeners =
        new ConcurrentHashMap<>();

    public CareRecommendationService(Context context,
                                     Handler mainHandler,
                                     ExecutorService ioExecutor,
                                     PlantDao plantDao,
                                     SpeciesRepository speciesRepository,
                                     EnvironmentRepository environmentRepository,
                                     SharedPreferences sharedPreferences,
                                     ReminderSuggestionManager reminderSuggestionManager) {
        super(context, mainHandler, ioExecutor);
        this.plantDao = Objects.requireNonNull(plantDao, "plantDao");
        this.speciesRepository = Objects.requireNonNull(speciesRepository, "speciesRepository");
        this.environmentRepository = Objects.requireNonNull(environmentRepository, "environmentRepository");
        this.sharedPreferences = Objects.requireNonNull(sharedPreferences, "sharedPreferences");
        this.reminderSuggestionManager = Objects.requireNonNull(reminderSuggestionManager, "reminderSuggestionManager");
    }

    public void getCareRecommendations(long plantId,
                                       Consumer<List<CareRecommendation>> callback,
                                       @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> computeCareRecommendations(plantId), recommendations -> {
            if (callback == null) {
                return;
            }
            List<CareRecommendation> payload = recommendations == null || recommendations.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(recommendations));
            callback.accept(payload);
        }, errorCallback);
    }

    public void registerCareRecommendationListener(long plantId, CareRecommendationListener listener) {
        if (listener == null) {
            return;
        }
        listeners
            .computeIfAbsent(plantId, id -> new CopyOnWriteArrayList<>())
            .addIfAbsent(listener);
    }

    public void unregisterCareRecommendationListener(long plantId, CareRecommendationListener listener) {
        if (listener == null) {
            return;
        }
        CopyOnWriteArrayList<CareRecommendationListener> plantListeners = listeners.get(plantId);
        if (plantListeners != null) {
            plantListeners.remove(listener);
            if (plantListeners.isEmpty()) {
                listeners.remove(plantId, plantListeners);
            }
        }
    }

    public void dismissCareRecommendation(long plantId, String recommendationId) {
        dismissCareRecommendation(plantId, recommendationId, null, null);
    }

    public void dismissCareRecommendation(long plantId,
                                          String recommendationId,
                                          @Nullable Runnable callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        if (TextUtils.isEmpty(recommendationId)) {
            if (errorCallback != null) {
                mainHandler.post(() -> errorCallback.accept(new IllegalArgumentException("recommendationId")));
            }
            return;
        }
        runAsync(() -> addDismissedId(plantId, recommendationId),
            careRecommendationRefreshSupplier(plantId), callback, errorCallback);
    }

    public void restoreCareRecommendation(long plantId, String recommendationId) {
        restoreCareRecommendation(plantId, recommendationId, null, null);
    }

    public void restoreCareRecommendation(long plantId,
                                          String recommendationId,
                                          @Nullable Runnable callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        if (TextUtils.isEmpty(recommendationId)) {
            if (errorCallback != null) {
                mainHandler.post(() -> errorCallback.accept(new IllegalArgumentException("recommendationId")));
            }
            return;
        }
        runAsync(() -> removeDismissedId(plantId, recommendationId),
            careRecommendationRefreshSupplier(plantId), callback, errorCallback);
    }

    @Override
    public Runnable refreshCareRecommendationsAsync(long plantId) {
        return () -> {
            try {
                List<CareRecommendation> recommendations = computeCareRecommendations(plantId);
                notifyCareRecommendationListeners(plantId, recommendations);
            } catch (Exception e) {
                notifyCareRecommendationError(plantId, e);
            }
            try {
                reminderSuggestionManager.refreshReminderSuggestionSync(plantId, System.currentTimeMillis());
            } catch (Exception e) {
                Log.w(TAG, "Failed to refresh reminder suggestion", e);
            }
        };
    }

    private Supplier<Runnable> careRecommendationRefreshSupplier(long plantId) {
        return () -> refreshCareRecommendationsAsync(plantId);
    }

    private List<CareRecommendation> computeCareRecommendations(long plantId) {
        Plant plant = plantDao.findById(plantId);
        if (plant == null) {
            clearDismissedCareRecommendations(plantId);
            return Collections.emptyList();
        }
        PlantProfile profile = null;
        String speciesKey = plant.getSpecies();
        if (!TextUtils.isEmpty(speciesKey)) {
            SpeciesTarget target = speciesRepository.getSpeciesTargetSync(speciesKey);
            profile = PlantProfile.fromTarget(target);
        }
        List<EnvironmentEntry> entries = environmentRepository.getRecentEntriesForPlantSync(plantId,
            CARE_RECOMMENDATION_ENTRY_LIMIT);
        if (entries == null) {
            entries = Collections.emptyList();
        }
        List<CareRecommendation> evaluated = careRecommendationEngine.evaluate(profile, entries, context.getResources());
        return applyDismissals(plantId, evaluated);
    }

    private List<CareRecommendation> applyDismissals(long plantId, List<CareRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            clearDismissedCareRecommendations(plantId);
            return Collections.emptyList();
        }
        Set<String> dismissed = loadDismissedIds(plantId);
        List<CareRecommendation> filtered = new ArrayList<>();
        for (CareRecommendation recommendation : recommendations) {
            if (!dismissed.contains(recommendation.getId())) {
                filtered.add(recommendation);
            }
        }
        pruneDismissedCareRecommendations(plantId, dismissed, recommendations);
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(filtered);
    }

    private void notifyCareRecommendationListeners(long plantId, List<CareRecommendation> recommendations) {
        CopyOnWriteArrayList<CareRecommendationListener> plantListeners = listeners.get(plantId);
        if (plantListeners == null || plantListeners.isEmpty()) {
            return;
        }
        List<CareRecommendation> payload = recommendations == null || recommendations.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(recommendations));
        mainHandler.post(() -> {
            for (CareRecommendationListener listener : plantListeners) {
                listener.onCareRecommendationsUpdated(plantId, payload);
            }
        });
    }

    private void notifyCareRecommendationError(long plantId, Exception exception) {
        CopyOnWriteArrayList<CareRecommendationListener> plantListeners = listeners.get(plantId);
        if (plantListeners == null || plantListeners.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            for (CareRecommendationListener listener : plantListeners) {
                listener.onCareRecommendationsError(plantId, exception);
            }
        });
    }

    private Set<String> loadDismissedIds(long plantId) {
        Set<String> stored = sharedPreferences.getStringSet(getDismissedPrefKey(plantId), Collections.emptySet());
        return new HashSet<>(stored);
    }

    private void saveDismissedCareRecommendations(long plantId, Set<String> ids) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String key = getDismissedPrefKey(plantId);
        if (ids == null || ids.isEmpty()) {
            editor.remove(key);
        } else {
            editor.putStringSet(key, new HashSet<>(ids));
        }
        editor.apply();
    }

    private void clearDismissedCareRecommendations(long plantId) {
        sharedPreferences.edit().remove(getDismissedPrefKey(plantId)).apply();
    }

    private void pruneDismissedCareRecommendations(long plantId,
                                                   Set<String> dismissed,
                                                   List<CareRecommendation> active) {
        if (dismissed.isEmpty()) {
            return;
        }
        Set<String> activeIds = new HashSet<>();
        for (CareRecommendation recommendation : active) {
            activeIds.add(recommendation.getId());
        }
        if (dismissed.retainAll(activeIds)) {
            saveDismissedCareRecommendations(plantId, dismissed);
        }
    }

    private void addDismissedId(long plantId, String recommendationId) {
        Set<String> dismissed = loadDismissedIds(plantId);
        if (dismissed.add(recommendationId)) {
            saveDismissedCareRecommendations(plantId, dismissed);
        }
    }

    private void removeDismissedId(long plantId, String recommendationId) {
        Set<String> dismissed = loadDismissedIds(plantId);
        if (dismissed.remove(recommendationId)) {
            saveDismissedCareRecommendations(plantId, dismissed);
        }
    }

    private String getDismissedPrefKey(long plantId) {
        return SettingsKeys.KEY_DISMISSED_CARE_RECOMMENDATIONS + "_" + plantId;
    }

    /**
     * Listener receiving automatic care recommendation updates.
     */
    public interface CareRecommendationListener {
        void onCareRecommendationsUpdated(long plantId, List<CareRecommendation> recommendations);

        void onCareRecommendationsError(long plantId, Exception exception);
    }
}
