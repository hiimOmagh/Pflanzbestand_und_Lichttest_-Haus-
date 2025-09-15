package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.util.Collections;

/**
 * Tests for {@link PlantListFragment}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = PlantApp.class)
public class PlantListFragmentTest {

    @Test
    public void onImportResult_displaysProvidedMessage() {
        Context context = ApplicationProvider.getApplicationContext();
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        PlantRepository repository = new PlantRepository(context);
        PlantListFragment fragment = PlantListFragment.newInstance(repository);
        activity.getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow();

        String message = "custom message";
        fragment.onImportResult(false, null, Collections.emptyList(), message);

        assertEquals(message, ShadowToast.getTextOfLatestToast());
    }
}
