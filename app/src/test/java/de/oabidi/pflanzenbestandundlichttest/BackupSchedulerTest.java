package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Method;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BackupSchedulerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cleanupOldBackups_retainsNewestFiveFiles() throws Exception {
        File dir = temporaryFolder.newFolder("backups");
        long baseTime = System.currentTimeMillis();

        for (int i = 0; i < 6; i++) {
            File backup = new File(dir, "backup-" + i + ".zip");
            assertTrue("Failed to create mock backup", backup.createNewFile());
            // Ensure a stable ordering when sorted by last modified timestamp.
            assertTrue(backup.setLastModified(baseTime + (i * 1000L)));
        }

        Method cleanup = BackupScheduler.class.getDeclaredMethod("cleanupOldBackups", File.class);
        cleanup.setAccessible(true);
        cleanup.invoke(null, dir);

        File[] remaining = dir.listFiles((d, name) -> name.startsWith("backup-") && name.endsWith(".zip"));
        assertNotNull("Backup listing should not be null", remaining);
        assertEquals("Exactly five backups should remain", 5, remaining.length);

        long oldestRemainingTimestamp = Long.MAX_VALUE;
        for (File backup : remaining) {
            oldestRemainingTimestamp = Math.min(oldestRemainingTimestamp, backup.lastModified());
        }

        assertTrue("Oldest remaining backup should be newer than the deleted one", oldestRemainingTimestamp > baseTime);
    }
}
