package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.Objects;

/**
 * RecyclerView adapter that renders a list of {@link Plant} items and forwards
 * click events to an {@link OnPlantClickListener}. Each list item displays the
 * plant name and notifies the listener when tapped.
 */
public class PlantAdapter extends ListAdapter<Plant, PlantAdapter.PlantViewHolder> {

    private static final DiffUtil.ItemCallback<Plant> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<Plant>() {
            @Override
            public boolean areItemsTheSame(@NonNull Plant oldItem, @NonNull Plant newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Plant oldItem, @NonNull Plant newItem) {
                return Objects.equals(oldItem.getName(), newItem.getName())
                    && Objects.equals(oldItem.getDescription(), newItem.getDescription())
                    && Objects.equals(oldItem.getSpecies(), newItem.getSpecies())
                    && Objects.equals(oldItem.getLocationHint(), newItem.getLocationHint())
                    && oldItem.getAcquiredAtEpoch() == newItem.getAcquiredAtEpoch()
                    && Objects.equals(oldItem.getPhotoUri(), newItem.getPhotoUri());
            }
        };
    private final OnPlantClickListener listener;

    public PlantAdapter(OnPlantClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    /**
     * Creates a new {@link PlantViewHolder} by inflating the item layout.
     *
     * @param parent   the RecyclerView to which the new view will be added
     * @param viewType ignored in this adapter; one view type is used
     * @return a holder capable of displaying a plant
     */
    @NonNull
    @Override
    public PlantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plant, parent, false);
        return new PlantViewHolder(view);
    }

    /**
     * Populates the provided holder with the plant at the specified position
     * and attaches the click listener.
     *
     * @param holder   holder to bind
     * @param position adapter position of the plant
     */
    @Override
    public void onBindViewHolder(@NonNull PlantViewHolder holder, int position) {
        Plant plant = getItem(position);
        holder.bind(plant, listener);
    }

    public interface OnPlantClickListener {
        void onPlantClick(Plant plant);

        void onPlantLongClick(Plant plant);
    }

    static class PlantViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView nameView;

        PlantViewHolder(View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            cardView.setCheckable(true);
            nameView = cardView.findViewById(R.id.plant_name);
        }

        /**
         * Binds a plant to the view and sets up the click callback.
         *
         * @param plant    the plant whose data should be displayed
         * @param listener listener to notify when the item is clicked
         */
        void bind(Plant plant, OnPlantClickListener listener) {
            nameView.setText(plant.getName());
            cardView.setContentDescription(plant.getName());
            cardView.setChecked(false);
            cardView.setOnClickListener(v -> listener.onPlantClick(plant));
            cardView.setOnLongClickListener(v -> {
                cardView.toggle();
                listener.onPlantLongClick(plant);
                return true;
            });
        }
    }
}
