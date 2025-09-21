package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import androidx.core.view.ViewCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import de.oabidi.pflanzenbestandundlichttest.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.common.ui.InsetsUtils;

/**
 * Activity hosting the main navigation of the app.
 */
public class MainActivity extends AppCompatActivity implements MainView {
    /** Intent extra to navigate directly to the measurement screen. */
    public static final String EXTRA_NAVIGATE_MEASURE =
        "de.oabidi.pflanzenbestandundlichttest.NAVIGATE_MEASURE";

    /** Intent extra to open the diary screen for logging tasks. */
    public static final String EXTRA_NAVIGATE_DIARY =
        "de.oabidi.pflanzenbestandundlichttest.NAVIGATE_DIARY";

    private PlantRepository repository;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private MainPresenter presenter;
    private LinearProgressIndicator exportProgressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        repository = ((RepositoryProvider) getApplication()).getRepository();
        presenter = new MainPresenterImpl(this, getApplicationContext(), repository);

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
            presenter::handleExportResult);

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            presenter::handleImportResult);

        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            presenter::onNotificationPermissionResult);

        ViewCompat.requestApplyInsets(navHost);
        bottomNavigationView.setOnItemSelectedListener(item ->
            presenter.onNavigationItemSelected(item.getItemId()));

        presenter.onCreate(savedInstanceState, getIntent());

    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
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
}
