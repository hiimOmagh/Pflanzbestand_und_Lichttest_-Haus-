package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * Data access object for {@link ReminderSuggestion} entities.
 */
@Dao
public interface ReminderSuggestionDao {
    @Query("SELECT * FROM ReminderSuggestion WHERE plantId = :plantId")
    ReminderSuggestion findByPlantId(long plantId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(ReminderSuggestion suggestion);

    @Query("DELETE FROM ReminderSuggestion WHERE plantId = :plantId")
    void deleteByPlantId(long plantId);
}
