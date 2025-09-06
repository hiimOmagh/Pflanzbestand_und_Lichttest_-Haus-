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
import android.app.AlarmManager;
import android.content.Intent;

import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPendingIntent;

import de.oabidi.pflanzenbestandundlichttest.data.util.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;
import de.oabidi.pflanzenbestandundlichttest.Reminder;
import de.oabidi.pflanzenbestandundlichttest.ReminderScheduler;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
        ShadowAlarmManager.reset();

        // Insert species target and plant with measurement and diary entry
        SpeciesTarget target = new SpeciesTarget("ExportSpecies", 10f, 20f);
        repository.insertSpeciesTarget(target, null).get();

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
        repository.insert(plant, null).get();

        Measurement m = new Measurement(plant.getId(), 1000L, 1f, 2f, 3f);
        repository.insertMeasurement(m, null).get();

        DiaryEntry d = new DiaryEntry(plant.getId(), 2000L, "note", "hello");
        d.setPhotoUri(diaryPhotoUri.toString());
        repository.insertDiaryEntry(d, null).get();

        long reminderTrigger = System.currentTimeMillis() + 5000;
        Reminder reminder = new Reminder(reminderTrigger, "ExportReminder");
        long reminderId = PlantDatabase.databaseWriteExecutor.submit(() ->
            PlantDatabase.getDatabase(context).reminderDao().insert(reminder)
        ).get();
        reminder.setId(reminderId);

        // Export data to temporary file
        File file = new File(context.getCacheDir(), "export.zip");
        Uri uri = Uri.fromFile(file);
        CountDownLatch exportLatch = new CountDownLatch(1);
        new ExportManager(context).export(uri, success -> exportLatch.countDown());
        assertTrue(exportLatch.await(5, TimeUnit.SECONDS));

        // Wipe database
        PlantDatabase.databaseWriteExecutor.submit(() ->
            PlantDatabase.getDatabase(context).clearAllTables()
        ).get();

        // Import data
        CountDownLatch importLatch = new CountDownLatch(1);
        new ImportManager(context).importData(uri, ImportManager.Mode.REPLACE,
            (success, warnings) -> importLatch.countDown());
        assertTrue(importLatch.await(5, TimeUnit.SECONDS));

        // Verify all data restored
        int plantCount = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllPlantsSync().size()
        ).get();
        int targetCount = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllSpeciesTargetsSync().size()
        ).get();
        int measurementCount = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllMeasurementsSync().size()
        ).get();
        int diaryCount = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllDiaryEntriesSync().size()
        ).get();
        int reminderCount = PlantDatabase.databaseWriteExecutor.submit(
            () -> PlantDatabase.getDatabase(context).reminderDao().getAll().size()
        ).get();

        assertEquals(1, plantCount);
        assertEquals(1, targetCount);
        assertEquals(1, measurementCount);
        assertEquals(1, diaryCount);
        assertEquals(1, reminderCount);

        // Verify photos restored
        Plant restoredPlant = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllPlantsSync().get(0)
        ).get();
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

        DiaryEntry restoredDiary = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllDiaryEntriesSync().get(0)
        ).get();
        assertNotNull(restoredDiary.getPhotoUri());
        Uri restoredDiaryUri = Uri.parse(restoredDiary.getPhotoUri());
        try (InputStream is = context.getContentResolver().openInputStream(restoredDiaryUri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            assertArrayEquals(diaryPhotoBytes, baos.toByteArray());
        }

        Reminder restoredReminder = PlantDatabase.databaseWriteExecutor.submit(
            () -> PlantDatabase.getDatabase(context).reminderDao().getAll().get(0)
        ).get();
        assertEquals(reminderTrigger, restoredReminder.getTriggerAt());
        assertEquals("ExportReminder", restoredReminder.getMessage());

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAm = Shadows.shadowOf(am);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAm.getNextScheduledAlarm();
        assertNotNull(alarm);
        ShadowPendingIntent pending = Shadows.shadowOf(alarm.operation);
        Intent scheduledIntent = pending.getSavedIntent();
        assertEquals("ExportReminder", scheduledIntent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE));
        assertEquals(restoredReminder.getId(), scheduledIntent.getLongExtra(ReminderScheduler.EXTRA_ID, -1));
    }

    @Test
    public void importedPhotosSurviveCacheClear() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository repository = new PlantRepository(context);

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
        repository.insert(plant, null).get();

        DiaryEntry entry = new DiaryEntry(plant.getId(), 1000L, "note", "import");
        entry.setPhotoUri(diaryPhotoUri.toString());
        repository.insertDiaryEntry(entry, null).get();

        File export = new File(context.getCacheDir(), "persist_export.zip");
        Uri exportUri = Uri.fromFile(export);
        CountDownLatch exportLatch = new CountDownLatch(1);
        new ExportManager(context).export(exportUri, success -> exportLatch.countDown());
        assertTrue(exportLatch.await(5, TimeUnit.SECONDS));

        PlantDatabase.databaseWriteExecutor.submit(() ->
            PlantDatabase.getDatabase(context).clearAllTables()
        ).get();

        CountDownLatch importLatch = new CountDownLatch(1);
        new ImportManager(context).importData(exportUri, ImportManager.Mode.REPLACE,
            (success, warnings) -> importLatch.countDown());
        assertTrue(importLatch.await(5, TimeUnit.SECONDS));

        Plant restoredPlant = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllPlantsSync().get(0)
        ).get();
        Uri restoredPlantUri = restoredPlant.getPhotoUri();

        DiaryEntry restoredDiary = PlantDatabase.databaseWriteExecutor.submit(
            () -> repository.getAllDiaryEntriesSync().get(0)
        ).get();
        Uri restoredDiaryUri = Uri.parse(restoredDiary.getPhotoUri());

        clearDir(context.getCacheDir());

        try (InputStream is = context.getContentResolver().openInputStream(restoredPlantUri)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            assertArrayEquals(plantPhotoBytes, baos.toByteArray());
        }

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
}
