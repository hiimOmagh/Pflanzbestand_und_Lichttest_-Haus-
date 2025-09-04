package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.data.util.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test verifying that exported data can be reimported after wiping the database.
 */
@RunWith(AndroidJUnit4.class)
public class DataImportExportInstrumentedTest {
    @Test
    public void testExportImportRestoresData() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository repository = new PlantRepository(context);

        // Insert plant with measurement and diary entry
        Plant plant = new Plant("ExportPlant", null, null, null, 0L, null);
        repository.insert(plant, null).get();

        Measurement m = new Measurement(plant.getId(), 1000L, 1f, 2f, 3f);
        repository.insertMeasurement(m, null).get();

        DiaryEntry d = new DiaryEntry(plant.getId(), 2000L, "note", "hello");
        repository.insertDiaryEntry(d, null).get();

        // Export data to temporary file
        File file = new File(context.getCacheDir(), "export.csv");
        Uri uri = Uri.fromFile(file);
        CountDownLatch exportLatch = new CountDownLatch(1);
        new ExportManager(context).export(uri, success -> exportLatch.countDown());
        assertTrue(exportLatch.await(5, TimeUnit.SECONDS));

        // Wipe database
        PlantDatabase.databaseWriteExecutor.submit(() ->
            PlantDatabase.getDatabase(context).clearAllTables()
        ).get();

        // Recreate plant with same ID
        Plant newPlant = new Plant("ExportPlant", null, null, null, 0L, null);
        repository.insert(newPlant, null).get();

        // Import data
        CountDownLatch importLatch = new CountDownLatch(1);
        new ImportManager(context).importData(uri, success -> importLatch.countDown());
        assertTrue(importLatch.await(5, TimeUnit.SECONDS));

        // Verify measurements and diary entries restored
        int measurementCount = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllMeasurementsSync().size()
        ).get();
        int diaryCount = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllDiaryEntriesSync().size()
        ).get();

        assertEquals(1, measurementCount);
        assertEquals(1, diaryCount);
    }
}
