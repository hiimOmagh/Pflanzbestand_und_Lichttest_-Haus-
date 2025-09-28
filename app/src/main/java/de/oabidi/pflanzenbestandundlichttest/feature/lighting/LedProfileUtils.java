package de.oabidi.pflanzenbestandundlichttest.feature.lighting;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import de.oabidi.pflanzenbestandundlichttest.LightMath;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfile;

/**
 * Helper utilities for working with {@link LedProfile} schedules and calibration data.
 */
public final class LedProfileUtils {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final float DEFAULT_FULL_POWER_PPFD = 200f;

    private LedProfileUtils() {
        // Utility class
    }

    /**
     * Calculates the daily photon-hours emitted by the provided LED schedule. Photon-hours are the
     * equivalent number of hours at full output after accounting for each entry's intensity
     * percentage. The helper tolerates missing or malformed entries and clamps overlapping windows
     * to the highest configured intensity.
     *
     * @param schedule list of schedule entries, possibly {@code null}
     * @return weighted photon-hours for a full day
     */
    public static float computeWeightedPhotonHours(@Nullable List<LedProfile.ScheduleEntry> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return 0f;
        }
        double[] minuteFractions = new double[MINUTES_PER_DAY];
        boolean hasAny = false;
        for (LedProfile.ScheduleEntry entry : schedule) {
            if (entry == null) {
                continue;
            }
            int start = parseTime(entry.getStartTime());
            int end = parseTime(entry.getEndTime());
            if (start < 0 || end < 0) {
                continue;
            }
            int intensity = Math.max(0, Math.min(100, entry.getIntensityPercent()));
            if (intensity <= 0) {
                continue;
            }
            double fraction = intensity / 100d;
            hasAny = true;
            if (end == start) {
                continue;
            }
            if (end < start) {
                accumulate(minuteFractions, start, MINUTES_PER_DAY, fraction);
                accumulate(minuteFractions, 0, end, fraction);
            } else {
                accumulate(minuteFractions, start, end, fraction);
            }
        }
        if (!hasAny) {
            return 0f;
        }
        double fractionSum = 0d;
        for (double minuteFraction : minuteFractions) {
            fractionSum += minuteFraction;
        }
        return (float) (fractionSum / 60d);
    }

    private static void accumulate(double[] minuteFractions, int startMinute, int endMinute, double fraction) {
        startMinute = Math.max(0, Math.min(MINUTES_PER_DAY, startMinute));
        endMinute = Math.max(0, Math.min(MINUTES_PER_DAY, endMinute));
        if (endMinute <= startMinute) {
            return;
        }
        for (int minute = startMinute; minute < endMinute; minute++) {
            minuteFractions[minute] = Math.max(minuteFractions[minute], fraction);
        }
    }

    private static int parseTime(@Nullable String time) {
        if (time == null) {
            return -1;
        }
        String trimmed = time.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            return -1;
        }
        try {
            int hours = Integer.parseInt(trimmed.substring(0, colon));
            int minutes = Integer.parseInt(trimmed.substring(colon + 1));
            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                return -1;
            }
            return hours * 60 + minutes;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Convenience wrapper returning an artificial light estimate for the supplied profile.
     */
    @NonNull
    public static ArtificialLightEstimate estimateArtificialLight(@Nullable LedProfile profile) {
        if (profile == null) {
            return new ArtificialLightEstimate(0f, 0f, true, 0f, true);
        }
        return estimateArtificialLight(profile.getSchedule(), profile.getCalibrationFactors());
    }

    /**
     * Estimates the artificial light contribution of the provided schedule and calibration values.
     * The result combines the weighted photon-hours with optional ambient and camera daily light
     * integral calculations. When a calibration factor is missing or invalid the helper falls back
     * to a conservative full-power PPFD value so callers still receive an estimate.
     *
     * @param schedule list of schedule entries describing LED operation
     * @param calibrationFactors calibration map keyed by {@link LedProfile#CALIBRATION_KEY_AMBIENT}
     *                           and {@link LedProfile#CALIBRATION_KEY_CAMERA}
     * @return aggregated artificial light estimate
     */
    @NonNull
    public static ArtificialLightEstimate estimateArtificialLight(
        @Nullable List<LedProfile.ScheduleEntry> schedule,
        @Nullable Map<String, Float> calibrationFactors) {
        float photonHours = computeWeightedPhotonHours(schedule);
        Calibration ambient = resolveCalibration(calibrationFactors, LedProfile.CALIBRATION_KEY_AMBIENT);
        Calibration camera = resolveCalibration(calibrationFactors, LedProfile.CALIBRATION_KEY_CAMERA);
        float ambientDli = photonHours > 0f
            ? LightMath.dliFromPpfd(ambient.ppfd, photonHours)
            : 0f;
        float cameraDli = photonHours > 0f
            ? LightMath.dliFromPpfd(camera.ppfd, photonHours)
            : 0f;
        return new ArtificialLightEstimate(photonHours, ambientDli, ambient.usedFallback,
            cameraDli, camera.usedFallback);
    }

    private static Calibration resolveCalibration(@Nullable Map<String, Float> factors, @NonNull String key) {
        if (factors != null) {
            Float value = factors.get(key);
            if (value != null && value > 0f) {
                return new Calibration(value, false);
            }
        }
        return new Calibration(DEFAULT_FULL_POWER_PPFD, true);
    }

    private static final class Calibration {
        final float ppfd;
        final boolean usedFallback;

        Calibration(float ppfd, boolean usedFallback) {
            this.ppfd = ppfd;
            this.usedFallback = usedFallback;
        }
    }

    /** Value object describing schedule-derived light information. */
    public static final class ArtificialLightEstimate {
        private final float photonHours;
        private final float ambientDli;
        private final boolean ambientUsedFallback;
        private final float cameraDli;
        private final boolean cameraUsedFallback;

        ArtificialLightEstimate(float photonHours, float ambientDli, boolean ambientUsedFallback,
                                float cameraDli, boolean cameraUsedFallback) {
            this.photonHours = photonHours;
            this.ambientDli = ambientDli;
            this.ambientUsedFallback = ambientUsedFallback;
            this.cameraDli = cameraDli;
            this.cameraUsedFallback = cameraUsedFallback;
        }

        /** Returns the total weighted photon-hours for the schedule. */
        public float getPhotonHours() {
            return photonHours;
        }

        /** Returns the ambient-sensor-based artificial DLI estimate. */
        public float getAmbientDli() {
            return ambientDli;
        }

        /** Returns whether the ambient estimate relied on the fallback PPFD value. */
        public boolean isAmbientUsingFallback() {
            return ambientUsedFallback;
        }

        /** Returns the camera-based artificial DLI estimate. */
        public float getCameraDli() {
            return cameraDli;
        }

        /** Returns whether the camera estimate relied on the fallback PPFD value. */
        public boolean isCameraUsingFallback() {
            return cameraUsedFallback;
        }
    }
}
