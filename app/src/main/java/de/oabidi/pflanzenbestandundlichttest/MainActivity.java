package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PlantAdapter.OnPlantClickListener, LightSensorHelper.OnLuxChangedListener {
    private TextView luxView;
    private TextView ppfdView;
    private TextView dliView;
    private LightSensorHelper lightSensorHelper;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        luxView = findViewById(R.id.lux_value);
        ppfdView = findViewById(R.id.ppfd_value);
        dliView = findViewById(R.id.dli_value);
        lightSensorHelper = new LightSensorHelper(this, this);

        RecyclerView recyclerView = findViewById(R.id.plant_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Plant> plants = Arrays.asList(
                new Plant("Rose", "A thorny flowering shrub."),
                new Plant("Tulip", "A bulbous spring-flowering plant."),
                new Plant("Sunflower", "A tall plant with a large daisy-like flower.")
        );
        PlantAdapter adapter = new PlantAdapter(plants, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lightSensorHelper.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        lightSensorHelper.stop();
    }

    @Override
    public void onPlantClick(Plant plant) {
        Intent intent = new Intent(this, PlantDetailActivity.class);
        intent.putExtra("name", plant.getName());
        intent.putExtra("description", plant.getDescription());
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
