package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Fragment showing diary entries for a specific plant.
 */
public class DiaryFragment extends Fragment {
    private static final String ARG_PLANT_ID = "plantId";

    private long plantId;
    private PlantRepository repository;
    private ArrayAdapter<String> adapter;

    /**
     * Creates a new instance of the fragment for the given plant.
     */
    public static DiaryFragment newInstance(long plantId) {
        DiaryFragment fragment = new DiaryFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLANT_ID, plantId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            plantId = args.getLong(ARG_PLANT_ID, -1);
        }
        repository = new PlantRepository(requireContext().getApplicationContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView listView = view.findViewById(R.id.diary_list);
        adapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_entry);
        fab.setOnClickListener(v -> addEntry());

        loadEntries();
    }

    private void addEntry() {
        DiaryEntry entry = new DiaryEntry(plantId, System.currentTimeMillis(), "note", "");
        repository.insertDiaryEntry(entry, this::loadEntries);
    }

    private void loadEntries() {
        repository.diaryEntriesForPlant(plantId, entries -> {
            List<String> items = new ArrayList<>();
            DateFormat df = DateFormat.getDateTimeInstance();
            for (DiaryEntry e : entries) {
                String time = df.format(new Date(e.getTimeEpoch()));
                String note = e.getNote() != null ? e.getNote() : "";
                items.add(time + " - " + note);
            }
            adapter.clear();
            adapter.addAll(items);
        });
    }
}
