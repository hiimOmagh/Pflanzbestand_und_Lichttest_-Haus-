package de.oabidi.pflanzenbestandundlichttest.data.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import de.oabidi.pflanzenbestandundlichttest.ExecutorProvider;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.TestExecutorApp;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class SectionParserTest {
    private PlantDatabase db;
    private Context context;
    private ImportManager importer;
    private File baseDir;
    private ExecutorService executor;

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
        baseDir = new File(context.getCacheDir(), "section_parser_test");
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
    public void plantsParserMalformedRows() throws Exception {
        String csv = "Plants\n" +
            "id,name,description,species,location,acquired,photo\n" +
            "1,Valid,,,loc,0,\n" +
            "2\n" +
            "3,Valid,,species,loc,notnumber,\n" +
            "SpeciesTargets\n" +
            "key,ppfdMin,ppfdMax\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        assertEquals(ImportManager.Section.PLANTS, sectionReader.nextSection(importer));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        List<Uri> uris = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.PlantsSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, uris, newNumberFormat());
        boolean imported = parser.parseSection(sectionReader, context);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("plants", warnings.get(0).category);
    }

    @Test
    public void speciesTargetsParserMalformedRows() throws Exception {
        String csv = "SpeciesTargets\n" +
            "key,ppfdMin,ppfdMax\n" +
            "species,10,20\n" +
            "bad,row\n" +
            "Measurements\n" +
            "id,plantId,timestamp,lux,ppfd,dli,note\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        assertEquals(ImportManager.Section.SPECIES_TARGETS, sectionReader.nextSection(importer));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.SpeciesTargetsSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            new HashMap<>(), warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(sectionReader, context);
        assertTrue(imported);
        assertEquals(1, warnings.size());
        assertEquals("species targets", warnings.get(0).category);
    }

    @Test
    public void plantPhotosParserReportsWarnings() throws Exception {
        String csv = "PlantPhotos\n" +
            "id,plantId,uri,createdAt\n" +
            "1,1,missing.jpg,0\n" +
            "2,notid,photo.jpg,0\n" +
            "SpeciesTargets\n" +
            "key,ppfdMin,ppfdMax\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        assertEquals(ImportManager.Section.PLANT_PHOTOS, sectionReader.nextSection(importer));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.PlantPhotosSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.REPLACE,
            new HashMap<>(), warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(sectionReader, context);
        assertFalse(imported);
        assertEquals(2, warnings.size());
        assertEquals("plant photos", warnings.get(0).category);
    }

    @Test
    public void measurementsParserMalformedRows() throws Exception {
        String csv = "Measurements\n" +
            "id,plantId,timestamp,lux,ppfd,dli,note\n" +
            "0,1,0,0,0,0,note\n" +
            "bad,row\n" +
            "DiaryEntries\n" +
            "id,plantId,timestamp,type,note,photo\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        assertEquals(ImportManager.Section.MEASUREMENTS, sectionReader.nextSection(importer));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.MeasurementsSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(sectionReader, context);
        assertTrue(imported);
        assertEquals(1, warnings.size());
        assertEquals("measurements", warnings.get(0).category);
    }

    @Test
    public void diaryEntriesParserMalformedRows() throws Exception {
        String csv = "DiaryEntries\n" +
            "id,plantId,timestamp,type,note,photo\n" +
            "0,1,0,type,note,\n" +
            "1,notid,0,type,note,\n" +
            "bad,row\n" +
            "Reminders\n" +
            "id,plantId,triggerAt,message\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        assertEquals(ImportManager.Section.DIARY_ENTRIES, sectionReader.nextSection(importer));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        List<Uri> uris = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.DiaryEntriesSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, uris, newNumberFormat());
        boolean imported = parser.parseSection(sectionReader, context);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("diary entries", warnings.get(0).category);
    }

    @Test
    public void remindersParserMalformedRows() throws Exception {
        String csv = "Reminders\n" +
            "id,plantId,triggerAt,message\n" +
            "1,1,0,hi\n" +
            "2,1,bad,hi\n" +
            "3,1,0\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        assertEquals(ImportManager.Section.REMINDERS, sectionReader.nextSection(importer));
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.RemindersSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(sectionReader, context);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("reminders", warnings.get(0).category);
    }

    private ImportManager.SectionReader newSectionReader(String csv) {
        return new ImportManager.SectionReader(
            new BufferedReader(new StringReader(csv)),
            new AtomicInteger(1)
        );
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
            nf
        );
    }

    private NumberFormat newNumberFormat() {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        return nf;
    }
}
