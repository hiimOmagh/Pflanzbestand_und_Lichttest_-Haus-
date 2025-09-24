package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data access object for {@link SpeciesTarget} records.
 */
@Dao
public interface SpeciesTargetDao {
    /**
     * Look up the PPFD target range for the specified species.
     *
     * @param speciesKey identifier of the species
     * @return the matching target or {@code null} if none is defined
     */
    @Query("SELECT * FROM SpeciesTarget WHERE speciesKey = :speciesKey LIMIT 1")
    SpeciesTarget findBySpeciesKey(String speciesKey);

    /**
     * Look up a plant profile by its common name.
     *
     * @param commonName common name to match
     * @return the matching profile or {@code null} when none exists
     */
    @Query("SELECT * FROM SpeciesTarget WHERE commonName = :commonName COLLATE NOCASE LIMIT 1")
    SpeciesTarget findByCommonName(String commonName);

    /**
     * Look up a plant profile by its scientific name.
     *
     * @param scientificName scientific name to match
     * @return the matching profile or {@code null} when none exists
     */
    @Query("SELECT * FROM SpeciesTarget WHERE scientificName = :scientificName COLLATE NOCASE LIMIT 1")
    SpeciesTarget findByScientificName(String scientificName);

    /**
     * Retrieve all stored species targets.
     *
     * @return list of all targets sorted by species key
     */
    @Query("SELECT * FROM SpeciesTarget ORDER BY speciesKey")
    List<SpeciesTarget> getAll();

    /**
     * Retrieve all profiles that belong to the provided category.
     *
     * @param category category to filter by
     * @return list of profiles ordered by their common name and key
     */
    @Query("SELECT * FROM SpeciesTarget WHERE category = :category "
        + "ORDER BY commonName COLLATE NOCASE, scientificName COLLATE NOCASE, speciesKey")
    List<SpeciesTarget> getByCategory(SpeciesTarget.Category category);

    /**
     * Retrieve all profiles matching the supplied growth habit.
     *
     * @param growthHabit growth habit descriptor to match
     * @return list of matching profiles ordered alphabetically
     */
    @Query("SELECT * FROM SpeciesTarget WHERE growthHabit = :growthHabit COLLATE NOCASE "
        + "ORDER BY commonName COLLATE NOCASE, scientificName COLLATE NOCASE, speciesKey")
    List<SpeciesTarget> getByGrowthHabit(String growthHabit);

    /**
     * Retrieve all profiles grouped by toxicity information.
     *
     * @param isToxic {@code true} to fetch toxic plants, {@code false} for non-toxic
     * @return list of matching profiles ordered alphabetically
     */
    @Query("SELECT * FROM SpeciesTarget WHERE toxicToPets = :isToxic "
        + "ORDER BY commonName COLLATE NOCASE, scientificName COLLATE NOCASE, speciesKey")
    List<SpeciesTarget> getByToxicity(boolean isToxic);

    /**
     * Retrieve all profiles without recorded toxicity information.
     *
     * @return list of profiles lacking toxicity metadata
     */
    @Query("SELECT * FROM SpeciesTarget WHERE toxicToPets IS NULL "
        + "ORDER BY commonName COLLATE NOCASE, scientificName COLLATE NOCASE, speciesKey")
    List<SpeciesTarget> getWithUnknownToxicity();

    /**
     * Insert or replace a species target in the database.
     * *
     *
     * @param target entity to persist
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SpeciesTarget target);

    /**
     * Remove the species target for the given key.
     *
     * @param speciesKey identifier of the species to remove
     */
    @Query("DELETE FROM SpeciesTarget WHERE speciesKey = :speciesKey")
    void deleteBySpeciesKey(String speciesKey);
}
