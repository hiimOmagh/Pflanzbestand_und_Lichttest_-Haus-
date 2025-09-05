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

import java.util.List;

/**
 * Fragment responsible for displaying live light measurements.
 */
public class LightMeasurementFragment extends Fragment implements LightMeasurementPresenter.View {
    private static final String PREFS_NAME = "settings";
    private static final String KEY_CALIBRATION = "calibration_factor";
    private static final String KEY_LIGHT_HOURS = "light_hours";
    private static final String KEY_SAMPLE_SIZE = "sample_size";

    private TextView luxRawView;
    private TextView luxView;
    private TextView ppfdView;
    private TextView dliView;
    private Spinner plantSelector;
    private Button saveMeasurementButton;
    private TextView locationCheckView;
    private float calibrationFactor;
    private float lightHours;
    private float lastLux;
    private float lastPpfd;
    private float lastDli;
    private int sampleSize;
    private long selectedPlantId = -1;
    private List<Plant> plants;
    private SharedPreferences preferences;
    private LightMeasurementPresenter presenter;

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

        Context context = requireContext().getApplicationContext();
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        calibrationFactor = preferences.getFloat(KEY_CALIBRATION, 0.0185f);
        lightHours = preferences.getFloat(KEY_LIGHT_HOURS, 24f);
        sampleSize = preferences.getInt(KEY_SAMPLE_SIZE, 10);

        presenter = new LightMeasurementPresenter(this, context, calibrationFactor, lightHours, sampleSize);

        plantSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                if (plants != null && position >= 0 && position < plants.size()) {
                    selectedPlantId = plants.get(position).getId();
                    presenter.selectPlant(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPlantId = -1;
            }
        });

        saveMeasurementButton.setOnClickListener(v -> {
            if (selectedPlantId != -1) {
                presenter.saveMeasurement(selectedPlantId, lastLux, lastPpfd, lastDli,
                    () -> requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), R.string.measurement_saved, Toast.LENGTH_SHORT).show()));
            } else {
                Toast.makeText(requireContext(), R.string.error_select_plant, Toast.LENGTH_SHORT).show();
            }
        });

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
        float k = preferences.getFloat(KEY_CALIBRATION, calibrationFactor);
        float hours = preferences.getFloat(KEY_LIGHT_HOURS, lightHours);
        int size = preferences.getInt(KEY_SAMPLE_SIZE, sampleSize);
        if (k != calibrationFactor) {
            calibrationFactor = k;
            presenter.setCalibrationFactor(calibrationFactor);
        }
        if (hours != lightHours) {
            lightHours = hours;
            presenter.setLightHours(lightHours);
        }
        if (size != sampleSize) {
            sampleSize = size;
            presenter.setSampleSize(sampleSize);
        }
        presenter.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        presenter.stop();
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

    @Override
    public void showLightData(float rawLux, float lux, float ppfd, float dli) {
        luxRawView.setText(getString(R.string.format_raw_lux, rawLux));
        luxView.setText(getString(R.string.format_lux, lux));
        ppfdView.setText(getString(R.string.format_ppfd, ppfd));
        dliView.setText(getString(R.string.format_dli, dli));
        lastLux = lux;
        lastPpfd = ppfd;
        lastDli = dli;
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
            plantSelector.setSelection(0);
            selectedPlantId = plants.get(0).getId();
        } else {
            selectedPlantId = -1;
        }
    }
}
