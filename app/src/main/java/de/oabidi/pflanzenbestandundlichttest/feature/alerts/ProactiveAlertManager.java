package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.ProactiveAlertLog;
import de.oabidi.pflanzenbestandundlichttest.repository.DiaryRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.EnvironmentRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.ProactiveAlertRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.SpeciesRepository;

/**
 * Coordinates fetching domain data, running evaluations and storing alert history.
 */
public class ProactiveAlertManager {
    private static final long SUPPRESSION_WINDOW_MS = TimeUnit.HOURS.toMillis(12);
    private static final long RETENTION_WINDOW_MS = TimeUnit.DAYS.toMillis(30);

    private final Context context;
    private final PlantRepository plantRepository;
    private final EnvironmentRepository environmentRepository;
    private final DiaryRepository diaryRepository;
    private final SpeciesRepository speciesRepository;
    private final ProactiveAlertRepository alertRepository;
    private final ProactiveAlertEvaluator evaluator = new ProactiveAlertEvaluator();

    public ProactiveAlertManager(@NonNull Context context,
                                 @NonNull PlantRepository plantRepository,
                                 @NonNull EnvironmentRepository environmentRepository,
                                 @NonNull DiaryRepository diaryRepository,
                                 @NonNull SpeciesRepository speciesRepository,
                                 @NonNull ProactiveAlertRepository alertRepository) {
        this.context = context.getApplicationContext();
        this.plantRepository = plantRepository;
        this.environmentRepository = environmentRepository;
        this.diaryRepository = diaryRepository;
        this.speciesRepository = speciesRepository;
        this.alertRepository = alertRepository;
    }

    public List<ProactiveAlert> evaluateNewAlerts() {
        List<Plant> plants = plantRepository.getAllPlantsSync();
        if (plants == null || plants.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProactiveAlert> freshAlerts = new ArrayList<>();
        for (Plant plant : plants) {
            List<EnvironmentEntry> entries = environmentRepository.getRecentEntriesForPlantSync(plant.getId(), 5);
            PlantProfile profile = resolveProfile(plant);
            freshAlerts.addAll(handlePlantAlerts(plant, profile, entries));
        }
        pruneHistory();
        return freshAlerts;
    }

    private List<ProactiveAlert> handlePlantAlerts(Plant plant,
                                                   @Nullable PlantProfile profile,
                                                   @Nullable List<EnvironmentEntry> entries) {
        List<ProactiveAlert> resolved = new ArrayList<>();
        DiaryEntry latestDiary =
            diaryRepository.getLatestDiaryEntrySync(plant.getId());
        List<ProactiveAlert> evaluations = evaluator.evaluate(plant, profile, entries, latestDiary,
            context.getResources());
        for (ProactiveAlert candidate : evaluations) {
            if (shouldRecord(candidate)) {
                alertRepository.insertSync(candidate.toLog());
                resolved.add(candidate);
            }
        }
        return resolved;
    }

    private boolean shouldRecord(ProactiveAlert alert) {
        ProactiveAlertLog previous = alertRepository.latestForTrigger(alert.getPlant().getId(),
            alert.getTrigger().getId());
        if (previous == null) {
            return true;
        }
        long delta = alert.getCreatedAt() - previous.getCreatedAt();
        if (delta >= SUPPRESSION_WINDOW_MS) {
            return true;
        }
        return !TextUtils.equals(previous.getMessage(), alert.getMessage());
    }

    private void pruneHistory() {
        long threshold = System.currentTimeMillis() - RETENTION_WINDOW_MS;
        if (threshold > 0L) {
            alertRepository.deleteOlderThan(threshold);
        }
    }

    @Nullable
    private PlantProfile resolveProfile(Plant plant) {
        String speciesKey = plant.getSpecies();
        if (speciesKey == null || speciesKey.isEmpty()) {
            return null;
        }
        SpeciesRepository repo = speciesRepository;
        SpeciesTarget target = repo.getSpeciesTargetSync(speciesKey);
        return PlantProfile.fromTarget(target);
    }
}
