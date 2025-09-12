package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.room.Room;

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
public class ImportManagerHelperTest {
    private PlantDatabase db;
    private Context context;

    @Before
    public void setup() throws Exception {
        context = ApplicationProvider.getApplicationContext();
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
    public void readSection_unknownLineReturnsNull() throws Exception {
        ImportManager importer = new ImportManager(context);
        BufferedReader reader = new BufferedReader(new StringReader("header1,header2\n"));
        AtomicInteger lineNumber = new AtomicInteger(0);
        AtomicInteger progress = new AtomicInteger(0);
        Method m = ImportManager.class.getDeclaredMethod("readSection", String.class,
            ImportManager.Section.class, BufferedReader.class, AtomicInteger.class,
            AtomicInteger.class, ImportManager.ProgressCallback.class, int.class);
        m.setAccessible(true);
        Object result = m.invoke(importer, "Unknown", ImportManager.Section.NONE,
            reader, lineNumber, progress, null, 5);
        assertNull(result);
        assertEquals(0, progress.get());
        assertEquals(0, lineNumber.get());
    }

    @Test
    public void parsePlantRow_missingFields() throws Exception {
        ImportManager importer = new ImportManager(context);
        List<String> parts = new ArrayList<>();
        parts.add("1");
        parts.add("OnlyName");
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Method m = ImportManager.class.getDeclaredMethod("parsePlantRow", List.class,
            ImportManager.Mode.class, File.class, Map.class, List.class, int.class,
            List.class, PlantDatabase.class);
        m.setAccessible(true);
        boolean inserted = (boolean) m.invoke(importer, parts, ImportManager.Mode.REPLACE,
            context.getCacheDir(), new HashMap<Long, Long>(), warnings, 1,
            new ArrayList<>(), db);
        assertFalse(inserted);
        assertEquals(1, warnings.size());
    }

    @Test
    public void insertMeasurementRow_invalidPlantId() throws Exception {
        ImportManager importer = new ImportManager(context);
        List<String> parts = new ArrayList<>();
        parts.add("0");
        parts.add("abc");
        parts.add("123");
        parts.add("1");
        parts.add("");
        parts.add("");
        parts.add("");
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Method m = ImportManager.class.getDeclaredMethod("insertMeasurementRow", List.class,
            ImportManager.Mode.class, Map.class, List.class, int.class,
            NumberFormat.class, PlantDatabase.class);
        m.setAccessible(true);
        boolean inserted = (boolean) m.invoke(importer, parts, ImportManager.Mode.REPLACE,
            new HashMap<Long, Long>(), warnings, 1,
            NumberFormat.getInstance(Locale.US), db);
        assertFalse(inserted);
        assertEquals("invalid plant id", warnings.get(0).reason);
    }
}
