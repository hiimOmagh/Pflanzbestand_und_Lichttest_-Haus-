package de.oabidi.pflanzenbestandundlichttest.core.system.reminder;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data access object for {@link ReminderSuggestion} entities.
 */
@Dao
public interface ReminderSuggestionDao {
    @Query("SELECT * FROM ReminderSuggestion WHERE plantId = :plantId LIMIT 1")
    ReminderSuggestion getByPlantId(long plantId);

    @Query("SELECT * FROM ReminderSuggestion")
    List<ReminderSuggestion> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ReminderSuggestion suggestion);

    @Query("DELETE FROM ReminderSuggestion WHERE plantId = :plantId")
    void deleteByPlantId(long plantId);
}
