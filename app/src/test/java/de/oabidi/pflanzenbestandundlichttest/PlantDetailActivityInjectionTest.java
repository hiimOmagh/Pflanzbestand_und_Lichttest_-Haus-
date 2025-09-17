package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Verifies that {@link PlantDetailActivity} uses the repository supplied
 * via the {@link RepositoryProvider} application implementation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = PlantDetailActivityInjectionTest.TestApp.class)
public class PlantDetailActivityInjectionTest {
    public static class TestApp extends Application implements RepositoryProvider {
        private PlantRepository repository;

        @Override
        public PlantRepository getRepository() {
            return repository;
        }

        void setRepository(PlantRepository repository) {
            this.repository = repository;
        }
    }

    @Test
    public void usesInjectedRepository() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PlantRepository fakeRepo = new PlantRepository(context, TestExecutors.newImmediateExecutor());
        ((TestApp) context).setRepository(fakeRepo);

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
