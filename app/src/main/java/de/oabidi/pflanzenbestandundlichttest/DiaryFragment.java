package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment showing diary entries.
 *
 * <p>When instantiated with {@link #newInstance(long)} it displays entries for the
 * provided plant. If no plant ID is supplied the fragment simply shows an empty
 * view, making it suitable as a top-level destination as well.</p>
 */
public class DiaryFragment extends Fragment {
    private static final String ARG_PLANT_ID = "plantId";

    private long plantId = -1;
    private PlantRepository repository;
    private ArrayAdapter<String> adapter;
    private final List<DiaryEntry> entries = new ArrayList<>();

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
        listView.setOnItemLongClickListener((parent, v1, position, id) -> {
            DiaryEntry entry = entries.get(position);
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_delete_diary_entry)
                .setMessage(R.string.confirm_delete_diary_entry)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                    repository.deleteDiaryEntry(entry, this::loadEntries))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return true;
        });

        FloatingActionButton fab = view.findViewById(R.id.fab_add_entry);

        if (plantId < 0) {
            listView.setVisibility(View.GONE);
            fab.setVisibility(View.GONE);
            return;
        }

        fab.setOnClickListener(v -> addEntry());

        loadEntries();
    }

    private void addEntry() {
        if (plantId < 0) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_diary_entry, null);
        Spinner typeSpinner = dialogView.findViewById(R.id.diary_entry_type);
        EditText noteEdit = dialogView.findViewById(R.id.diary_entry_note);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.diary_entry_types,
            android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(spinnerAdapter);

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_add_diary_entry)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String label = (String) typeSpinner.getSelectedItem();
                String type = codeFromLabel(label);
                String note = noteEdit.getText().toString();
                DiaryEntry entry = new DiaryEntry(plantId, System.currentTimeMillis(), type, note);
                repository.insertDiaryEntry(entry, this::loadEntries);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void loadEntries() {
        if (plantId < 0) {
            return;
        }
        repository.diaryEntriesForPlant(plantId, result -> {
            entries.clear();
            entries.addAll(result);
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            List<String> items = new ArrayList<>();
            for (DiaryEntry e : entries) {
                String note = e.getNote() != null ? e.getNote() : "";
                String item = df.format(new Date(e.getTimeEpoch())) + " – " + labelFromCode(e.getType());
                if (!note.isEmpty()) {
                    item += " – " + note;
                }
                items.add(item);
            }
            adapter.clear();
            adapter.addAll(items);
        });
    }

    private String codeFromLabel(String label) {
        if (getString(R.string.diary_type_water).equals(label)) {
            return DiaryEntry.TYPE_WATER;
        } else if (getString(R.string.diary_type_fertilize).equals(label)) {
            return DiaryEntry.TYPE_FERTILIZE;
        } else if (getString(R.string.diary_type_prune).equals(label)) {
            return DiaryEntry.TYPE_PRUNE;
        }
        return label;
    }

    private String labelFromCode(String code) {
        switch (code) {
            case DiaryEntry.TYPE_WATER:
                return getString(R.string.diary_type_water);
            case DiaryEntry.TYPE_FERTILIZE:
                return getString(R.string.diary_type_fertilize);
            case DiaryEntry.TYPE_PRUNE:
                return getString(R.string.diary_type_prune);
            default:
                return code;
        }
    }
}
