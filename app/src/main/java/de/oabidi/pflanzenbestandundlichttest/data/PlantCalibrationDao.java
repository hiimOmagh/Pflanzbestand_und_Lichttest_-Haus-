package de.oabidi.pflanzenbestandundlichttest.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * DAO providing CRUD operations for {@link PlantCalibration} entities.
 */
@Dao
public interface PlantCalibrationDao {
    /**
     * Retrieves the calibration associated with the given plant or {@code null} if none exists.
     */
    @Query("SELECT * FROM PlantCalibration WHERE plantId = :plantId LIMIT 1")
    PlantCalibration getForPlant(long plantId);

    /**
     * Retrieves all stored calibrations.
     */
    @Query("SELECT * FROM PlantCalibration")
    List<PlantCalibration> getAll();

    /**
     * Persists the provided calibration, replacing any existing value for the plant.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(PlantCalibration calibration);

    /**
     * Deletes the calibration associated with the given plant.
     */
    @Query("DELETE FROM PlantCalibration WHERE plantId = :plantId")
    void deleteForPlant(long plantId);
}
