package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Displays simple statistics such as recent PPFD and DLI measurements for a plant.
 */
public class StatsFragment extends Fragment {
    private MeasurementAdapter adapter;
    private PlantRepository repository;
    private TextView diaryCountsView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView list = view.findViewById(R.id.stats_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MeasurementAdapter();
        list.setAdapter(adapter);
        diaryCountsView = view.findViewById(R.id.stats_diary_counts);
        repository = new PlantRepository(requireContext().getApplicationContext());
        repository.getAllPlants(plants -> {
            if (!plants.isEmpty()) {
                long plantId = plants.get(0).getId();
                repository.recentMeasurementsForPlant(plantId, 10, adapter::setMeasurements);
                repository.diaryEntriesForPlant(plantId, entries -> updateDiaryCounts(entries));
            }
        });
    }

    private void updateDiaryCounts(List<DiaryEntry> entries) {
        int water = 0;
        int fertilize = 0;
        int prune = 0;
        for (DiaryEntry e : entries) {
            String type = e.getType();
            if ("WATER".equals(type)) {
                water++;
            } else if ("FERTILIZE".equals(type)) {
                fertilize++;
            } else if ("PRUNE".equals(type)) {
                prune++;
            }
        }
        String text;
        if (entries.isEmpty()) {
            text = getString(R.string.stats_no_diary_entries);
        } else {
            text = getString(R.string.format_diary_counts, water, fertilize, prune);
        }
        diaryCountsView.setText(text);
    }
}
