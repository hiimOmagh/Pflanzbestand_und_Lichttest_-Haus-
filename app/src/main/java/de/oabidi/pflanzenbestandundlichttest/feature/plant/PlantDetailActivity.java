package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import de.oabidi.pflanzenbestandundlichttest.R;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.feature.light.measurement.LightSensorHelper;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.common.location.LocationProvider;
import de.oabidi.pflanzenbestandundlichttest.common.sensor.CameraLumaMonitor;
import de.oabidi.pflanzenbestandundlichttest.core.data.LightSummary;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExecutorProvider;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.feature.camera.PlantPhotoCaptureFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.environment.EnvironmentLogFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoAdapter;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoLoader;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoViewerFragment;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;

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
    private final NumberFormat naturalDliFormat = NumberFormat.getNumberInstance();
    private final DateFormat naturalDliDateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    private PlantDetailPresenter presenter;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
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
    @Nullable
    private MaterialCardView lightSummaryCard;
    @Nullable
    private TextView naturalDliValueView;
    @Nullable
    private TextView naturalDliTimestampView;
    @Nullable
    private TextView artificialDliValueView;
    @Nullable
    private TextView artificialDliTimestampView;
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
    private boolean locationPermissionDenied;
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
    @Nullable
    private MaterialCardView speciesMetadataCard;
    @Nullable
    private View wateringSection;
    @Nullable
    private TextView wateringFrequencyView;
    @Nullable
    private TextView wateringSoilView;
    @Nullable
    private TextView wateringToleranceView;
    @Nullable
    private View temperatureSection;
    @Nullable
    private TextView temperatureRangeView;
    @Nullable
    private View humiditySection;
    @Nullable
    private TextView humidityRangeView;
    @Nullable
    private View toxicitySection;
    @Nullable
    private TextView toxicityTextView;
    @Nullable
    private View careTipsSection;
    @Nullable
    private TextView careTipsTextView;
    @Nullable
    private TextView metadataUnavailableView;
    @Nullable
    private ImageView wateringIconView;
    @Nullable
    private ImageView temperatureIconView;
    @Nullable
    private ImageView humidityIconView;
    @Nullable
    private ImageView toxicityIconView;
    @Nullable
    private ImageView careTipsIconView;
    @Nullable
    private PlantMetadataViewModel pendingMetadata;
    @Nullable
    private String pendingMetadataFallback;
    @Nullable
    private String speciesKey;
    private boolean metadataViewsReady;
    private LocationProvider locationProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Allow the layout to extend into the system bar areas.
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plant_detail);
        MaterialToolbar toolbar = findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_24);
            actionBar.setHomeActionContentDescription(R.string.content_description_navigate_up);
        }
        toolbar.setNavigationContentDescription(R.string.content_description_navigate_up);
        toolbar.setNavigationOnClickListener(v -> finish());
        naturalDliFormat.setMinimumFractionDigits(1);
        naturalDliFormat.setMaximumFractionDigits(2);

        detailViewPager = findViewById(R.id.detail_view_pager);
        detailTabLayout = findViewById(R.id.detail_tab_layout);
        repository = ((RepositoryProvider) getApplication()).getRepository();
        locationProvider = new LocationProvider(this);

        plantId = getIntent().getLongExtra("plantId", -1L); // Database ID of the plant
        String name = getIntent().getStringExtra("name"); // Plant's display name
        String description = getIntent().getStringExtra("description"); // Additional notes about the plant
        String species = getIntent().getStringExtra("species"); // Botanical species identifier
        String locationHint = getIntent().getStringExtra("locationHint"); // Where the plant is located
        long acquiredAtEpoch = getIntent().getLongExtra("acquiredAtEpoch", 0L); // Acquisition time in milliseconds since the Unix epoch
        plantName = name;
        speciesKey = species;
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

        locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                boolean granted = false;
                for (Boolean value : permissions.values()) {
                    if (Boolean.TRUE.equals(value)) {
                        granted = true;
                        break;
                    }
                }
                if (granted) {
                    fetchAndPersistLocation();
                } else {
                    locationPermissionDenied = true;
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
        presenter.loadSpeciesMetadata(speciesKey);

        String nameText = presenter.getTextOrFallback(name);
        String descriptionText = presenter.getTextOrFallback(description);
        String speciesText = presenter.getTextOrFallback(species);
        String locationText = presenter.getTextOrFallback(locationHint);
        String acquiredText = presenter.formatAcquiredAt(acquiredAtEpoch);

        pagerAdapter = new PlantDetailPagerAdapter(this, plantId, repository,
            nameText, descriptionText, speciesText, locationText, acquiredText);
        detailViewPager.setAdapter(pagerAdapter);
        detailViewPager.setOffscreenPageLimit(pagerAdapter.getItemCount());
        detailTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        new TabLayoutMediator(detailTabLayout, detailViewPager, (tab, position) -> {
            switch (position) {
                case PlantDetailPagerAdapter.POSITION_MEASUREMENTS:
                    tab.setText(R.string.plant_detail_tab_measurements);
                    tab.setContentDescription(R.string.plant_detail_tab_measurements);
                    break;
                case PlantDetailPagerAdapter.POSITION_REMINDERS:
                    tab.setText(R.string.plant_detail_tab_reminders);
                    tab.setContentDescription(R.string.plant_detail_tab_reminders);
                    break;
                case PlantDetailPagerAdapter.POSITION_GALLERY:
                    tab.setText(R.string.plant_detail_tab_gallery);
                    tab.setContentDescription(R.string.plant_detail_tab_gallery);
                    break;
                case PlantDetailPagerAdapter.POSITION_ENVIRONMENT:
                    tab.setText(R.string.plant_detail_tab_environment);
                    tab.setContentDescription(R.string.plant_detail_tab_environment);
                    break;
                case PlantDetailPagerAdapter.POSITION_DETAILS:
                default:
                    tab.setText(R.string.plant_detail_tab_details);
                    tab.setContentDescription(R.string.plant_detail_tab_details);
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
            (requestKey, bundle) -> {
                presenter.loadCareRecommendations();
                presenter.loadLatestLightSummary();
            }
        );
        getSupportFragmentManager().setFragmentResultListener(
            PlantEditFragment.RESULT_KEY,
            this,
            (requestKey, bundle) -> presenter.loadCareRecommendations()
        );

        View mainView = findViewById(R.id.main);
        InsetsUtils.applySystemWindowInsetsPadding(mainView, false, true, false, true);
        InsetsUtils.requestApplyInsetsWhenAttached(mainView);
    }

    @Override
    public void onDetailInfoViewsReady(@NonNull PlantDetailInfoFragment.DetailViews views) {
        ambientValueView = views.ambientValueView;
        ambientBandView = views.ambientBandView;
        cameraValueView = views.cameraValueView;
        cameraBandView = views.cameraBandView;
        ambientColumn = views.ambientColumnView;
        lightMeterSpacer = views.lightMeterSpacerView;
        lightSummaryCard = views.lightSummaryCardView;
        naturalDliValueView = views.naturalDliValueView;
        naturalDliTimestampView = views.naturalDliTimestampView;
        artificialDliValueView = views.artificialDliValueView;
        artificialDliTimestampView = views.artificialDliTimestampView;
        careTipsCard = views.careTipsCardView;
        careTipsList = views.careRecommendationsListView;
        careTipsLoadingView = views.careRecommendationsLoadingView;
        careTipsEmptyView = views.careRecommendationsEmptyView;
        speciesMetadataCard = views.speciesMetadataCardView;
        wateringSection = views.wateringSectionView;
        wateringFrequencyView = views.wateringFrequencyView;
        wateringSoilView = views.wateringSoilView;
        wateringToleranceView = views.wateringToleranceView;
        temperatureSection = views.temperatureSectionView;
        temperatureRangeView = views.temperatureRangeView;
        humiditySection = views.humiditySectionView;
        humidityRangeView = views.humidityRangeView;
        toxicitySection = views.toxicitySectionView;
        toxicityTextView = views.toxicityTextView;
        careTipsSection = views.careTipsSectionView;
        careTipsTextView = views.careTipsTextView;
        metadataUnavailableView = views.metadataUnavailableView;
        wateringIconView = views.wateringIconView;
        temperatureIconView = views.temperatureIconView;
        humidityIconView = views.humidityIconView;
        toxicityIconView = views.toxicityIconView;
        careTipsIconView = views.careTipsIconView;
        showLightSummary(new LightSummary(null, null, null, null));

        if (wateringIconView != null) {
            TooltipCompat.setTooltipText(wateringIconView, getString(R.string.metadata_tooltip_watering));
        }
        if (temperatureIconView != null) {
            TooltipCompat.setTooltipText(temperatureIconView, getString(R.string.metadata_tooltip_temperature));
        }
        if (humidityIconView != null) {
            TooltipCompat.setTooltipText(humidityIconView, getString(R.string.metadata_tooltip_humidity));
        }
        if (toxicityIconView != null) {
            TooltipCompat.setTooltipText(toxicityIconView, getString(R.string.metadata_tooltip_toxicity));
        }
        if (careTipsIconView != null) {
            TooltipCompat.setTooltipText(careTipsIconView, getString(R.string.metadata_tooltip_care_tips));
        }

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
                public void onDismiss(@NonNull CareRecommendation recommendation) {
                    presenter.dismissRecommendation(recommendation.getId());
                }
            });
            careTipsList.setAdapter(careRecommendationAdapter);
        }

        metadataViewsReady = true;
        if (pendingMetadataFallback != null) {
            bindSpeciesMetadataUnavailable(pendingMetadataFallback);
            pendingMetadataFallback = null;
        } else if (pendingMetadata != null) {
            bindSpeciesMetadata(pendingMetadata);
            pendingMetadata = null;
        }

        presenter.loadCareRecommendations();
    }

    @Override
    public void showSpeciesMetadata(PlantMetadataViewModel metadata) {
        if (!metadataViewsReady) {
            pendingMetadata = metadata;
            pendingMetadataFallback = null;
            return;
        }
        bindSpeciesMetadata(metadata);
    }

    @Override
    public void showSpeciesMetadataUnavailable(String message) {
        if (!metadataViewsReady) {
            pendingMetadata = null;
            pendingMetadataFallback = message;
            return;
        }
        bindSpeciesMetadataUnavailable(message);
    }

    @Override
    public String getSpeciesMetadataUnavailableText() {
        return getString(R.string.metadata_unavailable);
    }

    @Override
    public void showLightSummary(@NonNull LightSummary summary) {
        if (lightSummaryCard == null || naturalDliValueView == null || naturalDliTimestampView == null
            || artificialDliValueView == null || artificialDliTimestampView == null) {
            return;
        }
        Float natural = summary.getNaturalDli();
        Float artificial = summary.getArtificialDli();
        if (natural == null && artificial == null) {
            lightSummaryCard.setVisibility(View.GONE);
            naturalDliValueView.setText(R.string.plant_detail_light_natural_placeholder);
            naturalDliTimestampView.setText(null);
            naturalDliTimestampView.setVisibility(View.GONE);
            artificialDliValueView.setText(R.string.plant_detail_light_artificial_placeholder);
            artificialDliTimestampView.setText(null);
            artificialDliTimestampView.setVisibility(View.GONE);
            return;
        }
        lightSummaryCard.setVisibility(View.VISIBLE);
        if (natural != null) {
            naturalDliValueView.setText(getString(R.string.plant_detail_light_natural_value,
                naturalDliFormat.format(natural)));
            Long timestamp = summary.getNaturalTimestamp();
            if (timestamp != null) {
                naturalDliTimestampView.setVisibility(View.VISIBLE);
                naturalDliTimestampView.setText(getString(R.string.plant_detail_light_natural_updated,
                    naturalDliDateFormat.format(new Date(timestamp))));
            } else {
                naturalDliTimestampView.setText(null);
                naturalDliTimestampView.setVisibility(View.GONE);
            }
        } else {
            naturalDliValueView.setText(R.string.plant_detail_light_natural_placeholder);
            naturalDliTimestampView.setText(null);
            naturalDliTimestampView.setVisibility(View.GONE);
        }

        if (artificial != null) {
            artificialDliValueView.setText(getString(R.string.plant_detail_light_artificial_value,
                naturalDliFormat.format(artificial)));
            Long timestamp = summary.getArtificialTimestamp();
            if (timestamp != null) {
                artificialDliTimestampView.setVisibility(View.VISIBLE);
                artificialDliTimestampView.setText(getString(R.string.plant_detail_light_artificial_updated,
                    naturalDliDateFormat.format(new Date(timestamp))));
            } else {
                artificialDliTimestampView.setText(null);
                artificialDliTimestampView.setVisibility(View.GONE);
            }
        } else {
            artificialDliValueView.setText(R.string.plant_detail_light_artificial_placeholder);
            artificialDliTimestampView.setText(null);
            artificialDliTimestampView.setVisibility(View.GONE);
        }
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
        presenter.loadLatestLightSummary();
        ensureLocationPermission();
    }

    @Override
    protected void onPause() {
        if (lightSensorHelper != null) {
            lightSensorHelper.stop();
        }
        stopCameraAnalysis();
        super.onPause();
    }

    private void ensureLocationPermission() {
        if (locationProvider == null) {
            return;
        }
        if (locationProvider.hasLocationPermission()) {
            fetchAndPersistLocation();
        } else if (!locationPermissionDenied && locationPermissionLauncher != null) {
            locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            });
        }
    }

    private void fetchAndPersistLocation() {
        if (locationProvider == null || repository == null) {
            return;
        }
        locationProvider.getCurrentLocation(this::cacheLocation,
            error -> Log.w("PlantDetailActivity", "Unable to obtain location", error));
    }

    private void cacheLocation(@NonNull Location location) {
        if (repository == null) {
            return;
        }
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.NaN;
        repository.updateLastKnownLocation(location.getLatitude(), location.getLongitude(), accuracy,
            System.currentTimeMillis());
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.plant_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_export_plant) {
            presenter.onExportRequested();
            return true;
        } else if (itemId == R.id.action_add_photo) {
            launchCamera();
            refreshPlantPhotos();
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

    private void bindSpeciesMetadata(PlantMetadataViewModel metadata) {
        pendingMetadata = null;
        pendingMetadataFallback = null;
        if (speciesMetadataCard != null) {
            speciesMetadataCard.setVisibility(View.VISIBLE);
        }
        if (metadataUnavailableView != null) {
            metadataUnavailableView.setVisibility(View.GONE);
        }
        if (wateringSection != null) {
            wateringSection.setVisibility(View.VISIBLE);
        }
        PlantMetadataViewModel.WateringInfo watering = metadata.getWateringInfo();
        if (wateringFrequencyView != null) {
            String frequency = watering != null ? watering.getFrequency() : null;
            if (TextUtils.isEmpty(frequency)) {
                wateringFrequencyView.setText(getString(R.string.metadata_watering_frequency_fallback));
            } else {
                wateringFrequencyView.setText(frequency);
            }
        }
        if (wateringSoilView != null) {
            String soil = watering != null ? watering.getSoilType() : null;
            if (TextUtils.isEmpty(soil)) {
                wateringSoilView.setVisibility(View.GONE);
            } else {
                wateringSoilView.setVisibility(View.VISIBLE);
                wateringSoilView.setText(getString(R.string.metadata_watering_soil, soil));
            }
        }
        if (wateringToleranceView != null) {
            String tolerance = watering != null ? watering.getTolerance() : null;
            if (TextUtils.isEmpty(tolerance)) {
                wateringToleranceView.setVisibility(View.GONE);
            } else {
                wateringToleranceView.setVisibility(View.VISIBLE);
                wateringToleranceView.setText(getString(R.string.metadata_watering_tolerance, tolerance));
            }
        }
        if (temperatureSection != null) {
            temperatureSection.setVisibility(View.VISIBLE);
        }
        if (temperatureRangeView != null) {
            temperatureRangeView.setText(formatRangeText(metadata.getTemperatureRange(),
                R.string.metadata_temperature_range_fallback,
                getString(R.string.metadata_temperature_unit)));
        }
        if (humiditySection != null) {
            humiditySection.setVisibility(View.VISIBLE);
        }
        if (humidityRangeView != null) {
            humidityRangeView.setText(formatRangeText(metadata.getHumidityRange(),
                R.string.metadata_humidity_range_fallback,
                getString(R.string.metadata_humidity_unit)));
        }
        if (toxicitySection != null) {
            toxicitySection.setVisibility(View.VISIBLE);
        }
        if (toxicityTextView != null) {
            Boolean toxic = metadata.getToxicToPets();
            if (toxic == null) {
                toxicityTextView.setText(R.string.metadata_toxic_unknown);
            } else if (toxic) {
                toxicityTextView.setText(R.string.metadata_toxic_true);
            } else {
                toxicityTextView.setText(R.string.metadata_toxic_false);
            }
        }
        if (careTipsSection != null) {
            careTipsSection.setVisibility(View.VISIBLE);
        }
        if (careTipsTextView != null) {
            List<String> tips = metadata.getCareTips();
            if (tips == null || tips.isEmpty()) {
                careTipsTextView.setText(getString(R.string.metadata_care_tips_fallback));
            } else {
                careTipsTextView.setText(buildBulletList(tips));
            }
        }
    }

    private void bindSpeciesMetadataUnavailable(String message) {
        pendingMetadata = null;
        pendingMetadataFallback = null;
        if (speciesMetadataCard != null) {
            speciesMetadataCard.setVisibility(View.VISIBLE);
        }
        if (wateringSection != null) {
            wateringSection.setVisibility(View.GONE);
        }
        if (temperatureSection != null) {
            temperatureSection.setVisibility(View.GONE);
        }
        if (humiditySection != null) {
            humiditySection.setVisibility(View.GONE);
        }
        if (toxicitySection != null) {
            toxicitySection.setVisibility(View.GONE);
        }
        if (careTipsSection != null) {
            careTipsSection.setVisibility(View.GONE);
        }
        if (metadataUnavailableView != null) {
            metadataUnavailableView.setVisibility(View.VISIBLE);
            metadataUnavailableView.setText(message);
        }
    }

    private String formatRangeText(@Nullable PlantMetadataViewModel.RangeInfo range,
                                   int fallbackResId,
                                   String unit) {
        if (range == null || !range.hasValues()) {
            return getString(fallbackResId);
        }
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(0);
        Float min = range.getMin();
        Float max = range.getMax();
        if (min != null && max != null) {
            if (Math.abs(min - max) < 0.01f) {
                return getString(R.string.metadata_range_single_value, format.format(min), unit);
            }
            return getString(R.string.metadata_range_between, format.format(min), format.format(max), unit);
        }
        if (min != null) {
            return getString(R.string.metadata_range_min_only, format.format(min), unit);
        }
        return getString(R.string.metadata_range_max_only, format.format(max), unit);
    }

    private String buildBulletList(List<String> tips) {
        StringBuilder builder = new StringBuilder();
        for (String tip : tips) {
            if (tip != null && !tip.trim().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append('\u2022').append(' ').append(tip.trim());
            }
        }
        if (builder.length() == 0) {
            return getString(R.string.metadata_care_tips_fallback);
        }
        return builder.toString();
    }

    private enum LightBand {
        LOW,
        MEDIUM,
        HIGH
    }
}
