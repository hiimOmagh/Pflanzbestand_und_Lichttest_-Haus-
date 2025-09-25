package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Verifies that the bundled species catalog seeds extended metadata fields.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class SpeciesCatalogSeedingTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase.resetInstanceForTesting(context);
    }

    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase.resetInstanceForTesting(context);
    }

    @Test
    public void seededCatalog_containsMetadata() {
        Context context = ApplicationProvider.getApplicationContext();
        PlantDatabase database = PlantDatabase.getDatabase(context);
        SpeciesTarget target = database.speciesTargetDao().findBySpeciesKey("pothos-golden");
        assertNotNull(target);
        assertNotNull(target.getWateringInfo());
        assertEquals("Allow top 2-3 cm of soil to dry between waterings", target.getWateringInfo().getFrequency());
        assertNotNull(target.getTemperatureRange());
        assertEquals(18f, target.getTemperatureRange().getMin(), 0.001f);
        assertEquals(29f, target.getTemperatureRange().getMax(), 0.001f);
        assertNotNull(target.getHumidityRange());
        assertEquals(40f, target.getHumidityRange().getMin(), 0.001f);
        assertEquals(70f, target.getHumidityRange().getMax(), 0.001f);
        assertTrue(Boolean.TRUE.equals(target.getToxicToPets()));
        assertNotNull(target.getCareTips());
        assertFalse(target.getCareTips().isEmpty());
    }
}
