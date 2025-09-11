package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.annotation.Config;

/**
 * Tests for PlantListFragment filtering logic ensuring species and location are matched.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = PlantListFragmentTest.TestApp.class)
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

    public static class TestApp extends PlantApp {
        PlantRepository repository;
        @Override
        public synchronized PlantRepository getRepository() {
            return repository;
        }
        void setRepository(PlantRepository repo) {
            this.repository = repo;
        }
    }

    private static class StubPlantRepository extends PlantRepository {
        private final List<Plant> data;
        StubPlantRepository(Context context, List<Plant> data) {
            super(context);
            this.data = data;
        }
        @Override
        public void searchPlants(String query, java.util.function.Consumer<List<Plant>> cb) {
            String lower = query.toLowerCase(Locale.ROOT);
            List<Plant> result = new ArrayList<>();
            for (Plant plant : data) {
                String name = plant.getName();
                String species = plant.getSpecies();
                String location = plant.getLocationHint();
                if ((name != null && name.toLowerCase(Locale.ROOT).contains(lower))
                    || (species != null && species.toLowerCase(Locale.ROOT).contains(lower))
                    || (location != null && location.toLowerCase(Locale.ROOT).contains(lower))) {
                    result.add(plant);
                }
            }
            if (cb != null) {
                cb.accept(result);
            }
        }

        @Override
        public void getAllPlants(java.util.function.Consumer<List<Plant>> callback,
                                 java.util.function.Consumer<Exception> errorCallback) {
            if (callback != null) {
                callback.accept(new ArrayList<>(data));
            }
        }
    }

    private List<Plant> invokeFilter(List<Plant> plants, String query) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ((TestApp) context).setRepository(new StubPlantRepository(context, plants));

        PlantListFragment fragment = Robolectric.buildFragment(PlantListFragment.class).create().start().resume().get();
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
