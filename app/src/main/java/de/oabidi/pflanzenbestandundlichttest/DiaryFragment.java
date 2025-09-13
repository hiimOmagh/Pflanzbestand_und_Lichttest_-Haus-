package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

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
import android.widget.Toast;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fragment showing diary entries.
 *
 * <p>When instantiated with {@link #newInstance(long)} it displays entries for the
 * provided plant. If no plant ID is supplied the fragment simply shows an empty
 * view, making it suitable as a top-level destination as well.</p>
 */
public class DiaryFragment extends Fragment implements DiaryPresenter.View {
    private static final String ARG_PLANT_ID = "plantId";

    private long plantId = -1;
    private DiaryPresenter presenter;
    private DiaryEntryAdapter adapter;
    private PlantRepository repository;
    private ActivityResultLauncher<String> photoPickerLauncher;
    private Consumer<Uri> photoPickedCallback;
    private String searchQuery = "";
    private String typeFilter = "";

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
        setHasOptionsMenu(true);
        Bundle args = getArguments();
        if (args != null) {
            plantId = args.getLong(ARG_PLANT_ID, -1);
        }
        Context context = requireContext().getApplicationContext();
        repository = ((PlantApp) context).getRepository();
        presenter = new DiaryPresenter(this, repository, plantId, context);
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

        Spinner filterSpinner = view.findViewById(R.id.diary_filter_spinner);
        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.diary_filter_labels,
            android.R.layout.simple_spinner_item);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(filterAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                String[] typeCodes = getResources().getStringArray(R.array.diary_filter_codes);
                typeFilter = typeCodes[position];
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
                    presenter.updateEntry(entry);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        }, entry -> {
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_delete_diary_entry)
                .setMessage(R.string.confirm_delete_diary_entry)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                    presenter.deleteEntry(entry, () ->
                        Snackbar.make(requireView(), R.string.diary_entry_deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_undo, v -> presenter.insertEntry(entry))
                            .show()
                    ))
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
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.diary_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.diary_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        Objects.requireNonNull(searchView).setQueryHint(getString(R.string.diary_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                loadEntries();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                loadEntries();
                return true;
            }
        });
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
                presenter.insertEntry(entry);

                String daysText = remindEdit.getText().toString().trim();
                if (!daysText.isEmpty()) {
                    try {
                        int days = Integer.parseInt(daysText);
                        String message = note.isEmpty() ? label : label + getString(R.string.note_separator) + note;
                        boolean scheduled = ReminderScheduler.scheduleReminder(
                            requireContext(),
                            repository,
                            days,
                            message,
                            plantId,
                            e -> Toast.makeText(requireContext(), R.string.error_database, Toast.LENGTH_SHORT).show()
                        );
                        if (!scheduled) {
                            Toast.makeText(requireContext(), R.string.error_positive_number, Toast.LENGTH_SHORT).show();
                        }
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
        String query = searchQuery.trim();
        if (!typeFilter.isEmpty()) {
            String typeQuery = "type:" + typeFilter;
            if (query.isEmpty()) {
                query = typeQuery;
            } else {
                query = typeQuery + " AND " + query;
            }
        }
        presenter.loadEntries(query);
    }

    @Override
    public void showEntries(java.util.List<DiaryEntry> entries) {
        for (DiaryEntry entry : entries) {
            String photo = entry.getPhotoUri();
            if (photo != null && photo.startsWith("content:")) {
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(
                        Uri.parse(photo), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
            }
        }
        adapter.submitList(entries);
    }

    @Override
    public void showError(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
