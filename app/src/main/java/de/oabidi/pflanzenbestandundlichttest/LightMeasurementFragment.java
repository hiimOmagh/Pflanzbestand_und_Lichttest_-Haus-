package de.oabidi.pflanzenbestandundlichttest;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.oabidi.pflanzenbestandundlichttest.common.sensor.CameraLumaMonitor;
import de.oabidi.pflanzenbestandundlichttest.common.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Fragment responsible for displaying live light measurements.
 */
public class LightMeasurementFragment extends Fragment implements LightMeasurementPresenter.View {
    private PlantRepository repository;

    private TextView luxRawView;
    private TextView luxView;
    private TextView ppfdView;
    private TextView dliView;
    private Spinner plantSelector;
    private Button saveMeasurementButton;
    private TextView locationCheckView;
    private TextView cameraLumaView;
    private TextView cameraPpfdView;
    private TextView cameraDliView;
    private float lastLux;
    private float lastPpfd;
    private float lastDli;
    private int sampleSize;
    private float lightHours = 12f;
    private long selectedPlantId = -1;
    private List<Plant> plants;
    private SharedPreferences preferences;
    private LightMeasurementPresenter presenter;
    private boolean hasValidReading = false;
    private CameraLumaMonitor cameraLumaMonitor;
    private ExecutorService cameraExecutor;
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private ImageAnalysis cameraImageAnalysis;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private boolean cameraPermissionDenied;
    private boolean cameraUnavailable;
    private static final float DEFAULT_CALIBRATION = 0.0185f;

