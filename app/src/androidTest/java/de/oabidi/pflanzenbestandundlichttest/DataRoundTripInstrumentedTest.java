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
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

import static org.junit.Assert.*;

/**
 * Ensures that exporting then importing a backup restores all plant and diary data,
 * including associated photos.
 */
@RunWith(AndroidJUnit4.class)
public class DataRoundTripInstrumentedTest {
    private static <T> T awaitDb(Callable<T> task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        return result.get();
    }

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
        new ExportManager(context, repository).export(exportUri, success -> exportLatch.countDown());
        assertTrue(exportLatch.await(10, TimeUnit.SECONDS));

        // Wipe database
        awaitDb(() -> {
            PlantDatabase.getDatabase(context).clearAllTables();
            return null;
        });

        // Import data back
        CountDownLatch importLatch = new CountDownLatch(1);
        new ImportManager(context)
            .importData(exportUri, ImportManager.Mode.REPLACE,
                (success, error, warnings, message) -> importLatch.countDown());
        assertTrue(importLatch.await(10, TimeUnit.SECONDS));

        // Verify counts
        int plantCount = awaitDb(() -> repository.getAllPlantsSync().size());
        int diaryCount = awaitDb(() -> repository.getAllDiaryEntriesSync().size());
        assertEquals(1, plantCount);
        assertEquals(1, diaryCount);

        // Verify plant photo restored
        Plant restoredPlant = awaitDb(() -> repository.getAllPlantsSync().get(0));
        Uri restoredPlantUri = restoredPlant.getPhotoUri();
        assertNotNull(restoredPlantUri);
        try (InputStream is = context.getContentResolver().openInputStream(restoredPlantUri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = Objects.requireNonNull(is).read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            assertArrayEquals(plantPhotoBytes, baos.toByteArray());
        }

        // Verify diary photo restored
        DiaryEntry restoredEntry = awaitDb(() -> repository.getAllDiaryEntriesSync().get(0));
        assertNotNull(restoredEntry.getPhotoUri());
        Uri restoredDiaryUri = Uri.parse(restoredEntry.getPhotoUri());
        try (InputStream is = context.getContentResolver().openInputStream(restoredDiaryUri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = Objects.requireNonNull(is).read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            assertArrayEquals(diaryPhotoBytes, baos.toByteArray());
        }
    }
}
