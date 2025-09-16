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
@Config(application = PlantApp.class)
public class ImportExportSpeciesTargetTest {
    private PlantDatabase db;
    private Context context;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        executor = PlantApp.from(context).getIoExecutor();
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
        SpeciesTarget target = new SpeciesTarget("roundTrip", 100f, 200f);
        db.speciesTargetDao().insert(target);

        File exportFile = new File(context.getCacheDir(), "export.zip");
        if (exportFile.exists()) {
            exportFile.delete();
        }
        PlantRepository repository = new PlantRepository(context);
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
        assertEquals(100f, loaded.getPpfdMin(), 0.001f);
        assertEquals(200f, loaded.getPpfdMax(), 0.001f);
    }
}
