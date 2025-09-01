package de.oabidi.pflanzenbestandundlichttest;

import static de.oabidi.pflanzenbestandundlichttest.R.*;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainActivity extends AppCompatActivity implements PlantAdapter.OnPlantClickListener, LightSensorHelper.OnLuxChangedListener {
    private static final String PREFS_NAME = "settings";
    private static final String KEY_CALIBRATION = "calibration_factor";
    private static final String KEY_LIGHT_HOURS = "light_hours";

    private TextView luxView;
    private TextView ppfdView;
    private TextView dliView;
    private LightSensorHelper lightSensorHelper;
    private EditText kInput;
    private EditText hoursInput;
    private float calibrationFactor;
    private float lightHours;
    private SharedPreferences preferences;
    private PlantRepository plantRepository;
    private PlantAdapter adapter;
    private List<Plant> plants;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        luxView = findViewById(R.id.lux_value);
        ppfdView = findViewById(R.id.ppfd_value);
        dliView = findViewById(R.id.dli_value);
        lightSensorHelper = new LightSensorHelper(this, this);
        kInput = findViewById(R.id.k_input);
        hoursInput = findViewById(R.id.light_hours_input);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        calibrationFactor = preferences.getFloat(KEY_CALIBRATION, 0.0185f);
        lightHours = preferences.getFloat(KEY_LIGHT_HOURS, 24f);

        kInput.setText(getString(R.string.format_calibration_factor, calibrationFactor));
        hoursInput.setText(getString(R.string.format_light_hours, lightHours));

        kInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    calibrationFactor = Float.parseFloat(s.toString());
                    preferences.edit().putFloat(KEY_CALIBRATION, calibrationFactor).apply();
                } catch (NumberFormatException ignored) {}
            }
        });

        hoursInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    lightHours = Float.parseFloat(s.toString());
                    preferences.edit().putFloat(KEY_LIGHT_HOURS, lightHours).apply();
                } catch (NumberFormatException ignored) {}
            }
        });

        if (!lightSensorHelper.hasLightSensor()) {
            luxView.setText(R.string.no_light_sensor);
            ppfdView.setText("");
            dliView.setText("");
        }

        RecyclerView recyclerView = findViewById(R.id.plant_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        plantRepository = new PlantRepository(this);
        plants = plantRepository.getAllPlants();
        adapter = new PlantAdapter(plants, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensorHelper.hasLightSensor()) {
            lightSensorHelper.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        lightSensorHelper.stop();
    }

    private void refreshPlants() {
        plants = plantRepository.getAllPlants();
        adapter.updatePlants(plants);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                plantRepository.insert(new Plant(
                    getString(R.string.new_plant_name),
                    getString(R.string.new_plant_description),
                    getString(R.string.unknown),
                    getString(R.string.unknown),
                    System.currentTimeMillis(),
                    Uri.EMPTY));
                refreshPlants();
                return true;
            case R.id.action_update:
                if (!plants.isEmpty()) {
                    Plant first = plants.get(0);
                    first.setDescription(first.getDescription() + getString(R.string.updated_suffix));
                    plantRepository.update(first);
                    refreshPlants();
                }
                return true;
            case R.id.action_delete:
                if (!plants.isEmpty()) {
                    plantRepository.delete(plants.get(0));
                    refreshPlants();
                }
                return true;
            default:
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
        intent.putExtra("photoUri", plant.getPhotoUri().toString());
        startActivity(intent);
    }

    @Override
    public void onLuxChanged(float lux) {
        float ppfd = LightMath.ppfdFromLux(lux, calibrationFactor);
        float dli = LightMath.dliFromPpfd(ppfd, lightHours);
        luxView.setText(getString(R.string.format_lux, lux));
        ppfdView.setText(getString(R.string.format_ppfd, ppfd));
        dliView.setText(getString(R.string.format_dli, dli));
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
