package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class ExportManagerCleanupTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static class FailingWriteRepository extends PlantRepository {
        private final BulkReadDao dao;

        FailingWriteRepository(Context ctx) {
            super(ctx);
            dao = new BulkReadDao() {
                @Override
                public List<Plant> getAllPlants() {
                    Plant p = new Plant("p", null, null, null, 0L, Uri.parse("content://missing"));
                    p.setId(1);
                    return Collections.singletonList(p);
                }

                @Override public Plant getPlant(long id) { return null; }
                @Override public List<Measurement> getAllMeasurements() { return Collections.emptyList(); }
                @Override public List<Measurement> getMeasurementsForPlant(long plantId) { return Collections.emptyList(); }
                @Override public List<DiaryEntry> getAllDiaryEntries() { return Collections.emptyList(); }
                @Override public List<DiaryEntry> getDiaryEntriesForPlant(long plantId) { return Collections.emptyList(); }
                @Override public List<Reminder> getAllReminders() { return Collections.emptyList(); }
                @Override public List<Reminder> getRemindersForPlant(long plantId) { return Collections.emptyList(); }
                @Override public List<SpeciesTarget> getAllSpeciesTargets() { return Collections.emptyList(); }
            };
        }

        @Override
        public BulkReadDao bulkDao() {
            return dao;
        }
    }

    @Test
    public void export_removesTempDirOnFailure() throws Exception {
        File cacheDir = context.getCacheDir();
        File[] existing = cacheDir.listFiles((dir, name) -> name.startsWith("export_"));
        if (existing != null) {
            for (File f : existing) {
                deleteRecursive(f);
            }
        }

        ExportManager mgr = new ExportManager(context, new FailingWriteRepository(context));
        File out = new File(cacheDir, "fail.zip");
        if (out.exists()) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }

        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {true};
        mgr.export(Uri.fromFile(out), success -> {
            result[0] = success;
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(result[0]);

        String[] remaining = cacheDir.list((dir, name) -> name.startsWith("export_"));
        assertNotNull(remaining);
        assertEquals(0, remaining.length);
    }
}
