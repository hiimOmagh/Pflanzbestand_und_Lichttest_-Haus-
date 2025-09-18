package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.app.AlarmManager;
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
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class ImportManagerHelperTest {
    private PlantDatabase db;
    private Context context;
    private ExecutorService executor;

    @Before
    public void setup() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        executor = ((ExecutorProvider) context).getIoExecutor();
        db = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, db);
    }

    @Test
    public void importData_skipsMaliciousZipEntry() throws Exception {
        ImportManager importer = new ImportManager(context, executor);
        File evilFile = new File(context.getCacheDir(), "evil.txt");
        if (evilFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            evilFile.delete();
        }
        File zipFile = createImportArchive(true);
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] successHolder = {false};
        importer.importData(Uri.fromFile(zipFile), ImportManager.Mode.REPLACE,
            (success, err, warnings, message) -> {
                successHolder[0] = success;
                latch.countDown();
            });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(successHolder[0]);
        assertNotNull(db.plantDao().findById(1));
        assertFalse("Malicious file should not be created", evilFile.exists());
        //noinspection ResultOfMethodCallIgnored
        zipFile.delete();
    }

    @Test
    public void importData_importsBenignZipEntry() throws Exception {
        ImportManager importer = new ImportManager(context, executor);
        File zipFile = createImportArchive(false);
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] successHolder = {false};
        importer.importData(Uri.fromFile(zipFile), ImportManager.Mode.REPLACE,
            (success, err, warnings, message) -> {
                successHolder[0] = success;
                latch.countDown();
            });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(successHolder[0]);
        assertNotNull(db.plantDao().findById(1));
        //noinspection ResultOfMethodCallIgnored
        zipFile.delete();
    }

    @Test
    public void replaceModeSchedulesCleanupAfterTransaction() throws Exception {
        ShadowAlarmManager.reset();
        ImportManager importer = new ImportManager(context, executor);

        File photo = File.createTempFile("existing", ".jpg", context.getCacheDir());
        assertTrue(photo.exists());
        Plant existing = new Plant("Existing", null, null, null, 0L, Uri.fromFile(photo));
        long plantId = db.plantDao().insert(existing);
        existing.setId(plantId);

        long triggerAt = System.currentTimeMillis() + 1000L;
        Reminder reminder = new Reminder(triggerAt, "Water", plantId);
        long reminderId = db.reminderDao().insert(reminder);
        reminder.setId(reminderId);
        ReminderScheduler.scheduleReminderAt(context, triggerAt, reminder.getMessage(), reminderId, plantId);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        assertNotNull(shadowAlarmManager.getNextScheduledAlarm());

        File zipFile = createImportArchive(false);
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] successHolder = {false};
        final List<ImportManager.ImportWarning>[] warningsHolder = new List[]{null};
        importer.importData(Uri.fromFile(zipFile), ImportManager.Mode.REPLACE,
            (success, err, warnings, message) -> {
                successHolder[0] = success;
                warningsHolder[0] = warnings;
                latch.countDown();
            });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(successHolder[0]);
        assertNotNull(warningsHolder[0]);
        assertTrue(warningsHolder[0].isEmpty());
        assertFalse(photo.exists());
        assertNull(shadowAlarmManager.getNextScheduledAlarm());
        assertNotNull(db.plantDao().findById(1));
        //noinspection ResultOfMethodCallIgnored
        zipFile.delete();
    }

    @Test
    public void replaceModeKeepsExistingDataOnFailure() throws Exception {
        ShadowAlarmManager.reset();
        ImportManager importer = new ImportManager(context, executor);

        File photo = File.createTempFile("existing_error", ".jpg", context.getCacheDir());
        assertTrue(photo.exists());
        Plant existing = new Plant("Existing", null, null, null, 0L, Uri.fromFile(photo));
        long plantId = db.plantDao().insert(existing);
        existing.setId(plantId);

        long triggerAt = System.currentTimeMillis() + 1000L;
        Reminder reminder = new Reminder(triggerAt, "Water", plantId);
        long reminderId = db.reminderDao().insert(reminder);
        reminder.setId(reminderId);
        ReminderScheduler.scheduleReminderAt(context, triggerAt, reminder.getMessage(), reminderId, plantId);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        assertNotNull(shadowAlarmManager.getNextScheduledAlarm());

        File zipFile = createEmptyImportArchive();
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] successHolder = {true};
        final ImportManager.ImportError[] errorHolder = {null};
        final List<ImportManager.ImportWarning>[] warningsHolder = new List[]{null};
        importer.importData(Uri.fromFile(zipFile), ImportManager.Mode.REPLACE,
            (success, err, warnings, message) -> {
                successHolder[0] = success;
                errorHolder[0] = err;
                warningsHolder[0] = warnings;
                latch.countDown();
            });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(successHolder[0]);
        assertEquals(ImportManager.ImportError.NO_DATA, errorHolder[0]);
        assertNotNull(warningsHolder[0]);
        assertTrue(warningsHolder[0].isEmpty());
        assertTrue(photo.exists());
        shadowAlarmManager = Shadows.shadowOf(alarmManager);
        assertNotNull(shadowAlarmManager.getNextScheduledAlarm());
        assertNotNull(db.plantDao().findById(plantId));
        //noinspection ResultOfMethodCallIgnored
        zipFile.delete();
    }

    private File createImportArchive(boolean includeMalicious) throws Exception {
        File zipFile = File.createTempFile("import_test", ".zip", context.getCacheDir());
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("data.csv", buildBasicCsv().getBytes(StandardCharsets.UTF_8));
        if (includeMalicious) {
            entries.put("../evil.txt", "malicious".getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    private File createEmptyImportArchive() throws Exception {
        File zipFile = File.createTempFile("import_empty", ".zip", context.getCacheDir());
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("data.csv", buildEmptyCsv().getBytes(StandardCharsets.UTF_8));
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    private String buildBasicCsv() {
        return buildCsv(true);
    }

    private String buildEmptyCsv() {
        return buildCsv(false);
    }

    private String buildCsv(boolean includePlant) {
        StringBuilder builder = new StringBuilder();
        builder.append("Version,1\n\n")
            .append("Plants\n")
            .append("id,name,description,species,locationHint,acquiredAtEpoch,photoUri\n");
        if (includePlant) {
            builder.append("1,Test Plant,,,,0,\n");
        }
        builder.append("\nSpeciesTargets\n")
            .append("speciesKey,ppfdMin,ppfdMax\n")
            .append("\nMeasurements\n")
            .append("id,plantId,timeEpoch,luxAvg,ppfd\n")
            .append("\nDiaryEntries\n")
            .append("id,plantId,timeEpoch,type,note,photoUri\n")
            .append("\nReminders\n")
            .append("id,plantId,triggerAt,message\n");
        return builder.toString();
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
        ImportManager importer = new ImportManager(context, executor);
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
        ImportManager importer = new ImportManager(context, executor);
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
        ImportManager importer = new ImportManager(context, executor);
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

    @Test
    public void insertDiaryRow_invalidPlantId() throws Exception {
        ImportManager importer = new ImportManager(context, executor);
        List<String> parts = new ArrayList<>();
        parts.add("0");
        parts.add("abc");
        parts.add("123");
        parts.add("type");
        parts.add("note");
        parts.add("");
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Method m = ImportManager.class.getDeclaredMethod("insertDiaryRow", List.class,
            ImportManager.Mode.class, File.class, Map.class, List.class, int.class,
            List.class, PlantDatabase.class);
        m.setAccessible(true);
        boolean inserted = (boolean) m.invoke(importer, parts, ImportManager.Mode.REPLACE,
            context.getCacheDir(), new HashMap<Long, Long>(), warnings, 1,
            new ArrayList<Uri>(), db);
        assertFalse(inserted);
        assertEquals("invalid plant id", warnings.get(0).reason);
    }

    @Test
    public void insertReminderRow_invalidTimestamp() throws Exception {
        ImportManager importer = new ImportManager(context, executor);
        List<String> parts = new ArrayList<>();
        parts.add("0");
        parts.add("1");
        parts.add("notime");
        parts.add("msg");
        List<ImportManager.ImportWarning> warnings = new ArrayList<>();
        Map<Long, Long> map = new HashMap<>();
        map.put(1L, 1L);
        Method m = ImportManager.class.getDeclaredMethod("insertReminderRow", List.class,
            ImportManager.Mode.class, Map.class, List.class, int.class, PlantDatabase.class);
        m.setAccessible(true);
        boolean inserted = (boolean) m.invoke(importer, parts, ImportManager.Mode.MERGE,
            map, warnings, 1, db);
        assertFalse(inserted);
        assertEquals("invalid timestamp", warnings.get(0).reason);
    }
}
