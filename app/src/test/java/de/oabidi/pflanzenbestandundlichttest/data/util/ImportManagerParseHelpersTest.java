package de.oabidi.pflanzenbestandundlichttest.data.util;

import static org.junit.Assert.*;

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
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.oabidi.pflanzenbestandundlichttest.ExecutorProvider;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.TestExecutorApp;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class ImportManagerParseHelpersTest {
    private static final String SPECIES_TARGETS_HEADER =
        "speciesKey,seedlingPpfdMin,seedlingPpfdMax,seedlingDliMin,seedlingDliMax,"
            + "vegetativePpfdMin,vegetativePpfdMax,vegetativeDliMin,vegetativeDliMax,"
            + "flowerPpfdMin,flowerPpfdMax,flowerDliMin,flowerDliMax,tolerance,source";
    private PlantDatabase db;
    private Context context;
    private ImportManager importer;
    private File baseDir;
    private ExecutorService executor;

    private NumberFormat newNumberFormat() {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        return nf;
    }

    private ImportManager.SectionContext newContext(ImportManager.Mode mode,
                                                    Map<Long, Long> plantIdMap,
                                                    List<ImportManager.ImportWarning> warnings,
                                                    List<Uri> restoredUris,
                                                    NumberFormat nf) {
        return new ImportManager.SectionContext(
            importer,
            mode,
            baseDir,
            plantIdMap,
            warnings,
            restoredUris,
            db,
            nf,
            2,
            new AtomicBoolean(false)
        );
    }

    private ImportManager.SectionReader newSectionReader(String csv) {
        return new ImportManager.SectionReader(
            new BufferedReader(new StringReader(csv)),
            new AtomicInteger(1)
        );
    }

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, db);
        executor = ((ExecutorProvider) context).getIoExecutor();
        importer = new ImportManager(context, executor);
        baseDir = new File(context.getCacheDir(), "import_test");
        baseDir.mkdirs();

        Plant existing = new Plant("existing", null, null, null, 0L, null);
        existing.setId(1);
        db.plantDao().insert(existing);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void validateVersionErrors() throws Exception {
        Method m = ImportManager.class.getDeclaredMethod("validateVersion", BufferedReader.class);
        m.setAccessible(true);

        ImportManager.ImportError err = (ImportManager.ImportError)
            m.invoke(importer, new BufferedReader(new StringReader("")));
        assertEquals(ImportManager.ImportError.MISSING_VERSION, err);

        err = (ImportManager.ImportError)
            m.invoke(importer, new BufferedReader(new StringReader("Version,abc")));
        assertEquals(ImportManager.ImportError.INVALID_VERSION, err);

        err = (ImportManager.ImportError)
            m.invoke(importer, new BufferedReader(new StringReader("Version,3")));
        assertEquals(ImportManager.ImportError.UNSUPPORTED_VERSION, err);

        err = (ImportManager.ImportError)
            m.invoke(importer, new BufferedReader(new StringReader("Version,2")));
        assertNull(err);

        err = (ImportManager.ImportError)
            m.invoke(importer, new BufferedReader(new StringReader("Version,1")));
        assertNull(err);
    }

    @Test
    public void readSectionMalformedHeader() throws Exception {
        Method m = ImportManager.class.getDeclaredMethod("readSection", String.class);
        m.setAccessible(true);
        try {
            m.invoke(importer, "UnknownSection");
            fail("Expected exception");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void stepProgressPostsUpdates() throws Exception {
        Method m = ImportManager.class.getDeclaredMethod("stepProgress", AtomicInteger.class,
            ImportManager.ProgressCallback.class, int.class);
        m.setAccessible(true);
        AtomicInteger progress = new AtomicInteger();
        List<int[]> updates = new ArrayList<>();
        ImportManager.ProgressCallback callback = (current, total) ->
            updates.add(new int[]{current, total});
        m.invoke(importer, progress, callback, 7);
        Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks();
        assertEquals(1, updates.size());
        assertEquals(1, updates.get(0)[0]);
        assertEquals(7, updates.get(0)[1]);
    }

    @Test
    public void stepProgressMonotonicWithCustomDeltas() throws Exception {
        Method m = ImportManager.class.getDeclaredMethod("stepProgress", AtomicInteger.class,
            ImportManager.ProgressCallback.class, int.class, int.class);
        m.setAccessible(true);
        AtomicInteger progress = new AtomicInteger();
        List<Integer> updates = new ArrayList<>();
        ImportManager.ProgressCallback callback = (current, total) -> updates.add(current);
        m.invoke(importer, progress, callback, 10, 3);
        m.invoke(importer, progress, callback, 10, 0);
        m.invoke(importer, progress, callback, 10, 4);
        m.invoke(importer, progress, callback, 10, 10);
        m.invoke(importer, progress, callback, 10, -5);
        Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks();
        assertEquals(Arrays.asList(3, 7, 10), updates);
        for (int i = 1; i < updates.size(); i++) {
            assertTrue(updates.get(i) >= updates.get(i - 1));
        }
    }

    @Test
    public void coordinatorEmitsProgressPerSection() throws Exception {
        String csv = "Plants\n" +
            "id,name,description,species,location,acquired,photo\n" +
            "1,Plant,,,loc,0,\n" +
            "PlantPhotos\n" +
            "id,plantId,uri,createdAt\n" +
            "PlantCalibrations\n" +
            "plantId,ambientFactor,cameraFactor\n" +
            "1,0.02,0.03\n" +
            "SpeciesTargets\n" +
            SPECIES_TARGETS_HEADER + "\n" +
            "species,5,10,1,2,5,10,3,4,5,10,5,6,,\n" +
            "Measurements\n" +
            "id,plantId,timestamp,lux,ppfd,dli,note\n" +
            "1,1,0,10,1,1,note\n" +
            "DiaryEntries\n" +
            "id,plantId,timestamp,type,note,photo\n" +
            "1,1,0,type,note,\n" +
            "Reminders\n" +
            "id,plantId,triggerAt,message\n" +
            "1,1,0,hi\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        List<ImportManager.SectionParser> parsers = Arrays.asList(
            new de.oabidi.pflanzenbestandundlichttest.data.util.PlantsSectionParser(),
            new de.oabidi.pflanzenbestandundlichttest.data.util.PlantPhotosSectionParser(),
            new de.oabidi.pflanzenbestandundlichttest.data.util.PlantCalibrationsSectionParser(),
            new de.oabidi.pflanzenbestandundlichttest.data.util.SpeciesTargetsSectionParser(),
            new de.oabidi.pflanzenbestandundlichttest.data.util.MeasurementsSectionParser(),
            new de.oabidi.pflanzenbestandundlichttest.data.util.DiaryEntriesSectionParser(),
            new de.oabidi.pflanzenbestandundlichttest.data.util.RemindersSectionParser()
        );
        Map<Long, Long> plantIdMap = new HashMap<>();
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        List<Uri> uris = new ArrayList<>();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.REPLACE,
            plantIdMap, warnings, uris, newNumberFormat());
        AtomicInteger progress = new AtomicInteger();
        AtomicInteger totalSteps = new AtomicInteger(7);
        List<Integer> updates = new ArrayList<>();
        ImportManager.ProgressCallback callback = (current, total) -> updates.add(current);
        de.oabidi.pflanzenbestandundlichttest.data.util.SectionCoordinator coordinator = new de.oabidi.pflanzenbestandundlichttest.data.util.SectionCoordinator(
            importer, sectionReader, parsers, context, progress, callback, totalSteps, context.cancelled);
        assertTrue(coordinator.process());
        Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks();
        List<Integer> expectedProgress = Arrays.asList(0, 1, 2, 3, 4, 5, 6);
        assertEquals(
            "Empty sections like PlantPhotos report zero work units, so only six positive "
                + "steps follow the initial publish.",
            expectedProgress,
            updates
        );
    }
}
