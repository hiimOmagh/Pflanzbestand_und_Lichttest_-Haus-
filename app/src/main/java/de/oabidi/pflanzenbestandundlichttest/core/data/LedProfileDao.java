package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data access object for {@link LedProfile} entries.
 */
@Dao
public interface LedProfileDao {
    /**
     * Returns all LED profiles stored in the database ordered by name.
     */
    @Query("SELECT * FROM LedProfile ORDER BY name ASC")
    List<LedProfile> getAll();

    /**
     * Finds a LED profile by its identifier.
     */
    @Query("SELECT * FROM LedProfile WHERE id = :id LIMIT 1")
    LedProfile findById(long id);

    /**
     * Inserts a new LED profile.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LedProfile profile);

    /**
     * Updates an existing LED profile.
     */
    @Update
    void update(LedProfile profile);

    /**
     * Deletes the provided LED profile.
     */
    @Delete
    void delete(LedProfile profile);
}
