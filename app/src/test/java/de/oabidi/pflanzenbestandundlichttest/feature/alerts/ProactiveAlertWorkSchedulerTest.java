package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ProactiveAlertWorkSchedulerTest {
    private Context context;
    private WorkManager workManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
        workManager = WorkManager.getInstance(context);
    }

    @Test
    public void ensureScheduledTwice_keepsExistingPeriodicWork() throws ExecutionException, InterruptedException {
        ProactiveAlertWorkScheduler.ensureScheduled(context, true);

        List<WorkInfo> firstWorkInfos = workManager.getWorkInfosForUniqueWork("proactive-care-evaluation").get();
        assertEquals(1, firstWorkInfos.size());
        WorkInfo firstInfo = firstWorkInfos.get(0);
        UUID firstId = firstInfo.getId();
        long firstNextRunTime = firstInfo.getNextScheduleTimeMillis();

        ProactiveAlertWorkScheduler.ensureScheduled(context, true);

        List<WorkInfo> secondWorkInfos = workManager.getWorkInfosForUniqueWork("proactive-care-evaluation").get();
        assertEquals(1, secondWorkInfos.size());
        WorkInfo secondInfo = secondWorkInfos.get(0);

        assertEquals("Existing work instance should be preserved", firstId, secondInfo.getId());
        assertEquals(WorkInfo.State.ENQUEUED, secondInfo.getState());
        assertEquals("Next execution window should remain unchanged", firstNextRunTime, secondInfo.getNextScheduleTimeMillis());
    }
}
