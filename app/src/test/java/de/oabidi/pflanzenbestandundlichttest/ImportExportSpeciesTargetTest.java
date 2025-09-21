package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class ImportExportSpeciesTargetTest {
    private PlantDatabase db;
    private Context context;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        executor = ((ExecutorProvider) context).getIoExecutor();
        db = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, db);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void speciesTargetRoundTrip() throws Exception {
        SpeciesTarget.StageTarget seedling = new SpeciesTarget.StageTarget(80f, 120f, 3.5f, 5.2f);
        SpeciesTarget.StageTarget vegetative = new SpeciesTarget.StageTarget(100f, 220f, 4.3f, 9.5f);
        SpeciesTarget.StageTarget flower = new SpeciesTarget.StageTarget(120f, 240f, 5.2f, 10.4f);
        SpeciesTarget target = new SpeciesTarget("roundTrip", seedling, vegetative, flower, "moderate", "unit test");
        db.speciesTargetDao().insert(target);

        File exportFile = new File(context.getCacheDir(), "export.zip");
        if (exportFile.exists()) {
            exportFile.delete();
        }
        PlantRepository repository = new PlantRepository(context, executor);
        ExportManager exporter = new ExportManager(context, repository, executor);
        CountDownLatch exportLatch = new CountDownLatch(1);
        final boolean[] exportSuccess = {false};
        exporter.export(Uri.fromFile(exportFile), success -> {
            exportSuccess[0] = success;
            exportLatch.countDown();
        });
        assertTrue(exportLatch.await(10, TimeUnit.SECONDS));
        assertTrue(exportSuccess[0]);

        db.clearAllTables();

        ImportManager importer = new ImportManager(context, executor);
        CountDownLatch importLatch = new CountDownLatch(1);
        final boolean[] importSuccess = {false};
        final List<ImportManager.ImportWarning>[] warnings = new List[]{null};
        final String[] messageHolder = new String[1];
        importer.importData(Uri.fromFile(exportFile), ImportManager.Mode.REPLACE,
            (success, err, w, message) -> {
                importSuccess[0] = success;
                warnings[0] = w;
                messageHolder[0] = message;
                importLatch.countDown();
            });
        assertTrue(importLatch.await(10, TimeUnit.SECONDS));
        assertTrue(importSuccess[0]);
        assertTrue(warnings[0] == null || warnings[0].isEmpty());
        assertNotNull(messageHolder[0]);

        SpeciesTarget loaded = db.speciesTargetDao().findBySpeciesKey("roundTrip");
        assertNotNull(loaded);
        assertNotNull(loaded);
        SpeciesTarget.StageTarget loadedSeedling = loaded.getSeedlingStage();
        SpeciesTarget.StageTarget loadedVegetative = loaded.getVegetativeStage();
        SpeciesTarget.StageTarget loadedFlower = loaded.getFlowerStage();
        assertNotNull(loadedSeedling);
        assertNotNull(loadedVegetative);
        assertNotNull(loadedFlower);
        assertEquals(80f, loadedSeedling.getPpfdMin(), 0.001f);
        assertEquals(120f, loadedSeedling.getPpfdMax(), 0.001f);
        assertEquals(3.5f, loadedSeedling.getDliMin(), 0.001f);
        assertEquals(5.2f, loadedSeedling.getDliMax(), 0.001f);
        assertEquals(100f, loadedVegetative.getPpfdMin(), 0.001f);
        assertEquals(220f, loadedVegetative.getPpfdMax(), 0.001f);
        assertEquals(4.3f, loadedVegetative.getDliMin(), 0.001f);
        assertEquals(9.5f, loadedVegetative.getDliMax(), 0.001f);
        assertEquals(120f, loadedFlower.getPpfdMin(), 0.001f);
        assertEquals(240f, loadedFlower.getPpfdMax(), 0.001f);
        assertEquals(5.2f, loadedFlower.getDliMin(), 0.001f);
        assertEquals(10.4f, loadedFlower.getDliMax(), 0.001f);
        assertEquals("moderate", loaded.getTolerance());
        assertEquals("unit test", loaded.getSource());
    }
}
