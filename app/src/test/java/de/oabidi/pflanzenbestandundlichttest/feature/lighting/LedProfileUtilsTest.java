package de.oabidi.pflanzenbestandundlichttest.feature.lighting;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.oabidi.pflanzenbestandundlichttest.LightMath;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link LedProfileUtils}.
 */
public class LedProfileUtilsTest {

    private static LedProfile.ScheduleEntry entry(String start, String end, int intensity) {
        return new LedProfile.ScheduleEntry(start, end, intensity);
    }

    @Test
    public void computeWeightedPhotonHours_emptyScheduleReturnsZero() {
        assertEquals(0f, LedProfileUtils.computeWeightedPhotonHours(null), 0f);
        assertEquals(0f, LedProfileUtils.computeWeightedPhotonHours(new ArrayList<>()), 0f);
    }

    @Test
    public void computeWeightedPhotonHours_wraparoundAndOverlapHandled() {
        List<LedProfile.ScheduleEntry> schedule = new ArrayList<>();
        schedule.add(entry("22:00", "02:00", 50));
        schedule.add(entry("01:00", "03:00", 100));
        schedule.add(entry("08:00", "10:30", 60));
        float hours = LedProfileUtils.computeWeightedPhotonHours(schedule);
        // Equivalent hours: 22-24 (1.0), 00-01 (0.5), 01-03 (2.0), 08-10 (1.2), 10-10:30 (0.3) = 5.0
        assertEquals(5f, hours, 0.0001f);
    }

    @Test
    public void computeWeightedPhotonHours_ignoresInvalidEntries() {
        List<LedProfile.ScheduleEntry> schedule = new ArrayList<>();
        schedule.add(entry(null, "12:00", 80));
        schedule.add(entry("08:00", null, 80));
        schedule.add(entry("bad", "12:00", 80));
        schedule.add(entry("10:00", "10:00", 80));
        schedule.add(entry("11:00", "12:00", 0));
        assertEquals(0f, LedProfileUtils.computeWeightedPhotonHours(schedule), 0f);
    }

    @Test
    public void estimateArtificialLight_withCalibrations() {
        List<LedProfile.ScheduleEntry> schedule = new ArrayList<>();
        schedule.add(entry("08:00", "12:00", 75));
        Map<String, Float> factors = new HashMap<>();
        factors.put(LedProfile.CALIBRATION_KEY_AMBIENT, 200f);
        factors.put(LedProfile.CALIBRATION_KEY_CAMERA, 150f);

        LedProfileUtils.ArtificialLightEstimate estimate =
            LedProfileUtils.estimateArtificialLight(schedule, factors);

        float expectedPhotonHours = 3f; // 4 hours at 75%
        assertEquals(expectedPhotonHours, estimate.getPhotonHours(), 0.0001f);
        assertEquals(LightMath.dliFromPpfd(200f, expectedPhotonHours), estimate.getAmbientDli(), 0.0001f);
        assertEquals(LightMath.dliFromPpfd(150f, expectedPhotonHours), estimate.getCameraDli(), 0.0001f);
        assertFalse(estimate.isAmbientUsingFallback());
        assertFalse(estimate.isCameraUsingFallback());
    }

    @Test
    public void estimateArtificialLight_missingCalibrationFallsBack() {
        List<LedProfile.ScheduleEntry> schedule = new ArrayList<>();
        schedule.add(entry("06:00", "18:00", 100));
        LedProfileUtils.ArtificialLightEstimate estimate =
            LedProfileUtils.estimateArtificialLight(schedule, new HashMap<>());

        float expectedPhotonHours = 12f;
        assertEquals(expectedPhotonHours, estimate.getPhotonHours(), 0.0001f);
        assertEquals(LightMath.dliFromPpfd(200f, expectedPhotonHours), estimate.getAmbientDli(), 0.0001f);
        assertEquals(LightMath.dliFromPpfd(200f, expectedPhotonHours), estimate.getCameraDli(), 0.0001f);
        assertTrue(estimate.isAmbientUsingFallback());
        assertTrue(estimate.isCameraUsingFallback());
    }
}
