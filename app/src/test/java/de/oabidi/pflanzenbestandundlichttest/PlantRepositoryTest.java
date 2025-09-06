package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link PlantRepository} verifying basic CRUD operations and
 * that callbacks are dispatched on the main thread.
 */
@RunWith(RobolectricTestRunner.class)
public class PlantRepositoryTest {
    private PlantRepository repository;
    private PlantDatabase db;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, db);
        repository = new PlantRepository(context);
    }

    @After
    public void tearDown() throws Exception {
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
        });
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
        });
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
        });
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

        Measurement measurement = new Measurement(plant.getId(), 1L, 2f, 3f, 4f);
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
}
