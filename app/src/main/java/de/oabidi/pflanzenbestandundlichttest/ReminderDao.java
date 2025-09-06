package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data access object for {@link Reminder} entities.
 */
@Dao
public interface ReminderDao {
    /**
     * Inserts a reminder into the database.
     *
     * @param reminder the entity to insert
     * @return generated row ID
     */
    @Insert
    long insert(Reminder reminder);

    /**
     * Deletes a reminder by its identifier.
     *
     * @param id identifier of the reminder to remove
     */
    @Query("DELETE FROM Reminder WHERE id = :id")
    void deleteById(long id);

    /**
     * Updates an existing reminder in the database.
     *
     * @param reminder the entity to update
     */
    @Update
    void update(Reminder reminder);

    /**
     * Returns all reminders stored in the database.
     *
     * @return list of reminders
     */
    @Query("SELECT id, triggerAt, message, plantId FROM Reminder")
    List<Reminder> getAll();

    /**
     * Returns reminders for a specific plant.
     *
     * @param plantId identifier of the plant
     * @return list of reminders associated with the plant
     */
    @Query("SELECT id, triggerAt, message, plantId FROM Reminder WHERE plantId = :plantId")
    List<Reminder> getForPlant(long plantId);
}
