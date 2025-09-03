package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

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
     * Insert a species target into the database.
     *
     * @param target entity to persist
     * @return row ID of the inserted entity
     */
    @Insert
    long insert(SpeciesTarget target);
}
