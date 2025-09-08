package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.text.TextUtils;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

import java.util.List;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.oabidi.pflanzenbestandundlichttest.common.ui.BarChartView;
import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.MeasurementListFragment;

/**
 * Displays simple statistics such as recent PPFD and DLI measurements for a plant.
 */
public class StatsFragment extends Fragment {
    private PlantRepository repository;
    private TextView diaryCountsView;
    private TextView dliView;
    private Button plantSelectorButton;
    private BarChartView chart;
    private List<Plant> plants;
    private final List<Long> selectedPlantIds = new ArrayList<>();
    private View viewMeasurementsButton;
    private TextView placeholderView;
    private static final int DLI_DAYS = 7;
    private SharedPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        chart = view.findViewById(R.id.stats_chart);
        diaryCountsView = view.findViewById(R.id.stats_diary_counts);
        dliView = view.findViewById(R.id.stats_dli);
        plantSelectorButton = view.findViewById(R.id.stats_select_plants);
        viewMeasurementsButton = view.findViewById(R.id.stats_view_measurements);
        placeholderView = view.findViewById(R.id.stats_placeholder);
        Context context = requireContext().getApplicationContext();
        repository = ((PlantApp) context).getRepository();
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
                MeasurementListFragment fragment = MeasurementListFragment.newInstance(id);
                requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, fragment)
                    .addToBackStack(null)
                    .commit();
            }
        });

        loadPlants();
        updateButtonText();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPlants();
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

    private void loadPlants() {
        repository.getAllPlants(result -> {
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
                loadDataForPlants(selectedPlantIds);
            }
        });
    }

    private void loadDataForPlants(List<Long> plantIds) {
        if (plantIds.isEmpty()) {
            chart.setMeasurements(null);
            dliView.setText(getString(R.string.dli_placeholder));
            diaryCountsView.setText(getString(R.string.stats_no_diary_entries));
            viewMeasurementsButton.setEnabled(false);
            return;
        }

        Map<String, List<Measurement>> data = new HashMap<>();
        Set<Long> remaining = new HashSet<>(plantIds);
        for (Long id : plantIds) {
            repository.recentMeasurementsForPlant(id, 30, list -> {
                String name = findPlantName(id);
                data.put(name, list);
                remaining.remove(id);
                if (remaining.isEmpty()) {
                    chart.setMeasurements(data);
                }
            });
        }

        if (plantIds.size() == 1) {
            long id = plantIds.get(0);
            repository.diaryEntriesForPlant(id, entries -> updateDiaryCounts(entries));
            computeDli(id);
            viewMeasurementsButton.setEnabled(true);
        } else {
            dliView.setText(getString(R.string.dli_placeholder));
            diaryCountsView.setText(getString(R.string.stats_no_diary_entries));
            viewMeasurementsButton.setEnabled(false);
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
        new AlertDialog.Builder(requireContext())
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
                loadDataForPlants(selectedPlantIds);
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

    private void computeDli(long plantId) {
        long now = System.currentTimeMillis();
        long end = startOfDay(now) + 86400000L;
        long start = end - DLI_DAYS * 86400000L;
        repository.sumPpfdForRange(plantId, start, end, sumPpfd ->
            repository.countDaysWithData(plantId, start, end, dayCount -> {
                if (dayCount > 0) {
                    float totalDli = (sumPpfd != null ? sumPpfd : 0f) * 0.0036f;
                    float avgDli = totalDli / dayCount;
                    dliView.setText(getString(R.string.format_dli, avgDli));
                } else {
                    dliView.setText(getString(R.string.dli_placeholder));
                }
            })
        );
    }

    private static long startOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void updateDiaryCounts(List<DiaryEntry> entries) {
        int water = 0;
        int fertilize = 0;
        int prune = 0;
        for (DiaryEntry e : entries) {
            String type = e.getType();
            if (DiaryEntry.TYPE_WATER.equals(type)) {
                water++;
            } else if (DiaryEntry.TYPE_FERTILIZE.equals(type)) {
                fertilize++;
            } else if (DiaryEntry.TYPE_PRUNE.equals(type)) {
                prune++;
            }
        }
        String text;
        if (entries.isEmpty()) {
            text = getString(R.string.stats_no_diary_entries);
        } else {
            String waterLabel = getString(R.string.diary_type_water);
            String fertilizeLabel = getString(R.string.diary_type_fertilize);
            String pruneLabel = getString(R.string.diary_type_prune);
            text = getString(R.string.format_diary_counts,
                waterLabel, water,
                fertilizeLabel, fertilize,
                pruneLabel, prune);
        }
        diaryCountsView.setText(text);
    }
}
