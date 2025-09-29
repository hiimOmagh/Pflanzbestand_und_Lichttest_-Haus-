package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTargetDao;

/**
 * Repository providing species target persistence APIs.
 */
public class SpeciesRepository extends BaseRepository {
    private final SpeciesTargetDao speciesTargetDao;

    public SpeciesRepository(Context context, Handler mainHandler, ExecutorService ioExecutor,
                             SpeciesTargetDao speciesTargetDao) {
        super(context, mainHandler, ioExecutor);
        this.speciesTargetDao = Objects.requireNonNull(speciesTargetDao, "speciesTargetDao");
    }

    public void getSpeciesTarget(String speciesKey, Consumer<SpeciesTarget> callback,
                                 @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> speciesTargetDao.findBySpeciesKey(speciesKey), callback, errorCallback);
    }

    public void getSpeciesTarget(String speciesKey, Consumer<SpeciesTarget> callback) {
        getSpeciesTarget(speciesKey, callback, null);
    }

    public void getPlantProfileByCommonName(String commonName, Consumer<PlantProfile> callback,
                                            @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> PlantProfile.fromTarget(speciesTargetDao.findByCommonName(commonName)), callback, errorCallback);
    }

    public void getPlantProfileByCommonName(String commonName, Consumer<PlantProfile> callback) {
        getPlantProfileByCommonName(commonName, callback, null);
    }

    public void getPlantProfileByScientificName(String scientificName, Consumer<PlantProfile> callback,
                                                @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> PlantProfile.fromTarget(speciesTargetDao.findByScientificName(scientificName)), callback, errorCallback);
    }

    public void getPlantProfileByScientificName(String scientificName, Consumer<PlantProfile> callback) {
        getPlantProfileByScientificName(scientificName, callback, null);
    }

    public void getAllSpeciesTargets(Consumer<List<SpeciesTarget>> callback, @Nullable Consumer<Exception> errorCallback) {
        queryAsync(speciesTargetDao::getAll, callback, errorCallback);
    }

    public void getAllSpeciesTargets(Consumer<List<SpeciesTarget>> callback) {
        getAllSpeciesTargets(callback, null);
    }

    public void searchSpecies(String query, Consumer<List<SpeciesTarget>> callback,
                              @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> speciesTargetDao.searchSpeciesTargets(query), callback, errorCallback);
    }

    public void searchSpecies(String query, Consumer<List<SpeciesTarget>> callback) {
        searchSpecies(query, callback, null);
    }

    public void getPlantProfilesByCategory(SpeciesTarget.Category category, Consumer<List<PlantProfile>> callback,
                                           @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> hydrateProfiles(speciesTargetDao.getByCategory(category)), callback, errorCallback);
    }

    public void getPlantProfilesByCategory(SpeciesTarget.Category category, Consumer<List<PlantProfile>> callback) {
        getPlantProfilesByCategory(category, callback, null);
    }

    public void getPlantProfilesByGrowthHabit(String growthHabit, Consumer<List<PlantProfile>> callback,
                                              @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> hydrateProfiles(speciesTargetDao.getByGrowthHabit(growthHabit)), callback, errorCallback);
    }

    public void getPlantProfilesByGrowthHabit(String growthHabit, Consumer<List<PlantProfile>> callback) {
        getPlantProfilesByGrowthHabit(growthHabit, callback, null);
    }

    public void getPlantProfilesByToxicity(boolean isToxic, Consumer<List<PlantProfile>> callback,
                                           @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> hydrateProfiles(speciesTargetDao.getByToxicity(isToxic)), callback, errorCallback);
    }

    public void getPlantProfilesByToxicity(boolean isToxic, Consumer<List<PlantProfile>> callback) {
        getPlantProfilesByToxicity(isToxic, callback, null);
    }

    public void getPlantProfilesWithUnknownToxicity(Consumer<List<PlantProfile>> callback,
                                                    @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> hydrateProfiles(speciesTargetDao.getWithUnknownToxicity()), callback, errorCallback);
    }

    public void getPlantProfilesWithUnknownToxicity(Consumer<List<PlantProfile>> callback) {
        getPlantProfilesWithUnknownToxicity(callback, null);
    }

    public void insertSpeciesTarget(SpeciesTarget target, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(target, "target");
        runAsync(() -> speciesTargetDao.insert(target), callback, errorCallback);
    }

    public void insertSpeciesTarget(SpeciesTarget target, Runnable callback) {
        insertSpeciesTarget(target, callback, null);
    }

    public void deleteSpeciesTarget(String speciesKey, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        runAsync(() -> speciesTargetDao.deleteBySpeciesKey(speciesKey), callback, errorCallback);
    }

    public void deleteSpeciesTarget(String speciesKey, Runnable callback) {
        deleteSpeciesTarget(speciesKey, callback, null);
    }

    public SpeciesTarget getSpeciesTargetSync(String speciesKey) {
        return speciesTargetDao.findBySpeciesKey(speciesKey);
    }

    private List<PlantProfile> hydrateProfiles(@Nullable List<SpeciesTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return Collections.emptyList();
        }
        List<PlantProfile> profiles = new ArrayList<>(targets.size());
        for (SpeciesTarget target : targets) {
            PlantProfile profile = PlantProfile.fromTarget(target);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }
}
