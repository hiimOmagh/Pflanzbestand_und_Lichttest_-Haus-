package de.oabidi.pflanzenbestandundlichttest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PlantAdapter extends RecyclerView.Adapter<PlantAdapter.PlantViewHolder> {

    public interface OnPlantClickListener {
        void onPlantClick(Plant plant);
    }

    private final List<Plant> plants;
    private final OnPlantClickListener listener;

    public PlantAdapter(List<Plant> plants, OnPlantClickListener listener) {
        this.plants = plants;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plant, parent, false);
        return new PlantViewHolder(view);
    }

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

        void bind(Plant plant, OnPlantClickListener listener) {
            nameView.setText(plant.getName());
            itemView.setOnClickListener(v -> listener.onPlantClick(plant));
        }
    }
}
