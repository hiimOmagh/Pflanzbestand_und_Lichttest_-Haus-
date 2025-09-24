package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;

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

    /** Retrieves all gallery photos. */
    @Query("SELECT * FROM PlantPhoto")
    List<PlantPhoto> getAllPlantPhotos();

    /** Retrieves gallery photos associated with the given plant. */
    @Query("SELECT * FROM PlantPhoto WHERE plantId = :plantId")
    List<PlantPhoto> getPlantPhotosForPlant(long plantId);

    /** Retrieves all reminders. */
    @Query("SELECT * FROM Reminder")
    List<Reminder> getAllReminders();

    /** Retrieves reminders associated with the given plant. */
    @Query("SELECT * FROM Reminder WHERE plantId = :plantId")
    List<Reminder> getRemindersForPlant(long plantId);

    /** Retrieves all species targets. */
    @Query("SELECT * FROM SpeciesTarget")
    List<SpeciesTarget> getAllSpeciesTargets();

    /** Retrieves all plant calibrations. */
    @Query("SELECT * FROM PlantCalibration")
    List<PlantCalibration> getAllPlantCalibrations();

    /** Retrieves calibrations associated with the given plant. */
    @Query("SELECT * FROM PlantCalibration WHERE plantId = :plantId")
    List<PlantCalibration> getPlantCalibrationsForPlant(long plantId);

    /** Retrieves all environment log entries. */
    @Query("SELECT * FROM EnvironmentEntry ORDER BY timestamp ASC, id ASC")
    List<EnvironmentEntry> getAllEnvironmentEntries();

    /** Retrieves environment log entries associated with the given plant. */
    @Query("SELECT * FROM EnvironmentEntry WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
    List<EnvironmentEntry> getEnvironmentEntriesForPlant(long plantId);
}
