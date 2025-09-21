package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.TypedValue;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MainActivityInflationTest {
    @Test
    public void mainActivityInflatesMaterialComponents() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        MaterialToolbar toolbar = activity.findViewById(R.id.top_app_bar);
        NavigationBarView navView = activity.findViewById(R.id.bottom_nav);
        LinearProgressIndicator progress = activity.findViewById(R.id.export_progress_bar);

        assertNotNull("MaterialToolbar should be present", toolbar);
        assertNotNull("NavigationBarView should be present", navView);
        assertNotNull("LinearProgressIndicator should be present", progress);

        TypedValue typedValue = new TypedValue();
        boolean found = activity.getTheme().resolveAttribute(
            com.google.android.material.R.attr.isMaterial3Theme, typedValue, true);
        assertTrue("Material3 theme flag should be enabled", found && typedValue.data != 0);
    }
}
