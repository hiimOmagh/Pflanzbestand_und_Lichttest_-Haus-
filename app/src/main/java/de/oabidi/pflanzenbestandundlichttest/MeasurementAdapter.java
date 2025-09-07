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
 * RecyclerView adapter displaying recent PPFD and DLI measurements.
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
                    && Float.compare(oldItem.getPpfd(), newItem.getPpfd()) == 0
                    && Float.compare(oldItem.getDli(), newItem.getDli()) == 0
                    && ((oldItem.getNote() == null && newItem.getNote() == null)
                    || (oldItem.getNote() != null && oldItem.getNote().equals(newItem.getNote())));
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
        holder.ppfdView.setText(ppfdText);
        holder.dliView.setText(dliText);
        String timeText = DateFormat.getDateTimeInstance().format(new Date(m.getTimeEpoch()));
        holder.timeView.setText(timeText);
        if (m.getNote() != null && !m.getNote().isEmpty()) {
            holder.noteView.setVisibility(View.VISIBLE);
            holder.noteView.setText(m.getNote());
        } else {
            holder.noteView.setVisibility(View.GONE);
        }
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMeasurementLongClick(m);
            }
            return true;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView ppfdView;
        final TextView dliView;
        final TextView timeView;
        final TextView noteView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ppfdView = itemView.findViewById(R.id.measurement_ppfd);
            dliView = itemView.findViewById(R.id.measurement_dli);
            timeView = itemView.findViewById(R.id.measurement_time);
            noteView = itemView.findViewById(R.id.measurement_note);
        }
    }
}
