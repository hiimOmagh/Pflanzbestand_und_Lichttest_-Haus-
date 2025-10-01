package de.oabidi.pflanzenbestandundlichttest.feature.main;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantDetailActivity;
import de.oabidi.pflanzenbestandundlichttest.feature.onboarding.OnboardingActivity;
import de.oabidi.pflanzenbestandundlichttest.feature.onboarding.OnboardingManager;

/**
 * Activity hosting the main navigation of the app.
 */
public class MainActivity extends AppCompatActivity implements MainView {
    /**
     * Intent extra to navigate directly to the measurement screen.
     */
    public static final String EXTRA_NAVIGATE_MEASURE =
        "de.oabidi.pflanzenbestandundlichttest.NAVIGATE_MEASURE";

    /**
     * Intent extra to open the diary screen for logging tasks.
     */
    public static final String EXTRA_NAVIGATE_DIARY =
        "de.oabidi.pflanzenbestandundlichttest.NAVIGATE_DIARY";

    private PlantRepository repository;
    private SharedPreferences preferences;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<Intent> onboardingLauncher;
    private MainPresenter presenter;
    private LinearProgressIndicator exportProgressBar;
    @Nullable
    private Bundle pendingSavedInstanceState;
    @Nullable
    private OnboardingManager onboardingManager;
    private boolean onboardingInProgress;

    /**
     * Creates an intent pre-filled with the given plant's details.
     *
     * @param context the context used to create the intent
     * @param plant   the plant whose details should be shown
     * @return an intent for {@link PlantDetailActivity}
     */
    public static Intent createPlantDetailIntent(Context context,
                                                 Plant plant) {
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        pendingSavedInstanceState = savedInstanceState;
        repository = ((RepositoryProvider) getApplication()).getRepository();
        preferences = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.top_app_bar);
        setSupportActionBar(toolbar);

        View navHost = findViewById(R.id.nav_host_fragment);
        exportProgressBar = findViewById(R.id.export_progress_bar);
        NavigationBarView bottomNavigationView = findViewById(R.id.bottom_nav);

