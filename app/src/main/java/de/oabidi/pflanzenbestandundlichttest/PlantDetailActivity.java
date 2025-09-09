package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
public class PlantDetailActivity extends AppCompatActivity {
    private long plantId;
    private ExportManager exportManager;
    private ActivityResultLauncher<String> exportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Allow the layout to extend into the system bar areas.
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plant_detail);

        plantId = getIntent().getLongExtra("plantId", -1L); // Database ID of the plant
        String name = getIntent().getStringExtra("name"); // Plant's display name
        String description = getIntent().getStringExtra("description"); // Additional notes about the plant
        String species = getIntent().getStringExtra("species"); // Botanical species identifier
        String locationHint = getIntent().getStringExtra("locationHint"); // Where the plant is located
        long acquiredAtEpoch = getIntent().getLongExtra("acquiredAtEpoch", 0L); // Acquisition time in milliseconds since the Unix epoch
        String photoUriStr = getIntent().getStringExtra("photoUri"); // String form of the plant photo URI
        Uri photoUri = null;
        if (photoUriStr != null && !photoUriStr.isEmpty()) {
            photoUri = Uri.parse(photoUriStr);
        }

        TextView nameView = findViewById(R.id.detail_name);
        TextView descriptionView = findViewById(R.id.detail_description);
        TextView speciesView = findViewById(R.id.detail_species);
        TextView locationHintView = findViewById(R.id.detail_location_hint);
        TextView acquiredAtView = findViewById(R.id.detail_acquired_at);
        ImageView photoView = findViewById(R.id.detail_photo_uri);
        View diaryButton = findViewById(R.id.detail_diary);

        exportManager = new ExportManager(this);
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
            if (uri != null) {
                exportManager.export(uri, plantId, success -> {
                    int msg = success ? R.string.export_success : R.string.export_failure;
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(this, R.string.export_failure, Toast.LENGTH_SHORT).show();
            }
        });

        setTextOrFallback(nameView, name);
        setTextOrFallback(descriptionView, description);
        setTextOrFallback(speciesView, species);
        setTextOrFallback(locationHintView, locationHint);
        if (acquiredAtEpoch == 0) {
            acquiredAtView.setText(R.string.unknown_date);
        } else {
            DateFormat dateFormat = DateFormat.getDateTimeInstance();
            String acquiredAt = dateFormat.format(new Date(acquiredAtEpoch));
            acquiredAtView.setText(acquiredAt);
        }
        if (photoUri == null) {
            photoView.setVisibility(View.GONE);
        } else {
            photoView.setImageURI(photoUri);
            photoView.setVisibility(View.VISIBLE);
            String cdName = (name == null || name.isEmpty())
                ? getString(R.string.unknown)
                : name;
            photoView.setContentDescription(
                getString(R.string.plant_photo_desc_format, cdName));
        }

        diaryButton.setOnClickListener(v -> {
            DiaryFragment fragment = DiaryFragment.newInstance(plantId);
            getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
        });

        // After drawing edge-to-edge, pad the root view so content isn't
        // obscured by system bars like the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.plant_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_export_plant) {
            exportLauncher.launch(getString(R.string.export_file_name));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sets the text of the given view or a fallback when the text is {@code null} or empty.
     *
     * @param view the TextView to update
     * @param text the text to set, may be {@code null}
     */
    private static void setTextOrFallback(TextView view, String text) {
        view.setText((text == null || text.isEmpty())
            ? view.getContext().getString(R.string.placeholder_dash)
            : text);
    }
}
