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
 * Tests for {@link PlantRepository} verifying main-thread callbacks and data retrieval using
 * an in-memory Room database.
 */
@RunWith(RobolectricTestRunner.class)
public class PlantRepositoryTest {
    private PlantDatabase database;
    private PlantRepository repository;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        // Inject in-memory DB into singleton
        Field field = PlantDatabase.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, database);
        repository = new PlantRepository(context);
    }

    @After
    public void tearDown() throws Exception {
        database.close();
        Field field = PlantDatabase.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, null);
    }

    private void waitForExecutor() throws Exception {
        PlantDatabase.databaseWriteExecutor.submit(() -> {}).get();
    }

    @Test
    public void getAllPlants_callbackOnMainThread() throws Exception {
        Plant plant = new Plant("Aloe", null, null, null, 0, null);
        repository.insert(plant, null);
        waitForExecutor();

        CountDownLatch latch = new CountDownLatch(1);
        repository.getAllPlants(plants -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            assertEquals(1, plants.size());
            latch.countDown();
        });
        waitForExecutor();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void insert_callbackOnMainThread() throws Exception {
        Plant plant = new Plant("Basil", null, null, null, 0, null);
        CountDownLatch latch = new CountDownLatch(1);
        repository.insert(plant, () -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            latch.countDown();
        });
        waitForExecutor();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(plant.getId() > 0);
    }

    @Test
    public void update_callbackOnMainThread() throws Exception {
        Plant plant = new Plant("Mint", null, null, null, 0, null);
        repository.insert(plant, null);
        waitForExecutor();

        plant.setName("Peppermint");
        CountDownLatch latch = new CountDownLatch(1);
        repository.update(plant, () -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            latch.countDown();
        });
        waitForExecutor();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        List<Plant> plants = database.plantDao().getAll();
        assertEquals("Peppermint", plants.get(0).getName());
    }

    @Test
    public void delete_callbackOnMainThread() throws Exception {
        Plant plant = new Plant("Rose", null, null, null, 0, null);
        repository.insert(plant, null);
        waitForExecutor();

        CountDownLatch latch = new CountDownLatch(1);
        repository.delete(plant, () -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            latch.countDown();
        });
        waitForExecutor();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(database.plantDao().getAll().isEmpty());
    }

    @Test
    public void insertMeasurement_and_retrieve() throws Exception {
        Plant plant = new Plant("Cactus", null, null, null, 0, null);
        repository.insert(plant, null);
        waitForExecutor();

        Measurement measurement = new Measurement(plant.getId(), 123L, 100f, 10f, 1f);
        CountDownLatch insertLatch = new CountDownLatch(1);
        repository.insertMeasurement(measurement, () -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            insertLatch.countDown();
        });
        waitForExecutor();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(insertLatch.await(1, TimeUnit.SECONDS));

        CountDownLatch latch = new CountDownLatch(1);
        repository.recentMeasurementsForPlant(plant.getId(), 10, list -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            assertEquals(1, list.size());
            assertEquals(100f, list.get(0).getLuxAvg(), 0.0f);
            latch.countDown();
        });
        waitForExecutor();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void diaryEntriesForPlant_returnsEntries() throws Exception {
        Plant plant = new Plant("Fern", null, null, null, 0, null);
        repository.insert(plant, null);
        waitForExecutor();

        DiaryEntry entry = new DiaryEntry(plant.getId(), 456L, DiaryEntry.TYPE_WATER, "note");
        repository.insertDiaryEntry(entry, null);
        waitForExecutor();

        CountDownLatch latch = new CountDownLatch(1);
        repository.diaryEntriesForPlant(plant.getId(), entries -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            assertEquals(1, entries.size());
            assertEquals(DiaryEntry.TYPE_WATER, entries.get(0).getType());
            latch.countDown();
        });
        waitForExecutor();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}
