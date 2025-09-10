package de.oabidi.pflanzenbestandundlichttest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link LightMath} utility methods.
 */
public class LightMathTest {

    /**
     * Verify conversion from a lux reading to PPFD using a typical calibration
     * factor for white LEDs. 10,000 lux multiplied by a factor of 0.015 should
     * yield 150 µmol·m⁻²·s⁻¹.
     */
    @Test
    public void ppfdFromLux_returnsExpectedValue() {
        float lux = 10_000f;
        float k = 0.015f; // Approx. 15 µmol·m⁻²·s⁻¹ per 1000 lux
        float expectedPpfd = 150f;
        assertEquals(expectedPpfd, LightMath.ppfdFromLux(lux, k), 0.0001f);
    }

    /**
     * A reading of zero lux should always return zero PPFD regardless of
     * the calibration factor.
     */
    @Test
    public void ppfdFromLux_withZeroLux_returnsZero() {
        assertEquals(0f, LightMath.ppfdFromLux(0f, 0.02f), 0f);
    }

    /**
     * Verify daily light integral calculation for a known PPFD value and
     * photoperiod. 200 µmol·m⁻²·s⁻¹ over 16 hours equals 11.52 mol·m⁻²·day⁻¹.
     */
    @Test
    public void dliFromPpfd_returnsExpectedValue() {
        float ppfd = 200f;
        float hours = 16f;
        float expectedDli = 11.52f;
        assertEquals(expectedDli, LightMath.dliFromPpfd(ppfd, hours), 0.0001f);
    }

    /**
     * Zero light hours should yield a daily light integral of zero.
     */
    @Test
    public void dliFromPpfd_withZeroHours_returnsZero() {
        assertEquals(0f, LightMath.dliFromPpfd(100f, 0f), 0f);
    }
}
