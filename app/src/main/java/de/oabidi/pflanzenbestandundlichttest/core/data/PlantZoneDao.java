package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data access object for {@link PlantZone} entries.
 */
@Dao
public interface PlantZoneDao {
    /**
     * Returns the zone associated with the given plant or {@code null}.
     */
    @Query("SELECT * FROM PlantZone WHERE plantId = :plantId LIMIT 1")
    PlantZone getForPlant(long plantId);

    /**
     * Inserts a new zone record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PlantZone zone);

    /**
     * Updates an existing zone record.
     */
    @Update
    void update(PlantZone zone);

    /**
     * Removes the provided zone record.
     */
    @Delete
    void delete(PlantZone zone);

    /**
     * Removes the zone associated with the given plant.
     */
    @Query("DELETE FROM PlantZone WHERE plantId = :plantId")
    void deleteForPlant(long plantId);

    /**
     * Returns all zones stored in the database.
     */
    @Query("SELECT * FROM PlantZone")
    List<PlantZone> getAll();
}
