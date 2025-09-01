package de.oabidi.pflanzenbestandundlichttest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView adapter that renders a list of {@link Plant} items and forwards
 * click events to an {@link OnPlantClickListener}. Each list item displays the
 * plant name and notifies the listener when tapped.
 */
public class PlantAdapter extends RecyclerView.Adapter<PlantAdapter.PlantViewHolder> {

    public interface OnPlantClickListener {
        void onPlantClick(Plant plant);
    }

    private List<Plant> plants;
    private final OnPlantClickListener listener;

    public PlantAdapter(List<Plant> plants, OnPlantClickListener listener) {
        this.plants = plants;
        this.listener = listener;
    }

    /**
     * Replaces the current data set with the provided list of plants and
     * refreshes the attached RecyclerView.
     *
     * @param plants new list of plants to display
     */
    public void updatePlants(List<Plant> plants) {
        this.plants = plants;
        notifyDataSetChanged();
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
        Plant plant = plants.get(position);
        holder.bind(plant, listener);
    }

    @Override
    public int getItemCount() {
        return plants.size();
    }

    static class PlantViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameView;

        PlantViewHolder(View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.plant_name);
        }

        /**
         * Binds a plant to the view and sets up the click callback.
         *
         * @param plant    the plant whose data should be displayed
         * @param listener listener to notify when the item is clicked
         */
        void bind(Plant plant, OnPlantClickListener listener) {
            nameView.setText(plant.getName());
            itemView.setOnClickListener(v -> listener.onPlantClick(plant));
        }
    }
}
