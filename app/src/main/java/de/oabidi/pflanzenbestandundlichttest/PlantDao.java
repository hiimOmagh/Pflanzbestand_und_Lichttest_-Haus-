package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PlantDao {
    /**
     * Retrieves all {@link Plant} entries from the database.
     * <p>
     * Note: Should be called off the main thread for production usage.
     *
     * @return a list containing all plants stored in the database
     */
    @Query("SELECT * FROM Plant ORDER BY name ASC")
    List<Plant> getAll();

    /**
     * Inserts the given {@link Plant} into the database.
     * <p>
     * Note: Should be called off the main thread for production usage. Returns the
     * newly generated row ID of the inserted entity.
     *
     * @param plant the plant entity to insert
     * @return the database identifier for the inserted plant
     */
    @Insert
    long insert(Plant plant);

    /**
     * Updates the given {@link Plant} in the database.
     * <p>
     * Note: Should be called off the main thread for production usage. This method
     * does not return a value.
     *
     * @param plant the plant entity to update
     */
    @Update
    void update(Plant plant);

    /**
     * Deletes the given {@link Plant} from the database.
     * <p>
     * Note: Should be called off the main thread for production usage. This method
     * does not return a value.
     *
     * @param plant the plant entity to delete
     */
    @Delete
    void delete(Plant plant);
}
