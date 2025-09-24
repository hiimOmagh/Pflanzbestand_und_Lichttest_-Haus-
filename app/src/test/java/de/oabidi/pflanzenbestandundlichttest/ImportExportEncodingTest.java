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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class ImportExportEncodingTest {
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

    private Charset forceDefaultCharset(Charset cs) throws Exception {
        Field f = Charset.class.getDeclaredField("defaultCharset");
        f.setAccessible(true);
        Charset previous = (Charset) f.get(null);
        System.setProperty("file.encoding", cs.name());
        f.set(null, null);
        return previous;
    }

    @Test
    public void roundTripWithNonUtf8Default() throws Exception {
        Charset original = forceDefaultCharset(StandardCharsets.US_ASCII);
        try {
            SpeciesTarget.StageTarget seedling = new SpeciesTarget.StageTarget(90f, 150f, 3.9f, 6.5f);
            SpeciesTarget.StageTarget vegetative = new SpeciesTarget.StageTarget(110f, 210f, 4.7f, 9.1f);
            SpeciesTarget.StageTarget flower = new SpeciesTarget.StageTarget(130f, 230f, 5.6f, 9.9f);
            SpeciesTarget target = new SpeciesTarget("roundTrip€", seedling, vegetative, flower, "médiocre", "source€");
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
            importer.importData(Uri.fromFile(exportFile), ImportManager.Mode.REPLACE,
                (success, err, w, message) -> {
                    importSuccess[0] = success;
                    warnings[0] = w;
                    importLatch.countDown();
                });
            assertTrue(importLatch.await(10, TimeUnit.SECONDS));
            assertTrue(importSuccess[0]);
            assertTrue(warnings[0] == null || warnings[0].isEmpty());

            SpeciesTarget loaded = db.speciesTargetDao().findBySpeciesKey("roundTrip€");
            assertNotNull(loaded);
            assertNotNull(loaded);
            assertEquals("médiocre", loaded.getTolerance());
            assertEquals("source€", loaded.getSource());
            assertEquals(90f, loaded.getSeedlingStage().getPpfdMin(), 0.001f);
            assertEquals(150f, loaded.getSeedlingStage().getPpfdMax(), 0.001f);
            assertEquals(3.9f, loaded.getSeedlingStage().getDliMin(), 0.001f);
            assertEquals(6.5f, loaded.getSeedlingStage().getDliMax(), 0.001f);
            assertEquals(110f, loaded.getVegetativeStage().getPpfdMin(), 0.001f);
            assertEquals(210f, loaded.getVegetativeStage().getPpfdMax(), 0.001f);
            assertEquals(4.7f, loaded.getVegetativeStage().getDliMin(), 0.001f);
            assertEquals(9.1f, loaded.getVegetativeStage().getDliMax(), 0.001f);
            assertEquals(130f, loaded.getFlowerStage().getPpfdMin(), 0.001f);
            assertEquals(230f, loaded.getFlowerStage().getPpfdMax(), 0.001f);
            assertEquals(5.6f, loaded.getFlowerStage().getDliMin(), 0.001f);
            assertEquals(9.9f, loaded.getFlowerStage().getDliMax(), 0.001f);
        } finally {
            forceDefaultCharset(original);
        }
    }
}
