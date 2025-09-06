package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fragment displaying all scheduled reminders.
 */
public class ReminderListFragment extends Fragment {
    private ReminderAdapter adapter;
    private PlantRepository repository;
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

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
                repository.deleteReminderById(reminder.getId(), ReminderListFragment.this::loadReminders);
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
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_edit_reminder)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                String message = messageEdit.getText().toString();
                String dateStr = dateEdit.getText().toString();
                try {
                    long triggerAt = df.parse(dateStr).getTime();
                    reminder.setMessage(message);
                    reminder.setTriggerAt(triggerAt);
                    ReminderScheduler.scheduleReminderAt(requireContext(), triggerAt, message, reminder.getId(), reminder.getPlantId());
                    repository.updateReminder(reminder, this::loadReminders);
                } catch (ParseException ignored) {
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
