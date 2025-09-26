package de.oabidi.pflanzenbestandundlichttest.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data access object managing {@link ProactiveAlertLog} entries.
 */
@Dao
public interface ProactiveAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ProactiveAlertLog log);

    @Query("SELECT * FROM ProactiveAlertLog WHERE plantId = :plantId AND triggerId = :triggerId ORDER BY createdAt DESC, id DESC LIMIT 1")
    ProactiveAlertLog latestForTrigger(long plantId, String triggerId);

    @Query("SELECT * FROM ProactiveAlertLog ORDER BY createdAt DESC, id DESC LIMIT :limit")
    List<ProactiveAlertLog> recent(int limit);

    @Query("DELETE FROM ProactiveAlertLog WHERE createdAt < :threshold")
    void deleteOlderThan(long threshold);
}
