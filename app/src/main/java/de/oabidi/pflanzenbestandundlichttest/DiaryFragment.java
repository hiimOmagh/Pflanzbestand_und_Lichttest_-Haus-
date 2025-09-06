package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
    private DiaryEntryAdapter adapter;
    private ActivityResultLauncher<String> photoPickerLauncher;
    private Consumer<Uri> photoPickedCallback;
    private Spinner filterSpinner;

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
        photoPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (photoPickedCallback != null && uri != null) {
                requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                photoPickedCallback.accept(uri);
            }
        });
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
        RecyclerView listView = view.findViewById(R.id.diary_list);
        listView.setLayoutManager(new LinearLayoutManager(requireContext()));
        filterSpinner = view.findViewById(R.id.diary_filter_spinner);
        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.diary_filter_labels,
            android.R.layout.simple_spinner_item);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(filterAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                loadEntries();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        adapter = new DiaryEntryAdapter(entry -> {
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            View dialogView = inflater.inflate(R.layout.dialog_diary_entry, null);
            Spinner typeSpinner = dialogView.findViewById(R.id.diary_entry_type);
            EditText noteEdit = dialogView.findViewById(R.id.diary_entry_note);
            Button photoButton = dialogView.findViewById(R.id.diary_entry_add_photo);
            final String[] photoUri = new String[]{entry.getPhotoUri()};
            photoButton.setOnClickListener(v2 -> {
                photoPickedCallback = uri -> photoUri[0] = uri.toString();
                photoPickerLauncher.launch("image/*");
            });

            ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.diary_entry_type_labels,
                android.R.layout.simple_spinner_item);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            typeSpinner.setAdapter(spinnerAdapter);
            String[] typeCodes = getResources().getStringArray(R.array.diary_entry_type_codes);
            for (int i = 0; i < typeCodes.length; i++) {
                if (typeCodes[i].equals(entry.getType())) {
                    typeSpinner.setSelection(i);
                    break;
                }
            }
            noteEdit.setText(entry.getNote());

            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_edit_diary_entry)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String[] typeCodes1 = getResources().getStringArray(R.array.diary_entry_type_codes);
                    int pos = typeSpinner.getSelectedItemPosition();
                    entry.setType(typeCodes1[pos]);
                    entry.setNote(noteEdit.getText().toString());
                    entry.setPhotoUri(photoUri[0]);
                    repository.updateDiaryEntry(entry, this::loadEntries);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        }, entry -> {
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_delete_diary_entry)
                .setMessage(R.string.confirm_delete_diary_entry)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                    repository.deleteDiaryEntry(entry, () -> {
                        loadEntries();
                        Snackbar.make(requireView(), R.string.diary_entry_deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_undo, v -> repository.insertDiaryEntry(entry, this::loadEntries))
                            .show();
                    }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
        listView.setAdapter(adapter);

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
        EditText remindEdit = dialogView.findViewById(R.id.diary_entry_remind_days);
        Button photoButton = dialogView.findViewById(R.id.diary_entry_add_photo);
        final String[] photoUri = new String[1];
        photoButton.setOnClickListener(v -> {
            photoPickedCallback = uri -> photoUri[0] = uri.toString();
            photoPickerLauncher.launch("image/*");
        });

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.diary_entry_type_labels,
            android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(spinnerAdapter);

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_add_diary_entry)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String[] typeCodes = getResources().getStringArray(R.array.diary_entry_type_codes);
                String[] typeLabels = getResources().getStringArray(R.array.diary_entry_type_labels);
                int pos = typeSpinner.getSelectedItemPosition();
                String type = typeCodes[pos];
                String label = typeLabels[pos];
                String note = noteEdit.getText().toString();
                DiaryEntry entry = new DiaryEntry(plantId, System.currentTimeMillis(), type, note);
                entry.setPhotoUri(photoUri[0]);
                repository.insertDiaryEntry(entry, this::loadEntries);

                String daysText = remindEdit.getText().toString().trim();
                if (!daysText.isEmpty()) {
                    try {
                        int days = Integer.parseInt(daysText);
                        String message = note.isEmpty() ? label : label + getString(R.string.note_separator) + note;
                        ReminderScheduler.scheduleReminder(requireContext(), days, message);
                    } catch (NumberFormatException ignored) {
                    }
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void loadEntries() {
        if (plantId < 0) {
            return;
        }
        repository.diaryEntriesForPlant(plantId, result -> {
            if (filterSpinner != null) {
                String[] filterCodes = getResources().getStringArray(R.array.diary_filter_codes);
                int pos = filterSpinner.getSelectedItemPosition();
                String code = filterCodes[pos];
                if (!code.isEmpty()) {
                    List<DiaryEntry> filtered = new ArrayList<>();
                    for (DiaryEntry entry : result) {
                        if (entry.getType().equals(code)) {
                            filtered.add(entry);
                        }
                    }
                    adapter.submitList(filtered);
                    return;
                }
            }
            adapter.submitList(result);
        });
    }
}
