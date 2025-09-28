package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data access object for {@link LedProfileAssociation} join records.
 */
@Dao
public interface LedProfileAssociationDao {
    /**
     * Returns all plant associations for the given LED profile.
     */
    @Query("SELECT * FROM LedProfileAssociation WHERE ledProfileId = :profileId")
    List<LedProfileAssociation> getAssociationsForProfile(long profileId);

    /**
     * Returns all LED profile associations attached to the specified plant.
     */
    @Query("SELECT * FROM LedProfileAssociation WHERE plantId = :plantId")
    List<LedProfileAssociation> getAssociationsForPlant(long plantId);

    /**
     * Inserts or replaces an association between a plant and an LED profile.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(LedProfileAssociation association);

    /**
     * Removes a persisted association.
     */
    @Delete
    void delete(LedProfileAssociation association);

    /**
     * Clears all associations for the provided plant identifier.
     */
    @Query("DELETE FROM LedProfileAssociation WHERE plantId = :plantId")
    void deleteByPlantId(long plantId);
}
