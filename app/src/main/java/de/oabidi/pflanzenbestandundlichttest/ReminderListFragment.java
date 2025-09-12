package de.oabidi.pflanzenbestandundlichttest;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

/**
 * Fragment displaying all scheduled reminders.
 */
public class ReminderListFragment extends Fragment {
    private ReminderAdapter adapter;
    private PlantRepository repository;
    private SimpleDateFormat df;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reminder_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = view.findViewById(R.id.reminder_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ReminderAdapter(this::showEditDialog);
        recyclerView.setAdapter(adapter);
        repository = ((PlantApp) requireContext().getApplicationContext()).getRepository();
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
                repository.deleteReminderById(reminder.getId(), ReminderListFragment.this::loadReminders,
                    e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); });
                Snackbar.make(requireView(), R.string.reminder_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo, v ->
                        repository.insertReminder(reminder, () -> {
                            ReminderScheduler.scheduleReminderAt(requireContext(),
                                reminder.getTriggerAt(),
                                reminder.getMessage(),
                                reminder.getId(),
                                reminder.getPlantId());
                            loadReminders();
                        }, e2 -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); }))
                    .show();
            }
        });
        helper.attachToRecyclerView(recyclerView);

        loadReminders();
    }

    private void loadReminders() {
        repository.getAllReminders(reminders -> {
            if (isAdded()) {
                adapter.submitList(reminders);
            }
        });
    }

    private void showEditDialog(Reminder reminder) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_reminder, null);
        EditText messageEdit = dialogView.findViewById(R.id.reminder_edit_message);
        EditText dateEdit = dialogView.findViewById(R.id.reminder_edit_date);
        messageEdit.setText(reminder.getMessage());
        dateEdit.setText(df.format(new Date(reminder.getTriggerAt())));

        dateEdit.setInputType(InputType.TYPE_NULL);
        dateEdit.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            try {
                cal.setTime(Objects.requireNonNull(df.parse(dateEdit.getText().toString())));
            } catch (ParseException ignored) {
            }
            new DatePickerDialog(requireContext(), (view1, year, month, day) -> {
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, month);
                cal.set(Calendar.DAY_OF_MONTH, day);
                new TimePickerDialog(requireContext(), (view2, hour, minute) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    dateEdit.setText(df.format(cal.getTime()));
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_edit_reminder)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String message = messageEdit.getText().toString();
            String dateStr = dateEdit.getText().toString();
            try {
                long triggerAt = Objects.requireNonNull(df.parse(dateStr)).getTime();
                reminder.setMessage(message);
                reminder.setTriggerAt(triggerAt);
                ReminderScheduler.scheduleReminderAt(requireContext(), triggerAt, message, reminder.getId(), reminder.getPlantId());
                repository.updateReminder(reminder, this::loadReminders,
                    e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); });
                dialog.dismiss();
            } catch (ParseException e) {
                dateEdit.setError(getString(R.string.error_invalid_date));
            }
        }));

        dialog.show();
    }
}
