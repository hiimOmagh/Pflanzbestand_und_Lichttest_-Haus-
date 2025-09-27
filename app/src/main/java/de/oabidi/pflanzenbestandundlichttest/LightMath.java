package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;

/**
 * Utility methods for light calculations.
 */
public class LightMath {

    private static final float PAR_MOL_PER_MJ = 2.02f;

    public enum RangeStatus {
        LOW,
        OK,
        HIGH
    }
    /**
     * Convert a lux measurement to PPFD using a calibration factor.
     *
     * @param lux measured illuminance
     * @param k calibration factor (µmol·m−2·s−1 per lux)
     * @return photosynthetic photon flux density in µmol·m⁻²·s⁻¹
     */
    public static float ppfdFromLux(float lux, float k) {
        return lux * k;
    }

    /**
     * Calculate daily light integral from PPFD and light hours.
     *
     * @param ppfd photosynthetic photon flux density in µmol·m⁻²·s⁻¹
     * @param hours hours of light per day
     * @return daily light integral in mol·m⁻²·day⁻¹
     */
    public static float dliFromPpfd(float ppfd, float hours) {
        return ppfd * hours * 0.0036f;
    }

    /**
     * Converts short-wave radiation energy (MJ/m²) into a DLI approximation.
     */
    public static float dliFromShortwaveRadiation(float shortwaveRadiationMj) {
        return Math.max(0f, shortwaveRadiationMj) * PAR_MOL_PER_MJ;
    }

    /**
     * Estimates the solar declination angle for a given date.
     */
    public static double solarDeclination(@NonNull LocalDate date) {
        double dayOfYear = date.getDayOfYear();
        return Math.toRadians(23.44) * Math.sin(Math.toRadians((360d / 365d) * (dayOfYear - 81d)));
    }

    /**
     * Calculates daylight duration (sunrise to sunset) for a latitude.
     */
    public static double daylightDurationHours(double latitudeDegrees, @NonNull LocalDate date) {
        double latRad = Math.toRadians(latitudeDegrees);
        double declination = solarDeclination(date);
        double cosH = -Math.tan(latRad) * Math.tan(declination);
        if (cosH >= 1d) {
            return 0d;
        }
        if (cosH <= -1d) {
            return 24d;
        }
        double hourAngle = Math.acos(cosH);
        return (2d * hourAngle * 24d) / (2d * Math.PI);
    }

    /**
     * Applies a simple orientation factor for indoor placement.
     */
    public static float orientationModifier(@Nullable String orientationCode) {
        if (orientationCode == null) {
            return 1f;
        }
        switch (orientationCode) {
            case "S":
            case "SOUTH":
                return 1f;
            case "E":
            case "EAST":
            case "W":
            case "WEST":
                return 0.85f;
            case "N":
            case "NORTH":
                return 0.7f;
            default:
                return 0.9f;
        }
    }

    /**
     * Applies a heuristic attenuation based on mean cloud cover percentage.
     */
    public static float applyCloudCover(float dli, float cloudCoverPercent) {
        float normalized = Math.min(Math.max(cloudCoverPercent / 100f, 0f), 1f);
        float attenuation = 1f - (0.6f * normalized);
        return Math.max(0f, dli * attenuation);
    }

    /**
     * Evaluate whether a PPFD value falls below, within or above the provided
     * target range.
     *
     * @param ppfd current PPFD in µmol·m⁻²·s⁻¹
     * @param min  lower bound of the target range
     * @param max  upper bound of the target range
     * @return {@link RangeStatus#LOW} if {@code ppfd} is below {@code min},
     * {@link RangeStatus#HIGH} if above {@code max} and
     * {@link RangeStatus#OK} when the value lies within the range
     */
    public static RangeStatus rangeCheck(float ppfd, float min, float max) {
        if (ppfd < min) {
            return RangeStatus.LOW;
        } else if (ppfd > max) {
            return RangeStatus.HIGH;
        } else {
            return RangeStatus.OK;
        }
    }
}
