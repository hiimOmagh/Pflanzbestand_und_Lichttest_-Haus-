package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying the list of plants.
 */
public class PlantListFragment extends Fragment implements PlantAdapter.OnPlantClickListener, PlantListPresenter.View {
    private PlantListPresenter presenter;
    private PlantAdapter adapter;
    private List<Plant> plants = new ArrayList<>();

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
        }
        return super.onOptionsItemSelected(item);
    }
}
