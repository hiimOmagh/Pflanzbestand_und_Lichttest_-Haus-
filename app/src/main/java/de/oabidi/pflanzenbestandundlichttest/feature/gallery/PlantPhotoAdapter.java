package de.oabidi.pflanzenbestandundlichttest.feature.gallery;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantPhoto;

/**
 * RecyclerView adapter that displays {@link PlantPhoto} thumbnails in a grid. Clicking an item opens
 * it in a full-screen viewer while long-pressing triggers a deletion callback.
 */
public class PlantPhotoAdapter extends ListAdapter<PlantPhoto, PlantPhotoAdapter.ViewHolder> {

    public interface Callbacks {
        void onPhotoClicked(@NonNull PlantPhoto photo);

        void onPhotoLongClicked(@NonNull PlantPhoto photo);
    }

    private static final DiffUtil.ItemCallback<PlantPhoto> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<PlantPhoto>() {
            @Override
            public boolean areItemsTheSame(@NonNull PlantPhoto oldItem, @NonNull PlantPhoto newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull PlantPhoto oldItem, @NonNull PlantPhoto newItem) {
                return oldItem.getId() == newItem.getId()
                    && oldItem.getCreatedAt() == newItem.getCreatedAt()
                    && oldItem.getUri().equals(newItem.getUri());
            }
        };

    private final PlantPhotoLoader loader;
    private final Callbacks callbacks;

    public PlantPhotoAdapter(@NonNull PlantPhotoLoader loader, @NonNull Callbacks callbacks) {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
        this.loader = loader;
        this.callbacks = callbacks;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_plant_photo, parent, false);
        return new ViewHolder(view, loader);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlantPhoto photo = getItem(position);
        holder.bind(photo, callbacks);
    }

    @Override
    public long getItemId(int position) {
        PlantPhoto photo = getItem(position);
        return photo != null ? photo.getId() : RecyclerView.NO_ID;
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.recycle();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final PlantPhotoLoader loader;

        ViewHolder(@NonNull View itemView, @NonNull PlantPhotoLoader loader) {
            super(itemView);
            this.loader = loader;
            imageView = itemView.findViewById(R.id.plant_photo_image);
        }

        void bind(@NonNull PlantPhoto photo, @NonNull Callbacks callbacks) {
            String uri = photo.getUri();
            loader.loadInto(imageView, uri);
            itemView.setOnClickListener(v -> callbacks.onPhotoClicked(photo));
            itemView.setOnLongClickListener(v -> {
                callbacks.onPhotoLongClicked(photo);
                return true;
            });
            itemView.setContentDescription(itemView.getResources().getString(R.string.plant_photo_desc));
        }

        void recycle() {
            loader.clear(imageView);
            itemView.setOnClickListener(null);
            itemView.setOnLongClickListener(null);
        }
    }
}