        InsetsUtils.applySystemWindowInsetsPadding(toolbar, true, true, true, false);
        InsetsUtils.applySystemWindowInsetsPadding(bottomNavigationView, true, false, true, true);
        InsetsUtils.requestApplyInsetsWhenAttached(navHost);

        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            uri -> {
                if (presenter != null) {
                    presenter.handleExportResult(uri);
                }
            });

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (presenter != null) {
                    presenter.handleImportResult(uri);
                }
            });

        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (presenter != null) {
                    presenter.onNotificationPermissionResult(granted);
                }
            });

        onboardingLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                onboardingInProgress = false;
                if (result.getResultCode() == Activity.RESULT_OK
                    && onboardingManager != null) {
                    onboardingManager.maybeStartTour();
                }
            });

        ViewCompat.requestApplyInsets(navHost);

        initialisePresenterIfNeeded(savedInstanceState, getIntent());

        onboardingManager = new OnboardingManager(this, preferences, new OnboardingCallbacks());

        if (!preferences.getBoolean(SettingsKeys.KEY_ONBOARDING_DONE, false)) {
            startOnboardingActivity();
        }

    }

    private void initialisePresenterIfNeeded(@Nullable Bundle savedInstanceState, Intent intent) {
        if (presenter == null) {
            presenter = new MainPresenterImpl(this, getApplicationContext(), repository);
            NavigationBarView bottomNavigationView = findViewById(R.id.bottom_nav);
            bottomNavigationView.setOnItemSelectedListener(item ->
                presenter.onNavigationItemSelected(item.getItemId()));
            Bundle stateToUse = savedInstanceState != null ? savedInstanceState : pendingSavedInstanceState;
            presenter.onCreate(stateToUse, intent);
        } else if (intent != null) {
            presenter.onNewIntent(intent);
        }
        pendingSavedInstanceState = null;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        initialisePresenterIfNeeded(null, intent);
    }

    @Override
    protected void onStop() {
        if (onboardingManager != null) {
            onboardingManager.onStop();
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.help_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (presenter != null && presenter.onOptionsItemSelected(item.getItemId())) {
            return true;
        }
        if (presenter == null && item.getItemId() == R.id.action_help) {
            launchOnboarding();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        NavigationBarView bottomNavigationView = findViewById(R.id.bottom_nav);
        if (bottomNavigationView != null
            && bottomNavigationView.getSelectedItemId() == R.id.nav_species_search) {
            bottomNavigationView.setSelectedItemId(R.id.nav_plants);
            return;
        }
        super.onBackPressed();
    }

    // MainView implementation
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
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showLongToast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showExportProgress(int current, int total) {
        exportProgressBar.setMax(total);
        exportProgressBar.setProgress(current);
    }

    @Override
    public void showProgressBar() {
        exportProgressBar.setIndeterminate(false);
        exportProgressBar.setProgress(0);
        exportProgressBar.setVisibility(android.view.View.VISIBLE);
    }

    @Override
    public void hideProgressBar() {
        exportProgressBar.setVisibility(android.view.View.GONE);
    }

    @Override
    public void selectNavigationItem(int itemId) {
        NavigationBarView bottomNavigationView = findViewById(R.id.bottom_nav);
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
    public void showExportFormatChooser(ExportManager.Format currentFormat) {
        String[] options = {
            getString(R.string.export_format_option_csv),
            getString(R.string.export_format_option_json)
        };
        int selected = currentFormat == ExportManager.Format.JSON ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_format_title)
            .setSingleChoiceItems(options, selected, (dialog, which) -> {
                ExportManager.Format format = which == 1
                    ? ExportManager.Format.JSON
                    : ExportManager.Format.CSV;
                dialog.dismiss();
                presenter.onExportFormatChosen(format);
            })
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
            .show();
    }

    @Override
    public void launchImport(String[] mimeTypes) {
        importLauncher.launch(mimeTypes);
    }

    @Override
    public void showImportWarnings(String message) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_warnings_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    @Override
    public void launchOnboarding() {
        startOnboardingActivity();
    }

    private void startOnboardingActivity() {
        if (onboardingLauncher == null || onboardingInProgress) {
            return;
        }
        onboardingInProgress = true;
        Intent onboardingIntent = new Intent(this, OnboardingActivity.class);
        onboardingLauncher.launch(onboardingIntent);
    }

    private final class OnboardingCallbacks implements OnboardingManager.HostCallbacks {
        @Override
        public void ensurePlantListVisible(@NonNull Runnable onReady) {
            runOnUiThread(() -> {
                NavigationBarView bottomNavigationView = findViewById(R.id.bottom_nav);
                if (bottomNavigationView != null) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_plants);
                }
                View root = getWindow().getDecorView();
                if (root != null) {
                    root.post(onReady);
                } else {
                    onReady.run();
                }
            });
        }

        @Override
        public void openFirstPlantDetail(@NonNull OnboardingManager.PlantDetailLaunchCallback callback) {
            if (repository == null) {
                callback.onUnavailable();
                return;
            }
            repository.getAllPlants(plants -> {
                if (plants == null || plants.isEmpty()) {
                    callback.onUnavailable();
                    return;
                }
                Plant plant = plants.get(0);
                Intent intent = createPlantDetailIntent(MainActivity.this, plant);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                callback.onLaunched();
            }, e -> callback.onUnavailable());
        }

        @Override
        public void showRemindersScreen(@NonNull Runnable onReady) {
            runOnUiThread(() -> {
                NavigationBarView bottomNavigationView = findViewById(R.id.bottom_nav);
                if (bottomNavigationView != null) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_reminders);
                }
                View root = getWindow().getDecorView();
                if (root != null) {
                    root.post(onReady);
                } else {
                    onReady.run();
                }
            });
        }

        @Override
        public void returnToDefaultScreen() {
            runOnUiThread(() -> {
                NavigationBarView bottomNavigationView = findViewById(R.id.bottom_nav);
                if (bottomNavigationView != null) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_plants);
                }
            });
        }
    }
}
