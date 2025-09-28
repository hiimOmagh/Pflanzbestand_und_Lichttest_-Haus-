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

import com.google.android.material.imageview.ShapeableImageView;

import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Adapter showing a horizontal strip of photo highlights for environment entries.
 */
class EnvironmentPhotoAdapter extends ListAdapter<EnvironmentLogPresenter.PhotoHighlight,
    EnvironmentPhotoAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<EnvironmentLogPresenter.PhotoHighlight> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<EnvironmentLogPresenter.PhotoHighlight>() {
            @Override
            public boolean areItemsTheSame(@NonNull EnvironmentLogPresenter.PhotoHighlight oldItem,
                                           @NonNull EnvironmentLogPresenter.PhotoHighlight newItem) {
                return oldItem.getEntryId() == newItem.getEntryId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull EnvironmentLogPresenter.PhotoHighlight oldItem,
                                              @NonNull EnvironmentLogPresenter.PhotoHighlight newItem) {
                return oldItem.getTimestamp() == newItem.getTimestamp()
                    && oldItem.getPhotoUri().equals(newItem.getPhotoUri())
                    && oldItem.getLabel().equals(newItem.getLabel());
            }
        };
    @NonNull
    private final Callback callback;

    EnvironmentPhotoAdapter(@NonNull Callback callback) {
        super(DIFF_CALLBACK);
        this.callback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_environment_photo_preview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), callback);
    }

    void submit(java.util.List<EnvironmentLogPresenter.PhotoHighlight> items) {
        submitList(items == null ? java.util.Collections.emptyList() : new java.util.ArrayList<>(items));
    }

    interface Callback {
        void onPhotoClicked(@NonNull String uri);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView imageView;
        private final TextView labelView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.environment_photo_thumbnail);
            labelView = itemView.findViewById(R.id.environment_photo_label);
        }

        void bind(EnvironmentLogPresenter.PhotoHighlight highlight, Callback callback) {
            try {
                imageView.setImageURI(Uri.parse(highlight.getPhotoUri()));
            } catch (Exception e) {
                imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            labelView.setText(highlight.getLabel());
            itemView.setOnClickListener(v -> callback.onPhotoClicked(highlight.getPhotoUri()));
        }
    }
}
