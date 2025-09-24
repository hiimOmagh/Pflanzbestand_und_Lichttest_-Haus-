package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;

import java.lang.reflect.Field;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;

/**
 * Unit tests for {@link PlantRepository} verifying basic CRUD operations and
 * that callbacks are dispatched on the main thread.
 */
@RunWith(RobolectricTestRunner.class)
public class PlantRepositoryTest {
    private PlantRepository repository;
    private PlantDatabase db;
    private ExecutorService ioExecutor;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, db);
        ioExecutor = Executors.newSingleThreadExecutor();
        repository = new PlantRepository(context, ioExecutor);
    }

    @After
    public void tearDown() throws Exception {
        ioExecutor.shutdownNow();
        ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        db.close();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private void awaitLatch(CountDownLatch latch) throws InterruptedException {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (latch.await(100, TimeUnit.MILLISECONDS)) {
                return;
            }
        }
        fail("Callback not invoked");
    }

    @Test
    public void plantCrudOperations() throws Exception {
        Plant plant = new Plant();
        plant.setName("Aloe");
        plant.setAcquiredAtEpoch(123);

        CountDownLatch insertLatch = new CountDownLatch(1);
        repository.insert(plant, () -> {
            assertTrue(plant.getId() > 0);
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            insertLatch.countDown();
        });
        awaitLatch(insertLatch);

        CountDownLatch queryLatch = new CountDownLatch(1);
        final List<Plant>[] holder = new List[1];
        repository.getAllPlants(plants -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            holder[0] = plants;
            queryLatch.countDown();
        }, e -> fail("error callback"));
        awaitLatch(queryLatch);
        assertEquals(1, holder[0].size());

        plant.setName("Aloe Vera");
        CountDownLatch updateLatch = new CountDownLatch(1);
        repository.update(plant, () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            updateLatch.countDown();
        });
        awaitLatch(updateLatch);

        CountDownLatch queryLatch2 = new CountDownLatch(1);
        repository.getAllPlants(plants -> {
            assertEquals("Aloe Vera", plants.get(0).getName());
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            queryLatch2.countDown();
        }, e -> fail("error callback"));
        awaitLatch(queryLatch2);

        CountDownLatch deleteLatch = new CountDownLatch(1);
        repository.delete(plant, () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            deleteLatch.countDown();
        });
        awaitLatch(deleteLatch);

        CountDownLatch queryLatch3 = new CountDownLatch(1);
        repository.getAllPlants(plants -> {
            assertTrue(plants.isEmpty());
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            queryLatch3.countDown();
        }, e -> fail("error callback"));
        awaitLatch(queryLatch3);
    }

    @Test
    public void measurementOperations() throws Exception {
        Plant plant = new Plant();
        plant.setName("Basil");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        Measurement measurement = new Measurement(plant.getId(), 1L, 2f, 3f, null, null);
        CountDownLatch insertLatch = new CountDownLatch(1);
        repository.insertMeasurement(measurement, () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            insertLatch.countDown();
        });
        awaitLatch(insertLatch);

        CountDownLatch queryLatch = new CountDownLatch(1);
        final List<Measurement>[] holder = new List[1];
        repository.measurementsForPlantSince(plant.getId(), 0L, ms -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            holder[0] = ms;
            queryLatch.countDown();
        });
        awaitLatch(queryLatch);
        assertEquals(1, holder[0].size());

        CountDownLatch rangeLatch = new CountDownLatch(1);
        final List<Measurement>[] holder2 = new List[1];
        repository.measurementsForPlantInRange(plant.getId(), 0L, 5L, ms -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            holder2[0] = ms;
            rangeLatch.countDown();
        });
        awaitLatch(rangeLatch);
        assertEquals(1, holder2[0].size());

        CountDownLatch deleteLatch = new CountDownLatch(1);
        repository.deleteMeasurement(holder[0].get(0), () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            deleteLatch.countDown();
        });
        awaitLatch(deleteLatch);

        CountDownLatch queryLatch2 = new CountDownLatch(1);
        repository.measurementsForPlantSince(plant.getId(), 0L, ms -> {
            assertTrue(ms.isEmpty());
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            queryLatch2.countDown();
        });
        awaitLatch(queryLatch2);
    }

    @Test
    public void environmentEntryOperations() throws Exception {
        Plant plant = new Plant();
        plant.setName("Fern");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        EnvironmentEntry entry = new EnvironmentEntry(plant.getId(), 5L, 21.5f, 40f, 0.5f,
            10f, 5f, "initial", null);
        CountDownLatch insertLatch = new CountDownLatch(1);
        repository.insertEnvironmentEntry(entry, () -> {
            assertTrue(entry.getId() > 0);
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            insertLatch.countDown();
        });
        awaitLatch(insertLatch);

        List<EnvironmentEntry> bulkEntries = repository.bulkDao().getEnvironmentEntriesForPlant(plant.getId());
        assertEquals(1, bulkEntries.size());
        assertEquals("initial", bulkEntries.get(0).getNotes());

        CountDownLatch queryLatch = new CountDownLatch(1);
        final List<EnvironmentEntry>[] holder = new List[1];
        repository.environmentEntriesForPlant(plant.getId(), entries -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            holder[0] = entries;
            queryLatch.countDown();
        });
        awaitLatch(queryLatch);
        assertEquals(1, holder[0].size());

        entry.setNotes("updated");
        CountDownLatch updateLatch = new CountDownLatch(1);
        repository.updateEnvironmentEntry(entry, entry.getPhotoUri(), () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            updateLatch.countDown();
        });
        awaitLatch(updateLatch);

        CountDownLatch queryLatch2 = new CountDownLatch(1);
        repository.environmentEntriesForPlant(plant.getId(), entries -> {
            assertEquals("updated", entries.get(0).getNotes());
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            queryLatch2.countDown();
        });
        awaitLatch(queryLatch2);

        CountDownLatch deleteLatch = new CountDownLatch(1);
        repository.deleteEnvironmentEntry(entry, () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            deleteLatch.countDown();
        });
        awaitLatch(deleteLatch);

        assertTrue(repository.bulkDao().getAllEnvironmentEntries().isEmpty());

        CountDownLatch queryLatch3 = new CountDownLatch(1);
        repository.environmentEntriesForPlant(plant.getId(), entries -> {
            assertTrue(entries.isEmpty());
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            queryLatch3.countDown();
        });
        awaitLatch(queryLatch3);
    }

    @Test
    public void environmentEntryPhotoLifecycle() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Plant plant = new Plant();
        plant.setName("PhotoPlant");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        File source = File.createTempFile("env_source", ".jpg", context.getCacheDir());
        try (FileOutputStream out = new FileOutputStream(source)) {
            out.write(new byte[]{0, 1, 2});
        }

        EnvironmentEntry entry = new EnvironmentEntry(plant.getId(), 10L, null, null, null,
            null, null, "with photo", Uri.fromFile(source).toString());
        CountDownLatch insertLatch = new CountDownLatch(1);
        repository.insertEnvironmentEntry(entry, insertLatch::countDown);
        awaitLatch(insertLatch);

        String storedPhoto = entry.getPhotoUri();
        assertNotNull(storedPhoto);
        assertNotEquals(Uri.fromFile(source).toString(), storedPhoto);
        File storedFile = new File(Uri.parse(storedPhoto).getPath());
        assertTrue(storedFile.exists());

        File secondSource = File.createTempFile("env_second", ".jpg", context.getCacheDir());
        try (FileOutputStream out = new FileOutputStream(secondSource)) {
            out.write(new byte[]{3, 4, 5});
        }

        String previous = storedPhoto;
        entry.setPhotoUri(Uri.fromFile(secondSource).toString());
        entry.setNotes("updated photo");
        CountDownLatch updateLatch = new CountDownLatch(1);
        repository.updateEnvironmentEntry(entry, previous, updateLatch::countDown);
        awaitLatch(updateLatch);

        String updatedPhoto = entry.getPhotoUri();
        assertNotNull(updatedPhoto);
        assertNotEquals(previous, updatedPhoto);
        File updatedFile = new File(Uri.parse(updatedPhoto).getPath());
        assertTrue(updatedFile.exists());
        assertFalse(new File(Uri.parse(previous).getPath()).exists());

        CountDownLatch deleteLatch = new CountDownLatch(1);
        repository.deleteEnvironmentEntry(entry, deleteLatch::countDown);
        awaitLatch(deleteLatch);
        assertFalse(updatedFile.exists());
    }

    @Test
    public void environmentEntriesAreSortedByTimestampAndId() throws Exception {
        Plant plant = new Plant();
        plant.setName("Order");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        EnvironmentEntry oldest = new EnvironmentEntry(plant.getId(), 1_000L, null, null,
            null, null, null, "oldest", null);
        EnvironmentEntry newer = new EnvironmentEntry(plant.getId(), 5_000L, null, null,
            null, null, null, "newer", null);
        EnvironmentEntry latest = new EnvironmentEntry(plant.getId(), 5_000L, null, null,
            null, null, null, "latest", null);

        CountDownLatch insertLatch = new CountDownLatch(3);
        repository.insertEnvironmentEntry(oldest, insertLatch::countDown);
        repository.insertEnvironmentEntry(newer, insertLatch::countDown);
        repository.insertEnvironmentEntry(latest, insertLatch::countDown);
        awaitLatch(insertLatch);

        CountDownLatch queryLatch = new CountDownLatch(1);
        final List<EnvironmentEntry>[] holder = new List[1];
        repository.environmentEntriesForPlant(plant.getId(), entries -> {
            holder[0] = entries;
            queryLatch.countDown();
        });
        awaitLatch(queryLatch);

        assertEquals(3, holder[0].size());
        assertEquals("latest", holder[0].get(0).getNotes());
        assertEquals("newer", holder[0].get(1).getNotes());
        assertEquals("oldest", holder[0].get(2).getNotes());
        assertTrue(holder[0].get(0).getId() > holder[0].get(1).getId());
        assertTrue(holder[0].get(1).getTimestamp() >= holder[0].get(2).getTimestamp());
    }

    @Test
    public void insertErrorPropagatesToCallback() throws Exception {
        Plant plant = new Plant();
        plant.setAcquiredAtEpoch(0L);
        db.close();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        repository.insert(plant, () -> fail("should not succeed"), e -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            errorCalled.set(true);
            latch.countDown();
        });
        awaitLatch(latch);
        assertTrue(errorCalled.get());
    }

    @Test
    public void getAllPlantsErrorPropagatesToCallback() throws Exception {
        db.close();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        repository.getAllPlants(plants -> fail("should not succeed"), e -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            errorCalled.set(true);
            latch.countDown();
        });
        awaitLatch(latch);
        assertTrue(errorCalled.get());
    }

    @Test
    public void searchPlantsErrorPropagatesToCallback() throws Exception {
        db.close();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        repository.searchPlants("a", plants -> fail("should not succeed"), e -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            errorCalled.set(true);
            latch.countDown();
        });
        awaitLatch(latch);
        assertTrue(errorCalled.get());
    }

    @Test
    public void ppfdAggregationMatchesManualComputation() throws Exception {
        Plant plant = new Plant();
        plant.setName("DLI");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        long dayMillis = 86400000L;
        Measurement m1 = new Measurement(plant.getId(), 0L, 0f, 12f, 1f, null);
        Measurement m2 = new Measurement(plant.getId(), dayMillis, 0f, 6f, 1f, null);
        CountDownLatch insertLatch = new CountDownLatch(2);
        repository.insertMeasurement(m1, insertLatch::countDown);
        repository.insertMeasurement(m2, insertLatch::countDown);
        awaitLatch(insertLatch);

        float[] sumHolder = new float[1];
        CountDownLatch sumLatch = new CountDownLatch(1);
        repository.sumPpfdForRange(plant.getId(), 0L, dayMillis * 2, value -> {
            sumHolder[0] = value;
            sumLatch.countDown();
        });
        awaitLatch(sumLatch);

        int[] countHolder = new int[1];
        CountDownLatch countLatch = new CountDownLatch(1);
        repository.countDaysWithData(plant.getId(), 0L, dayMillis * 2, value -> {
            countHolder[0] = value;
            countLatch.countDown();
        });
        awaitLatch(countLatch);

        assertEquals(18f, sumHolder[0], 0.001f);
        assertEquals(2, countHolder[0]);
        assertEquals(9f, sumHolder[0] / countHolder[0], 0.001f);
    }

    @Test
    public void diaryEntryOperations() throws Exception {
        Plant plant = new Plant();
        plant.setName("Cactus");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        DiaryEntry entry = new DiaryEntry(plant.getId(), 1L, DiaryEntry.TYPE_WATER, "note");
        CountDownLatch insertLatch = new CountDownLatch(1);
        repository.insertDiaryEntry(entry, () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            insertLatch.countDown();
        });
        awaitLatch(insertLatch);

        CountDownLatch queryLatch = new CountDownLatch(1);
        final List<DiaryEntry>[] holder = new List[1];
        repository.diaryEntriesForPlant(plant.getId(), entries -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            holder[0] = entries;
            queryLatch.countDown();
        });
        awaitLatch(queryLatch);
        assertEquals(1, holder[0].size());

        entry.setNote("updated");
        CountDownLatch updateLatch = new CountDownLatch(1);
        repository.updateDiaryEntry(entry, () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            updateLatch.countDown();
        });
        awaitLatch(updateLatch);

        CountDownLatch queryLatch2 = new CountDownLatch(1);
        repository.diaryEntriesForPlant(plant.getId(), entries -> {
            assertEquals("updated", entries.get(0).getNote());
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            queryLatch2.countDown();
        });
        awaitLatch(queryLatch2);

        CountDownLatch deleteLatch = new CountDownLatch(1);
        repository.deleteDiaryEntry(entry, () -> {
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            deleteLatch.countDown();
        });
        awaitLatch(deleteLatch);

        CountDownLatch queryLatch3 = new CountDownLatch(1);
        repository.diaryEntriesForPlant(plant.getId(), entries -> {
            assertTrue(entries.isEmpty());
            assertSame(Looper.getMainLooper().getThread(), Thread.currentThread());
            queryLatch3.countDown();
        });
        awaitLatch(queryLatch3);
    }

    @Test
    public void deletePlantRemovesPhoto() throws Exception {
        Plant plant = new Plant();
        plant.setName("Photo");
        plant.setAcquiredAtEpoch(0L);
        File image = File.createTempFile("plant", ".jpg");
        plant.setPhotoUri(Uri.fromFile(image));
        CountDownLatch insertLatch = new CountDownLatch(1);
        repository.insert(plant, insertLatch::countDown);
        awaitLatch(insertLatch);

        assertTrue(image.exists());
        CountDownLatch deleteLatch = new CountDownLatch(1);
        repository.delete(plant, deleteLatch::countDown);
        awaitLatch(deleteLatch);
        assertFalse(image.exists());
    }

    @Test
    public void deleteDiaryEntryRemovesPhoto() throws Exception {
        Plant plant = new Plant();
        plant.setName("Diary");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        File image = File.createTempFile("entry", ".jpg");
        DiaryEntry entry = new DiaryEntry(plant.getId(), 1L, DiaryEntry.TYPE_WATER, "note");
        entry.setPhotoUri(Uri.fromFile(image).toString());
        CountDownLatch insertLatch = new CountDownLatch(1);
        repository.insertDiaryEntry(entry, insertLatch::countDown);
        awaitLatch(insertLatch);

        assertTrue(image.exists());
        CountDownLatch deleteLatch = new CountDownLatch(1);
        repository.deleteDiaryEntry(entry, deleteLatch::countDown);
        awaitLatch(deleteLatch);
        assertFalse(image.exists());
    }

    /**
     * Deleting a plant cancels and removes all associated reminders.
     */
    @Test
    public void deletePlantCancelsReminders() throws Exception {
        ShadowAlarmManager.reset();
        Context context = ApplicationProvider.getApplicationContext();

        Plant plant = new Plant();
        plant.setName("Reminder");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        long triggerAt = System.currentTimeMillis() + 1000;
        Reminder reminder = new Reminder(triggerAt, "Water", plant.getId());
        CountDownLatch reminderLatch = new CountDownLatch(1);
        repository.insertReminder(reminder, reminderLatch::countDown);
        awaitLatch(reminderLatch);

        ReminderScheduler.scheduleReminderAt(context, triggerAt, "Water", reminder.getId(), plant.getId());

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAm = Shadows.shadowOf(am);
        assertNotNull("Alarm scheduled", shadowAm.getNextScheduledAlarm());

        CountDownLatch deleteLatch = new CountDownLatch(1);
        repository.delete(plant, deleteLatch::countDown);
        awaitLatch(deleteLatch);

        CountDownLatch verifyLatch = new CountDownLatch(1);
        repository.getAllReminders(reminders -> {
            assertTrue("Reminders removed from DB", reminders.isEmpty());
            verifyLatch.countDown();
        }, e -> fail("error callback"));
        awaitLatch(verifyLatch);
        assertNull("Alarm cancelled", shadowAm.getNextScheduledAlarm());
    }

    @Test
    public void deletePlantWaitsForIoBeforeCallback() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch ioStarted = new CountDownLatch(1);
        CountDownLatch allowIo = new CountDownLatch(1);
        BlockingExecutor blockingExecutor = new BlockingExecutor(ioStarted, allowIo);
        PlantRepository blockingRepo = new PlantRepository(context, blockingExecutor);

        Plant plant = new Plant();
        plant.setName("Async");
        plant.setAcquiredAtEpoch(0L);
        File image = File.createTempFile("async", ".jpg");
        plant.setPhotoUri(Uri.fromFile(image));

        CountDownLatch insertLatch = new CountDownLatch(1);
        blockingRepo.insert(plant, insertLatch::countDown);
        awaitLatch(insertLatch);

        CountDownLatch deleteLatch = new CountDownLatch(1);
        blockingRepo.delete(plant, deleteLatch::countDown);

        try {
            assertTrue("IO work should be queued", ioStarted.await(2, TimeUnit.SECONDS));
            assertEquals("Callback must wait for IO to finish", 1, deleteLatch.getCount());

            allowIo.countDown();
            awaitLatch(deleteLatch);
        } finally {
            allowIo.countDown();
            blockingExecutor.shutdownNow();
            blockingExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void deleteDiaryEntryWaitsForIoBeforeCallback() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch ioStarted = new CountDownLatch(1);
        CountDownLatch allowIo = new CountDownLatch(1);
        BlockingExecutor blockingExecutor = new BlockingExecutor(ioStarted, allowIo);
        PlantRepository blockingRepo = new PlantRepository(context, blockingExecutor);

        Plant plant = new Plant();
        plant.setName("DiaryAsync");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        blockingRepo.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        File image = File.createTempFile("diary", ".jpg");
        DiaryEntry entry = new DiaryEntry(plant.getId(), 1L, DiaryEntry.TYPE_WATER, "note");
        entry.setPhotoUri(Uri.fromFile(image).toString());
        CountDownLatch entryLatch = new CountDownLatch(1);
        blockingRepo.insertDiaryEntry(entry, entryLatch::countDown);
        awaitLatch(entryLatch);

        CountDownLatch deleteLatch = new CountDownLatch(1);
        blockingRepo.deleteDiaryEntry(entry, deleteLatch::countDown);

        try {
            assertTrue("IO work should be queued", ioStarted.await(2, TimeUnit.SECONDS));
            assertEquals("Callback must wait for IO to finish", 1, deleteLatch.getCount());

            allowIo.countDown();
            awaitLatch(deleteLatch);
        } finally {
            allowIo.countDown();
            blockingExecutor.shutdownNow();
            blockingExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void calibrationOperations() throws Exception {
        Plant plant = new Plant();
        plant.setName("Fern");
        plant.setAcquiredAtEpoch(0L);
        CountDownLatch plantLatch = new CountDownLatch(1);
        repository.insert(plant, plantLatch::countDown);
        awaitLatch(plantLatch);

        CountDownLatch missingLatch = new CountDownLatch(1);
        final PlantCalibration[] initial = new PlantCalibration[1];
        repository.getPlantCalibration(plant.getId(), calibration -> {
            initial[0] = calibration;
            missingLatch.countDown();
        });
        awaitLatch(missingLatch);
        assertNull(initial[0]);

        CountDownLatch saveLatch = new CountDownLatch(1);
        repository.savePlantCalibration(plant.getId(), 0.02f, 0.03f, saveLatch::countDown);
        awaitLatch(saveLatch);

        CountDownLatch loadLatch = new CountDownLatch(1);
        final PlantCalibration[] holder = new PlantCalibration[1];
        repository.getPlantCalibration(plant.getId(), calibration -> {
            holder[0] = calibration;
            loadLatch.countDown();
        });
        awaitLatch(loadLatch);
        assertNotNull(holder[0]);
        assertEquals(plant.getId(), holder[0].getPlantId());
        assertEquals(0.02f, holder[0].getAmbientFactor(), 0.0001f);
        assertEquals(0.03f, holder[0].getCameraFactor(), 0.0001f);

        CountDownLatch updateLatch = new CountDownLatch(1);
        repository.savePlantCalibration(plant.getId(), 0.05f, 0.06f, updateLatch::countDown);
        awaitLatch(updateLatch);

        CountDownLatch reloadLatch = new CountDownLatch(1);
        final PlantCalibration[] updated = new PlantCalibration[1];
        repository.getPlantCalibration(plant.getId(), calibration -> {
            updated[0] = calibration;
            reloadLatch.countDown();
        });
        awaitLatch(reloadLatch);
        assertNotNull(updated[0]);
        assertEquals(0.05f, updated[0].getAmbientFactor(), 0.0001f);
        assertEquals(0.06f, updated[0].getCameraFactor(), 0.0001f);
    }

    private static final class BlockingExecutor extends AbstractExecutorService {
        private final ExecutorService delegate = Executors.newSingleThreadExecutor();
        private final CountDownLatch startLatch;
        private final CountDownLatch releaseLatch;
        private volatile boolean shutdown;

        BlockingExecutor(CountDownLatch startLatch, CountDownLatch releaseLatch) {
            this.startLatch = startLatch;
            this.releaseLatch = releaseLatch;
        }

        @Override
        public void shutdown() {
            shutdown = true;
            delegate.shutdown();
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(() -> {
                startLatch.countDown();
                try {
                    releaseLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                command.run();
            });
        }
    }
}
