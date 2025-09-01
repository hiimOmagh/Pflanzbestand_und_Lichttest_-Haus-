package de.oabidi.pflanzenbestandundlichttest;

/**
 * Utility methods for light calculations.
 */
public class LightMath {
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
}
