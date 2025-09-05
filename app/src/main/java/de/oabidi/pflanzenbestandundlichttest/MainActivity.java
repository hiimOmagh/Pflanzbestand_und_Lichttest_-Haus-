package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.feature.settings.SettingsFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Activity hosting the main navigation of the app.
 */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_nav);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, new PlantListFragment())
                .commit();
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_plants) {
                fragment = new PlantListFragment();
            } else if (itemId == R.id.nav_measure) {
                fragment = new LightMeasurementFragment();
            } else if (itemId == R.id.nav_diary) {
                fragment = new DiaryFragment();
            } else if (itemId == R.id.nav_reminders) {
                fragment = new ReminderListFragment();
            } else if (itemId == R.id.nav_stats) {
                fragment = new StatsFragment();
            } else {
                fragment = new SettingsFragment();
            }
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
            return true;
        });
    }
}
