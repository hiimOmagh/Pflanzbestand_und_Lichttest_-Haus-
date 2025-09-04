package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.common.ui.BarChartView;

/**
 * Displays simple statistics such as recent PPFD and DLI measurements for a plant.
 */
public class StatsFragment extends Fragment {
    private PlantRepository repository;
    private TextView diaryCountsView;
    private Spinner plantSelector;
    private BarChartView chart;
    private List<Plant> plants;
    private long selectedPlantId = -1;

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
        plantSelector = view.findViewById(R.id.stats_plant_selector);
        repository = new PlantRepository(requireContext().getApplicationContext());

        plantSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                if (plants != null && position >= 0 && position < plants.size()) {
                    selectedPlantId = plants.get(position).getId();
                    loadDataForPlant(selectedPlantId);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPlantId = -1;
            }
        });

        repository.getAllPlants(result -> {
            plants = result;
            String[] names = new String[plants.size()];
            for (int i = 0; i < plants.size(); i++) {
                names[i] = plants.get(i).getName();
            }
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                names);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            plantSelector.setAdapter(spinnerAdapter);
            if (!plants.isEmpty()) {
                plantSelector.setSelection(0);
            }
        });
    }

    private void loadDataForPlant(long plantId) {
        repository.recentMeasurementsForPlant(plantId, 30,
            list -> chart.setMeasurements(list));
        repository.diaryEntriesForPlant(plantId, entries -> updateDiaryCounts(entries));
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
