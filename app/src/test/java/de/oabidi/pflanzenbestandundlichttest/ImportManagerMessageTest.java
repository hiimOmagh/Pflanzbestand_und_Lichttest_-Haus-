package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class ImportManagerMessageTest {

    private Context context;
    private ExecutorService executor;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        executor = ((ExecutorProvider) context).getIoExecutor();
    }

    @Test
    public void messageNotNullOnFailure() throws Exception {
        File invalid = new File(context.getCacheDir(), "invalid.txt");
        try (FileOutputStream fos = new FileOutputStream(invalid)) {
            fos.write("not a zip".getBytes(StandardCharsets.UTF_8));
        }
        ImportManager importer = new ImportManager(context, executor);
        CountDownLatch latch = new CountDownLatch(1);
        final String[] messageHolder = new String[1];
        importer.importData(Uri.fromFile(invalid), ImportManager.Mode.REPLACE,
            (success, err, warnings, message) -> {
                messageHolder[0] = message;
                latch.countDown();
            });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(messageHolder[0]);
        // Clean up
        //noinspection ResultOfMethodCallIgnored
        invalid.delete();
    }
}
