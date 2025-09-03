package de.oabidi.pflanzenbestandundlichttest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView adapter displaying recent PPFD and DLI measurements.
 */
public class MeasurementAdapter extends ListAdapter<Measurement, MeasurementAdapter.ViewHolder> {
    protected MeasurementAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Measurement> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<Measurement>() {
            @Override
            public boolean areItemsTheSame(@NonNull Measurement oldItem,
                                           @NonNull Measurement newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Measurement oldItem,
                                              @NonNull Measurement newItem) {
                return oldItem.getPlantId() == newItem.getPlantId()
                    && oldItem.getTimeEpoch() == newItem.getTimeEpoch()
                    && Float.compare(oldItem.getLuxAvg(), newItem.getLuxAvg()) == 0
                    && Float.compare(oldItem.getPpfd(), newItem.getPpfd()) == 0
                    && Float.compare(oldItem.getDli(), newItem.getDli()) == 0;
            }
        };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_measurement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Measurement m = getItem(position);
        String ppfdText = holder.itemView.getContext().getString(R.string.format_ppfd, m.getPpfd());
        String dliText = holder.itemView.getContext().getString(R.string.format_dli, m.getDli());
        holder.valueView.setText(ppfdText + ", " + dliText);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView valueView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            valueView = itemView.findViewById(R.id.measurement_value);
        }
    }
}
