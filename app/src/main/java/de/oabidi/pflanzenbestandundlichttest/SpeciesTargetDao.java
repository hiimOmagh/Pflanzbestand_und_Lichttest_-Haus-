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
     * Retrieve all stored species targets.
     *
     * @return list of all targets sorted by species key
     */
    @Query("SELECT * FROM SpeciesTarget ORDER BY speciesKey")
    List<SpeciesTarget> getAll();

    /**
     * Insert or replace a species target in the database.
     * *
     * @param target entity to persist
     * @return row ID of the inserted entity
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SpeciesTarget target);

    /**
     * Remove the species target for the given key.
     *
     * @param speciesKey identifier of the species to remove
     */
    @Query("DELETE FROM SpeciesTarget WHERE speciesKey = :speciesKey")
    void deleteBySpeciesKey(String speciesKey);
}
