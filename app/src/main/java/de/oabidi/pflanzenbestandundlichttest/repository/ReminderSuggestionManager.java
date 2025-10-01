package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.PlantDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.feature.reminders.ReminderSuggestionFormatter;
import de.oabidi.pflanzenbestandundlichttest.feature.reminders.SmartReminderEngine;

/**
 * Computes, persists and exposes smart watering reminder suggestions.
 */
public class ReminderSuggestionManager extends BaseRepository {
    private static final String TAG = "ReminderSuggestionMgr";

    private final PlantDao plantDao;
    private final ReminderRepository reminderRepository;
    private final EnvironmentRepository environmentRepository;
    private final SpeciesRepository speciesRepository;
    private final SmartReminderEngine smartReminderEngine;
    private final ReminderSuggestionFormatter formatter;

    public ReminderSuggestionManager(Context context,
                                     Handler mainHandler,
                                     ExecutorService ioExecutor,
                                     PlantDao plantDao,
                                     ReminderRepository reminderRepository,
                                     EnvironmentRepository environmentRepository,
                                     SpeciesRepository speciesRepository,
                                     SmartReminderEngine smartReminderEngine,
                                     ReminderSuggestionFormatter formatter) {
        super(context, mainHandler, ioExecutor);
        this.plantDao = Objects.requireNonNull(plantDao, "plantDao");
        this.reminderRepository = Objects.requireNonNull(reminderRepository, "reminderRepository");
        this.environmentRepository = Objects.requireNonNull(environmentRepository, "environmentRepository");
        this.speciesRepository = Objects.requireNonNull(speciesRepository, "speciesRepository");
        this.smartReminderEngine = Objects.requireNonNull(smartReminderEngine, "smartReminderEngine");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    public void getReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback) {
        getReminderSuggestion(plantId, callback, null);
    }

    public void getReminderSuggestion(long plantId,
                                      Consumer<ReminderSuggestion> callback,
                                      @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> reminderRepository.getSuggestionForPlantSync(plantId), callback, errorCallback);
    }

    public void computeReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback) {
        computeReminderSuggestion(plantId, callback, null);
    }

    public void computeReminderSuggestion(long plantId,
                                          Consumer<ReminderSuggestion> callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> refreshReminderSuggestionSync(plantId, System.currentTimeMillis()), callback, errorCallback);
    }

    public ReminderSuggestion refreshReminderSuggestionSync(long plantId) {
        return refreshReminderSuggestionSync(plantId, System.currentTimeMillis());
    }

    public void refreshAllReminderSuggestionsSync() {
        List<Plant> plants = plantDao.getAll();
        if (plants == null || plants.isEmpty()) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        for (Plant plant : plants) {
            try {
                refreshReminderSuggestionForPlant(plant, timestamp);
            } catch (Exception e) {
                Log.w(TAG, "Failed to refresh reminder suggestion for plant " + plant.getId(), e);
            }
        }
    }

    ReminderSuggestion refreshReminderSuggestionSync(long plantId, long timestamp) {
        Plant plant = plantDao.findById(plantId);
        if (plant == null) {
            reminderRepository.deleteSuggestionSync(plantId);
            return null;
        }
        ReminderSuggestion suggestion = buildReminderSuggestion(plant, timestamp);
        reminderRepository.saveSuggestionSync(suggestion);
        return suggestion;
    }

    private void refreshReminderSuggestionForPlant(Plant plant, long timestamp) {
        if (plant == null) {
            return;
        }
        ReminderSuggestion suggestion = buildReminderSuggestion(plant, timestamp);
        reminderRepository.saveSuggestionSync(suggestion);
    }

    private ReminderSuggestion buildReminderSuggestion(Plant plant, long timestamp) {
        long plantId = plant.getId();
        PlantProfile profile = resolveProfileForPlant(plant);
        List<EnvironmentEntry> entries = environmentRepository.getRecentEntriesForPlantSync(plantId,
            SmartReminderEngine.HISTORY_LIMIT);
        if (entries == null) {
            entries = Collections.emptyList();
        }
        SmartReminderEngine.Suggestion suggestion = smartReminderEngine.suggest(profile, entries);
        ReminderSuggestion entity = new ReminderSuggestion();
        entity.setPlantId(plantId);
        entity.setSuggestedIntervalDays(suggestion.getSuggestedDays());
        entity.setLastEvaluatedAt(timestamp);
        entity.setConfidenceScore(computeConfidenceScore(suggestion));
        entity.setExplanation(formatter.format(plant, profile, suggestion));
        entity.setAlgorithmVersion(SmartReminderEngine.ALGORITHM_VERSION);
        return entity;
    }

    private float computeConfidenceScore(SmartReminderEngine.Suggestion suggestion) {
        float confidence = 0.3f;
        if (suggestion.getBaselineDays() > 0) {
            confidence += 0.3f;
        }
        if (suggestion.getEnvironmentSignal() != SmartReminderEngine.EnvironmentSignal.NO_DATA) {
            confidence += 0.2f;
        }
        if (suggestion.getAverageSoilMoisture() != null) {
            confidence += 0.2f;
        }
        if (suggestion.getLatestSoilMoisture() != null) {
            confidence += 0.1f;
        }
        return Math.max(0f, Math.min(1f, confidence));
    }

    private PlantProfile resolveProfileForPlant(Plant plant) {
        if (plant == null) {
            return null;
        }
        String speciesKey = plant.getSpecies();
        if (speciesKey != null && !speciesKey.isEmpty()) {
            SpeciesTarget target = speciesRepository.getSpeciesTargetSync(speciesKey);
            return PlantProfile.fromTarget(target);
        }
        return null;
    }
}
