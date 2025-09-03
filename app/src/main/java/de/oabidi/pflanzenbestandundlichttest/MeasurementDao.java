package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Data access object for {@link Measurement} entities.
 */
@Dao
public interface MeasurementDao {
    /**
     * Persists a measurement in the database.
     *
     * @param measurement the entity to insert
     * @return the generated row ID
     */
    @Insert
    long insert(Measurement measurement);

    /**
     * Retrieves the most recent measurements for the given plant.
     *
     * @param plantId identifier of the plant
     * @param limit   maximum number of results to return
     * @return list of measurements ordered by most recent first
     */
    @Query("SELECT * FROM Measurement WHERE plantId = :plantId ORDER BY timeEpoch DESC LIMIT :limit")
    List<Measurement> recentForPlant(long plantId, int limit);
}
