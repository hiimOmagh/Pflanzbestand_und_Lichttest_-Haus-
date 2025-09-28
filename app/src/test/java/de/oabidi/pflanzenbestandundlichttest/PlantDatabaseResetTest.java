package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.Future;

import de.oabidi.pflanzenbestandundlichttest.core.data.db.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;

/**
 * Tests confirming that the database resets instead of performing incremental migrations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class PlantDatabaseResetTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase.resetInstanceForTesting(context);
    }

    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase.resetInstanceForTesting(context);
    }

    @Test
    public void resetInstanceForTesting_clearsExistingPlants() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase database = PlantDatabase.getDatabase(context);
        Plant plant = new Plant();
        plant.setName("Reset");
        plant.setAcquiredAtEpoch(0L);
        Future<?> insertFuture = PlantDatabase.databaseWriteExecutor.submit(() -> {
            database.plantDao().insert(plant);
        });
        insertFuture.get();

        PlantDatabase.resetInstanceForTesting(context);
        PlantDatabase newDatabase = PlantDatabase.getDatabase(context);
        Future<java.util.List<Plant>> listFuture = PlantDatabase.databaseWriteExecutor.submit(() -> newDatabase.plantDao().getAll());
        assertTrue(listFuture.get().isEmpty());
    }
}
