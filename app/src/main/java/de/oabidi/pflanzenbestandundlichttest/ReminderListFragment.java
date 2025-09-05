package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Fragment displaying all scheduled reminders.
 */
public class ReminderListFragment extends Fragment {
    private ReminderAdapter adapter;

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
        adapter = new ReminderAdapter();
        recyclerView.setAdapter(adapter);

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
                PlantDatabase.databaseWriteExecutor.execute(() ->
                    PlantDatabase.getDatabase(requireContext()).reminderDao().deleteById(reminder.getId()));
                loadReminders();
            }
        });
        helper.attachToRecyclerView(recyclerView);

        loadReminders();
    }

    private void loadReminders() {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            List<Reminder> reminders = PlantDatabase.getDatabase(requireContext()).reminderDao().getAll();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> adapter.submitList(reminders));
            }
        });
    }
}
