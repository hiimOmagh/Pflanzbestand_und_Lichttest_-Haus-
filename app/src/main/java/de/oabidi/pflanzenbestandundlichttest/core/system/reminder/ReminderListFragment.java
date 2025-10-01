package de.oabidi.pflanzenbestandundlichttest.core.system.reminder;

import de.oabidi.pflanzenbestandundlichttest.R;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.repository.ReminderRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fragment displaying all scheduled reminders.
 */
public class ReminderListFragment extends Fragment {
    private static final String ARG_PLANT_ID = "plantId";

    private ReminderAdapter adapter;
    private RecyclerView reminderListView;
    private View emptyStateView;
    private PlantRepository repository;
    private ReminderRepository reminderRepository;
    private SimpleDateFormat df;
    private long plantId = -1;
    private SharedPreferences preferences;
    private boolean selectingPlant;

    public ReminderListFragment() {
    }

    public ReminderListFragment(PlantRepository repository) {
        this.repository = repository;
        this.reminderRepository = repository.reminderRepository();
    }

    public static ReminderListFragment newInstance(long plantId, PlantRepository repository) {
        ReminderListFragment fragment = new ReminderListFragment(repository);
        Bundle args = new Bundle();
        args.putLong(ARG_PLANT_ID, plantId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reminder_list, container, false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (repository == null) {
            repository = RepositoryProvider.getRepository(context);
            reminderRepository = RepositoryProvider.getReminderRepository(context);
        } else if (reminderRepository == null) {
            reminderRepository = repository.reminderRepository();
        }
        preferences = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            plantId = args.getLong(ARG_PLANT_ID, -1);
        }
        if (savedInstanceState != null) {
            plantId = savedInstanceState.getLong(ARG_PLANT_ID, plantId);
        }
        if (plantId < 0 && preferences != null) {
            plantId = preferences.getLong(SettingsKeys.KEY_SELECTED_PLANT, -1);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.requestApplyInsetsWhenAttached(view);
        reminderListView = view.findViewById(R.id.reminder_list);
        emptyStateView = view.findViewById(R.id.reminder_empty_state);
        reminderListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        reminderListView.setClipToPadding(false);
        InsetsUtils.applySystemWindowInsetsPadding(reminderListView, false, false, false, true);
        adapter = new ReminderAdapter(this::showEditDialog);
        reminderListView.setAdapter(adapter);
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }
        if (reminderRepository == null) {
            reminderRepository = RepositoryProvider.getReminderRepository(requireContext());
        }
        df = new SimpleDateFormat(getString(R.string.date_time_pattern), Locale.getDefault());

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
            ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Reminder reminder = adapter.getCurrentList().get(position);
                ReminderScheduler.cancelReminder(requireContext(), reminder.getId());
                reminderRepository.deleteReminderById(reminder.getId(), ReminderListFragment.this::loadReminders,
                    e -> {
                        if (isAdded())
                            Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show();
                    });
                Snackbar.make(requireView(), R.string.reminder_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo, v -> {
                        boolean accepted = reminderRepository.insertReminder(reminder, () -> {
                            if (!isAdded()) {
                                return;
                            }
                            ReminderScheduler.scheduleReminderAt(requireContext(),
                                reminder.getTriggerAt(),
                                reminder.getMessage(),
                                reminder.getId(),
                                reminder.getPlantId());
                            loadReminders();
                        }, ReminderListFragment.this::handleReminderError);
                        if (!accepted) {
                            return;
                        }
                    })
                    .show();
            }
        });
        helper.attachToRecyclerView(reminderListView);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_reminder);
        InsetsUtils.applySystemWindowInsetsMargin(fab, false, false, false, true);
        fab.setOnClickListener(v -> showCreateDialog());

        if (plantId >= 0) {
            loadReminders();
        } else {
            requestPlantSelection();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(ARG_PLANT_ID, plantId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        reminderListView = null;
        emptyStateView = null;
    }

    private void loadReminders() {
        if (reminderRepository == null || plantId < 0) {
            if (adapter != null) {
                List<Reminder> emptyList = Collections.emptyList();
                adapter.submitList(emptyList);
                updateEmptyState(emptyList);
            }
            return;
        }
        reminderRepository.getRemindersForPlant(plantId, reminders -> {
            if (isAdded()) {
                List<Reminder> safeReminders = reminders != null ? reminders : Collections.emptyList();
                adapter.submitList(safeReminders);
                updateEmptyState(safeReminders);
            }
        }, e -> {
            if (isAdded()) {
                Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void handleReminderError(Exception e) {
        if (!isAdded()) {
            return;
        }
        int messageRes = e instanceof IllegalArgumentException
            ? R.string.error_select_plant
            : R.string.error_database;
        Snackbar.make(requireView(), messageRes, Snackbar.LENGTH_LONG).show();
    }

    private void showEditDialog(Reminder reminder) {
        showReminderDialog(reminder);
    }

    private void showCreateDialog() {
        if (!ensurePlantSelected()) {
            return;
        }
        showReminderDialog(null);
    }

    private void showReminderDialog(@Nullable Reminder reminder) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_reminder, null);
        TextInputEditText messageEdit = dialogView.findViewById(R.id.reminder_edit_message);
        TextInputEditText dateEdit = dialogView.findViewById(R.id.reminder_edit_date);
        Calendar cal = Calendar.getInstance();
        if (reminder != null) {
            messageEdit.setText(reminder.getMessage());
            dateEdit.setText(df.format(new Date(reminder.getTriggerAt())));
            cal.setTime(new Date(reminder.getTriggerAt()));
        } else {
            dateEdit.setText(df.format(cal.getTime()));
        }

        setupDateField(dateEdit, cal);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setTitle(reminder != null ? R.string.action_edit_reminder : R.string.action_add_reminder)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String message = messageEdit.getText() != null ? messageEdit.getText().toString() : "";
            String dateStr = dateEdit.getText() != null ? dateEdit.getText().toString() : "";
            try {
                dateEdit.setError(null);
                long triggerAt = Objects.requireNonNull(df.parse(dateStr)).getTime();
                boolean accepted;
                if (reminder != null) {
                    reminder.setMessage(message);
                    reminder.setTriggerAt(triggerAt);
                    accepted = reminderRepository.updateReminder(reminder, () -> {
                        if (!isAdded()) {
                            return;
                        }
                        ReminderScheduler.scheduleReminderAt(requireContext(), triggerAt, message,
                            reminder.getId(), reminder.getPlantId());
                        loadReminders();
                    }, this::handleReminderError);
                } else {
                    if (!ensurePlantSelected()) {
                        return;
                    }
                    Reminder newReminder = new Reminder(triggerAt, message, plantId);
                    accepted = reminderRepository.insertReminder(newReminder, () -> {
                        if (!isAdded()) {
                            return;
                        }
                        ReminderScheduler.scheduleReminderAt(requireContext(), triggerAt, message,
                            newReminder.getId(), newReminder.getPlantId());
                        loadReminders();
                    }, this::handleReminderError);
                }
                if (!accepted) {
                    return;
                }
                dialog.dismiss();
            } catch (ParseException e) {
                dateEdit.setError(getString(R.string.error_invalid_date));
            }
        }));

        dialog.show();
    }

    private void setupDateField(TextInputEditText dateEdit, Calendar cal) {
        dateEdit.setInputType(InputType.TYPE_NULL);
        dateEdit.setKeyListener(null);
        dateEdit.setOnClickListener(v -> showDatePicker(dateEdit, cal));
    }

    private void showDatePicker(TextInputEditText dateEdit, Calendar cal) {
        CharSequence currentText = dateEdit.getText();
        if (currentText != null) {
            try {
                cal.setTime(Objects.requireNonNull(df.parse(currentText.toString())));
            } catch (ParseException ignored) {
            }
        }
        new DatePickerDialog(requireContext(), (view1, year, month, day) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(requireContext(), (view2, hour, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                dateEdit.setText(df.format(cal.getTime()));
                dateEdit.setError(null);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private boolean ensurePlantSelected() {
        if (plantId >= 0) {
            return true;
        }
        requestPlantSelection();
        return false;
    }

    private void requestPlantSelection() {
        if (!isAdded() || repository == null || selectingPlant) {
            return;
        }
        selectingPlant = true;
        repository.getAllPlants((List<Plant> plants) -> {
            if (!isAdded()) {
                selectingPlant = false;
                return;
            }
            if (plants == null || plants.isEmpty()) {
                selectingPlant = false;
                List<Reminder> emptyList = Collections.emptyList();
                adapter.submitList(emptyList);
                updateEmptyState(emptyList);
                Snackbar.make(requireView(), R.string.error_select_plant, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (plants.size() == 1) {
                setPlantSelection(plants.get(0).getId());
                return;
            }
            CharSequence[] names = new CharSequence[plants.size()];
            for (int i = 0; i < plants.size(); i++) {
                names[i] = plants.get(i).getName();
            }
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.select_plant)
                .setItems(names, (dialog, which) -> {
                    if (which >= 0 && which < plants.size()) {
                        setPlantSelection(plants.get(which).getId());
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // Keep plantId unset; user can retry by tapping add.
                })
                .setOnDismissListener(this::onSelectionDialogDismissed)
                .show();
        }, e -> {
            selectingPlant = false;
            if (isAdded()) {
                Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void onSelectionDialogDismissed(DialogInterface dialogInterface) {
        selectingPlant = false;
    }

    private void setPlantSelection(long selectedPlantId) {
        selectingPlant = false;
        plantId = selectedPlantId;
        if (preferences != null) {
            preferences.edit()
                .putLong(SettingsKeys.KEY_SELECTED_PLANT, selectedPlantId)
                .apply();
        }
        loadReminders();
    }

    private void updateEmptyState(@Nullable List<Reminder> reminders) {
        if (reminderListView == null || emptyStateView == null) {
            return;
        }
        boolean isEmpty = reminders == null || reminders.isEmpty();
        reminderListView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }
}
