package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertSame;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * Verifies that {@link PlantDetailActivity} uses the repository supplied
 * via {@link PlantDetailActivity#setRepository(PlantRepository)}.
 */
@RunWith(RobolectricTestRunner.class)
public class PlantDetailActivityInjectionTest {
    @Test
    public void usesInjectedRepository() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository fakeRepo = new PlantRepository(context);
        PlantDetailActivity.setRepository(fakeRepo);

        Intent intent = new Intent(context, PlantDetailActivity.class);
        intent.putExtra("plantId", 1L);
        intent.putExtra("name", "");
        intent.putExtra("description", "");
        intent.putExtra("species", "");
        intent.putExtra("locationHint", "");
        intent.putExtra("acquiredAtEpoch", 0L);
        intent.putExtra("photoUri", "");

        PlantDetailActivity activity = Robolectric.buildActivity(PlantDetailActivity.class, intent)
            .setup()
            .get();

        java.lang.reflect.Field field = PlantDetailActivity.class.getDeclaredField("repository");
        field.setAccessible(true);
        PlantRepository usedRepo = (PlantRepository) field.get(activity);
        assertSame(fakeRepo, usedRepo);
    }
}
