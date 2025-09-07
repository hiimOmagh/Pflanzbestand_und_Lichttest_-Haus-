package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for PlantListFragment filtering logic ensuring species and location are matched.
 */
@RunWith(RobolectricTestRunner.class)
public class PlantListFragmentTest {

    private static class RecordingPlantAdapter extends PlantAdapter {
        List<Plant> last;
        RecordingPlantAdapter() {
            super(p -> {});
        }
        @Override
        public void submitList(List<Plant> list) {
            last = list;
        }
    }

    private List<Plant> invokeFilter(List<Plant> plants, String query) throws Exception {
        PlantListFragment fragment = new PlantListFragment();
        RecordingPlantAdapter adapter = new RecordingPlantAdapter();
        Field plantsField = PlantListFragment.class.getDeclaredField("plants");
        plantsField.setAccessible(true);
        plantsField.set(fragment, plants);
        Field adapterField = PlantListFragment.class.getDeclaredField("adapter");
        adapterField.setAccessible(true);
        adapterField.set(fragment, adapter);
        Method filter = PlantListFragment.class.getDeclaredMethod("filterPlants", String.class);
        filter.setAccessible(true);
        filter.invoke(fragment, query);
        return adapter.last;
    }

    @Test
    public void filterMatchesSpecies() throws Exception {
        Plant p1 = new Plant("Aloe", null, "Aloe Vera", null, 0L, null);
        Plant p2 = new Plant("Cactus", null, "Echinocactus", null, 0L, null);
        List<Plant> result = invokeFilter(Arrays.asList(p1, p2), "vera");
        assertEquals(1, result.size());
        assertSame(p1, result.get(0));
    }

    @Test
    public void filterMatchesLocationHint() throws Exception {
        Plant p1 = new Plant("Aloe", null, null, "Kitchen", 0L, null);
        Plant p2 = new Plant("Cactus", null, null, "Living Room", 0L, null);
        List<Plant> result = invokeFilter(Arrays.asList(p1, p2), "living");
        assertEquals(1, result.size());
        assertSame(p2, result.get(0));
    }

    @Test
    public void filterMatchesTurkishI() throws Exception {
        Locale prev = Locale.getDefault();
        Locale.setDefault(new Locale("tr"));
        try {
            Plant p = new Plant("İnci", null, null, null, 0L, null);
            List<Plant> result = invokeFilter(Arrays.asList(p), "I");
            assertEquals(1, result.size());
            assertSame(p, result.get(0));
        } finally {
            Locale.setDefault(prev);
        }
    }
}
