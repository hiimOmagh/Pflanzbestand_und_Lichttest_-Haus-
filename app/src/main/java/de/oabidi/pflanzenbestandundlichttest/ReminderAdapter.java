package de.oabidi.pflanzenbestandundlichttest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * RecyclerView adapter displaying scheduled reminders.
 */
public class ReminderAdapter extends ListAdapter<Reminder, ReminderAdapter.ViewHolder> {
    /** Listener invoked when a reminder is tapped. */
    public interface OnReminderClickListener {
        void onReminderClick(Reminder reminder);
    }

    private final OnReminderClickListener clickListener;

    public ReminderAdapter(OnReminderClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<Reminder> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<Reminder>() {
            @Override
            public boolean areItemsTheSame(@NonNull Reminder oldItem, @NonNull Reminder newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Reminder oldItem, @NonNull Reminder newItem) {
                return oldItem.getTriggerAt() == newItem.getTriggerAt()
                    && oldItem.getMessage().equals(newItem.getMessage());
            }
        };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.list_item_reminder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Reminder reminder = getItem(position);
        holder.bind(reminder, clickListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        private final DateFormat df;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.reminder_text);
            df = new SimpleDateFormat(itemView.getContext().getString(R.string.date_time_pattern),
                Locale.getDefault());
        }

        void bind(Reminder reminder, OnReminderClickListener listener) {
            String text = itemView.getContext().getString(R.string.format_reminder_item,
                df.format(new Date(reminder.getTriggerAt())), reminder.getMessage());
            textView.setText(text);
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReminderClick(reminder);
                }
            });
        }
    }
}
