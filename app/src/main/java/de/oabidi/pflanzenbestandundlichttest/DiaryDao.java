package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data access object for {@link DiaryEntry} entities.
 */
@Dao
public interface DiaryDao {
    /**
     * Inserts a diary entry into the database.
     *
     * @param entry the entity to insert
     * @return the generated row ID
     */
    @Insert
    long insert(DiaryEntry entry);

    /**
     * Removes a diary entry from the database.
     *
     * @param entry the entity to delete
     */
    @Delete
    void delete(DiaryEntry entry);

    /**
     * Updates an existing diary entry in the database.
     *
     * @param entry the entity to update
     */
    @Update
    void update(DiaryEntry entry);

    /**
     * Retrieves all diary entries for the given plant ordered by most recent first.
     *
     * @param plantId identifier of the plant
     * @return list of diary entries
     */
    @Query("SELECT id, plantId, timeEpoch, type, note, photoUri FROM DiaryEntry WHERE plantId = :plantId ORDER BY timeEpoch DESC")
    List<DiaryEntry> entriesForPlant(long plantId);

    /**
     * Retrieves all diary entries stored in the database.
     *
     * @return list of all diary entries
     */
    @Query("SELECT id, plantId, timeEpoch, type, note, photoUri FROM DiaryEntry")
    List<DiaryEntry> getAll();
}
