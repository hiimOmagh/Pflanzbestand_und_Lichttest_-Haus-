package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import android.widget.Toast;

import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderScheduler;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.repository.DiaryRepository;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Fragment showing diary entries.
 *
 * <p>When instantiated with {@link #newInstance(PlantRepository, long)} it displays entries for the
 * provided plant. If no plant ID is supplied the fragment simply shows an empty
 * view, making it suitable as a top-level destination as well.</p>
 */
public class DiaryFragment extends Fragment implements DiaryPresenter.View {
    private static final String ARG_PLANT_ID = "plantId";

    private long plantId = -1;
    private DiaryPresenter presenter;
    private DiaryEntryAdapter adapter;
    private PlantRepository repository;
    private DiaryRepository diaryRepository;
    private ActivityResultLauncher<String> photoPickerLauncher;
    private Consumer<Uri> photoPickedCallback;
    private String searchQuery = "";
    private String typeFilter = "";
    private RecyclerView diaryListView;
    private View emptyView;

    /**
     * Creates a new instance of the fragment for the given plant.
     */
    public static DiaryFragment newInstance(PlantRepository repository, long plantId) {
        DiaryFragment fragment = new DiaryFragment();
        fragment.repository = repository;
        fragment.diaryRepository = repository.diaryRepository();
        Bundle args = new Bundle();
        args.putLong(ARG_PLANT_ID, plantId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (repository == null) {
            repository = RepositoryProvider.getRepository(context);
            diaryRepository = RepositoryProvider.getDiaryRepository(context);
        } else if (diaryRepository == null) {
            diaryRepository = repository.diaryRepository();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Bundle args = getArguments();
        if (args != null) {
            plantId = args.getLong(ARG_PLANT_ID, -1);
        }
        PlantRepository repo = repository;
        if (repo == null) {
            repo = RepositoryProvider.getRepository(requireContext());
            repository = repo;
        }
        if (diaryRepository == null) {
            diaryRepository = repo.diaryRepository();
        }
        Context context = requireContext().getApplicationContext();
        presenter = new DiaryPresenter(this, diaryRepository, plantId, context);
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
        InsetsUtils.requestApplyInsetsWhenAttached(view);
        diaryListView = view.findViewById(R.id.diary_list);
        emptyView = view.findViewById(R.id.diary_empty_view);
        diaryListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        diaryListView.setClipToPadding(false);
        InsetsUtils.applySystemWindowInsetsPadding(diaryListView, false, false, false, true);

        MaterialAutoCompleteTextView filterDropdown = view.findViewById(R.id.diary_filter_spinner);
        String[] filterLabels = getResources().getStringArray(R.array.diary_filter_labels);
        String[] filterCodes = getResources().getStringArray(R.array.diary_filter_codes);
        filterDropdown.setSimpleItems(filterLabels);
        int initialFilterIndex = 0;
        for (int i = 0; i < filterCodes.length; i++) {
            if (filterCodes[i].equals(typeFilter)) {
                initialFilterIndex = i;
                break;
            }
        }
        filterDropdown.setText(filterLabels[initialFilterIndex], false);
        filterDropdown.setOnItemClickListener((parent, view1, position, id) -> {
            typeFilter = filterCodes[position];
            loadEntries();
        });

        adapter = new DiaryEntryAdapter(entry -> {
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            View dialogView = inflater.inflate(R.layout.dialog_diary_entry, null);
            MaterialAutoCompleteTextView typeDropdown = dialogView.findViewById(R.id.diary_entry_type);
            TextInputEditText noteEdit = dialogView.findViewById(R.id.diary_entry_note);
            MaterialButton photoButton = dialogView.findViewById(R.id.diary_entry_add_photo);
            final String[] photoUri = new String[]{entry.getPhotoUri()};
            photoButton.setOnClickListener(v2 -> {
                photoPickedCallback = uri -> photoUri[0] = uri.toString();
                photoPickerLauncher.launch("image/*");
            });

            String[] typeCodes = getResources().getStringArray(R.array.diary_entry_type_codes);
            String[] typeLabels = getResources().getStringArray(R.array.diary_entry_type_labels);
            typeDropdown.setSimpleItems(typeLabels);
            final int[] selectedType = new int[]{0};
            for (int i = 0; i < typeCodes.length; i++) {
                if (typeCodes[i].equals(entry.getType())) {
                    selectedType[0] = i;
                    break;
                }
            }
            typeDropdown.setText(typeLabels[selectedType[0]], false);
            typeDropdown.setOnItemClickListener((parent, view1, position, id) -> selectedType[0] = position);
            noteEdit.setText(entry.getNote());

            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_edit_diary_entry)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int pos = resolveDropdownSelection(typeDropdown, typeLabels, selectedType[0]);
                    entry.setType(typeCodes[pos]);
                    entry.setNote(noteEdit.getText().toString());
                    entry.setPhotoUri(photoUri[0]);
                    presenter.updateEntry(entry);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        }, entry -> {
            new MaterialAlertDialogBuilder(requireContext())
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
        diaryListView.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_entry);
        InsetsUtils.applySystemWindowInsetsMargin(fab, false, false, false, true);

        if (plantId < 0) {
            diaryListView.setVisibility(View.GONE);
            if (emptyView != null) {
                emptyView.setVisibility(View.GONE);
            }
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
        MaterialAutoCompleteTextView typeDropdown = dialogView.findViewById(R.id.diary_entry_type);
        TextInputEditText noteEdit = dialogView.findViewById(R.id.diary_entry_note);
        TextInputEditText remindEdit = dialogView.findViewById(R.id.diary_entry_remind_days);
        TextView suggestionView = dialogView.findViewById(R.id.diary_entry_suggestion);
        suggestionView.setVisibility(View.GONE);
        AtomicBoolean userEdited = new AtomicBoolean(false);
        AtomicBoolean suppressWatcher = new AtomicBoolean(false);
        remindEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressWatcher.get()) {
                    userEdited.set(true);
                }
            }
        });
        MaterialButton photoButton = dialogView.findViewById(R.id.diary_entry_add_photo);
        final String[] photoUri = new String[1];
        photoButton.setOnClickListener(v -> {
            photoPickedCallback = uri -> photoUri[0] = uri.toString();
            photoPickerLauncher.launch("image/*");
        });

        String[] typeLabels = getResources().getStringArray(R.array.diary_entry_type_labels);
        String[] typeCodes = getResources().getStringArray(R.array.diary_entry_type_codes);
        typeDropdown.setSimpleItems(typeLabels);
        final int[] selectedType = new int[]{0};
        typeDropdown.setText(typeLabels[selectedType[0]], false);
        typeDropdown.setOnItemClickListener((parent, view1, position, id) -> selectedType[0] = position);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_add_diary_entry)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                int pos = resolveDropdownSelection(typeDropdown, typeLabels, selectedType[0]);
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

        PlantRepository repo = repository;
        if (repo != null) {
            final long targetPlantId = plantId;
            repo.getReminderSuggestion(targetPlantId, suggestion ->
                applyReminderSuggestion(suggestionView, remindEdit, suggestion, suppressWatcher, userEdited));
            repo.computeReminderSuggestion(targetPlantId, suggestion ->
                    applyReminderSuggestion(suggestionView, remindEdit, suggestion, suppressWatcher, userEdited),
                e -> {
                    if (isAdded()) {
                        Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show();
                    }
                });
        }
    }

    private void applyReminderSuggestion(TextView suggestionView,
                                         TextInputEditText remindEdit,
                                         @Nullable ReminderSuggestion suggestion,
                                         AtomicBoolean suppressWatcher,
                                         AtomicBoolean userEdited) {
        if (!isAdded()) {
            return;
        }
        if (suggestion == null) {
            suggestionView.setVisibility(View.GONE);
            return;
        }
        String explanation = suggestion.getExplanation();
        if (explanation == null || explanation.trim().isEmpty()) {
            suggestionView.setVisibility(View.GONE);
        } else {
            suggestionView.setVisibility(View.VISIBLE);
            suggestionView.setText(explanation);
        }
        if (!userEdited.get()) {
            String value = String.valueOf(suggestion.getSuggestedIntervalDays());
            String current = remindEdit.getText().toString().trim();
            if (!value.equals(current)) {
                suppressWatcher.set(true);
                remindEdit.setText(value);
                remindEdit.setSelection(value.length());
                suppressWatcher.set(false);
            }
        }
    }

    private int resolveDropdownSelection(MaterialAutoCompleteTextView dropdown, String[] labels,
                                         int fallbackIndex) {
        CharSequence text = dropdown.getText();
        if (text != null) {
            for (int i = 0; i < labels.length; i++) {
                if (labels[i].contentEquals(text)) {
                    return i;
                }
            }
        }
        return fallbackIndex;
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
        if (diaryListView != null && emptyView != null) {
            boolean hasEntries = !entries.isEmpty();
            diaryListView.setVisibility(hasEntries ? View.VISIBLE : View.GONE);
            emptyView.setVisibility(hasEntries ? View.GONE : View.VISIBLE);
        }
        adapter.submitList(entries);
    }

    @Override
    public void showError(String message) {
        if (isAdded()) {
            String displayMessage = message != null
                ? message
                : getString(R.string.error_database);
            Snackbar.make(requireView(), displayMessage, Snackbar.LENGTH_LONG).show();
        }
    }
}
