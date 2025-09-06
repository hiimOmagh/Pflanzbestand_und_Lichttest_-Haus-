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
     * Retrieves all measurements for the given plant since the specified time.
     *
     * @param plantId identifier of the plant
     * @param since   minimum timestamp (inclusive) of measurements to return
     * @return list of measurements ordered by most recent first
     */
    @Query("SELECT * FROM Measurement WHERE plantId = :plantId AND timeEpoch >= :since ORDER BY timeEpoch DESC")
    List<Measurement> getForPlantSince(long plantId, long since);

    /**
     * Retrieves all measurements for the given plant within the specified time range.
     *
     * @param plantId identifier of the plant
     * @param start   start of the time range (inclusive)
     * @param end     end of the time range (exclusive)
     * @return list of measurements ordered by most recent first
     */
    @Query("SELECT * FROM Measurement WHERE plantId = :plantId AND timeEpoch >= :start AND timeEpoch < :end ORDER BY timeEpoch DESC")
    List<Measurement> getForPlantInRange(long plantId, long start, long end);

    /**
     * Sums PPFD measurements for the given plant on a specific day.
     *
     * <p>The day is defined by its start time in epoch milliseconds and
     * spans 24 hours. The resulting value represents the accumulated
     * PPFD across all measurements of that day and can be converted to
     * DLI by multiplying with {@code 0.0036}.</p>
     *
     * @param plantId  identifier of the plant
     * @param dayStart start of the day in epoch milliseconds
     * @return summed PPFD value for the given day or {@code null} if no
     *         measurements exist
     */
    @Query("SELECT SUM(ppfd) FROM Measurement WHERE plantId = :plantId AND timeEpoch >= :dayStart AND timeEpoch < :dayStart + 86400000")
    Float dliForDay(long plantId, long dayStart);

    /**
     * Retrieves all stored measurements.
     *
     * @return list of all measurements in the database
     */
    @Query("SELECT * FROM Measurement")
    List<Measurement> getAll();
}
