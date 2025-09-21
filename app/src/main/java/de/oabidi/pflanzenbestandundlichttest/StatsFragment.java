package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.common.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import de.oabidi.pflanzenbestandundlichttest.common.ui.BarChartView;
import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.MeasurementListFragment;

/**
 * Displays simple statistics such as recent PPFD and DLI measurements for a plant.
 */
public class StatsFragment extends Fragment implements StatsPresenter.View {
    private PlantRepository repository;
    private StatsPresenter presenter;
    private TextView diaryCountsView;
    private TextView dliView;
    private Button plantSelectorButton;
    private BarChartView chart;
    private List<Plant> plants;
    private final List<Long> selectedPlantIds = new ArrayList<>();
    private View viewMeasurementsButton;
    private TextView placeholderView;
    private SharedPreferences preferences;

    public static StatsFragment newInstance(PlantRepository repository) {
        StatsFragment fragment = new StatsFragment();
        fragment.repository = repository;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.applySystemWindowInsetsPadding(view, false, true, false, true);
        InsetsUtils.requestApplyInsetsWhenAttached(view);
        chart = view.findViewById(R.id.stats_chart);
        diaryCountsView = view.findViewById(R.id.stats_diary_counts);
        dliView = view.findViewById(R.id.stats_dli);
        plantSelectorButton = view.findViewById(R.id.stats_select_plants);
        viewMeasurementsButton = view.findViewById(R.id.stats_view_measurements);
        placeholderView = view.findViewById(R.id.stats_placeholder);
        Context context = requireContext().getApplicationContext();
        PlantRepository repo = repository != null
            ? repository
            : RepositoryProvider.getRepository(requireContext());
        presenter = new StatsPresenter(this, repo, context);
        preferences = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);

        if (savedInstanceState != null) {
            long[] ids = savedInstanceState.getLongArray("selectedPlantIds");
            if (ids != null) {
                for (long id : ids) {
                    selectedPlantIds.add(id);
                }
            }
        }
        if (selectedPlantIds.isEmpty()) {
            long id = preferences.getLong(SettingsKeys.KEY_SELECTED_PLANT, -1);
            if (id != -1) {
                selectedPlantIds.add(id);
            }
        }

        plantSelectorButton.setOnClickListener(v -> {
            if (plants != null && !plants.isEmpty()) {
                showPlantSelectionDialog();
            }
        });

        viewMeasurementsButton.setOnClickListener(v -> {
            if (selectedPlantIds.size() == 1) {
                long id = selectedPlantIds.get(0);
                MeasurementListFragment fragment = MeasurementListFragment.newInstance(id, repo);
                requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, fragment)
                    .addToBackStack(null)
                    .commit();
            }
        });

        presenter.loadPlants();
        updateButtonText();
    }

    @Override
    public void onResume() {
        super.onResume();
        presenter.loadPlants();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        long[] ids = new long[selectedPlantIds.size()];
        for (int i = 0; i < selectedPlantIds.size(); i++) {
            ids[i] = selectedPlantIds.get(i);
        }
        outState.putLongArray("selectedPlantIds", ids);
    }

    @Override
    public void showPlants(List<Plant> result) {
        plants = result;
        if (plants.isEmpty()) {
            plantSelectorButton.setVisibility(View.GONE);
            chart.setVisibility(View.GONE);
            placeholderView.setVisibility(View.VISIBLE);
            viewMeasurementsButton.setEnabled(false);
            dliView.setText(getString(R.string.dli_placeholder));
            diaryCountsView.setText(getString(R.string.stats_no_diary_entries));
            selectedPlantIds.clear();
            updateButtonText();
        } else {
            plantSelectorButton.setVisibility(View.VISIBLE);
            chart.setVisibility(View.VISIBLE);
            placeholderView.setVisibility(View.GONE);
            if (selectedPlantIds.isEmpty()) {
                selectedPlantIds.add(plants.get(0).getId());
            }
            updateButtonText();
            presenter.loadDataForPlants(selectedPlantIds);
            viewMeasurementsButton.setEnabled(selectedPlantIds.size() == 1);
        }
    }

    @Override
    public void showMeasurements(Map<Long, List<Measurement>> data) {
        if (data == null) {
            chart.setMeasurements(null);
            return;
        }
        Map<String, List<Measurement>> named = new HashMap<>();
        for (Map.Entry<Long, List<Measurement>> e : data.entrySet()) {
            String name = findPlantName(e.getKey());
            named.put(name, e.getValue());
        }
        chart.setMeasurements(named);
    }

    @Override
    public void showDiaryCounts(String text) {
        diaryCountsView.setText(text);
    }

    @Override
    public void showDli(String text) {
        dliView.setText(text);
    }

    @Override
    public void showError(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showPlantSelectionDialog() {
        String[] names = new String[plants.size()];
        boolean[] checked = new boolean[plants.size()];
        for (int i = 0; i < plants.size(); i++) {
            Plant p = plants.get(i);
            names[i] = p.getName();
            checked[i] = selectedPlantIds.contains(p.getId());
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_plants)
            .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                long id = plants.get(which).getId();
                if (isChecked) {
                    if (!selectedPlantIds.contains(id)) {
                        selectedPlantIds.add(id);
                    }
                } else {
                    selectedPlantIds.remove(id);
                }
            })
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                updateButtonText();
                presenter.loadDataForPlants(selectedPlantIds);
                viewMeasurementsButton.setEnabled(selectedPlantIds.size() == 1);
                long first = selectedPlantIds.isEmpty() ? -1 : selectedPlantIds.get(0);
                preferences.edit().putLong(SettingsKeys.KEY_SELECTED_PLANT, first).apply();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void updateButtonText() {
        if (plantSelectorButton == null) {
            return;
        }
        if (plants == null || selectedPlantIds.isEmpty()) {
            plantSelectorButton.setText(R.string.select_plants);
        } else {
            List<String> names = new ArrayList<>();
            for (long id : selectedPlantIds) {
                names.add(findPlantName(id));
            }
            plantSelectorButton.setText(TextUtils.join(getString(R.string.list_separator), names));
        }
    }

    private String findPlantName(long id) {
        if (plants != null) {
            for (Plant p : plants) {
                if (p.getId() == id) {
                    return p.getName();
                }
            }
        }
        return "";
    }
}
