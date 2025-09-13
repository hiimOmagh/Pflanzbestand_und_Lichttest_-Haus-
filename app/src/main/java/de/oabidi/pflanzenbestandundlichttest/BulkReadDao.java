package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

/**
 * Specialized DAO providing bulk read access for export and import operations.
 */
@Dao
public interface BulkReadDao {
    /** Retrieves all plants in the database ordered by name. */
    @Query("SELECT * FROM Plant ORDER BY name ASC")
    List<Plant> getAllPlants();

    /** Retrieves a single plant by its identifier. */
    @Query("SELECT * FROM Plant WHERE id = :id")
    Plant getPlant(long id);

    /** Retrieves all measurements. */
    @Query("SELECT id, plantId, timeEpoch, luxAvg, ppfd, dli, note FROM Measurement")
    List<Measurement> getAllMeasurements();

    /** Retrieves measurements associated with the given plant. */
    @Query("SELECT id, plantId, timeEpoch, luxAvg, ppfd, dli, note FROM Measurement WHERE plantId = :plantId")
    List<Measurement> getMeasurementsForPlant(long plantId);

    /** Retrieves all diary entries. */
    @Query("SELECT * FROM DiaryEntry")
    List<DiaryEntry> getAllDiaryEntries();

    /** Retrieves diary entries associated with the given plant. */
    @Query("SELECT * FROM DiaryEntry WHERE plantId = :plantId")
    List<DiaryEntry> getDiaryEntriesForPlant(long plantId);

    /** Retrieves all reminders. */
    @Query("SELECT * FROM Reminder")
    List<Reminder> getAllReminders();

    /** Retrieves reminders associated with the given plant. */
    @Query("SELECT * FROM Reminder WHERE plantId = :plantId")
    List<Reminder> getRemindersForPlant(long plantId);

    /** Retrieves all species targets. */
    @Query("SELECT * FROM SpeciesTarget")
    List<SpeciesTarget> getAllSpeciesTargets();
}
