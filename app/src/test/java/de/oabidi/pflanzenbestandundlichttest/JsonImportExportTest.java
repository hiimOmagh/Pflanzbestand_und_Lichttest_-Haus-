package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestion;

/**
 * Verifies that JSON exports round-trip through the import pipeline.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class JsonImportExportTest {
    private PlantDatabase db;
    private Context context;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        executor = ((ExecutorProvider) context).getIoExecutor();
        db = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        java.lang.reflect.Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, db);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        java.lang.reflect.Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void jsonExportImport_roundTrip() throws Exception {
        Plant plant = new Plant("Json Plant", "desc", "species", "corner", 1234L, null);
        long plantId = db.plantDao().insert(plant);

        Measurement measurement = new Measurement(plantId, 5678L, 120f, 45f, 4.5f, "measurement note");
        db.measurementDao().insert(measurement);

        Reminder reminder = new Reminder(7890L, "Water", plantId);
        long reminderId = db.reminderDao().insert(reminder);
        reminder.setId(reminderId);

        DiaryEntry diaryEntry = new DiaryEntry(plantId, 1357L, DiaryEntry.TYPE_WATER, "diary note");
        db.diaryDao().insert(diaryEntry);

        PlantCalibration calibration = new PlantCalibration(plantId, 1.2f, 2.3f);
        db.plantCalibrationDao().insertOrUpdate(calibration);

        EnvironmentEntry environmentEntry = new EnvironmentEntry(plantId, 2468L, 19.5f, 55f, 0.45f,
            12.5f, 8.1f, "env note", null);
        db.environmentEntryDao().insert(environmentEntry);

        SpeciesTarget.StageTarget stage = new SpeciesTarget.StageTarget(80f, 120f, 3.2f, 4.6f);
        SpeciesTarget target = new SpeciesTarget("json-species", stage, stage, stage, "moderate", "unit");
        db.speciesTargetDao().insert(target);

        ReminderSuggestion suggestion = new ReminderSuggestion();
        suggestion.setPlantId(plantId);
        suggestion.setSuggestedIntervalDays(5);
        suggestion.setLastEvaluatedAt(9999L);
        suggestion.setConfidenceScore(0.6f);
        suggestion.setExplanation("json suggestion");
        db.reminderSuggestionDao().upsert(suggestion);

        File exportFile = new File(context.getCacheDir(), "json-export.zip");
        if (exportFile.exists()) {
            exportFile.delete();
        }
        PlantRepository repository = new PlantRepository(context, executor);
        ExportManager exporter = new ExportManager(context, repository, executor);
        CountDownLatch exportLatch = new CountDownLatch(1);
        final boolean[] exportSuccess = {false};
        exporter.exportJson(Uri.fromFile(exportFile), success -> {
            exportSuccess[0] = success;
            exportLatch.countDown();
        });
        assertTrue(exportLatch.await(5, TimeUnit.SECONDS));
        assertTrue(exportSuccess[0]);

        db.clearAllTables();

        ImportManager importer = new ImportManager(context, executor);
        CountDownLatch importLatch = new CountDownLatch(1);
        final boolean[] importSuccess = {false};
        final ImportManager.ImportError[] errorHolder = {null};
        importer.importData(Uri.fromFile(exportFile), ImportManager.Mode.REPLACE,
            (success, err, warnings, message) -> {
                importSuccess[0] = success;
                errorHolder[0] = err;
                importLatch.countDown();
            });
        assertTrue(importLatch.await(5, TimeUnit.SECONDS));
        assertTrue(importSuccess[0]);
        assertNull(errorHolder[0]);

        List<Plant> plants = db.plantDao().getAll();
        assertEquals(1, plants.size());
        assertEquals("Json Plant", plants.get(0).getName());

        List<Measurement> measurements = db.measurementDao().getAll();
        assertEquals(1, measurements.size());
        assertEquals(4.5f, measurements.get(0).getDli(), 0.0001f);
        assertEquals("measurement note", measurements.get(0).getNote());

        List<Reminder> reminders = db.reminderDao().getAll();
        assertEquals(1, reminders.size());
        assertEquals("Water", reminders.get(0).getMessage());

        List<DiaryEntry> diaries = db.diaryDao().getAll();
        assertEquals(1, diaries.size());
        assertEquals("diary note", diaries.get(0).getNote());

        List<PlantCalibration> calibrations = db.plantCalibrationDao().getAll();
        assertEquals(1, calibrations.size());
        assertEquals(1.2f, calibrations.get(0).getAmbientFactor(), 0.0001f);
        assertEquals(2.3f, calibrations.get(0).getCameraFactor(), 0.0001f);

        List<SpeciesTarget> targets = db.speciesTargetDao().getAll();
        assertEquals(1, targets.size());
        assertEquals("json-species", targets.get(0).getSpeciesKey());

        List<EnvironmentEntry> environmentEntries = db.environmentEntryDao().getForPlantOrdered(plants.get(0).getId());
        assertEquals(1, environmentEntries.size());
        EnvironmentEntry restoredEnv = environmentEntries.get(0);
        assertEquals(19.5f, restoredEnv.getTemperature(), 0.0001f);
        assertEquals(55f, restoredEnv.getHumidity(), 0.0001f);
        assertEquals("env note", restoredEnv.getNotes());

        List<ReminderSuggestion> suggestions = db.reminderSuggestionDao().getAll();
        assertEquals(1, suggestions.size());
        ReminderSuggestion restoredSuggestion = suggestions.get(0);
        assertEquals(plants.get(0).getId(), restoredSuggestion.getPlantId());
        assertEquals(5, restoredSuggestion.getSuggestedIntervalDays());
        assertEquals(9999L, restoredSuggestion.getLastEvaluatedAt());
        assertEquals(0.6f, restoredSuggestion.getConfidenceScore(), 0.0001f);
        assertEquals("json suggestion", restoredSuggestion.getExplanation());
    }

    @Test
    public void jsonExportImport_singlePlantHappyPath() throws Exception {
        Plant plantA = new Plant("Json Plant A", null, "speciesA", null, 2222L, null);
        long plantAId = db.plantDao().insert(plantA);
        Plant plantB = new Plant("Json Plant B", null, "speciesB", null, 3333L, null);
        db.plantDao().insert(plantB);

        Measurement measurementA = new Measurement(plantAId, 4444L, 90f, 35f, 3.5f, "A note");
        db.measurementDao().insert(measurementA);
        DiaryEntry diaryA = new DiaryEntry(plantAId, 5555L, DiaryEntry.TYPE_PRUNE, "A diary");
        db.diaryDao().insert(diaryA);
        Reminder reminderA = new Reminder(6666L, "A reminder", plantAId);
        long reminderAId = db.reminderDao().insert(reminderA);
        reminderA.setId(reminderAId);
        PlantCalibration calibrationA = new PlantCalibration(plantAId, 1.5f, 2.6f);
        db.plantCalibrationDao().insertOrUpdate(calibrationA);

        EnvironmentEntry environmentEntryA = new EnvironmentEntry(plantAId, 7777L, 23.2f, 60f, null,
            14.0f, 9.5f, "A env", null);
        db.environmentEntryDao().insert(environmentEntryA);

        SpeciesTarget.StageTarget stage = new SpeciesTarget.StageTarget(70f, 110f, 2.5f, 4.5f);
        SpeciesTarget target = new SpeciesTarget("json-species-single", stage, stage, stage, "wide", "unit");
        db.speciesTargetDao().insert(target);

        File exportFile = new File(context.getCacheDir(), "json-export-single.zip");
        if (exportFile.exists()) {
            exportFile.delete();
        }
        PlantRepository repository = new PlantRepository(context, executor);
        ExportManager exporter = new ExportManager(context, repository, executor);
        CountDownLatch exportLatch = new CountDownLatch(1);
        final boolean[] exportSuccess = {false};
        exporter.exportJson(Uri.fromFile(exportFile), plantAId, success -> {
            exportSuccess[0] = success;
            exportLatch.countDown();
        });
        assertTrue(exportLatch.await(5, TimeUnit.SECONDS));
        assertTrue(exportSuccess[0]);

        db.clearAllTables();

        ImportManager importer = new ImportManager(context, executor);
        CountDownLatch importLatch = new CountDownLatch(1);
        final boolean[] importSuccess = {false};
        final ImportManager.ImportError[] errorHolder = {null};
        importer.importData(Uri.fromFile(exportFile), ImportManager.Mode.REPLACE,
            (success, err, warnings, message) -> {
                importSuccess[0] = success;
                errorHolder[0] = err;
                importLatch.countDown();
            });
        assertTrue(importLatch.await(5, TimeUnit.SECONDS));
        assertTrue(importSuccess[0]);
        assertNull(errorHolder[0]);

        List<Plant> plants = db.plantDao().getAll();
        assertEquals(1, plants.size());
        assertEquals("Json Plant A", plants.get(0).getName());

        List<Measurement> measurements = db.measurementDao().getAll();
        assertEquals(1, measurements.size());
        assertEquals(plantAId, measurements.get(0).getPlantId());
        assertEquals(35f, measurements.get(0).getPpfd(), 0.0001f);

        List<DiaryEntry> diaries = db.diaryDao().getAll();
        assertEquals(1, diaries.size());
        assertEquals("A diary", diaries.get(0).getNote());
        assertEquals(DiaryEntry.TYPE_PRUNE, diaries.get(0).getType());

        List<Reminder> reminders = db.reminderDao().getAll();
        assertEquals(1, reminders.size());
        assertEquals("A reminder", reminders.get(0).getMessage());

        List<PlantCalibration> calibrations = db.plantCalibrationDao().getAll();
        assertEquals(1, calibrations.size());
        assertEquals(1.5f, calibrations.get(0).getAmbientFactor(), 0.0001f);
        assertEquals(2.6f, calibrations.get(0).getCameraFactor(), 0.0001f);

        List<SpeciesTarget> targets = db.speciesTargetDao().getAll();
        assertEquals(1, targets.size());
        assertEquals("json-species-single", targets.get(0).getSpeciesKey());

        List<EnvironmentEntry> envEntries = db.environmentEntryDao().getForPlantOrdered(plants.get(0).getId());
        assertEquals(1, envEntries.size());
        EnvironmentEntry restored = envEntries.get(0);
        assertEquals(23.2f, restored.getTemperature(), 0.0001f);
        assertEquals("A env", restored.getNotes());
    }
}
