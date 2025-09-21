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

import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

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

        SpeciesTarget.StageTarget stage = new SpeciesTarget.StageTarget(80f, 120f, 3.2f, 4.6f);
        SpeciesTarget target = new SpeciesTarget("json-species", stage, stage, stage, "moderate", "unit");
        db.speciesTargetDao().insert(target);

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
    }
}
