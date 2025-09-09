package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test verifying that {@link BackupScheduler#onReceive(Context, Intent)}
 * creates a ZIP file in the application's external files directory.
 */
@RunWith(AndroidJUnit4.class)
public class BackupSchedulerInstrumentedTest {

    @Test
    public void onReceiveCreatesBackupZip() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File dir = context.getExternalFilesDir(null);
        assertNotNull(dir);

        Set<String> existing = new HashSet<>();
        File[] before = dir.listFiles();
        if (before != null) {
            for (File f : before) {
                existing.add(f.getName());
            }
        }

        new BackupScheduler().onReceive(context, new Intent());

        File created = null;
        long timeout = System.currentTimeMillis() + 5000; // wait up to 5s
        while (System.currentTimeMillis() < timeout && created == null) {
            File[] after = dir.listFiles();
            if (after != null) {
                for (File f : after) {
                    String name = f.getName();
                    if (name.startsWith("backup-") && name.endsWith(".zip") && !existing.contains(name)) {
                        created = f;
                        break;
                    }
                }
            }
            if (created == null) {
                Thread.sleep(100);
            }
        }

        try {
            assertNotNull("Backup file not created", created);
            assertTrue(created.length() > 0);
        } finally {
            if (created != null && created.exists()) {
                //noinspection ResultOfMethodCallIgnored
                created.delete();
            }
        }
    }
}
