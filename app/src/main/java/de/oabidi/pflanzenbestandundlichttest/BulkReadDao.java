package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestion;

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

    /** Retrieves all reminder suggestions. */
    @Query("SELECT * FROM ReminderSuggestion")
    List<ReminderSuggestion> getAllReminderSuggestions();

    /** Retrieves the reminder suggestion associated with the given plant. */
    @Query("SELECT * FROM ReminderSuggestion WHERE plantId = :plantId LIMIT 1")
    ReminderSuggestion getReminderSuggestionForPlant(long plantId);

    /** Retrieves all species targets. */
    @Query("SELECT * FROM SpeciesTarget")
    List<SpeciesTarget> getAllSpeciesTargets();

    /** Retrieves all LED profiles. */
    @Query("SELECT * FROM LedProfile")
    List<LedProfile> getAllLedProfiles();

    /** Retrieves a specific LED profile by identifier. */
    @Query("SELECT * FROM LedProfile WHERE id = :profileId")
    LedProfile getLedProfile(long profileId);

    /** Retrieves all environment log entries. */
    @Query("SELECT * FROM EnvironmentEntry ORDER BY timestamp ASC, id ASC")
    List<EnvironmentEntry> getAllEnvironmentEntries();

    /** Retrieves environment log entries associated with the given plant. */
    @Query("SELECT * FROM EnvironmentEntry WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
    List<EnvironmentEntry> getEnvironmentEntriesForPlant(long plantId);
}
