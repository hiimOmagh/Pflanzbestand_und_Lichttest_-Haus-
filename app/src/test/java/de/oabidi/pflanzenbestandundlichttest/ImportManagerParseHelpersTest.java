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

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

@RunWith(RobolectricTestRunner.class)
@Config(application = PlantApp.class)
public class ImportManagerParseHelpersTest {
    private PlantDatabase db;
    private Context context;
    private ImportManager importer;
    private File baseDir;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, db);
        importer = new ImportManager(context);
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
            m.invoke(importer, new BufferedReader(new StringReader("Version,2")));
        assertEquals(ImportManager.ImportError.UNSUPPORTED_VERSION, err);

        err = (ImportManager.ImportError)
            m.invoke(importer, new BufferedReader(new StringReader("Version,1")));
        assertNull(err);
    }

    @Test
    public void parsePlantsMalformedRows() throws Exception {
        String csv = "Plants\n" +
            "id,name,description,species,location,acquired,photo\n" +
            "1,Valid,,,loc,0,\n" +
            "2\n" +
            "3,Valid,,species,loc,notnumber,\n" +
            "SpeciesTargets\n";
        BufferedReader reader = new BufferedReader(new StringReader(csv));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        List<Uri> uris = new ArrayList<>();
        AtomicInteger lineNumber = new AtomicInteger(1);
        Method m = ImportManager.class.getDeclaredMethod("parsePlants", BufferedReader.class,
            ImportManager.Mode.class, File.class, Map.class, List.class, AtomicInteger.class,
            List.class, PlantDatabase.class);
        m.setAccessible(true);
        boolean imported = (boolean) m.invoke(importer, reader, ImportManager.Mode.MERGE,
            baseDir, plantIdMap, warnings, lineNumber, uris, db);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("plants", warnings.get(0).category);
    }

    @Test
    public void parseSpeciesTargetsMalformedRows() throws Exception {
        String csv = "SpeciesTargets\n" +
            "key,ppfdMin,ppfdMax\n" +
            "species,10,20\n" +
            "bad,row\n" +
            "Measurements\n";
        BufferedReader reader = new BufferedReader(new StringReader(csv));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        AtomicInteger lineNumber = new AtomicInteger(1);
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        Method m = ImportManager.class.getDeclaredMethod("parseSpeciesTargets",
            BufferedReader.class, PlantDatabase.class, List.class, AtomicInteger.class,
            NumberFormat.class);
        m.setAccessible(true);
        boolean imported = (boolean) m.invoke(importer, reader, db, warnings, lineNumber, nf);
        assertTrue(imported);
        assertEquals(1, warnings.size());
        assertEquals("species targets", warnings.get(0).category);
    }

    @Test
    public void parseMeasurementsMalformedRows() throws Exception {
        String csv = "Measurements\n" +
            "id,plantId,timestamp,lux,ppfd,dli,note\n" +
            "0,1,0,0,0,0,note\n" +
            "bad,row\n" +
            "DiaryEntries\n";
        BufferedReader reader = new BufferedReader(new StringReader(csv));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        AtomicInteger lineNumber = new AtomicInteger(1);
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        Method m = ImportManager.class.getDeclaredMethod("parseMeasurements",
            BufferedReader.class, ImportManager.Mode.class, Map.class, List.class,
            AtomicInteger.class, NumberFormat.class, PlantDatabase.class);
        m.setAccessible(true);
        boolean imported = (boolean) m.invoke(importer, reader, ImportManager.Mode.MERGE,
            plantIdMap, warnings, lineNumber, nf, db);
        assertTrue(imported);
        assertEquals(1, warnings.size());
        assertEquals("measurements", warnings.get(0).category);
    }

    @Test
    public void parseDiaryEntriesMalformedRows() throws Exception {
        String csv = "DiaryEntries\n" +
            "id,plantId,timestamp,type,note,photo\n" +
            "0,1,0,type,note,\n" +
            "1,notid,0,type,note,\n" +
            "bad,row\n" +
            "Reminders\n";
        BufferedReader reader = new BufferedReader(new StringReader(csv));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        List<Uri> uris = new ArrayList<>();
        AtomicInteger lineNumber = new AtomicInteger(1);
        Method m = ImportManager.class.getDeclaredMethod("parseDiaryEntries", BufferedReader.class,
            ImportManager.Mode.class, File.class, Map.class, List.class, AtomicInteger.class,
            List.class, PlantDatabase.class);
        m.setAccessible(true);
        boolean imported = (boolean) m.invoke(importer, reader, ImportManager.Mode.MERGE,
            baseDir, plantIdMap, warnings, lineNumber, uris, db);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("diary entries", warnings.get(0).category);
    }

    @Test
    public void parseRemindersMalformedRows() throws Exception {
        String csv = "Reminders\n" +
            "id,plantId,triggerAt,message\n" +
            "1,1,0,hi\n" +
            "2,1,bad,hi\n" +
            "3,1,0\n";
        BufferedReader reader = new BufferedReader(new StringReader(csv));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        AtomicInteger lineNumber = new AtomicInteger(1);
        Method m = ImportManager.class.getDeclaredMethod("parseReminders", BufferedReader.class,
            ImportManager.Mode.class, Map.class, List.class, AtomicInteger.class,
            PlantDatabase.class);
        m.setAccessible(true);
        boolean imported = (boolean) m.invoke(importer, reader, ImportManager.Mode.MERGE,
            plantIdMap, warnings, lineNumber, db);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("reminders", warnings.get(0).category);
    }
}
