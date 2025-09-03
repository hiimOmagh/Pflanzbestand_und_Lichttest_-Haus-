package de.oabidi.pflanzenbestandundlichttest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter displaying recent PPFD measurements.
 */
public class MeasurementAdapter extends RecyclerView.Adapter<MeasurementAdapter.ViewHolder> {
    private final List<Measurement> measurements = new ArrayList<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_measurement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Measurement m = measurements.get(position);
        String text = holder.itemView.getContext().getString(R.string.format_ppfd, m.getPpfd());
        holder.valueView.setText(text);
    }

    @Override
    public int getItemCount() {
        return measurements.size();
    }

    public void setMeasurements(List<Measurement> list) {
        measurements.clear();
        if (list != null) {
            measurements.addAll(list);
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView valueView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            valueView = itemView.findViewById(R.id.measurement_value);
        }
    }
}
