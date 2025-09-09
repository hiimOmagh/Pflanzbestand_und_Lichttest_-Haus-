package de.oabidi.pflanzenbestandundlichttest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
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

        SharedPreferences prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE);
        boolean hasOnboarded = prefs.getBoolean(SettingsKeys.KEY_HAS_ONBOARDED, false);

        if (savedInstanceState == null) {
            if (!hasOnboarded) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, new OnboardingFragment())
                    .commit();
            } else if (getIntent().getBooleanExtra(EXTRA_NAVIGATE_MEASURE, false)) {
                bottomNavigationView.setSelectedItemId(R.id.nav_measure);
            } else {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, new PlantListFragment())
                    .commit();
            }
        }

        // Creates a button that mimics a crash when pressed
        Button crashButton = new Button(this);
        crashButton.setText("Test Crash");
        crashButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                throw new RuntimeException("Test Crash"); // Force a crash
            }
        });

        // Get the root FrameLayout
        FrameLayout rootView = (FrameLayout) findViewById(android.R.id.content);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = 180; // Increased from 100 to 180
        rootView.addView(crashButton, params);

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

    /**
     * Creates an intent pre-filled with the given plant's details.
     *
     * @param context the context used to create the intent
     * @param plant   the plant whose details should be shown
     * @return an intent for {@link PlantDetailActivity}
     */
    public static Intent createPlantDetailIntent(Context context, Plant plant) {
        Intent intent = new Intent(context, PlantDetailActivity.class);
        intent.putExtra("plantId", plant.getId());
        intent.putExtra("name", plant.getName());
        intent.putExtra("description", plant.getDescription());
        intent.putExtra("species", plant.getSpecies());
        intent.putExtra("locationHint", plant.getLocationHint());
        intent.putExtra("acquiredAtEpoch", plant.getAcquiredAtEpoch());
        String photo = plant.getPhotoUri() != null ? plant.getPhotoUri().toString() : "";
        intent.putExtra("photoUri", photo);
        return intent;
    }
}
