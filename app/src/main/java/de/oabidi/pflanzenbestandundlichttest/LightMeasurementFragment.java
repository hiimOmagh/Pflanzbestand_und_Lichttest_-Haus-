package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

import java.util.List;

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
    private float calibrationFactor;
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

    public static LightMeasurementFragment newInstance(PlantRepository repository) {
        LightMeasurementFragment fragment = new LightMeasurementFragment();
        fragment.repository = repository;
        return fragment;
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
        luxRawView = view.findViewById(R.id.lux_raw_value);
        luxView = view.findViewById(R.id.lux_value);
        ppfdView = view.findViewById(R.id.ppfd_value);
        dliView = view.findViewById(R.id.dli_value);
        plantSelector = view.findViewById(R.id.plant_selector);
        saveMeasurementButton = view.findViewById(R.id.measurement_save_button);
        locationCheckView = view.findViewById(R.id.location_check_value);
        Button calibrateButton = view.findViewById(R.id.measurement_calibrate_button);

        Context context = requireContext().getApplicationContext();
        preferences = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        String calibrationString = preferences.getString(SettingsKeys.KEY_CALIBRATION, "0.0185");
        try {
            calibrationFactor = Float.parseFloat(calibrationString);
        } catch (NumberFormatException e) {
            calibrationFactor = 0.0185f;
        }
        String sampleSizeString = preferences.getString(SettingsKeys.KEY_SAMPLE_SIZE, "10");
        try {
            sampleSize = Integer.parseInt(sampleSizeString);
        } catch (NumberFormatException e) {
            sampleSize = 10;
        }
        if (sampleSize < 1) {
            sampleSize = 1;
        }

        PlantRepository repo = repository != null ? repository : new PlantRepository(context);
        presenter = new LightMeasurementPresenter(this, repo, context, calibrationFactor, sampleSize);

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
        String calibrationString = preferences.getString(SettingsKeys.KEY_CALIBRATION, Float.toString(calibrationFactor));
        float k;
        try {
            k = Float.parseFloat(calibrationString);
        } catch (NumberFormatException e) {
            k = 0.0185f;
        }
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
        if (k != calibrationFactor) {
            calibrationFactor = k;
            presenter.setCalibrationFactor(calibrationFactor);
        }
        if (size != sampleSize) {
            sampleSize = size;
            presenter.setSampleSize(sampleSize);
        }
        if (hours != lightHours) {
            lightHours = hours;
        }
        presenter.setLightHours(lightHours);
        presenter.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        presenter.stop();
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

    private void navigateToCalibration() {
        CalibrationFragment fragment = new CalibrationFragment();
        getParentFragmentManager().beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit();
    }

    private void resetSaveButton() {
        hasValidReading = false;
        if (saveMeasurementButton != null) {
            saveMeasurementButton.setEnabled(false);
        }
    }

    @Override
    public void showLightData(float rawLux, float lux, float ppfd, float dli) {
        luxRawView.setText(getString(R.string.format_raw_lux, rawLux));
        luxView.setText(getString(R.string.format_lux, lux));
        ppfdView.setText(getString(R.string.format_ppfd, ppfd));
        dliView.setText(getString(R.string.format_dli, dli));
        lastLux = lux;
        lastPpfd = ppfd;
        lastDli = dli;
        if (!hasValidReading) {
            saveMeasurementButton.setEnabled(true);
            hasValidReading = true;
        }
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
