package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class ExportManagerMethodsTest {
    private Context context;
    private ExecutorService executor;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        executor = ((ExecutorProvider) context).getIoExecutor();
    }

    @Test
    public void loadData_success() throws Exception {
        ExportManager mgr = new ExportManager(context, new StubRepository(context), executor);

        Method m = ExportManager.class.getDeclaredMethod("loadData", long.class);
        m.setAccessible(true);
        Object data = m.invoke(mgr, -1L);
        assertNotNull(data);
    }

    @Test(expected = IOException.class)
    public void loadData_failure() throws Throwable {
        ExportManager mgr = new ExportManager(context, new FailingRepository(context), executor);

        Method m = ExportManager.class.getDeclaredMethod("loadData", long.class);
        m.setAccessible(true);
        try {
            m.invoke(mgr, -1L);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw Objects.requireNonNull(e.getCause());
        }
    }

    @Test
    public void writeCsv_success() throws Exception {
        ExportManager mgr = new ExportManager(context, new StubRepository(context), executor);

        Class<?> dataClass = Class.forName("de.oabidi.pflanzenbestandundlichttest.ExportManager$ExportData");
        Constructor<?> ctor = dataClass.getDeclaredConstructor(java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class);
        ctor.setAccessible(true);
        Object data = ctor.newInstance(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        File dir = new File(context.getCacheDir(), "csvtest");
        dir.mkdirs();

        Method m = ExportManager.class.getDeclaredMethod("writeCsv", File.class, dataClass);
        m.setAccessible(true);
        File csv = (File) m.invoke(mgr, dir, data);
        assertTrue(Objects.requireNonNull(csv).exists());
    }

    @Test(expected = IOException.class)
    public void writeCsv_failure() throws Throwable {
        ExportManager mgr = new ExportManager(context, new StubRepository(context), executor);

        Class<?> dataClass = Class.forName("de.oabidi.pflanzenbestandundlichttest.ExportManager$ExportData");
        Constructor<?> ctor = dataClass.getDeclaredConstructor(java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class);
        ctor.setAccessible(true);
        Plant plant = new Plant("p", null, null, null, 0L, Uri.parse("content://missing"));
        plant.setId(1);
        Object data = ctor.newInstance(Collections.singletonList(plant), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        File dir = new File(context.getCacheDir(), "csvfail");
        dir.mkdirs();

        Method m = ExportManager.class.getDeclaredMethod("writeCsv", File.class, dataClass);
        m.setAccessible(true);
        try {
            m.invoke(mgr, dir, data);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw Objects.requireNonNull(e.getCause());
        }
    }

    @Test
    public void zipFiles_success() throws Exception {
        ExportManager mgr = new ExportManager(context, new StubRepository(context), executor);

        File dir = new File(context.getCacheDir(), "zipdir");
        dir.mkdirs();
        File f = new File(dir, "a.txt");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
            fos.write("x".getBytes());
        }

        File out = new File(context.getCacheDir(), "out.zip");
        Method m = ExportManager.class.getDeclaredMethod("zipFiles", Uri.class, File.class);
        m.setAccessible(true);
        m.invoke(mgr, Uri.fromFile(out), dir);
        assertTrue(out.exists() && out.length() > 0);
    }

    @Test(expected = IOException.class)
    public void zipFiles_failure() throws Throwable {
        ExportManager mgr = new ExportManager(context, new StubRepository(context), executor);

        File dir = new File(context.getCacheDir(), "zipdir2");
        dir.mkdirs();

        Method m = ExportManager.class.getDeclaredMethod("zipFiles", Uri.class, File.class);
        m.setAccessible(true);
        try {
            m.invoke(mgr, Uri.parse("content://invalid/out.zip"), dir);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw Objects.requireNonNull(e.getCause());
        }
    }

    @Test
    public void notifyProgress_success() throws Exception {
        ExportManager mgr = new ExportManager(context, new StubRepository(context), executor);
        Method m = ExportManager.class.getDeclaredMethod("notifyProgress", ExportManager.ProgressCallback.class, int[].class, int.class);
        m.setAccessible(true);

        final int[] captured = new int[2];
        ExportManager.ProgressCallback cb = (c, t) -> {
            captured[0] = c;
            captured[1] = t;
        };
        int[] progress = {0};
        m.invoke(mgr, cb, progress, 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, progress[0]);
        assertEquals(1, captured[0]);
        assertEquals(3, captured[1]);
    }

    @Test
    public void notifyProgress_nullCallback() throws Exception {
        ExportManager mgr = new ExportManager(context, new StubRepository(context), executor);
        Method m = ExportManager.class.getDeclaredMethod("notifyProgress", ExportManager.ProgressCallback.class, int[].class, int.class);
        m.setAccessible(true);
        int[] progress = {0};
        m.invoke(mgr, null, progress, 3);
        assertEquals(1, progress[0]);
    }

    @Test
    public void export_completesAsynchronously() throws Exception {
        ExportManager mgr = new ExportManager(context, new StubRepository(context), executor);
        File out = new File(context.getCacheDir(), "export_async.zip");
        if (out.exists()) {
            out.delete();
        }
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};
        mgr.export(Uri.fromFile(out), success -> {
            result[0] = success;
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(result[0]);
    }

    private static class EmptyBulkDao implements BulkReadDao {
        @Override
        public List<Plant> getAllPlants() {
            return Collections.emptyList();
        }

        @Override
        public Plant getPlant(long id) {
            return null;
        }

        @Override
        public List<Measurement> getAllMeasurements() {
            return Collections.emptyList();
        }

        @Override
        public List<Measurement> getMeasurementsForPlant(long plantId) {
            return Collections.emptyList();
        }

        @Override
        public List<DiaryEntry> getAllDiaryEntries() {
            return Collections.emptyList();
        }

        @Override
        public List<DiaryEntry> getDiaryEntriesForPlant(long plantId) {
            return Collections.emptyList();
        }

        @Override
        public List<de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto> getAllPlantPhotos() {
            return Collections.emptyList();
        }

        @Override
        public List<de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto> getPlantPhotosForPlant(long plantId) {
            return Collections.emptyList();
        }

        @Override
        public List<Reminder> getAllReminders() {
            return Collections.emptyList();
        }

        @Override
        public List<Reminder> getRemindersForPlant(long plantId) {
            return Collections.emptyList();
        }

        @Override
        public List<SpeciesTarget> getAllSpeciesTargets() {
            return Collections.emptyList();
        }

        @Override
        public List<PlantCalibration> getAllPlantCalibrations() {
            return Collections.emptyList();
        }

        @Override
        public List<PlantCalibration> getPlantCalibrationsForPlant(long plantId) {
            return Collections.emptyList();
        }
    }

    private static class FailingBulkDao implements BulkReadDao {
        private RuntimeException fail() {
            return new RuntimeException("fail");
        }

        @Override
        public List<Plant> getAllPlants() {
            throw fail();
        }

        @Override
        public Plant getPlant(long id) {
            throw fail();
        }

        @Override
        public List<Measurement> getAllMeasurements() {
            throw fail();
        }

        @Override
        public List<Measurement> getMeasurementsForPlant(long plantId) {
            throw fail();
        }

        @Override
        public List<DiaryEntry> getAllDiaryEntries() {
            throw fail();
        }

        @Override
        public List<DiaryEntry> getDiaryEntriesForPlant(long plantId) {
            throw fail();
        }

        @Override
        public List<de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto> getAllPlantPhotos() {
            throw fail();
        }

        @Override
        public List<de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto> getPlantPhotosForPlant(long plantId) {
            throw fail();
        }

        @Override
        public List<Reminder> getAllReminders() {
            throw fail();
        }

        @Override
        public List<Reminder> getRemindersForPlant(long plantId) {
            throw fail();
        }

        @Override
        public List<SpeciesTarget> getAllSpeciesTargets() {
            throw fail();
        }

        @Override
        public List<PlantCalibration> getAllPlantCalibrations() {
            throw fail();
        }

        @Override
        public List<PlantCalibration> getPlantCalibrationsForPlant(long plantId) {
            throw fail();
        }
    }

    private static class StubRepository extends PlantRepository {
        private final BulkReadDao dao = new EmptyBulkDao();

        StubRepository(Context ctx) {
            super(ctx);
        }

        @Override
        public BulkReadDao bulkDao() {
            return dao;
        }
    }

    private static class FailingRepository extends PlantRepository {
        private final BulkReadDao dao = new FailingBulkDao();

        FailingRepository(Context ctx) {
            super(ctx);
        }

        @Override
        public BulkReadDao bulkDao() {
            return dao;
        }
    }
}
