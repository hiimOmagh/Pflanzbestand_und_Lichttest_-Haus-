package de.oabidi.pflanzenbestandundlichttest;

import static de.oabidi.pflanzenbestandundlichttest.R.*;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Main screen of the app showing current light sensor readings and a list of plants.
 * It delegates background work to {@link MainPresenter} and persists calibration data so
 * that light calculations remain stable between sessions.
 */
public class MainActivity extends AppCompatActivity implements PlantAdapter.OnPlantClickListener, MainPresenter.View {
    private static final String PREFS_NAME = "settings";
    private static final String KEY_CALIBRATION = "calibration_factor";
    private static final String KEY_LIGHT_HOURS = "light_hours";

    private TextView luxRawView;
    private TextView luxView;
    private TextView ppfdView;
    private TextView dliView;
    private EditText kInput;
    private EditText hoursInput;
    private Button saveMeasurementButton;
    private float calibrationFactor; // Factor converting lux to PPFD
    private float lightHours; // Expected daily exposure used for DLI
    private float lastLux;
    private float lastPpfd;
    private SharedPreferences preferences;
    private MainPresenter presenter;
    private PlantAdapter adapter;
    private List<Plant> plants;

    /**
     * Sets up views, sensor helper, and loads persisted calibration settings.
     * This method is invoked when the activity is created for the first time.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        luxRawView = findViewById(R.id.lux_raw_value);
        luxView = findViewById(R.id.lux_value);
        ppfdView = findViewById(R.id.ppfd_value);
        dliView = findViewById(R.id.dli_value);
        kInput = findViewById(R.id.k_input);
        hoursInput = findViewById(R.id.light_hours_input);
        saveMeasurementButton = findViewById(R.id.measurement_save_button);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        calibrationFactor = preferences.getFloat(KEY_CALIBRATION, 0.0185f); // default for typical LEDs
        lightHours = preferences.getFloat(KEY_LIGHT_HOURS, 24f); // assume continuous light as fallback

        presenter = new MainPresenter(this, this, calibrationFactor, lightHours);

        kInput.setText(getString(R.string.format_calibration_factor, calibrationFactor));
        hoursInput.setText(getString(R.string.format_light_hours, lightHours));

        kInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float value = Float.parseFloat(s.toString());
                    if (value > 0f) {
                        calibrationFactor = value;
                        // Persist user-provided calibration for consistent conversions
                        preferences.edit().putFloat(KEY_CALIBRATION, calibrationFactor).apply();
                        presenter.setCalibrationFactor(calibrationFactor);
                        kInput.setError(null);
                    } else {
                        kInput.setError(getString(R.string.error_positive_number));
                    }
                } catch (NumberFormatException e) {
                    kInput.setError(getString(R.string.error_positive_number));
                }
            }
        });

        hoursInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float value = Float.parseFloat(s.toString());
                    if (value > 0f) {
                        lightHours = value;
                        // Store expected light duration for later DLI calculations
                        preferences.edit().putFloat(KEY_LIGHT_HOURS, lightHours).apply();
                        presenter.setLightHours(lightHours);
                        hoursInput.setError(null);
                    } else {
                        hoursInput.setError(getString(R.string.error_positive_number));
                    }
                } catch (NumberFormatException e) {
                    hoursInput.setError(getString(R.string.error_positive_number));
                }
            }
        });

        saveMeasurementButton.setOnClickListener(v -> {
            if (!plants.isEmpty()) {
                long plantId = plants.get(0).getId();
                presenter.saveMeasurement(plantId, lastLux, lastPpfd);
            }
        });

        if (!presenter.hasLightSensor()) {
            luxRawView.setText(R.string.no_light_sensor);
            luxView.setText("");
            ppfdView.setText("");
            dliView.setText("");
        }

        RecyclerView recyclerView = findViewById(R.id.plant_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        plants = new ArrayList<>();
        adapter = new PlantAdapter(this);
        recyclerView.setAdapter(adapter);
        presenter.refreshPlants();
    }

    /**
     * Starts listening for light sensor updates when the activity comes to the foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        presenter.start();
    }

    /**
     * Stops sensor updates to conserve resources when the activity is not visible.
     */
    @Override
    protected void onPause() {
        super.onPause();
        presenter.stop();
    }

    /**
     * Inflates the menu providing actions to add, update, or delete plants.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Handles menu selections that modify the plant database.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_add) {
            presenter.insertPlant(new Plant(
                getString(R.string.new_plant_name),
                getString(R.string.new_plant_description),
                getString(R.string.unknown),
                getString(R.string.unknown),
                System.currentTimeMillis(),
                null));
            return true;
        } else if (itemId == R.id.action_update) {
            if (!plants.isEmpty()) {
                Plant first = plants.get(0);
                String description = first.getDescription();
                if (description == null) {
                    description = getString(R.string.default_description);
                }
                first.setDescription(description + getString(R.string.updated_suffix));
                presenter.updatePlant(first);
            }
            return true;
        } else if (itemId == R.id.action_delete) {
            if (!plants.isEmpty()) {
                presenter.deletePlant(plants.get(0));
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPlantClick(Plant plant) {
        Intent intent = new Intent(this, PlantDetailActivity.class);
        intent.putExtra("name", plant.getName());
        intent.putExtra("description", plant.getDescription());
        intent.putExtra("species", plant.getSpecies());
        intent.putExtra("locationHint", plant.getLocationHint());
        intent.putExtra("acquiredAtEpoch", plant.getAcquiredAtEpoch());
        String photo = plant.getPhotoUri() != null ? plant.getPhotoUri().toString() : "";
        intent.putExtra("photoUri", photo);
        startActivity(intent);
    }

    @Override
    public void showLightData(float rawLux, float lux, float ppfd, float dli) {
        luxRawView.setText(getString(R.string.format_raw_lux, rawLux));
        luxView.setText(getString(R.string.format_lux, lux));
        ppfdView.setText(getString(R.string.format_ppfd, ppfd));
        dliView.setText(getString(R.string.format_dli, dli));
        lastLux = lux;
        lastPpfd = ppfd;
    }

    @Override
    public void showPlants(List<Plant> plants) {
        this.plants = plants;
        adapter.submitList(new ArrayList<>(plants));
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
