package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.data.util.ExportManager;
/**
 * Fragment displaying the list of plants.
 */
public class PlantListFragment extends Fragment implements PlantAdapter.OnPlantClickListener, PlantListPresenter.View {
    private PlantListPresenter presenter;
    private PlantAdapter adapter;
    private List<Plant> plants = new ArrayList<>();
    private ExportManager exportManager;

    private final ActivityResultLauncher<String> exportLauncher =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
            if (uri != null) {
                exportManager.export(uri, success -> {
                    int msg = success ? R.string.export_success : R.string.export_failure;
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(requireContext(), R.string.export_failure, Toast.LENGTH_SHORT).show();
            }
        });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plant_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = view.findViewById(R.id.plant_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PlantAdapter(this);
        recyclerView.setAdapter(adapter);
        presenter = new PlantListPresenter(this, requireContext().getApplicationContext());
        presenter.refreshPlants();
        exportManager = new ExportManager(requireContext().getApplicationContext());

        getParentFragmentManager().setFragmentResultListener(PlantEditFragment.RESULT_KEY, this,
            (requestKey, bundle) -> {
                Plant plant = new Plant(
                    bundle.getString("name"),
                    bundle.getString("notes"),
                    bundle.getString("species"),
                    bundle.getString("location"),
                    bundle.getLong("acquired"),
                    bundle.containsKey("photo") ? Uri.parse(bundle.getString("photo")) : null);
                plant.setId(bundle.getLong("id", 0));
                if (plant.getId() == 0) {
                    presenter.insertPlant(plant);
                } else {
                    presenter.updatePlant(plant);
                }
            });

        setHasOptionsMenu(true);
    }

    @Override
    public void showPlants(List<Plant> plants) {
        this.plants = plants;
        adapter.submitList(new ArrayList<>(plants));
    }

    @Override
    public void onPlantClick(Plant plant) {
        Intent intent = new Intent(requireContext(), PlantDetailActivity.class);
        intent.putExtra("plantId", plant.getId());
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
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.main_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_add) {
            navigateToEdit(null);
            return true;
        } else if (itemId == R.id.action_update) {
            if (!plants.isEmpty()) {
                navigateToEdit(plants.get(0));
            }
            return true;
        } else if (itemId == R.id.action_delete) {
            if (!plants.isEmpty()) {
                presenter.deletePlant(plants.get(0));
            }
            return true;
        } else if (itemId == R.id.action_species_targets) {
            navigateToSpeciesTargets();
            return true;
        } else if (itemId == R.id.action_export_data) {
            exportLauncher.launch("plant_data.csv");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToEdit(@Nullable Plant plant) {
        PlantEditFragment fragment = PlantEditFragment.newInstance(plant);
        getParentFragmentManager().beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit();
    }

    private void navigateToSpeciesTargets() {
        SpeciesTargetListFragment fragment = new SpeciesTargetListFragment();
        getParentFragmentManager().beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit();
    }
}
