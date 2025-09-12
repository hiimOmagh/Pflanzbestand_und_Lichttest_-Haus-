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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.FutureTask;

@RunWith(RobolectricTestRunner.class)
public class ExportManagerMethodsTest {
    private Context context;

    private static class StubRepository extends PlantRepository {
        StubRepository(Context ctx) { super(ctx); }
        private <T> java.util.concurrent.Future<T> immediate(T value) {
            FutureTask<T> f = new FutureTask<>(() -> value);
            f.run();
            return f;
        }
        @Override public java.util.concurrent.Future<java.util.List<Plant>> getAllPlants() { return immediate(Collections.emptyList()); }
        @Override public java.util.concurrent.Future<java.util.List<Measurement>> getAllMeasurements() { return immediate(Collections.emptyList()); }
        @Override public java.util.concurrent.Future<java.util.List<DiaryEntry>> getAllDiaryEntries() { return immediate(Collections.emptyList()); }
        @Override public java.util.concurrent.Future<java.util.List<Reminder>> getAllReminders() { return immediate(Collections.emptyList()); }
        @Override public java.util.concurrent.Future<java.util.List<SpeciesTarget>> getAllSpeciesTargets() { return immediate(Collections.emptyList()); }
    }

    private static class FailingRepository extends PlantRepository {
        FailingRepository(Context ctx) { super(ctx); }
        private <T> java.util.concurrent.Future<T> fail() {
            FutureTask<T> f = new FutureTask<>(() -> { throw new IOException("fail"); });
            f.run();
            return f;
        }
        @Override public java.util.concurrent.Future<java.util.List<Plant>> getAllPlants() { return fail(); }
        @Override public java.util.concurrent.Future<java.util.List<Measurement>> getAllMeasurements() { return fail(); }
        @Override public java.util.concurrent.Future<java.util.List<DiaryEntry>> getAllDiaryEntries() { return fail(); }
        @Override public java.util.concurrent.Future<java.util.List<Reminder>> getAllReminders() { return fail(); }
        @Override public java.util.concurrent.Future<java.util.List<SpeciesTarget>> getAllSpeciesTargets() { return fail(); }
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void loadData_success() throws Exception {
        ExportManager mgr = new ExportManager(context);
        setRepository(mgr, new StubRepository(context));

        Method m = ExportManager.class.getDeclaredMethod("loadData", long.class);
        m.setAccessible(true);
        Object data = m.invoke(mgr, -1L);
        assertNotNull(data);
    }

    @Test(expected = IOException.class)
    public void loadData_failure() throws Throwable {
        ExportManager mgr = new ExportManager(context);
        setRepository(mgr, new FailingRepository(context));

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
        ExportManager mgr = new ExportManager(context);
        setRepository(mgr, new StubRepository(context));

        Class<?> dataClass = Class.forName("de.oabidi.pflanzenbestandundlichttest.ExportManager$ExportData");
        Constructor<?> ctor = dataClass.getDeclaredConstructor(java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class);
        ctor.setAccessible(true);
        Object data = ctor.newInstance(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        File dir = new File(context.getCacheDir(), "csvtest");
        dir.mkdirs();

        Method m = ExportManager.class.getDeclaredMethod("writeCsv", File.class, dataClass);
        m.setAccessible(true);
        File csv = (File) m.invoke(mgr, dir, data);
        assertTrue(Objects.requireNonNull(csv).exists());
    }

    @Test(expected = IOException.class)
    public void writeCsv_failure() throws Throwable {
        ExportManager mgr = new ExportManager(context);
        setRepository(mgr, new StubRepository(context));

        Class<?> dataClass = Class.forName("de.oabidi.pflanzenbestandundlichttest.ExportManager$ExportData");
        Constructor<?> ctor = dataClass.getDeclaredConstructor(java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class);
        ctor.setAccessible(true);
        Plant plant = new Plant("p", null, null, null, 0L, Uri.parse("content://missing"));
        plant.setId(1);
        Object data = ctor.newInstance(Collections.singletonList(plant), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

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
        ExportManager mgr = new ExportManager(context);
        setRepository(mgr, new StubRepository(context));

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
        ExportManager mgr = new ExportManager(context);
        setRepository(mgr, new StubRepository(context));

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
        ExportManager mgr = new ExportManager(context);
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
        ExportManager mgr = new ExportManager(context);
        Method m = ExportManager.class.getDeclaredMethod("notifyProgress", ExportManager.ProgressCallback.class, int[].class, int.class);
        m.setAccessible(true);
        int[] progress = {0};
        m.invoke(mgr, null, progress, 3);
        assertEquals(1, progress[0]);
    }

    private void setRepository(ExportManager mgr, PlantRepository repo) throws Exception {
        Field f = ExportManager.class.getDeclaredField("repository");
        f.setAccessible(true);
        f.set(mgr, repo);
    }
}
