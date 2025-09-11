package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.feature.settings.SettingsFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Activity hosting the main navigation of the app.
 */
public class MainActivity extends AppCompatActivity implements MainPresenter.View {
    /** Intent extra to navigate directly to the measurement screen. */
    public static final String EXTRA_NAVIGATE_MEASURE =
        "de.oabidi.pflanzenbestandundlichttest.NAVIGATE_MEASURE";

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private MainPresenter presenter;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        presenter = new MainPresenter(this, getApplicationContext());

        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            presenter::handleExportResult);

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            presenter::handleImportResult);

        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            presenter::onNotificationPermissionResult);

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

        presenter.onCreate(savedInstanceState, getIntent());

        if (BuildConfig.DEBUG) {
            // Creates a button that mimics a crash when pressed
            Button crashButton = new Button(this);
            crashButton.setText(R.string.test_crash);
            crashButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    throw new RuntimeException(getString(R.string.test_crash)); // Force a crash
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

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        presenter.onNewIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.help_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return presenter.onOptionsItemSelected(item.getItemId())
            || super.onOptionsItemSelected(item);
    }

    // MainPresenter.View implementation
    @Override
    public void navigateToFragment(Fragment fragment, boolean addToBackStack) {
        androidx.fragment.app.FragmentTransaction transaction =
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, fragment);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    @Override
    public void showToast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showLongToast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show();
    }

    @Override
    public void selectNavigationItem(int itemId) {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_nav);
        bottomNavigationView.setSelectedItemId(itemId);
    }

    @Override
    public void requestNotificationPermission(String permission) {
        notificationPermissionLauncher.launch(permission);
    }

    @Override
    public void launchExport(String fileName) {
        exportLauncher.launch(fileName);
    }

    @Override
    public void launchImport(String[] mimeTypes) {
        importLauncher.launch(mimeTypes);
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
