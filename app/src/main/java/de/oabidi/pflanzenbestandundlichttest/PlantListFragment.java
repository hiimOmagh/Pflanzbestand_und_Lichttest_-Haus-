package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
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
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

/**
 * Fragment displaying the list of plants.
 */
public class PlantListFragment extends Fragment implements PlantAdapter.OnPlantClickListener, PlantListPresenter.View {
    private PlantListPresenter presenter;
    private PlantAdapter adapter;
    private List<Plant> plants = new ArrayList<>();
    private ExportManager exportManager;
    private ImportManager importManager;
    private ProgressBar progressBar;
    private AlertDialog progressDialog;

    private void showProgress() {
        if (!isAdded()) {
            return;
        }
        if (progressDialog == null) {
            View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_progress, null);
            progressBar = dialogView.findViewById(R.id.progress_bar);
            progressDialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();
        }
        progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
        progressDialog.show();
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressBar.setProgress(0);
        }
    }
    
    private final ActivityResultLauncher<String> exportLauncher =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
            if (uri != null) {
                showProgress();
                exportManager.export(uri, success -> {
                    hideProgress();
                    if (isAdded()) {
                        int msg = success ? R.string.export_success : R.string.export_failure;
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        if (success) {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("application/zip");
                            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(shareIntent,
                                getString(R.string.share_backup)));
                        }
                    }
                }, (current, total) -> {
                    if (isAdded()) {
                        progressBar.setMax(total);
                        progressBar.setProgress(current);
                    }
                });
            } else if (isAdded()) {
                Toast.makeText(requireContext(), R.string.export_failure, Toast.LENGTH_SHORT).show();
            }
        });

    private final ActivityResultLauncher<String[]> importLauncher =
        registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                if (isAdded()) {
                    showImportChoiceDialog(uri);
                }
            } else if (isAdded()) {
                Toast.makeText(requireContext(), R.string.import_failure, Toast.LENGTH_SHORT).show();
            }
        });

    private void showImportChoiceDialog(@NonNull Uri uri) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_import_data)
            .setMessage(R.string.import_choice_message)
            .setPositiveButton(R.string.import_merge, (d, w) -> startImport(uri, ImportManager.Mode.MERGE))
            .setNegativeButton(R.string.import_replace, (d, w) -> startImport(uri, ImportManager.Mode.REPLACE))
            .setNeutralButton(android.R.string.cancel, null)
            .show();
    }

    private void startImport(@NonNull Uri uri, ImportManager.Mode mode) {
        showProgress();
        importManager.importData(uri, mode, (success, hadWarnings) -> {
            hideProgress();
            if (isAdded()) {
                int msg = success ? R.string.import_success : R.string.import_failure;
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                if (success && hadWarnings) {
                    Toast.makeText(requireContext(), R.string.import_image_warning, Toast.LENGTH_LONG).show();
                }
            }
        }, (current, total) -> {
            if (isAdded()) {
                progressBar.setMax(total);
                progressBar.setProgress(current);
            }
        });
    }

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
        importManager = new ImportManager(requireContext().getApplicationContext());

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
    public void onDestroyView() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressBar = null;
        progressDialog = null;
        super.onDestroyView();
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
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterPlants(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterPlants(newText);
                return true;
            }
        });
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
                Plant plant = plants.get(0);
                presenter.deletePlant(plant, () ->
                    Snackbar.make(requireView(), R.string.plant_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo, v -> presenter.insertPlant(plant))
                        .show());
            }
            return true;
        } else if (itemId == R.id.action_species_targets) {
            navigateToSpeciesTargets();
            return true;
        } else if (itemId == R.id.action_export_data) {
            exportLauncher.launch(getString(R.string.export_file_name));
            return true;
        } else if (itemId == R.id.action_import_data) {
            importLauncher.launch(new String[]{"text/csv"});
            return true;
        } else if (itemId == R.id.action_help) {
            getParentFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, new OnboardingFragment())
                .addToBackStack(null)
                .commit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void filterPlants(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.submitList(new ArrayList<>(plants));
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        PlantRepository repository = ((PlantApp) ctx.getApplicationContext()).getRepository();
        repository.searchPlants(query, result -> {
            if (isAdded()) {
                adapter.submitList(result);
            }
        });
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