    public static LightMeasurementFragment newInstance(PlantRepository repository) {
        LightMeasurementFragment fragment = new LightMeasurementFragment();
        fragment.repository = repository;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_light_measurement, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.applySystemWindowInsetsPadding(view, false, true, false, true);
        InsetsUtils.requestApplyInsetsWhenAttached(view);
        luxRawView = view.findViewById(R.id.lux_raw_value);
        luxView = view.findViewById(R.id.lux_value);
        ppfdView = view.findViewById(R.id.ppfd_value);
        dliView = view.findViewById(R.id.dli_value);
        cameraLumaView = view.findViewById(R.id.camera_luma_value);
        cameraPpfdView = view.findViewById(R.id.camera_ppfd_value);
        cameraDliView = view.findViewById(R.id.camera_dli_value);
        plantSelector = view.findViewById(R.id.plant_selector);
        saveMeasurementButton = view.findViewById(R.id.measurement_save_button);
        locationCheckView = view.findViewById(R.id.location_check_value);
        Button calibrateButton = view.findViewById(R.id.measurement_calibrate_button);

        Context context = requireContext().getApplicationContext();
        preferences = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        String sampleSizeString = preferences.getString(SettingsKeys.KEY_SAMPLE_SIZE, "10");
        try {
            sampleSize = Integer.parseInt(sampleSizeString);
        } catch (NumberFormatException e) {
            sampleSize = 10;
        }
        if (sampleSize < 1) {
            sampleSize = 1;
        }

        PlantRepository repo = repository != null
            ? repository
            : RepositoryProvider.getRepository(context);
        repository = repo;
        presenter = new LightMeasurementPresenter(this, repo, context, DEFAULT_CALIBRATION, sampleSize);
        cameraLumaMonitor = new CameraLumaMonitor((raw, smoothed) -> presenter.onCameraLumaChanged(raw, smoothed));

        if (cameraLumaView != null) {
            cameraLumaView.setText(R.string.camera_luma_placeholder);
        }
        if (cameraPpfdView != null) {
            cameraPpfdView.setText(R.string.camera_ppfd_placeholder);
        }
        if (cameraDliView != null) {
            cameraDliView.setText(R.string.camera_dli_placeholder);
        }

        if (savedInstanceState != null) {
            selectedPlantId = savedInstanceState.getLong("selectedPlantId", -1);
        }
        if (selectedPlantId == -1) {
            selectedPlantId = preferences.getLong(SettingsKeys.KEY_SELECTED_PLANT, -1);
        }

        plantSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                if (plants != null && position >= 0 && position < plants.size()) {
                    selectedPlantId = plants.get(position).getId();
                    presenter.selectPlant(position);
                    preferences.edit().putLong(SettingsKeys.KEY_SELECTED_PLANT, selectedPlantId).apply();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPlantId = -1;
                preferences.edit().remove(SettingsKeys.KEY_SELECTED_PLANT).apply();
            }
        });

        calibrateButton.setOnClickListener(v -> navigateToCalibration());
        
        saveMeasurementButton.setOnClickListener(v -> {
            if (selectedPlantId != -1) {
                Measurement m = new Measurement(selectedPlantId, System.currentTimeMillis(), lastLux, lastPpfd, lastDli, null);
                presenter.saveMeasurement(m, () ->
                    Toast.makeText(requireContext(), R.string.measurement_saved, Toast.LENGTH_SHORT).show());
            } else {
                Toast.makeText(requireContext(), R.string.error_select_plant, Toast.LENGTH_SHORT).show();
            }
        });

        resetSaveButton();

        if (!presenter.hasLightSensor()) {
            luxRawView.setText(R.string.no_light_sensor);
            luxView.setText("");
            ppfdView.setText("");
            dliView.setText("");
        }

        presenter.refreshPlants();
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        resetSaveButton();
        String sizeString = preferences.getString(SettingsKeys.KEY_SAMPLE_SIZE, Integer.toString(sampleSize));
        int size;
        try {
            size = Integer.parseInt(sizeString);
        } catch (NumberFormatException e) {
            size = sampleSize;
        }
        if (size < 1) {
            size = 1;
        }
        String hoursString = preferences.getString(SettingsKeys.KEY_LIGHT_HOURS, Float.toString(lightHours));
        float hours;
        try {
            hours = Float.parseFloat(hoursString);
        } catch (NumberFormatException e) {
            hours = lightHours;
        }
        if (size != sampleSize) {
            sampleSize = size;
            presenter.setSampleSize(sampleSize);
        }
        if (hours != lightHours) {
            lightHours = hours;
        }
        presenter.setLightHours(lightHours);
        refreshCalibrationForSelection();
        presenter.start();
        startCameraUpdatesIfPossible();
    }

    @Override
    public void onPause() {
        super.onPause();
        presenter.stop();
        stopCameraAnalysis();
        resetSaveButton();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.light_measurement_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_calibrate) {
            navigateToCalibration();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        stopCameraAnalysis();
        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
            cameraExecutor = null;
        }
        super.onDestroy();
    }

    private void navigateToCalibration() {
        CalibrationFragment fragment = CalibrationFragment.newInstance(selectedPlantId);
        getParentFragmentManager().beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit();
    }

    private void refreshCalibrationForSelection() {
        if (repository == null) {
            return;
        }
        if (selectedPlantId != -1) {
            repository.getPlantCalibration(selectedPlantId, calibration -> {
                if (calibration != null) {
                    presenter.setCalibrationFactor(calibration.getAmbientFactor());
                    presenter.setCameraCalibrationFactor(calibration.getCameraFactor());
                } else {
                    presenter.setCalibrationFactor(DEFAULT_CALIBRATION);
                    presenter.setCameraCalibrationFactor(DEFAULT_CALIBRATION);
                }
            }, e -> {
                presenter.setCalibrationFactor(DEFAULT_CALIBRATION);
                presenter.setCameraCalibrationFactor(DEFAULT_CALIBRATION);
                if (isAdded()) {
                    showError(getString(R.string.error_database));
                }
            });
        } else {
            presenter.setCalibrationFactor(DEFAULT_CALIBRATION);
            presenter.setCameraCalibrationFactor(DEFAULT_CALIBRATION);
        }
    }

    private void startCameraUpdatesIfPossible() {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            cameraPermissionDenied = false;
            cameraUnavailable = false;
            startCameraAnalysis();
        } else if (!cameraPermissionDenied && cameraPermissionLauncher != null) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            showCameraPermissionDenied();
        }
    }

    private void startCameraAnalysis() {
        if (!isAdded()) {
            return;
        }
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
        cameraUnavailable = false;
        if (cameraLumaMonitor != null) {
            cameraLumaMonitor.reset();
        }
        showCameraPlaceholders();
        if (cameraProvider != null) {
            bindCameraAnalysis(cameraProvider);
            return;
        }
        Context context = requireContext();
        final ListenableFuture<ProcessCameraProvider> providerFuture =
            ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                cameraProvider = provider;
                bindCameraAnalysis(provider);
            } catch (ExecutionException | InterruptedException e) {
                showCameraUnavailable();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraAnalysis(@NonNull ProcessCameraProvider provider) {
        if (cameraImageAnalysis != null) {
            provider.unbind(cameraImageAnalysis);
        }
        cameraImageAnalysis = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build();
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
        if (cameraLumaMonitor == null) {
            cameraLumaMonitor = new CameraLumaMonitor((raw, smoothed) -> presenter.onCameraLumaChanged(raw, smoothed));
        }
        cameraImageAnalysis.setAnalyzer(cameraExecutor, cameraLumaMonitor);
        try {
            provider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_FRONT_CAMERA, cameraImageAnalysis);
        } catch (IllegalArgumentException e) {
            try {
                provider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_BACK_CAMERA, cameraImageAnalysis);
            } catch (IllegalArgumentException inner) {
                provider.unbind(cameraImageAnalysis);
                cameraImageAnalysis = null;
                showCameraUnavailable();
            }
        }
    }

    private void stopCameraAnalysis() {
        if (cameraLumaMonitor != null) {
            cameraLumaMonitor.reset();
        }
        if (cameraProvider != null && cameraImageAnalysis != null) {
            cameraProvider.unbind(cameraImageAnalysis);
            cameraImageAnalysis = null;
        }
        if (!cameraPermissionDenied && !cameraUnavailable) {
            showCameraPlaceholders();
        }
    }

    private void showCameraPlaceholders() {
        if (cameraLumaView != null) {
            cameraLumaView.setText(R.string.camera_luma_placeholder);
        }
        if (cameraPpfdView != null) {
            cameraPpfdView.setText(R.string.camera_ppfd_placeholder);
        }
        if (cameraDliView != null) {
            cameraDliView.setText(R.string.camera_dli_placeholder);
        }
    }

    private void showCameraPermissionDenied() {
        cameraPermissionDenied = true;
        cameraUnavailable = false;
        if (cameraLumaView != null) {
            cameraLumaView.setText(R.string.light_meter_camera_permission_required);
        }
        if (cameraPpfdView != null) {
            cameraPpfdView.setText(R.string.camera_ppfd_placeholder);
        }
        if (cameraDliView != null) {
            cameraDliView.setText(R.string.camera_dli_placeholder);
        }
    }

    private void showCameraUnavailable() {
        cameraUnavailable = true;
        cameraPermissionDenied = false;
        if (cameraLumaView != null) {
            cameraLumaView.setText(R.string.light_meter_camera_unavailable);
        }
        if (cameraPpfdView != null) {
            cameraPpfdView.setText(R.string.camera_ppfd_placeholder);
        }
        if (cameraDliView != null) {
            cameraDliView.setText(R.string.camera_dli_placeholder);
        }
    }

    private void resetSaveButton() {
        hasValidReading = false;
        if (saveMeasurementButton != null) {
            saveMeasurementButton.setEnabled(false);
        }
    }

    @Override
    public void showLightData(@Nullable LightMeasurementPresenter.LightReading ambient,
                              @Nullable LightMeasurementPresenter.LightReading camera) {
        View view = getView();
        if (view == null) {
            return;
        }
        view.post(() -> {
            if (ambient != null) {
                luxRawView.setText(getString(R.string.format_raw_lux, ambient.getRaw()));
                luxView.setText(getString(R.string.format_lux, ambient.getValue()));
                ppfdView.setText(getString(R.string.format_ppfd, ambient.getPpfd()));
                dliView.setText(getString(R.string.format_dli, ambient.getDli()));
                lastLux = ambient.getValue();
                lastPpfd = ambient.getPpfd();
                lastDli = ambient.getDli();
                if (!hasValidReading && saveMeasurementButton != null) {
                    saveMeasurementButton.setEnabled(true);
                    hasValidReading = true;
                }
            }
            if (camera != null) {
                if (cameraLumaView != null) {
                    cameraLumaView.setText(getString(R.string.format_camera_luma, camera.getValue()));
                }
                if (cameraPpfdView != null) {
                    cameraPpfdView.setText(getString(R.string.format_camera_ppfd, camera.getPpfd()));
                }
                if (cameraDliView != null) {
                    cameraDliView.setText(getString(R.string.format_camera_dli, camera.getDli()));
                }
            }
        });
    }

    @Override
    public void showRangeStatus(String status) {
        locationCheckView.setText(getString(R.string.format_location_check, status));
    }

    @Override
    public void showPlants(List<Plant> plants) {
        this.plants = plants;
        String[] names = new String[plants.size()];
        for (int i = 0; i < plants.size(); i++) {
            names[i] = plants.get(i).getName();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_item,
            names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        plantSelector.setAdapter(adapter);
        if (!plants.isEmpty()) {
            int selection = 0;
            if (selectedPlantId != -1) {
                for (int i = 0; i < plants.size(); i++) {
                    if (plants.get(i).getId() == selectedPlantId) {
                        selection = i;
                        break;
                    }
                }
            } else {
                selectedPlantId = plants.get(0).getId();
            }
            plantSelector.setSelection(selection);
        } else {
            selectedPlantId = -1;
        }
    }

    @Override
    public void showError(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("selectedPlantId", selectedPlantId);
    }
}
