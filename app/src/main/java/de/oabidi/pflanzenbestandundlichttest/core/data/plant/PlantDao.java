package de.oabidi.pflanzenbestandundlichttest.core.data.plant;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
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
     * Searches plants whose name or notes match the given FTS query.
     *
     * @param q full-text search query
     * @return list of matching plants ordered by name
     */
    @Query("SELECT Plant.* FROM Plant JOIN PlantFts ON Plant.id = PlantFts.rowid WHERE PlantFts MATCH :q ORDER BY Plant.name")
    List<Plant> search(String q);

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
    long insertInternal(Plant plant);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFts(PlantFts plantFts);

    @Transaction
    default long insert(Plant plant) {
        final long id = insertInternal(plant);
        insertFts(new PlantFts(
            id,
            plant.getName(),
            plant.getSpecies() == null ? "" : plant.getSpecies(),
            plant.getLocationHint() == null ? "" : plant.getLocationHint(),
            plant.getDescription() == null ? "" : plant.getDescription()
        ));
        return id;
    }

    /**
     * Updates the given {@link Plant} in the database.
     * <p>
     * Note: Should be called off the main thread for production usage. This method
     * does not return a value.
     *
     * @param plant the plant entity to update
     */
    @Update
    void updateInternal(Plant plant);

    @Transaction
    default void update(Plant plant) {
        updateInternal(plant);
        insertFts(new PlantFts(
            plant.getId(),
            plant.getName(),
            plant.getSpecies() == null ? "" : plant.getSpecies(),
            plant.getLocationHint() == null ? "" : plant.getLocationHint(),
            plant.getDescription() == null ? "" : plant.getDescription()
        ));
    }

    /**
     * Deletes the given {@link Plant} from the database.
     * <p>
     * Note: Should be called off the main thread for production usage. This method
     * does not return a value.
     *
     * @param plant the plant entity to delete
     */
    @Delete
    void deleteInternal(Plant plant);

    @Query("DELETE FROM PlantFts WHERE rowid = :rowid")
    void deleteFts(long rowid);

    @Transaction
    default void delete(Plant plant) {
        deleteInternal(plant);
        deleteFts(plant.getId());
    }

    /**
     * Finds a plant by its identifier.
     *
     * @param id database identifier
     * @return the matching plant or {@code null} if not found
     */
    @Query("SELECT * FROM Plant WHERE id = :id")
    Plant findById(long id);
}
