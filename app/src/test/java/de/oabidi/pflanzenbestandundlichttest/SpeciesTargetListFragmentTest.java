package de.oabidi.pflanzenbestandundlichttest;

import org.junit.Test;

import static org.junit.Assert.*;

public class SpeciesTargetListFragmentTest {
    @Test
    public void isInputValid_returnsTrueForValidInput() {
        assertTrue(SpeciesTargetListFragment.isInputValid("species", 10f, 20f));
    }

    @Test
    public void isInputValid_returnsFalseForEmptyKey() {
        assertFalse(SpeciesTargetListFragment.isInputValid("", 10f, 20f));
    }

    @Test
    public void isInputValid_returnsFalseForInvalidRange() {
        assertFalse(SpeciesTargetListFragment.isInputValid("species", 20f, 10f));
    }
}
