package de.oabidi.pflanzenbestandundlichttest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link LightMath} utility methods.
 */
public class LightMathTest {

    @Test
    public void ppfdFromLux_calculatesPPFD() {
        float lux = 10000f;
        float k = 0.015f; // Approx. 15 μmol·m⁻²·s⁻¹ per 1000 lux for white LEDs
        float expectedPpfd = 150f; // 10,000 lux * 0.015 = 150 μmol·m⁻²·s⁻¹
        assertEquals(expectedPpfd, LightMath.ppfdFromLux(lux, k), 0.0001f);
    }

    @Test
    public void dliFromPpfd_calculatesDli() {
        float ppfd = 200f;
        float hours = 16f; // Example: 200 μmol·m⁻²·s⁻¹ for a 16h photoperiod
        float expectedDli = 11.52f; // 200 * 16 * 0.0036 = 11.52 mol·m⁻²·day⁻¹
        assertEquals(expectedDli, LightMath.dliFromPpfd(ppfd, hours), 0.0001f);
    }
}
