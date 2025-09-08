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
import java.util.Date;

/**
 * RecyclerView adapter displaying recent light measurements.
 */
public class MeasurementAdapter extends ListAdapter<Measurement, MeasurementAdapter.ViewHolder> {
    /** Listener invoked when a measurement is long-pressed. */
    public interface OnMeasurementLongClickListener {
        void onMeasurementLongClick(Measurement measurement);
    }

    private final OnMeasurementLongClickListener longClickListener;

    public MeasurementAdapter(OnMeasurementLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        this.longClickListener = longClickListener;
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
                    && ((oldItem.getPpfd() == null && newItem.getPpfd() == null)
                    || (oldItem.getPpfd() != null && newItem.getPpfd() != null && Float.compare(oldItem.getPpfd(), newItem.getPpfd()) == 0));
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
        String luxText = holder.itemView.getContext().getString(R.string.format_lux, m.getLuxAvg());
        holder.luxView.setText(luxText);
        if (m.getPpfd() != null) {
            String ppfdText = holder.itemView.getContext().getString(R.string.format_ppfd, m.getPpfd());
            holder.ppfdView.setText(ppfdText);
            holder.ppfdView.setVisibility(View.VISIBLE);
        } else {
            holder.ppfdView.setVisibility(View.GONE);
        }
        String timeText = DateFormat.getDateTimeInstance().format(new Date(m.getTimeEpoch()));
        holder.timeView.setText(timeText);
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMeasurementLongClick(m);
            }
            return true;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView luxView;
        final TextView ppfdView;
        final TextView timeView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            luxView = itemView.findViewById(R.id.measurement_lux);
            ppfdView = itemView.findViewById(R.id.measurement_ppfd);
            timeView = itemView.findViewById(R.id.measurement_time);
        }
    }
}
