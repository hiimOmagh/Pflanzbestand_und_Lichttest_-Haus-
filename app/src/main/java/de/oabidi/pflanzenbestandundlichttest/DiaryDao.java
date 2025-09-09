package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
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
    long insertInternal(DiaryEntry entry);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFts(DiaryEntryFts entryFts);

    @Transaction
    default long insert(DiaryEntry entry) {
        long id = insertInternal(entry);
        insertFts(new DiaryEntryFts(id, entry.getNote() == null ? "" : entry.getNote()));
        return id;
    }

    /**
     * Removes a diary entry from the database.
     *
     * @param entry the entity to delete
     */
    @Delete
    void deleteInternal(DiaryEntry entry);

    @Query("DELETE FROM DiaryEntryFts WHERE rowid = :rowid")
    void deleteFts(long rowid);

    @Transaction
    default void delete(DiaryEntry entry) {
        deleteInternal(entry);
        deleteFts(entry.getId());
    }

    /**
     * Updates an existing diary entry in the database.
     *
     * @param entry the entity to update
     */
    @Update
    void updateInternal(DiaryEntry entry);

    @Transaction
    default void update(DiaryEntry entry) {
        updateInternal(entry);
        insertFts(new DiaryEntryFts(entry.getId(), entry.getNote() == null ? "" : entry.getNote()));
    }

    /**
     * Retrieves all diary entries for the given plant ordered by most recent first.
     *
     * @param plantId identifier of the plant
     * @return list of diary entries
     */
    @Query("SELECT id, plantId, timeEpoch, type, note, photoUri FROM DiaryEntry WHERE plantId = :plantId ORDER BY timeEpoch DESC")
    List<DiaryEntry> entriesForPlant(long plantId);

    @Query("SELECT id, plantId, timeEpoch, type, note, photoUri FROM DiaryEntry WHERE plantId = :plantId AND (:type IS NULL OR type = :type) ORDER BY timeEpoch DESC")
    List<DiaryEntry> entriesForPlantFiltered(long plantId, String type);

    @Query("SELECT DiaryEntry.id, DiaryEntry.plantId, DiaryEntry.timeEpoch, DiaryEntry.type, DiaryEntry.note, DiaryEntry.photoUri FROM DiaryEntry JOIN DiaryEntryFts ON DiaryEntry.id = DiaryEntryFts.rowid WHERE DiaryEntry.plantId = :plantId AND (:type IS NULL OR DiaryEntry.type = :type) AND DiaryEntryFts MATCH :query ORDER BY DiaryEntry.timeEpoch DESC")
    List<DiaryEntry> searchForPlant(long plantId, String type, String query);

    /**
     * Retrieves all diary entries stored in the database.
     *
     * @return list of all diary entries
     */
    @Query("SELECT id, plantId, timeEpoch, type, note, photoUri FROM DiaryEntry")
    List<DiaryEntry> getAll();
}
