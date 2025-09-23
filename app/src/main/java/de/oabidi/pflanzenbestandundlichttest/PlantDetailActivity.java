package de.oabidi.pflanzenbestandundlichttest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import de.oabidi.pflanzenbestandundlichttest.common.sensor.CameraLumaMonitor;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.feature.camera.PlantPhotoCaptureFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoAdapter;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoLoader;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoViewerFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.environment.EnvironmentLogFragment;

/**
 * Activity responsible for showing detailed information about a plant.
 *
 * <p>The details are supplied via {@link android.content.Intent} extras when this
 * activity is launched.</p>
 *
 * <p>Expected extras and their defaults:</p>
 * <ul>
 *   <li>{@code name} – plant's display name (defaults to a placeholder dash)</li>
 *   <li>{@code description} – additional notes (defaults to a placeholder dash)</li>
 *   <li>{@code species} – botanical species identifier (defaults to a placeholder dash)</li>
 *   <li>{@code locationHint} – where the plant is located (defaults to a placeholder dash)</li>
 *   <li>{@code acquiredAtEpoch} – acquisition time in milliseconds since the Unix epoch;
 *       {@code 0} results in an unknown date</li>
 *   <li>{@code photoUri} – string form of the plant photo URI; empty string when unavailable</li>
 * </ul>
 */
