package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Data access object for {@link PlantPhoto} entities.
 */
@Dao
public interface PlantPhotoDao {
    /** Inserts the provided photo and returns its generated identifier. */
    @Insert
    long insert(@NonNull PlantPhoto photo);

    /** Returns all photos that belong to the given plant ordered by creation time (newest first). */
    @Query("SELECT * FROM PlantPhoto WHERE plantId = :plantId ORDER BY createdAt DESC, id DESC")
    List<PlantPhoto> getForPlant(long plantId);

    /** Deletes a specific photo belonging to a plant. */
    @Query("DELETE FROM PlantPhoto WHERE plantId = :plantId AND id = :photoId")
    void deleteForPlant(long plantId, long photoId);
}
