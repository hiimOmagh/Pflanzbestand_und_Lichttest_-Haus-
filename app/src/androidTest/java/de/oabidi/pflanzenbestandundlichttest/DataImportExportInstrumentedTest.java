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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.app.AlarmManager;
import android.content.Intent;

import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPendingIntent;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.Reminder;
import de.oabidi.pflanzenbestandundlichttest.ReminderScheduler;
import de.oabidi.pflanzenbestandundlichttest.Measurement;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test verifying that exported data can be reimported after wiping the database.
 */
@RunWith(AndroidJUnit4.class)
public class DataImportExportInstrumentedTest {
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

    private static void clearDir(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    clearDir(child);
                }
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
    }

    @Test
    public void testExportImportRestoresData() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Context appContext = context.getApplicationContext();
        ExecutorService executor = ((ExecutorProvider) appContext).getIoExecutor();
        PlantRepository repository = new PlantRepository(appContext, executor);
        ShadowAlarmManager.reset();

        // Insert species target and plant with measurement and diary entry
        SpeciesTarget target = new SpeciesTarget("ExportSpecies", 10f, 20f);
        awaitDb(() -> {
            PlantDatabase.getDatabase(context).speciesTargetDao().insert(target);
            return null;
        });

        // Create dummy photos
        byte[] plantPhotoBytes = new byte[]{1, 2, 3};
        File plantPhotoFile = new File(context.getCacheDir(), "plant.jpg");
        try (FileOutputStream fos = new FileOutputStream(plantPhotoFile)) {
            fos.write(plantPhotoBytes);
        }
        Uri plantPhotoUri = Uri.fromFile(plantPhotoFile);

        byte[] diaryPhotoBytes = new byte[]{4, 5, 6};
        File diaryPhotoFile = new File(context.getCacheDir(), "diary.jpg");
        try (FileOutputStream fos = new FileOutputStream(diaryPhotoFile)) {
            fos.write(diaryPhotoBytes);
        }
        Uri diaryPhotoUri = Uri.fromFile(diaryPhotoFile);

        Plant plant = new Plant("ExportPlant", null, "ExportSpecies", null, 0L, plantPhotoUri);
        awaitDb(() -> {
            long id = PlantDatabase.getDatabase(context).plantDao().insert(plant);
            plant.setId(id);
            return null;
        });

        awaitDb(() -> {
            PlantDatabase.getDatabase(context).plantCalibrationDao()
                .insertOrUpdate(new PlantCalibration(plant.getId(), 0.05f, 0.07f));
            return null;
        });

        Measurement m = new Measurement(plant.getId(), 1000L, 1f, 2f, 1f, "note");
        awaitDb(() -> {
            PlantDatabase.getDatabase(context).measurementDao().insert(m);
            return null;
        });

        DiaryEntry d = new DiaryEntry(plant.getId(), 2000L, "note", "hello");
        d.setPhotoUri(diaryPhotoUri.toString());
        awaitDb(() -> {
            long id = PlantDatabase.getDatabase(context).diaryDao().insert(d);
            d.setId(id);
            return null;
        });

        long reminderTrigger = System.currentTimeMillis() + 5000;
        Reminder reminder = new Reminder(reminderTrigger, "ExportReminder", plant.getId());
        long reminderId = awaitDb(() ->
            PlantDatabase.getDatabase(context).reminderDao().insert(reminder)
        );
        reminder.setId(reminderId);

        // Export data to temporary file
        File file = new File(context.getCacheDir(), "export.zip");
        Uri uri = Uri.fromFile(file);
        CountDownLatch exportLatch = new CountDownLatch(1);
        new ExportManager(context, repository, executor).export(uri, success -> exportLatch.countDown());
        assertTrue(exportLatch.await(10, TimeUnit.SECONDS));

        // Wipe database
        awaitDb(() -> {
            PlantDatabase.getDatabase(context).clearAllTables();
            return null;
        });

        // Import data
        CountDownLatch importLatch = new CountDownLatch(1);
        new ImportManager(context, executor).importData(uri, ImportManager.Mode.REPLACE,
            (success, error, warnings, message) -> importLatch.countDown());
        assertTrue(importLatch.await(10, TimeUnit.SECONDS));

        // Verify all data restored
        int plantCount = awaitDb(() -> PlantDatabase.getDatabase(context).plantDao().getAll().size());
        int targetCount = awaitDb(() -> PlantDatabase.getDatabase(context).speciesTargetDao().getAll().size());
        int measurementCount = awaitDb(() -> PlantDatabase.getDatabase(context).measurementDao().getAll().size());
        int diaryCount = awaitDb(() -> PlantDatabase.getDatabase(context).diaryDao().getAll().size());
        int reminderCount = awaitDb(() -> PlantDatabase.getDatabase(context).reminderDao().getAll().size());
        int calibrationCount = awaitDb(() -> PlantDatabase.getDatabase(context).plantCalibrationDao().getAll().size());

        assertEquals(1, plantCount);
        assertEquals(1, targetCount);
        assertEquals(1, measurementCount);
        assertEquals(1, diaryCount);
        assertEquals(1, reminderCount);
        assertEquals(1, calibrationCount);

        Measurement restoredMeasurement = awaitDb(
            () -> PlantDatabase.getDatabase(context).measurementDao().getAll().get(0)
        );

        // Verify photos restored
        Plant restoredPlant = awaitDb(
            () -> PlantDatabase.getDatabase(context).plantDao().getAll().get(0)
        );
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

        DiaryEntry restoredDiary = awaitDb(
            () -> PlantDatabase.getDatabase(context).diaryDao().getAll().get(0)
        );
        assertNotNull(restoredDiary.getPhotoUri());
        Uri restoredDiaryUri = Uri.parse(restoredDiary.getPhotoUri());
        try (InputStream is = context.getContentResolver().openInputStream(restoredDiaryUri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = Objects.requireNonNull(is).read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            assertArrayEquals(diaryPhotoBytes, baos.toByteArray());
        }

        Reminder restoredReminder = awaitDb(
            () -> PlantDatabase.getDatabase(context).reminderDao().getAll().get(0)
        );
        assertEquals(reminderTrigger, restoredReminder.getTriggerAt());
        assertEquals("ExportReminder", restoredReminder.getMessage());

        PlantCalibration restoredCalibration = awaitDb(
            () -> PlantDatabase.getDatabase(context).plantCalibrationDao().getAll().get(0)
        );
        assertEquals(0.05f, restoredCalibration.getAmbientFactor(), 0.0001f);
        assertEquals(0.07f, restoredCalibration.getCameraFactor(), 0.0001f);
        assertEquals(plant.getId(), restoredReminder.getPlantId());

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAm = Shadows.shadowOf(am);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAm.getNextScheduledAlarm();
        assertNotNull(alarm);
        ShadowPendingIntent pending = Shadows.shadowOf(alarm.operation);
        Intent scheduledIntent = pending.getSavedIntent();
        assertEquals("ExportReminder", scheduledIntent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE));
        assertEquals(restoredReminder.getId(), scheduledIntent.getLongExtra(ReminderScheduler.EXTRA_ID, -1));
        assertEquals(restoredReminder.getPlantId(), scheduledIntent.getLongExtra(ReminderScheduler.EXTRA_PLANT_ID, -1));
    }

    @Test
    public void importedPhotosSurviveCacheClear() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Context appContext = context.getApplicationContext();
        ExecutorService executor = ((ExecutorProvider) appContext).getIoExecutor();
        PlantRepository repository = new PlantRepository(appContext, executor);

        byte[] plantPhotoBytes = new byte[]{9, 8, 7};
        File plantPhotoFile = new File(context.getCacheDir(), "persist_plant.jpg");
        try (FileOutputStream fos = new FileOutputStream(plantPhotoFile)) {
            fos.write(plantPhotoBytes);
        }
        Uri plantPhotoUri = Uri.fromFile(plantPhotoFile);

        byte[] diaryPhotoBytes = new byte[]{6, 5, 4};
        File diaryPhotoFile = new File(context.getCacheDir(), "persist_diary.jpg");
        try (FileOutputStream fos = new FileOutputStream(diaryPhotoFile)) {
            fos.write(diaryPhotoBytes);
        }
        Uri diaryPhotoUri = Uri.fromFile(diaryPhotoFile);

        Plant plant = new Plant("PersistPlant", null, null, null, 0L, plantPhotoUri);
        awaitDb(() -> {
            long id = PlantDatabase.getDatabase(context).plantDao().insert(plant);
            plant.setId(id);
            return null;
        });

        DiaryEntry entry = new DiaryEntry(plant.getId(), 1000L, "note", "import");
        entry.setPhotoUri(diaryPhotoUri.toString());
        awaitDb(() -> {
            long id = PlantDatabase.getDatabase(context).diaryDao().insert(entry);
            entry.setId(id);
            return null;
        });

        File export = new File(context.getCacheDir(), "persist_export.zip");
        Uri exportUri = Uri.fromFile(export);
        CountDownLatch exportLatch = new CountDownLatch(1);
        new ExportManager(context, repository, executor).export(exportUri, success -> exportLatch.countDown());
        assertTrue(exportLatch.await(10, TimeUnit.SECONDS));

        awaitDb(() -> {
            PlantDatabase.getDatabase(context).clearAllTables();
            return null;
        });

        CountDownLatch importLatch = new CountDownLatch(1);
        new ImportManager(context, executor).importData(exportUri, ImportManager.Mode.REPLACE,
            (success, error, warnings, message) -> importLatch.countDown());
        assertTrue(importLatch.await(10, TimeUnit.SECONDS));

        Plant restoredPlant = awaitDb(
            () -> PlantDatabase.getDatabase(context).plantDao().getAll().get(0)
        );
        Uri restoredPlantUri = restoredPlant.getPhotoUri();

        DiaryEntry restoredDiary = awaitDb(
            () -> PlantDatabase.getDatabase(context).diaryDao().getAll().get(0)
        );
        Uri restoredDiaryUri = Uri.parse(restoredDiary.getPhotoUri());

        clearDir(context.getCacheDir());

        try (InputStream is = context.getContentResolver().openInputStream(restoredPlantUri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = Objects.requireNonNull(is).read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            assertArrayEquals(plantPhotoBytes, baos.toByteArray());
        }

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
