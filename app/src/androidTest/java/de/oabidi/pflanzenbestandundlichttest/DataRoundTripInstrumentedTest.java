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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.core.data.util.ImportManager;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileAssociation;

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
        Context appContext = context.getApplicationContext();
        ExecutorService executor = ((ExecutorProvider) appContext).getIoExecutor();
        PlantRepository repository = new PlantRepository(appContext, executor);

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
        awaitDb(() -> {
            long id = PlantDatabase.getDatabase(context).plantDao().insert(plant);
            plant.setId(id);
            return null;
        });

        awaitDb(() -> {
            LedProfile profile = new LedProfile();
            profile.setName("RoundTripProfile");
            Map<String, Float> factors = new HashMap<>();
            factors.put(LedProfile.CALIBRATION_KEY_AMBIENT, 0.04f);
            factors.put(LedProfile.CALIBRATION_KEY_CAMERA, 0.06f);
            profile.setCalibrationFactors(factors);
            long profileId = PlantDatabase.getDatabase(context).ledProfileDao().insert(profile);
            plant.setLedProfileId(profileId);
            PlantDatabase.getDatabase(context).plantDao().update(plant);
            PlantDatabase.getDatabase(context).ledProfileAssociationDao()
                .upsert(new LedProfileAssociation(plant.getId(), profileId));
            return null;
        });

        DiaryEntry entry = new DiaryEntry(plant.getId(), 1234L, "note", "round");
        entry.setPhotoUri(diaryPhotoUri.toString());
        awaitDb(() -> {
            long id = PlantDatabase.getDatabase(context).diaryDao().insert(entry);
            entry.setId(id);
            return null;
        });

        // Export database to temporary file
        File exportFile = new File(context.getCacheDir(), "round_trip.zip");
        Uri exportUri = Uri.fromFile(exportFile);
        CountDownLatch exportLatch = new CountDownLatch(1);
        new ExportManager(context, repository, executor).export(exportUri, success -> exportLatch.countDown());
        assertTrue(exportLatch.await(10, TimeUnit.SECONDS));

        // Wipe database
        awaitDb(() -> {
            PlantDatabase.getDatabase(context).clearAllTables();
            return null;
        });

        // Import data back
        CountDownLatch importLatch = new CountDownLatch(1);
        new ImportManager(context, executor)
            .importData(exportUri, ImportManager.Mode.REPLACE,
                (success, error, warnings, message) -> importLatch.countDown());
        assertTrue(importLatch.await(10, TimeUnit.SECONDS));

        // Verify counts
        int plantCount = awaitDb(() -> PlantDatabase.getDatabase(context).plantDao().getAll().size());
        int diaryCount = awaitDb(() -> PlantDatabase.getDatabase(context).diaryDao().getAll().size());
        int profileCount = awaitDb(() -> PlantDatabase.getDatabase(context).ledProfileDao().getAll().size());
        assertEquals(1, plantCount);
        assertEquals(1, diaryCount);
        assertEquals(1, profileCount);

        // Verify plant photo restored
        Plant restoredPlant = awaitDb(() -> PlantDatabase.getDatabase(context).plantDao().getAll().get(0));
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
        DiaryEntry restoredEntry = awaitDb(() -> PlantDatabase.getDatabase(context).diaryDao().getAll().get(0));
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

        LedProfile restoredProfile = awaitDb(
            () -> PlantDatabase.getDatabase(context).ledProfileDao().getAll().get(0)
        );
        Map<String, Float> restoredFactors = restoredProfile.getCalibrationFactors();
        assertEquals(0.04f, restoredFactors.get(LedProfile.CALIBRATION_KEY_AMBIENT), 0.0001f);
        assertEquals(0.06f, restoredFactors.get(LedProfile.CALIBRATION_KEY_CAMERA), 0.0001f);
        assertEquals(restoredProfile.getId(), restoredPlant.getLedProfileId().longValue());
    }
}
