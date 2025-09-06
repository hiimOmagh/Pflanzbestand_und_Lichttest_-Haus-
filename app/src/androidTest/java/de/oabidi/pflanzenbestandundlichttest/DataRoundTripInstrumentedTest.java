package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.data.util.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

import static org.junit.Assert.*;

/**
 * Ensures that exporting then importing a backup restores all plant and diary data,
 * including associated photos.
 */
@RunWith(AndroidJUnit4.class)
public class DataRoundTripInstrumentedTest {

    @Test
    public void plantAndDiaryRoundTrip() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository repository = new PlantRepository(context);

        // Create a plant photo
        byte[] plantPhotoBytes = new byte[]{1, 2, 3};
        File plantPhotoFile = new File(context.getCacheDir(), "round_trip_plant.jpg");
        try (FileOutputStream fos = new FileOutputStream(plantPhotoFile)) {
            fos.write(plantPhotoBytes);
        }
        Uri plantPhotoUri = Uri.fromFile(plantPhotoFile);

        // Create a diary photo
        byte[] diaryPhotoBytes = new byte[]{4, 5, 6};
        File diaryPhotoFile = new File(context.getCacheDir(), "round_trip_diary.jpg");
        try (FileOutputStream fos = new FileOutputStream(diaryPhotoFile)) {
            fos.write(diaryPhotoBytes);
        }
        Uri diaryPhotoUri = Uri.fromFile(diaryPhotoFile);

        // Insert plant and diary entry
        Plant plant = new Plant("RoundTrip", null, null, null, 0L, plantPhotoUri);
        repository.insert(plant, null).get();

        DiaryEntry entry = new DiaryEntry(plant.getId(), 1234L, "note", "round");
        entry.setPhotoUri(diaryPhotoUri.toString());
        repository.insertDiaryEntry(entry, null).get();

        // Export database to temporary file
        File exportFile = new File(context.getCacheDir(), "round_trip.zip");
        Uri exportUri = Uri.fromFile(exportFile);
        CountDownLatch exportLatch = new CountDownLatch(1);
        new ExportManager(context).export(exportUri, success -> exportLatch.countDown());
        assertTrue(exportLatch.await(5, TimeUnit.SECONDS));

        // Wipe database
        PlantDatabase.databaseWriteExecutor
            .submit(() -> PlantDatabase.getDatabase(context).clearAllTables())
            .get();

        // Import data back
        CountDownLatch importLatch = new CountDownLatch(1);
        new ImportManager(context)
            .importData(exportUri, ImportManager.Mode.REPLACE, success -> importLatch.countDown());
        assertTrue(importLatch.await(5, TimeUnit.SECONDS));

        // Verify counts
        int plantCount = PlantDatabase.databaseWriteExecutor
            .submit(() -> repository.getAllPlantsSync().size())
            .get();
        int diaryCount = PlantDatabase.databaseWriteExecutor
            .submit(() -> repository.getAllDiaryEntriesSync().size())
            .get();
        assertEquals(1, plantCount);
        assertEquals(1, diaryCount);

        // Verify plant photo restored
        Plant restoredPlant = PlantDatabase.databaseWriteExecutor
            .submit(() -> repository.getAllPlantsSync().get(0))
            .get();
        Uri restoredPlantUri = restoredPlant.getPhotoUri();
        assertNotNull(restoredPlantUri);
        try (InputStream is = context.getContentResolver().openInputStream(restoredPlantUri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            assertArrayEquals(plantPhotoBytes, baos.toByteArray());
        }

        // Verify diary photo restored
        DiaryEntry restoredEntry = PlantDatabase.databaseWriteExecutor
            .submit(() -> repository.getAllDiaryEntriesSync().get(0))
            .get();
        assertNotNull(restoredEntry.getPhotoUri());
        Uri restoredDiaryUri = Uri.parse(restoredEntry.getPhotoUri());
        try (InputStream is = context.getContentResolver().openInputStream(restoredDiaryUri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            assertArrayEquals(diaryPhotoBytes, baos.toByteArray());
        }
    }
}
