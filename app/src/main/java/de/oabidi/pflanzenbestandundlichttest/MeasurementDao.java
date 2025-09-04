package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Delete;
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
     * <p>The {@link Measurement} contains both PPFD and DLI values which will
     * be stored for later retrieval.</p>
     *
     * @param measurement the entity to insert
     * @return the generated row ID
     */
    @Insert
    long insert(Measurement measurement);

    /**
     * Removes a measurement from the database.
     *
     * @param measurement the entity to remove
     */
    @Delete
    void delete(Measurement measurement);

    /**
     * Retrieves the most recent measurements for the given plant.
     *
     * @param plantId identifier of the plant
     * @param limit   maximum number of results to return
     * @return list of measurements ordered by most recent first
     */
    @Query("SELECT * FROM Measurement WHERE plantId = :plantId ORDER BY timeEpoch DESC LIMIT :limit")
    List<Measurement> recentForPlant(long plantId, int limit);

    /**
     * Retrieves all stored measurements.
     *
     * @return list of all measurements in the database
     */
    @Query("SELECT * FROM Measurement")
    List<Measurement> getAll();
}
