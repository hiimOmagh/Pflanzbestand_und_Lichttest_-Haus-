package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainActivity extends AppCompatActivity implements PlantAdapter.OnPlantClickListener, LightSensorHelper.OnLuxChangedListener {
    private TextView luxView;
    private TextView ppfdView;
    private TextView dliView;
    private LightSensorHelper lightSensorHelper;
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
                    "New Plant",
                    "A newly added plant.",
                    "Unknown",
                    "Unknown",
                    System.currentTimeMillis(),
                    Uri.EMPTY));
                refreshPlants();
                return true;
            case R.id.action_update:
                if (!plants.isEmpty()) {
                    Plant first = plants.get(0);
                    first.setDescription(first.getDescription() + " (updated)");
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
        float ppfd = luxToPPFD(lux);
        float dli = ppfd * 0.0864f;
        luxView.setText(String.format("Lux: %.2f", lux));
        ppfdView.setText(String.format("PPFD: %.2f µmol/m²/s", ppfd));
        dliView.setText(String.format("DLI: %.2f mol/m²/day", dli));
    }

    private float luxToPPFD(float lux) {
        return lux * 0.0185f;
    }
}
