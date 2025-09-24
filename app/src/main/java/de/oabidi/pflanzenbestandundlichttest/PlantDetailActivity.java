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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.common.sensor.CameraLumaMonitor;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.feature.camera.PlantPhotoCaptureFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.environment.EnvironmentLogFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoAdapter;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoLoader;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoViewerFragment;

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
    implements PlantDetailView,
    LightSensorHelper.OnLuxChangedListener,
    PlantDetailInfoFragment.Callbacks,
    PlantGalleryTabFragment.Callbacks {
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
    private ViewPager2 detailViewPager;
    private TabLayout detailTabLayout;
    private PlantDetailPagerAdapter pagerAdapter;
    private ExecutorService ioExecutor;
    private ExecutorService cameraExecutor;
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private ImageAnalysis imageAnalysis;
    private CameraLumaMonitor cameraLumaMonitor;
    private boolean cameraPermissionDenied;
    @Nullable
    private MaterialCardView careTipsCard;
    @Nullable
    private RecyclerView careTipsList;
    @Nullable
    private View careTipsLoadingView;
    @Nullable
    private TextView careTipsEmptyView;
    @Nullable
    private CareRecommendationAdapter careRecommendationAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Allow the layout to extend into the system bar areas.
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plant_detail);

        detailViewPager = findViewById(R.id.detail_view_pager);
        detailTabLayout = findViewById(R.id.detail_tab_layout);
        repository = ((RepositoryProvider) getApplication()).getRepository();

        plantId = getIntent().getLongExtra("plantId", -1L); // Database ID of the plant
        String name = getIntent().getStringExtra("name"); // Plant's display name
        String description = getIntent().getStringExtra("description"); // Additional notes about the plant
        String species = getIntent().getStringExtra("species"); // Botanical species identifier
        String locationHint = getIntent().getStringExtra("locationHint"); // Where the plant is located
        long acquiredAtEpoch = getIntent().getLongExtra("acquiredAtEpoch", 0L); // Acquisition time in milliseconds since the Unix epoch
        plantName = name;
        lightSensorHelper = new LightSensorHelper(this, this, 6);
        hasAmbientSensor = lightSensorHelper.hasLightSensor();

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
        ioExecutor = ((ExecutorProvider) getApplicationContext()).getIoExecutor();
        presenter = new PlantDetailPresenter(this, plantId,
            new ExportManager(this, repository, ioExecutor), repository);
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), presenter::onExportUriSelected);

        String nameText = presenter.getTextOrFallback(name);
        String descriptionText = presenter.getTextOrFallback(description);
        String speciesText = presenter.getTextOrFallback(species);
        String locationText = presenter.getTextOrFallback(locationHint);
        String acquiredText = presenter.formatAcquiredAt(acquiredAtEpoch);

        pagerAdapter = new PlantDetailPagerAdapter(this, plantId,
            nameText, descriptionText, speciesText, locationText, acquiredText);
        detailViewPager.setAdapter(pagerAdapter);
        detailViewPager.setOffscreenPageLimit(pagerAdapter.getItemCount());
        new TabLayoutMediator(detailTabLayout, detailViewPager, (tab, position) -> {
            switch (position) {
                case PlantDetailPagerAdapter.POSITION_GALLERY:
                    tab.setText(R.string.plant_detail_tab_gallery);
                    break;
                case PlantDetailPagerAdapter.POSITION_ENVIRONMENT:
                    tab.setText(R.string.plant_detail_tab_environment);
                    break;
                case PlantDetailPagerAdapter.POSITION_DETAILS:
                default:
                    tab.setText(R.string.plant_detail_tab_details);
                    break;
            }
        }).attach();

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
                    refreshPlantPhotos();
                }
            }
        );

        getSupportFragmentManager().setFragmentResultListener(
            EnvironmentLogFragment.RESULT_KEY_CHANGES,
            this,
            (requestKey, bundle) -> presenter.loadCareRecommendations()
        );
        getSupportFragmentManager().setFragmentResultListener(
            PlantEditFragment.RESULT_KEY,
            this,
            (requestKey, bundle) -> presenter.loadCareRecommendations()
        );

        // After drawing edge-to-edge, pad the root view so content isn't
        // obscured by system bars like the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public void onDetailInfoViewsReady(@NonNull PlantDetailInfoFragment.DetailViews views) {
        ambientValueView = views.ambientValueView;
        ambientBandView = views.ambientBandView;
        cameraValueView = views.cameraValueView;
        cameraBandView = views.cameraBandView;
        ambientColumn = views.ambientColumnView;
        lightMeterSpacer = views.lightMeterSpacerView;
        careTipsCard = views.careTipsCardView;
        careTipsList = views.careRecommendationsListView;
        careTipsLoadingView = views.careRecommendationsLoadingView;
        careTipsEmptyView = views.careRecommendationsEmptyView;

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
        if (!hasAmbientSensor) {
            if (ambientColumn != null) {
                ambientColumn.setVisibility(View.GONE);
            }
            if (lightMeterSpacer != null) {
                lightMeterSpacer.setVisibility(View.GONE);
            }
        }

        if (careTipsList != null) {
            careTipsList.setLayoutManager(new LinearLayoutManager(this));
            careTipsList.setHasFixedSize(true);
            careRecommendationAdapter = new CareRecommendationAdapter(new CareRecommendationAdapter.Callbacks() {
                @Override
                public void onDismiss(@NonNull @NonNull CareRecommendation recommendation) {
                    presenter.dismissRecommendation(recommendation.getId());
                }
            });
            careTipsList.setAdapter(careRecommendationAdapter);
        }

        presenter.loadCareRecommendations();
    }

    @Override
    public void onDiaryButtonClicked() {
        presenter.onDiaryClicked();
    }

    @Override
    public void onEnvironmentLogButtonClicked() {
        presenter.onEnvironmentLogClicked();
    }

    @Override
    public void onGalleryViewsReady(@NonNull RecyclerView photoGrid,
                                    @NonNull View emptyView,
                                    @NonNull MaterialButton addPhotoButton) {
        this.photoGrid = photoGrid;
        this.photoEmptyView = emptyView;
        addPhotoButton.setOnClickListener(v -> launchCamera());
        setupPhotoGallery();
        refreshPlantPhotos();
    }

    private void setupPhotoGallery() {
        if (photoGrid == null || ioExecutor == null) {
            return;
        }
        if (plantPhotoLoader == null) {
            plantPhotoLoader = new PlantPhotoLoader(this, ioExecutor);
        }
        if (plantPhotoAdapter == null) {
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
        }
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

    private void refreshPlantPhotos() {
        if (plantPhotoAdapter == null) {
            if (null != null) {
                ((Runnable) null).run();
            }
            return;
        }
        if (plantId <= 0) {
            plantPhotoAdapter.submitList(Collections.emptyList());
            updateGalleryVisibility();
            if (null != null) {
                ((Runnable) null).run();
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
            if (null != null) {
                ((Runnable) null).run();
            }
        }, e -> {
            Toast.makeText(this, R.string.plant_photo_load_failed, Toast.LENGTH_SHORT).show();
            if (null != null) {
                ((Runnable) null).run();
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
            refreshPlantPhotos();
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
            refreshPlantPhotos();
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
        refreshPlantPhotos();
        if (hasAmbientSensor && lightSensorHelper != null) {
            lightSensorHelper.start();
        }
        startCameraUpdatesIfPossible();
        presenter.loadCareRecommendations();
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
        if (presenter != null) {
            presenter.onDestroy();
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
        if (detailViewPager != null) {
            detailViewPager.setCurrentItem(PlantDetailPagerAdapter.POSITION_ENVIRONMENT, true);
        }
    }

    @Override
    public void showCareRecommendations(List<CareRecommendation> items) {
        if (careTipsCard != null) {
            careTipsCard.setVisibility(View.VISIBLE);
        }
        if (careTipsLoadingView != null) {
            careTipsLoadingView.setVisibility(View.GONE);
        }
        if (careTipsEmptyView != null) {
            careTipsEmptyView.setVisibility(View.GONE);
        }
        if (careTipsList != null) {
            careTipsList.setVisibility(View.VISIBLE);
        }
        if (careRecommendationAdapter != null) {
            careRecommendationAdapter.submitList(new ArrayList<>(items));
        }
    }

    @Override
    public void showCareRecommendationsEmpty() {
        if (careRecommendationAdapter != null) {
            careRecommendationAdapter.submitList(Collections.emptyList());
        }
        if (careTipsCard != null) {
            careTipsCard.setVisibility(View.VISIBLE);
        }
        if (careTipsLoadingView != null) {
            careTipsLoadingView.setVisibility(View.GONE);
        }
        if (careTipsList != null) {
            careTipsList.setVisibility(View.GONE);
        }
        if (careTipsEmptyView != null) {
            careTipsEmptyView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showCareRecommendationError() {
        Toast.makeText(this, R.string.care_recommendation_error, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setCareRecommendationsLoading(boolean loading) {
        if (careTipsCard != null) {
            careTipsCard.setVisibility(View.VISIBLE);
        }
        if (careTipsLoadingView != null) {
            careTipsLoadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (loading) {
            if (careTipsList != null) {
                careTipsList.setVisibility(View.GONE);
            }
            if (careTipsEmptyView != null) {
                careTipsEmptyView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onCareRecommendationDismissed(String recommendationId) {
        if (careRecommendationAdapter == null) {
            return;
        }
        List<CareRecommendation> current = new ArrayList<>(careRecommendationAdapter.getCurrentList());
        boolean removed = false;
        for (int i = 0; i < current.size(); i++) {
            CareRecommendation item = current.get(i);
            if (item.getId().equals(recommendationId)) {
                current.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            careRecommendationAdapter.submitList(current);
            if (current.isEmpty()) {
                showCareRecommendationsEmpty();
            } else {
                if (careTipsCard != null) {
                    careTipsCard.setVisibility(View.VISIBLE);
                }
                if (careTipsLoadingView != null) {
                    careTipsLoadingView.setVisibility(View.GONE);
                }
                if (careTipsEmptyView != null) {
                    careTipsEmptyView.setVisibility(View.GONE);
                }
                if (careTipsList != null) {
                    careTipsList.setVisibility(View.VISIBLE);
                }
            }
        }
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
