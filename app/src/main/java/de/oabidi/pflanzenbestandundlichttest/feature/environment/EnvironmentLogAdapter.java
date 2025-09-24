package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.oabidi.pflanzenbestandundlichttest.R;
import com.google.android.material.imageview.ShapeableImageView;

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

        /** Invoked when the user taps the photo preview. */
        void onPhotoClicked(EnvironmentLogPresenter.EnvironmentLogItem item);
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
                boolean notesEqual = (oldItem.getNotes() == null && newItem.getNotes() == null)
                    || (oldItem.getNotes() != null && oldItem.getNotes().equals(newItem.getNotes()));
                boolean photosEqual = (oldItem.getPhotoUri() == null && newItem.getPhotoUri() == null)
                    || (oldItem.getPhotoUri() != null
                    && oldItem.getPhotoUri().equals(newItem.getPhotoUri()));
                return oldItem.getTimestampText().equals(newItem.getTimestampText())
                    && oldItem.getMetricsText().equals(newItem.getMetricsText())
                    && notesEqual
                    && photosEqual;
            }
        };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_environment_entry, parent, false);
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
        private final ShapeableImageView photoView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            timestampView = itemView.findViewById(R.id.environment_log_item_timestamp);
            metricsView = itemView.findViewById(R.id.environment_log_item_metrics);
            notesView = itemView.findViewById(R.id.environment_log_item_notes);
            photoView = itemView.findViewById(R.id.environment_log_item_photo);
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
            String photoUri = item.getPhotoUri();
            if (photoUri == null || photoUri.isEmpty()) {
                photoView.setVisibility(View.GONE);
                photoView.setImageDrawable(null);
                photoView.setOnClickListener(null);
            } else {
                photoView.setVisibility(View.VISIBLE);
                try {
                    photoView.setImageURI(Uri.parse(photoUri));
                } catch (Exception e) {
                    photoView.setImageResource(android.R.drawable.ic_menu_gallery);
                }
                photoView.setOnClickListener(v -> callbacks.onPhotoClicked(item));
            }
            itemView.setOnClickListener(v -> callbacks.onEdit(item));
            itemView.setOnLongClickListener(v -> {
                callbacks.onDelete(item);
                return true;
            });
        }
    }
}
