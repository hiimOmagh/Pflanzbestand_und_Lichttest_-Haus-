package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * RecyclerView adapter for displaying environment log entries.
 */
public class EnvironmentLogAdapter extends ListAdapter<EnvironmentLogPresenter.EnvironmentLogItem,
    EnvironmentLogAdapter.ViewHolder> {

    /** Callbacks for item interactions. */
    public interface Callbacks {
        /** Invoked when the user taps an item to edit it. */
        void onEdit(EnvironmentLogPresenter.EnvironmentLogItem item);

        /** Invoked when the user requests to delete an item. */
        void onDelete(EnvironmentLogPresenter.EnvironmentLogItem item);
    }

    private final Callbacks callbacks;

    public EnvironmentLogAdapter(@NonNull Callbacks callbacks) {
        super(DIFF_CALLBACK);
        this.callbacks = callbacks;
    }

    private static final DiffUtil.ItemCallback<EnvironmentLogPresenter.EnvironmentLogItem> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<EnvironmentLogPresenter.EnvironmentLogItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull EnvironmentLogPresenter.EnvironmentLogItem oldItem,
                                           @NonNull EnvironmentLogPresenter.EnvironmentLogItem newItem) {
                return oldItem.getEntry().getId() == newItem.getEntry().getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull EnvironmentLogPresenter.EnvironmentLogItem oldItem,
                                              @NonNull EnvironmentLogPresenter.EnvironmentLogItem newItem) {
                return oldItem.getTimestampText().equals(newItem.getTimestampText())
                    && oldItem.getMetricsText().equals(newItem.getMetricsText())
                    && ((oldItem.getNotes() == null && newItem.getNotes() == null)
                    || (oldItem.getNotes() != null && oldItem.getNotes().equals(newItem.getNotes())));
            }
        };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.list_item_environment_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EnvironmentLogPresenter.EnvironmentLogItem item = getItem(position);
        holder.bind(item, callbacks);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView timestampView;
        private final TextView metricsView;
        private final TextView notesView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            timestampView = itemView.findViewById(R.id.environment_log_item_timestamp);
            metricsView = itemView.findViewById(R.id.environment_log_item_metrics);
            notesView = itemView.findViewById(R.id.environment_log_item_notes);
        }

        void bind(EnvironmentLogPresenter.EnvironmentLogItem item, Callbacks callbacks) {
            timestampView.setText(item.getTimestampText());
            metricsView.setText(item.getMetricsText());
            String notes = item.getNotes();
            if (notes == null || notes.isEmpty()) {
                notesView.setVisibility(View.GONE);
                notesView.setText(null);
            } else {
                notesView.setVisibility(View.VISIBLE);
                notesView.setText(notes);
            }
            itemView.setOnClickListener(v -> callbacks.onEdit(item));
            itemView.setOnLongClickListener(v -> {
                callbacks.onDelete(item);
                return true;
            });
        }
    }
}
