package de.oabidi.pflanzenbestandundlichttest.core.data.plant;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data access object for {@link Measurement} entities.
 */
@Dao
public interface MeasurementDao {
    /**
     * Persists a measurement in the database.
     *
     * <p>The {@link Measurement} contains the recorded average lux and optional
     * PPFD value which will be stored for later retrieval.</p>
     *
     * @param measurement the entity to insert
     */
    @Insert
    void insert(Measurement measurement);

    /**
     * Removes a measurement from the database.
     *
     * @param measurement the entity to remove
     */
    @Delete
    void delete(Measurement measurement);

    /**
     * Updates an existing measurement.
     *
     * @param measurement the entity to update
     */
    @Update
    void update(Measurement measurement);

    /**
     * Retrieves the most recent measurements for the given plant.
     *
     * @param plantId identifier of the plant
     * @param limit   maximum number of results to return
     * @return list of measurements ordered by most recent first
     */
    @Query("SELECT id, plantId, timeEpoch, luxAvg, ppfd, dli, note FROM Measurement WHERE plantId = :plantId ORDER BY timeEpoch DESC LIMIT :limit")
    List<Measurement> recentForPlant(long plantId, int limit);

    /**
     * Retrieves all measurements for the given plant since the specified time.
     *
     * @param plantId identifier of the plant
     * @param since   minimum timestamp (inclusive) of measurements to return
     * @return list of measurements ordered by most recent first
     */
    @Query("SELECT id, plantId, timeEpoch, luxAvg, ppfd, dli, note FROM Measurement WHERE plantId = :plantId AND timeEpoch >= :since ORDER BY timeEpoch DESC")
    List<Measurement> getForPlantSince(long plantId, long since);

    /**
     * Retrieves all measurements for the given plant within the specified time range.
     *
     * @param plantId identifier of the plant
     * @param start   start of the time range (inclusive)
     * @param end     end of the time range (exclusive)
     * @return list of measurements ordered by most recent first
     */
    @Query("SELECT id, plantId, timeEpoch, luxAvg, ppfd, dli, note FROM Measurement WHERE plantId = :plantId AND timeEpoch >= :start AND timeEpoch < :end ORDER BY timeEpoch DESC")
    List<Measurement> getForPlantInRange(long plantId, long start, long end);

    /**
     * Sums PPFD measurements for the given plant within the specified time range.
     *
     * @param plantId identifier of the plant
     * @param start   start of the time range (inclusive)
     * @param end     end of the time range (exclusive)
     * @return summed PPFD value for the range or {@code null} if no measurements exist
     */
    @Query("SELECT SUM(ppfd) FROM Measurement WHERE plantId = :plantId AND timeEpoch >= :start AND timeEpoch < :end")
    Float sumPpfdForRange(long plantId, long start, long end);

    /**
     * Counts distinct days within the specified time range that contain
     * DLI measurements greater than zero for the given plant.
     *
     * @param id    identifier of the plant
     * @param start start of the time range (inclusive)
     * @param end   end of the time range (inclusive)
     * @return number of days with DLI data
     */
    @Query("SELECT COUNT(DISTINCT date(timeEpoch/86400000)) FROM Measurement WHERE plantId=:id AND timeEpoch BETWEEN :start AND :end AND dli>0")
    int countDaysWithData(long id, long start, long end);

    /**
     * Sums PPFD measurements and counts distinct days within the specified time range.
     *
     * @param id    identifier of the plant
     * @param start start of the time range (inclusive)
     * @param end   end of the time range (inclusive)
     * @return aggregated result containing the summed PPFD and day count
     */
    @Query("SELECT SUM(ppfd) AS sum, COUNT(DISTINCT date(timeEpoch/86400000)) AS days FROM Measurement WHERE plantId=:id AND timeEpoch BETWEEN :start AND :end")
    SumAndDays sumPpfdAndCountDays(long id, long start, long end);

    /**
     * Retrieves all stored measurements.
     *
     * @return list of all measurements in the database
     */
    @Query("SELECT id, plantId, timeEpoch, luxAvg, ppfd, dli, note FROM Measurement")
    List<Measurement> getAll();

    /**
     * Retrieves all measurements for the given plant.
     *
     * @param plantId identifier of the plant
     * @return list of measurements associated with the plant
     */
    @Query("SELECT id, plantId, timeEpoch, luxAvg, ppfd, dli, note FROM Measurement WHERE plantId = :plantId")
    List<Measurement> getAllForPlant(long plantId);

    /**
     * Simple container for PPFD sum and day count results.
     */
    class SumAndDays {
        public Float sum;
        public int days;
    }
}
