package de.oabidi.pflanzenbestandundlichttest;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.feature.settings.SettingsFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Activity hosting the main navigation of the app.
 */
public class MainActivity extends AppCompatActivity {
    /** Intent extra to navigate directly to the measurement screen. */
    public static final String EXTRA_NAVIGATE_MEASURE =
        "de.oabidi.pflanzenbestandundlichttest.NAVIGATE_MEASURE";

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted) {
                    Toast.makeText(
                        this,
                        R.string.notification_permission_denied,
                        Toast.LENGTH_LONG
                    ).show();
                }
            }
        );

        if (Build.VERSION.SDK_INT >= 33
            && !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_nav);
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

        if (savedInstanceState == null) {
            if (getIntent().getBooleanExtra(EXTRA_NAVIGATE_MEASURE, false)) {
                bottomNavigationView.setSelectedItemId(R.id.nav_measure);
            } else {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, new PlantListFragment())
                    .commit();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_NAVIGATE_MEASURE, false)) {
            BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_nav);
            bottomNavigationView.setSelectedItemId(R.id.nav_measure);
        }
    }
}
