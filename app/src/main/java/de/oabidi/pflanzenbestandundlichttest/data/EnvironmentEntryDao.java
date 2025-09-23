package de.oabidi.pflanzenbestandundlichttest.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data access object for {@link EnvironmentEntry} records.
 */
@Dao
public interface EnvironmentEntryDao {
    /** Returns all environment entries of the plant ordered by newest timestamp first. */
    @Query("SELECT * FROM EnvironmentEntry WHERE plantId = :plantId ORDER BY timestamp DESC, id DESC")
    List<EnvironmentEntry> getForPlantOrdered(long plantId);

    /** Inserts the provided entry and returns its generated identifier. */
    @Insert
    long insert(EnvironmentEntry entry);

    /** Updates an existing environment entry. */
    @Update
    void update(EnvironmentEntry entry);

    /** Deletes the provided entry. */
    @Delete
    void delete(EnvironmentEntry entry);

    /** Deletes an environment entry by its unique identifier. */
    @Query("DELETE FROM EnvironmentEntry WHERE id = :id")
    void deleteById(long id);
}
