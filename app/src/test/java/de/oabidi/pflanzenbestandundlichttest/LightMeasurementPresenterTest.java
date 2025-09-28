package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.LightMath;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileCalibration;
import de.oabidi.pflanzenbestandundlichttest.feature.lighting.LedProfileUtils;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExecutorProvider;

/** Tests for {@link LightMeasurementPresenter} artificial light projections. */
@RunWith(RobolectricTestRunner.class)
@Config(application = LightMeasurementPresenterTest.TestApp.class)
public class LightMeasurementPresenterTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void selectPlantComputesArtificialProjection() {
        LedProfile profile = createProfile(200f, 150f, scheduleEntry("08:00", "12:00", 75));
        LedProfileCalibration calibration = LedProfileCalibration.forProfile(profile, null);
        StubPlantRepository repository = new StubPlantRepository(context,
            singlePlantList(1L), profile, calibration);
        RecordingView view = new RecordingView();
        LightMeasurementPresenter presenter = new LightMeasurementPresenter(view, repository,
            context, 0.0185f, 5);

        presenter.refreshPlants();

        LightMeasurementPresenter.ArtificialLightProjection projection = view.lastProjection;
        assertNotNull(projection);
        assertEquals(3f, projection.getPhotonHours(), 0.0001f);
        assertEquals(LightMath.dliFromPpfd(200f, 3f), projection.getAmbientDli(), 0.0001f);
        assertEquals(LightMath.dliFromPpfd(150f, 3f), projection.getCameraDli(), 0.0001f);
        assertFalse(projection.isAmbientUsingFallback());
        assertFalse(projection.isCameraUsingFallback());
    }

    @Test
    public void applyCalibrationUpdatesProjection() {
        LedProfile initialProfile = createProfile(180f, 160f, scheduleEntry("06:00", "10:00", 100));
        StubPlantRepository repository = new StubPlantRepository(context,
            singlePlantList(2L), initialProfile,
            LedProfileCalibration.forProfile(initialProfile, null));
        RecordingView view = new RecordingView();
        LightMeasurementPresenter presenter = new LightMeasurementPresenter(view, repository,
            context, 0.0185f, 5);

        presenter.refreshPlants();

        LedProfile updatedProfile = createProfile(240f, 190f, scheduleEntry("05:00", "13:00", 50));
        repository.setProfile(updatedProfile, LedProfileCalibration.forProfile(updatedProfile, null));

        presenter.refreshActivePlantProfile();
        presenter.applyCalibration(repository.getCalibration());

        LightMeasurementPresenter.ArtificialLightProjection projection = view.lastProjection;
        assertNotNull(projection);
        float expectedHours = LedProfileUtils.computeWeightedPhotonHours(updatedProfile.getSchedule());
        assertEquals(expectedHours, projection.getPhotonHours(), 0.0001f);
        assertEquals(LightMath.dliFromPpfd(240f, expectedHours), projection.getAmbientDli(), 0.0001f);
        assertEquals(LightMath.dliFromPpfd(190f, expectedHours), projection.getCameraDli(), 0.0001f);
    }

    @Test
    public void missingCalibrationFallsBackToDefault() {
        LedProfile profile = createProfile(null, null, scheduleEntry("09:00", "15:00", 100));
        StubPlantRepository repository = new StubPlantRepository(context,
            singlePlantList(3L), profile, LedProfileCalibration.empty());
        RecordingView view = new RecordingView();
        LightMeasurementPresenter presenter = new LightMeasurementPresenter(view, repository,
            context, 0.0185f, 5);

        presenter.refreshPlants();

        LightMeasurementPresenter.ArtificialLightProjection projection = view.lastProjection;
        assertNotNull(projection);
        assertTrue(projection.isAmbientUsingFallback());
        assertTrue(projection.isCameraUsingFallback());
        float photonHours = LedProfileUtils.computeWeightedPhotonHours(profile.getSchedule());
        float expectedFallbackDli = LightMath.dliFromPpfd(200f, photonHours);
        assertEquals(expectedFallbackDli, projection.getAmbientDli(), 0.0001f);
        assertEquals(expectedFallbackDli, projection.getCameraDli(), 0.0001f);
    }

    private static List<Plant> singlePlantList(long id) {
        Plant plant = new Plant("Test", null, null, null, System.currentTimeMillis(), null);
        plant.setId(id);
        List<Plant> plants = new ArrayList<>();
        plants.add(plant);
        return plants;
    }

    private static LedProfile createProfile(Float ambient, Float camera, LedProfile.ScheduleEntry entry) {
        LedProfile profile = new LedProfile();
        profile.setId(1L);
        profile.setName("Profile");
        Map<String, Float> factors = new HashMap<>();
        if (ambient != null) {
            factors.put(LedProfile.CALIBRATION_KEY_AMBIENT, ambient);
        }
        if (camera != null) {
            factors.put(LedProfile.CALIBRATION_KEY_CAMERA, camera);
        }
        profile.setCalibrationFactors(factors);
        List<LedProfile.ScheduleEntry> schedule = new ArrayList<>();
        schedule.add(entry);
        profile.setSchedule(schedule);
        return profile;
    }

    private static LedProfile.ScheduleEntry scheduleEntry(String start, String end, int intensity) {
        return new LedProfile.ScheduleEntry(start, end, intensity);
    }

    private static class RecordingView implements LightMeasurementPresenter.View {
        LightMeasurementPresenter.ArtificialLightProjection lastProjection;

        @Override
        public void showLightData(@Nullable LightMeasurementPresenter.LightReading ambient,
                                  @Nullable LightMeasurementPresenter.LightReading camera) {
            // Not needed for these tests.
        }

        @Override
        public void showRangeStatus(String status) {
            // Not required.
        }

        @Override
        public void showPlants(List<Plant> plants) {
            // No-op
        }

        @Override
        public void showError(String message) {
            // No-op
        }

        @Override
        public void showSelectedStage(SpeciesTarget.GrowthStage stage) {
            // No-op
        }

        @Override
        public void showArtificialLightProjection(LightMeasurementPresenter.ArtificialLightProjection projection) {
            this.lastProjection = projection;
        }
    }

    public static class TestApp extends Application implements ExecutorProvider {
        private ExecutorService executor;

        @Override
        public synchronized ExecutorService getIoExecutor() {
            if (executor == null || executor.isShutdown()) {
                executor = TestExecutors.newImmediateExecutor();
            }
            return executor;
        }
    }

    private static class StubPlantRepository extends PlantRepository {
        private final List<Plant> plants;
        private LedProfile profile;
        private LedProfileCalibration calibration;

        StubPlantRepository(Context context, List<Plant> plants, LedProfile profile,
                            LedProfileCalibration calibration) {
            super(context, TestExecutors.newImmediateExecutor());
            this.plants = plants;
            this.profile = profile;
            this.calibration = calibration;
        }

        void setProfile(LedProfile profile, LedProfileCalibration calibration) {
            this.profile = profile;
            this.calibration = calibration;
        }

        LedProfileCalibration getCalibration() {
            return calibration;
        }

        @Override
        public void getAllPlants(Consumer<List<Plant>> callback, Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(new ArrayList<>(plants));
            }
        }

        @Override
        public void getLedProfileForPlant(long plantId, Consumer<LedProfile> callback,
                                          Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(profile);
            }
        }

        @Override
        public void getLedCalibrationForPlant(long plantId, Consumer<LedProfileCalibration> callback,
                                              Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(calibration);
            }
        }
    }
}
