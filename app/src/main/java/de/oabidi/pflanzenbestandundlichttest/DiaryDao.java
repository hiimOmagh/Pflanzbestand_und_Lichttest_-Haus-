package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

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
     * Retrieves all diary entries for the given plant ordered by most recent first.
     *
     * @param plantId identifier of the plant
     * @return list of diary entries
     */
    @Query("SELECT * FROM DiaryEntry WHERE plantId = :plantId ORDER BY timeEpoch DESC")
    List<DiaryEntry> entriesForPlant(long plantId);
}