public class PlantDetailActivity extends AppCompatActivity
    implements PlantDetailView, LightSensorHelper.OnLuxChangedListener {
    private static final float LUX_MEDIUM_THRESHOLD = 300f;
    private static final float LUX_HIGH_THRESHOLD = 1000f;
    private static final float LUMA_MEDIUM_THRESHOLD = 90f;
    private static final float LUMA_HIGH_THRESHOLD = 170f;

    private PlantDetailPresenter presenter;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private PlantRepository repository;
    private RecyclerView photoGrid;
    private View photoEmptyView;
    private PlantPhotoAdapter plantPhotoAdapter;
    private PlantPhotoLoader plantPhotoLoader;
    private long plantId;
    private String plantName;
    private boolean scrollToNewestOnNextUpdate;
    @Nullable
    private LightSensorHelper lightSensorHelper;
    private boolean hasAmbientSensor;
    private TextView ambientValueView;
    private TextView ambientBandView;
    private TextView cameraValueView;
    private TextView cameraBandView;
    private View ambientColumn;
    private View lightMeterSpacer;
    private ExecutorService cameraExecutor;
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private ImageAnalysis imageAnalysis;
    private CameraLumaMonitor cameraLumaMonitor;
    private boolean cameraPermissionDenied;
    @Nullable
    private View environmentOverlayContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Allow the layout to extend into the system bar areas.
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plant_detail);

        repository = ((RepositoryProvider) getApplication()).getRepository();

        plantId = getIntent().getLongExtra("plantId", -1L); // Database ID of the plant
        String name = getIntent().getStringExtra("name"); // Plant's display name
        String description = getIntent().getStringExtra("description"); // Additional notes about the plant
        String species = getIntent().getStringExtra("species"); // Botanical species identifier
        String locationHint = getIntent().getStringExtra("locationHint"); // Where the plant is located
        long acquiredAtEpoch = getIntent().getLongExtra("acquiredAtEpoch", 0L); // Acquisition time in milliseconds since the Unix epoch
        plantName = name;
        TextView nameView = findViewById(R.id.detail_name);
        TextView descriptionView = findViewById(R.id.detail_description);
        TextView speciesView = findViewById(R.id.detail_species);
        TextView locationHintView = findViewById(R.id.detail_location_hint);
        TextView acquiredAtView = findViewById(R.id.detail_acquired_at);
        View diaryButton = findViewById(R.id.detail_diary);
        View environmentLogButton = findViewById(R.id.detail_environment_log);
        Button addPhotoButton = findViewById(R.id.detail_add_photo);
        photoGrid = findViewById(R.id.detail_photo_grid);
        photoEmptyView = findViewById(R.id.detail_photo_empty);
        environmentOverlayContainer = findViewById(R.id.detail_fragment_container);

        ambientValueView = findViewById(R.id.detail_ambient_value);
        ambientBandView = findViewById(R.id.detail_ambient_band);
        cameraValueView = findViewById(R.id.detail_camera_value);
        cameraBandView = findViewById(R.id.detail_camera_band);
        ambientColumn = findViewById(R.id.detail_ambient_column);
        lightMeterSpacer = findViewById(R.id.detail_light_meter_spacer);

        if (ambientValueView != null) {
            ambientValueView.setText(R.string.light_meter_lux_placeholder);
        }
        if (ambientBandView != null) {
            ambientBandView.setText(R.string.light_meter_band_placeholder);
        }
        if (cameraValueView != null) {
            cameraValueView.setText(R.string.light_meter_camera_placeholder);
        }
        if (cameraBandView != null) {
            cameraBandView.setText(R.string.light_meter_band_placeholder);
        }

        lightSensorHelper = new LightSensorHelper(this, this, 6);
        hasAmbientSensor = lightSensorHelper.hasLightSensor();
        if (!hasAmbientSensor) {
            if (ambientColumn != null) {
                ambientColumn.setVisibility(View.GONE);
            }
            if (lightMeterSpacer != null) {
                lightMeterSpacer.setVisibility(View.GONE);
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraLumaMonitor = new CameraLumaMonitor((raw, smoothed) -> runOnUiThread(() -> {
            if (cameraValueView == null || cameraBandView == null) {
                return;
            }
            cameraValueView.setText(getString(R.string.light_meter_camera_value, smoothed));
            setBandText(cameraBandView, bandForLuma(smoothed));
        }));
        cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    cameraPermissionDenied = false;
                    startCameraAnalysis();
                } else {
                    cameraPermissionDenied = true;
                    showCameraPermissionDenied();
                }
            }
        );

        if (!(getApplicationContext() instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        ExecutorService executor = ((ExecutorProvider) getApplicationContext()).getIoExecutor();
        presenter = new PlantDetailPresenter(this, plantId,
            new ExportManager(this, repository, executor));
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), presenter::onExportUriSelected);
        nameView.setText(presenter.getTextOrFallback(name));
        descriptionView.setText(presenter.getTextOrFallback(description));
        speciesView.setText(presenter.getTextOrFallback(species));
        locationHintView.setText(presenter.getTextOrFallback(locationHint));
        acquiredAtView.setText(presenter.formatAcquiredAt(acquiredAtEpoch));
        setupPhotoGallery(executor);
        refreshPlantPhotos(null);

        diaryButton.setOnClickListener(v -> presenter.onDiaryClicked());
        if (environmentLogButton != null) {
            environmentLogButton.setOnClickListener(v -> presenter.onEnvironmentLogClicked());
        }
        addPhotoButton.setOnClickListener(v -> launchCamera());

        getSupportFragmentManager().setFragmentResultListener(
            PlantPhotoCaptureFragment.RESULT_KEY,
            this,
            (requestKey, bundle) -> {
                String uriString = bundle.getString(PlantPhotoCaptureFragment.EXTRA_PHOTO_URI);
                if (!TextUtils.isEmpty(uriString)) {
                    Uri newUri = Uri.parse(uriString);
                    handleCapturedPhoto(newUri);
                }
            }
        );

        getSupportFragmentManager().setFragmentResultListener(
            PlantPhotoViewerFragment.RESULT_KEY,
            this,
            (requestKey, bundle) -> {
                boolean refresh = bundle.getBoolean(PlantPhotoViewerFragment.EXTRA_REFRESH_GALLERY, false);
                if (refresh) {
                    refreshPlantPhotos(null);
                }
            }
        );

        getSupportFragmentManager().addOnBackStackChangedListener(this::updateEnvironmentOverlayVisibility);
        updateEnvironmentOverlayVisibility();

        // After drawing edge-to-edge, pad the root view so content isn't
        // obscured by system bars like the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupPhotoGallery(@NonNull ExecutorService executor) {
        if (photoGrid == null) {
            return;
        }
        plantPhotoLoader = new PlantPhotoLoader(this, executor);
        plantPhotoAdapter = new PlantPhotoAdapter(plantPhotoLoader, new PlantPhotoAdapter.Callbacks() {
            @Override
            public void onPhotoClicked(@NonNull PlantPhoto photo) {
                showPhotoViewer(photo);
            }

            @Override
            public void onPhotoLongClicked(@NonNull PlantPhoto photo) {
                confirmDeletePhoto(photo);
            }
        });
        photoGrid.setLayoutManager(new GridLayoutManager(this, calculatePhotoSpanCount()));
        photoGrid.setAdapter(plantPhotoAdapter);
        photoGrid.setHasFixedSize(true);
        updateGalleryVisibility();
    }

    private int calculatePhotoSpanCount() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int widthDp = (int) (metrics.widthPixels / metrics.density);
        if (widthDp >= 720) {
            return 5;
        }
        if (widthDp >= 600) {
            return 4;
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return 4;
        }
        return 3;
    }

    private void refreshPlantPhotos(@Nullable Runnable completion) {
        if (plantPhotoAdapter == null) {
            if (completion != null) {
                completion.run();
            }
            return;
        }
        if (plantId <= 0) {
            plantPhotoAdapter.submitList(Collections.emptyList());
            updateGalleryVisibility();
            if (completion != null) {
                completion.run();
            }
            return;
        }
        repository.plantPhotosForPlant(plantId, photos -> {
            List<PlantPhoto> copy = new ArrayList<>(photos);
            plantPhotoAdapter.submitList(copy);
            updateGalleryVisibility();
            if (scrollToNewestOnNextUpdate) {
                scrollToNewestOnNextUpdate = false;
                if (!copy.isEmpty() && photoGrid != null) {
                    photoGrid.post(() -> photoGrid.smoothScrollToPosition(0));
                }
            }
            if (completion != null) {
                completion.run();
            }
        }, e -> {
            Toast.makeText(this, R.string.plant_photo_load_failed, Toast.LENGTH_SHORT).show();
            if (completion != null) {
                completion.run();
            }
        });
    }

    private void updateGalleryVisibility() {
        if (photoGrid == null || photoEmptyView == null) {
            return;
        }
        boolean hasPhotos = plantPhotoAdapter != null && plantPhotoAdapter.getItemCount() > 0;
        photoGrid.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        photoEmptyView.setVisibility(hasPhotos ? View.GONE : View.VISIBLE);
    }

    private void handleCapturedPhoto(@NonNull Uri uri) {
        if (plantId <= 0) {
            Toast.makeText(this, R.string.photo_capture_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        repository.addPlantPhoto(plantId, uri, photo -> {
            scrollToNewestOnNextUpdate = true;
            refreshPlantPhotos(null);
        }, e -> Toast.makeText(this, R.string.photo_capture_failed, Toast.LENGTH_SHORT).show());
    }

    private void confirmDeletePhoto(@NonNull PlantPhoto photo) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.plant_photo_delete_title)
            .setMessage(R.string.plant_photo_delete_message)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> deletePhoto(photo))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deletePhoto(@NonNull PlantPhoto photo) {
        repository.deletePlantPhoto(photo, () -> {
            Toast.makeText(this, R.string.plant_photo_delete_success, Toast.LENGTH_SHORT).show();
            refreshPlantPhotos(null);
        }, e -> Toast.makeText(this, R.string.plant_photo_delete_failed, Toast.LENGTH_SHORT).show());
    }

    private void showPhotoViewer(@NonNull PlantPhoto photo) {
        if (plantId <= 0) {
            return;
        }
        PlantPhotoViewerFragment.show(getSupportFragmentManager(), plantId, photo.getId(), plantName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPlantPhotos(null);
        if (hasAmbientSensor && lightSensorHelper != null) {
            lightSensorHelper.start();
        }
        startCameraUpdatesIfPossible();
    }

    @Override
    protected void onPause() {
        if (lightSensorHelper != null) {
            lightSensorHelper.stop();
        }
        stopCameraAnalysis();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopCameraAnalysis();
        if (lightSensorHelper != null) {
            lightSensorHelper.stop();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
            cameraExecutor = null;
        }
        super.onDestroy();
    }

    private void launchCamera() {
        PlantPhotoCaptureFragment.show(getSupportFragmentManager(), android.R.id.content);
    }

    private void startCameraUpdatesIfPossible() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            cameraPermissionDenied = false;
            startCameraAnalysis();
        } else if (!cameraPermissionDenied && cameraPermissionLauncher != null) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            showCameraPermissionDenied();
        }
    }

    private void startCameraAnalysis() {
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
        if (cameraValueView != null) {
            runOnUiThread(() -> {
                cameraValueView.setText(R.string.light_meter_camera_placeholder);
                if (cameraBandView != null) {
                    cameraBandView.setText(R.string.light_meter_band_placeholder);
                }
            });
        }
        if (cameraProvider != null) {
            bindImageAnalysis(cameraProvider);
            return;
        }
        final ListenableFuture<ProcessCameraProvider> providerFuture =
            ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                cameraProvider = provider;
                bindImageAnalysis(provider);
            } catch (ExecutionException | InterruptedException e) {
                showCameraUnavailable();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindImageAnalysis(@NonNull ProcessCameraProvider provider) {
        if (imageAnalysis != null) {
            provider.unbind(imageAnalysis);
        }
        imageAnalysis = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build();
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
        if (cameraLumaMonitor == null) {
            cameraLumaMonitor = new CameraLumaMonitor((raw, smoothed) -> runOnUiThread(() -> {
                if (cameraValueView == null || cameraBandView == null) {
                    return;
                }
                cameraValueView.setText(getString(R.string.light_meter_camera_value, smoothed));
                setBandText(cameraBandView, bandForLuma(smoothed));
            }));
        }
        imageAnalysis.setAnalyzer(cameraExecutor, cameraLumaMonitor);
        try {
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis);
        } catch (IllegalArgumentException e) {
            try {
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis);
            } catch (IllegalArgumentException inner) {
                provider.unbind(imageAnalysis);
                imageAnalysis = null;
                showCameraUnavailable();
            }
        }
    }

    private void stopCameraAnalysis() {
        if (cameraLumaMonitor != null) {
            cameraLumaMonitor.reset();
        }
        if (cameraProvider != null && imageAnalysis != null) {
            cameraProvider.unbind(imageAnalysis);
            imageAnalysis = null;
        }
    }

    private void showCameraPermissionDenied() {
        if (cameraValueView != null && cameraBandView != null) {
            cameraValueView.setText(R.string.light_meter_camera_permission_required);
            cameraBandView.setText(R.string.light_meter_band_placeholder);
        }
    }

    private void showCameraUnavailable() {
        runOnUiThread(() -> {
            if (cameraValueView != null && cameraBandView != null) {
                cameraValueView.setText(R.string.light_meter_camera_unavailable);
                cameraBandView.setText(R.string.light_meter_band_placeholder);
            }
        });
    }

    @Override
    public void onLuxChanged(float rawLux, float avgLux) {
        runOnUiThread(() -> {
            if (ambientValueView == null || ambientBandView == null) {
                return;
            }
            ambientValueView.setText(getString(R.string.light_meter_lux_value, avgLux));
            setBandText(ambientBandView, bandForLux(avgLux));
        });
    }

    private void setBandText(@NonNull TextView view, @NonNull LightBand band) {
        int resId;
        switch (band) {
            case LOW:
                resId = R.string.light_band_low;
                break;
            case MEDIUM:
                resId = R.string.light_band_medium;
                break;
            case HIGH:
            default:
                resId = R.string.light_band_high;
                break;
        }
        view.setText(resId);
    }

    private LightBand bandForLux(float lux) {
        if (lux < LUX_MEDIUM_THRESHOLD) {
            return LightBand.LOW;
        } else if (lux < LUX_HIGH_THRESHOLD) {
            return LightBand.MEDIUM;
        } else {
            return LightBand.HIGH;
        }
    }

    private LightBand bandForLuma(float luma) {
        if (luma < LUMA_MEDIUM_THRESHOLD) {
            return LightBand.LOW;
        } else if (luma < LUMA_HIGH_THRESHOLD) {
            return LightBand.MEDIUM;
        } else {
            return LightBand.HIGH;
        }
    }

    private enum LightBand {
        LOW,
        MEDIUM,
        HIGH
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.plant_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_export_plant) {
            presenter.onExportRequested();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void showExportSuccess() {
        Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showExportFailure() {
        Toast.makeText(this, R.string.export_failure, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void launchExport() {
        exportLauncher.launch(getString(R.string.export_file_name));
    }

    @Override
    public void navigateToDiary(long plantId) {
        DiaryFragment fragment = DiaryFragment.newInstance(repository, plantId);
        getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit();
    }

    @Override
    public void navigateToEnvironmentLog(long plantId) {
        if (environmentOverlayContainer != null) {
            environmentOverlayContainer.setVisibility(View.VISIBLE);
        }
        Fragment existing = getSupportFragmentManager().findFragmentByTag(EnvironmentLogFragment.TAG);
        if (existing instanceof EnvironmentLogFragment) {
            updateEnvironmentOverlayVisibility();
            return;
        }
        EnvironmentLogFragment.show(getSupportFragmentManager(), R.id.detail_fragment_container, plantId);
    }

    private void updateEnvironmentOverlayVisibility() {
        if (environmentOverlayContainer == null) {
            return;
        }
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(EnvironmentLogFragment.TAG);
        boolean visible = fragment != null && fragment.isAdded() && fragment.getView() != null;
        environmentOverlayContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public String getPlaceholderDash() {
        return getString(R.string.placeholder_dash);
    }

    @Override
    public String getUnknownDateText() {
        return getString(R.string.unknown_date);
    }
}
