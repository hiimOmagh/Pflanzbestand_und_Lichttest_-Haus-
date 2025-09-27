package de.oabidi.pflanzenbestandundlichttest.data.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.oabidi.pflanzenbestandundlichttest.ExecutorProvider;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.TestExecutorApp;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestion;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class SectionParserTest {
    private static final String SPECIES_TARGETS_HEADER =
        "speciesKey,seedlingPpfdMin,seedlingPpfdMax,seedlingDliMin,seedlingDliMax,"
            + "vegetativePpfdMin,vegetativePpfdMax,vegetativeDliMin,vegetativeDliMax,"
            + "flowerPpfdMin,flowerPpfdMax,flowerDliMin,flowerDliMax,tolerance,source";
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
            SPECIES_TARGETS_HEADER + "\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.PLANTS, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        List<Uri> uris = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.PlantsSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, uris, newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("plants", warnings.get(0).category);
    }

    @Test
    public void speciesTargetsParserMalformedRows() throws Exception {
        String csv = "SpeciesTargets\n" +
            SPECIES_TARGETS_HEADER + "\n" +
            "species,10,20,,,,10,20,,,,10,20,,,,note,src\n" +
            "bad,row\n" +
            "Measurements\n" +
            "id,plantId,timestamp,lux,ppfd,dli,note\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.SPECIES_TARGETS, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.SpeciesTargetsSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            new HashMap<>(), warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
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
            SPECIES_TARGETS_HEADER + "\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.PLANT_PHOTOS, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.PlantPhotosSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.REPLACE,
            new HashMap<>(), warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
        assertFalse(imported);
        assertEquals(2, warnings.size());
        assertEquals("plant photos", warnings.get(0).category);
    }

    @Test
    public void plantCalibrationsParserValidatesRows() throws Exception {
        String csv = "PlantCalibrations\n" +
            "plantId,ambientFactor,cameraFactor\n" +
            "1,0.02,0.03\n" +
            "2,invalid,0.03\n" +
            "3,0.04,\n" +
            "SpeciesTargets\n" +
            SPECIES_TARGETS_HEADER + "\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.PLANT_CALIBRATIONS, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.PlantCalibrationsSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("calibrations", warnings.get(0).category);
        List<PlantCalibration> calibrations = db.plantCalibrationDao().getAll();
        assertEquals(1, calibrations.size());
        assertEquals(0.02f, calibrations.get(0).getAmbientFactor(), 0.0001f);
        assertEquals(0.03f, calibrations.get(0).getCameraFactor(), 0.0001f);
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
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.MEASUREMENTS, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.MeasurementsSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
        assertTrue(imported);
        assertEquals(1, warnings.size());
        assertEquals("measurements", warnings.get(0).category);
    }

    @Test
    public void environmentEntriesParserMalformedRows() throws Exception {
        String csv = "EnvironmentEntries\n" +
            "id,plantId,timestamp,temperature,humidity,soilMoisture,height,width,naturalDli,notes,photo\n" +
            "0,1,0,21.5,40,0.5,10,5,2.4,note,\n" +
            "1,missing,0,,,,,,,note,\n" +
            "bad,row\n" +
            "Reminders\n" +
            "id,plantId,triggerAt,message\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.ENVIRONMENT_ENTRIES, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.EnvironmentEntriesSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("environment entries", warnings.get(0).category);
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
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.DIARY_ENTRIES, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        List<Uri> uris = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.DiaryEntriesSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, uris, newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
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
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.REMINDERS, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> plantIdMap = new HashMap<>();
        plantIdMap.put(1L, 1L);
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.RemindersSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.MERGE,
            plantIdMap, warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
        assertTrue(imported);
        assertEquals(2, warnings.size());
        assertEquals("reminders", warnings.get(0).category);
    }

    @Test
    public void reminderSuggestionsParserImportsRows() throws Exception {
        String csv = "ReminderSuggestions\n" +
            "plantId,suggestedIntervalDays,lastEvaluatedAt,confidenceScore,explanation\n" +
            "1,6,12345,0.75,Needs water soon\n" +
            "1,invalid,12346,0.5,\n";
        ImportManager.SectionReader sectionReader = newSectionReader(csv);
        ImportManager.SectionChunk chunk = sectionReader.nextSectionChunk(importer);
        assertNotNull(chunk);
        assertEquals(ImportManager.Section.REMINDER_SUGGESTIONS, chunk.getSection());
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        ImportManager.SectionParser parser = new de.oabidi.pflanzenbestandundlichttest.data.util.ReminderSuggestionsSectionParser();
        ImportManager.SectionContext context = newContext(ImportManager.Mode.REPLACE,
            new HashMap<>(), warnings, new ArrayList<>(), newNumberFormat());
        boolean imported = parser.parseSection(chunk, context);
        assertTrue(imported);
        assertEquals(1, warnings.size());
        assertEquals("reminder suggestions", warnings.get(0).category);
        List<ReminderSuggestion> suggestions = db.reminderSuggestionDao().getAll();
        assertEquals(1, suggestions.size());
        ReminderSuggestion suggestion = suggestions.get(0);
        assertEquals(1, suggestion.getPlantId());
        assertEquals(6, suggestion.getSuggestedIntervalDays());
        assertEquals(12345L, suggestion.getLastEvaluatedAt());
        assertEquals(0.75f, suggestion.getConfidenceScore(), 0.0001f);
        assertEquals("Needs water soon", suggestion.getExplanation());
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
            nf,
            4,
            new AtomicBoolean(false)
        );
    }

    private NumberFormat newNumberFormat() {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        return nf;
    }
}
