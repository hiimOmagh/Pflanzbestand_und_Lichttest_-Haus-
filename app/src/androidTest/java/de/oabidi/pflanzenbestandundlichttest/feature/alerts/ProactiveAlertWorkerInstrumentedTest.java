package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantApp;
import de.oabidi.pflanzenbestandundlichttest.core.data.db.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.PlantDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTargetDao;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntryDao;

@RunWith(AndroidJUnit4.class)
public class ProactiveAlertWorkerInstrumentedTest {
    private Context context;
    private ExecutorService executor;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        executor = Executors.newSingleThreadExecutor();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS);
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(SettingsKeys.KEY_PROACTIVE_ALERTS_ENABLED, true).apply();
        PlantDatabase.resetInstanceForTesting(context);
    }

    @After
    public void tearDown() {
        ProactiveAlertNotifier.setCallback(null);
        PlantDatabase.resetInstanceForTesting(context);
        executor.shutdownNow();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void worker_postsCriticalAlertNotification() throws ExecutionException, InterruptedException {
        PlantDatabase db = PlantDatabase.getDatabase(context);
        PlantDao plantDao = db.plantDao();
        SpeciesTargetDao targetDao = db.speciesTargetDao();
        EnvironmentEntryDao entryDao = db.environmentEntryDao();

        Plant plant = new Plant();
        plant.setName("Fern");
        plant.setSpecies("fern");
        plant.setAcquiredAtEpoch(System.currentTimeMillis() - 86400000L);
        long plantId = plantDao.insert(plant);
        plant.setId(plantId);

        SpeciesTarget target = new SpeciesTarget();
        target.setSpeciesKey("fern");
        target.setHumidityRange(new SpeciesTarget.FloatRange(50f, 65f));
        targetDao.insert(target);

        EnvironmentEntry entry = new EnvironmentEntry();
        entry.setPlantId(plantId);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setHumidity(20f);
        entryDao.insert(entry);

        PlantRepository repository = PlantApp.from(context).getRepository();
        repository.alertRepository().deleteOlderThan(0); // ensure clean state

        CaptureCallback callback = new CaptureCallback();
        ProactiveAlertNotifier.setCallback(callback);

        ProactiveAlertWorker worker = TestWorkerBuilder.from(
            context,
            ProactiveAlertWorker.class,
            executor
        ).build();

        assertThat(worker.doWork(), equalTo(ListenableWorker.Result.success()));
        assertThat(callback.capturedAlert, notNullValue());
        assertThat(callback.capturedAlert.getTrigger(), equalTo(ProactiveAlertTrigger.HUMIDITY_LOW));
    }

    private static final class CaptureCallback implements ProactiveAlertNotifier.Callback {
        private volatile ProactiveAlert capturedAlert;

        @Override
        public void onNotificationDispatched(ProactiveAlert alert) {
            this.capturedAlert = alert;
        }
    }
}
