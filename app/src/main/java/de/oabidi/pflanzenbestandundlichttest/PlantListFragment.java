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
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import de.oabidi.pflanzenbestandundlichttest.ExportManager;
import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

/**
 * Fragment displaying the list of plants.
 */
public class PlantListFragment extends Fragment implements PlantAdapter.OnPlantClickListener, PlantListPresenter.View {
    private PlantRepository repository;
    private PlantListPresenter presenter;
    private PlantAdapter adapter;
    private ProgressBar progressBar;
    private AlertDialog progressDialog;

    public static PlantListFragment newInstance(PlantRepository repository) {
        PlantListFragment fragment = new PlantListFragment();
        fragment.repository = repository;
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (repository == null) {
            repository = RepositoryProvider.getRepository(context);
        }
    }

    @Override
    public void showProgress() {
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

    @Override
    public void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressBar.setProgress(0);
        }
    }
    
    private final ActivityResultLauncher<String> exportLauncher =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
            if (uri != null) {
                presenter.startExport(uri);
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

    @Override
    public void requestExport(String fileName) {
        exportLauncher.launch(fileName);
    }

    @Override
    public void requestImport() {
        importLauncher.launch(new String[]{"application/zip"});
    }

    private void showImportChoiceDialog(@NonNull Uri uri) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_import_data)
            .setMessage(R.string.import_choice_message)
            .setPositiveButton(R.string.import_merge, (d, w) -> presenter.startImport(uri, ImportManager.Mode.MERGE))
            .setNegativeButton(R.string.import_replace, (d, w) -> presenter.startImport(uri, ImportManager.Mode.REPLACE))
            .setNeutralButton(android.R.string.cancel, null)
            .show();
    }

    private void showWarningDialog(@NonNull List<ImportManager.ImportWarning> warnings) {
        String message = ImportManager.summarizeWarnings(warnings);
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_warnings_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show();
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
        Context context = requireContext().getApplicationContext();
        PlantRepository repo = repository;
        if (repo == null) {
            repo = RepositoryProvider.getRepository(requireContext());
            repository = repo;
        }
        if (!(context instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        ExecutorService executor = ((ExecutorProvider) context).getIoExecutor();
        ExportManager exportManager = new ExportManager(context, repo, executor);
        ImportManager importManager = new ImportManager(context, executor);
        presenter = new PlantListPresenter(this, repo, context, exportManager, importManager);
        presenter.refreshPlants();

        getParentFragmentManager().setFragmentResultListener(PlantEditFragment.RESULT_KEY, this,
            (requestKey, bundle) -> presenter.refreshPlants());

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
        adapter.submitList(new ArrayList<>(plants));
    }

    @Override
    public void showSearchResults(List<Plant> plants) {
        adapter.submitList(new ArrayList<>(plants));
    }

    @Override
    public void showError(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onExportProgress(int current, int total) {
        if (isAdded() && progressBar != null) {
            progressBar.setMax(total);
            progressBar.setProgress(current);
        }
    }

    @Override
    public void onExportResult(boolean success, Uri uri) {
        hideProgress();
        if (!isAdded()) {
            return;
        }
        if (success) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/zip");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Snackbar.make(requireView(), R.string.export_success, Snackbar.LENGTH_LONG)
                .setAction(R.string.share_backup, v -> startActivity(
                    Intent.createChooser(shareIntent, getString(R.string.share_backup))))
                .show();
        } else {
            Toast.makeText(requireContext(), R.string.export_failure, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onImportProgress(int current, int total) {
        if (isAdded() && progressBar != null) {
            progressBar.setMax(total);
            progressBar.setProgress(current);
        }
    }

    @Override
    public void onImportResult(boolean success, @Nullable ImportManager.ImportError error,
                               List<ImportManager.ImportWarning> warnings,
                               @Nullable String message) {
        hideProgress();
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        if (success && warnings != null && !warnings.isEmpty()) {
            showWarningDialog(warnings);
        }
    }

    @Override
    public void onPlantClick(Plant plant) {
        Intent intent = MainActivity.createPlantDetailIntent(requireContext(), plant);
        startActivity(intent);
    }

    @Override
    public void onPlantLongClick(Plant plant) {
        CharSequence[] options = {
            getString(R.string.action_edit_plant),
            getString(R.string.action_delete_plant)
        };
        new AlertDialog.Builder(requireContext())
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    navigateToEdit(plant);
                } else if (which == 1) {
                    String message = getString(R.string.confirm_delete_plant);
                    new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.action_delete_plant)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d2, w) ->
                            presenter.deletePlant(plant, () ->
                                Snackbar.make(requireView(), R.string.plant_deleted, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.action_undo, v -> presenter.insertPlant(plant))
                                    .show()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                }
            })
            .show();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.main_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        Objects.requireNonNull(searchView).setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                presenter.filterPlants(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                presenter.filterPlants(newText);
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
        } else if (itemId == R.id.action_species_targets) {
            navigateToSpeciesTargets();
            return true;
        } else if (itemId == R.id.action_export_data) {
            presenter.requestExport();
            return true;
        } else if (itemId == R.id.action_import_data) {
            presenter.requestImport();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToEdit(@Nullable Plant plant) {
        PlantEditFragment fragment = PlantEditFragment.newInstance(plant, repository);
        getParentFragmentManager().beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit();
    }

    private void navigateToSpeciesTargets() {
        SpeciesTargetListFragment fragment = SpeciesTargetListFragment.newInstance(repository);
        getParentFragmentManager().beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit();
    }
}
