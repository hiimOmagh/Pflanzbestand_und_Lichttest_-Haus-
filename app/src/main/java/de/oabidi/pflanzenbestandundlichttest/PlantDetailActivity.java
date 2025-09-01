package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PlantDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plant_detail);

        String name = getIntent().getStringExtra("name");
        String description = getIntent().getStringExtra("description");
        String species = getIntent().getStringExtra("species");
        String locationHint = getIntent().getStringExtra("locationHint");
        long acquiredAtEpoch = getIntent().getLongExtra("acquiredAtEpoch", 0L);
        String photoUriStr = getIntent().getStringExtra("photoUri");
        Uri photoUri = photoUriStr != null ? Uri.parse(photoUriStr) : Uri.EMPTY;

        TextView nameView = findViewById(R.id.detail_name);
        TextView descriptionView = findViewById(R.id.detail_description);
        TextView speciesView = findViewById(R.id.detail_species);
        TextView locationHintView = findViewById(R.id.detail_location_hint);
        TextView acquiredAtView = findViewById(R.id.detail_acquired_at);
        TextView photoUriView = findViewById(R.id.detail_photo_uri);

        nameView.setText(name);
        descriptionView.setText(description);
        speciesView.setText(species);
        locationHintView.setText(locationHint);
        acquiredAtView.setText(String.valueOf(acquiredAtEpoch));
        photoUriView.setText(photoUri.toString());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}