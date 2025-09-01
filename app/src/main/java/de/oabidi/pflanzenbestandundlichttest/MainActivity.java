package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PlantAdapter.OnPlantClickListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
    public void onPlantClick(Plant plant) {
        Intent intent = new Intent(this, PlantDetailActivity.class);
        intent.putExtra("name", plant.getName());
        intent.putExtra("description", plant.getDescription());
        startActivity(intent);
    }
}