package de.oabidi.pflanzenbestandundlichttest;

/**
 * Utility methods for light calculations.
 */
public class LightMath {

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
