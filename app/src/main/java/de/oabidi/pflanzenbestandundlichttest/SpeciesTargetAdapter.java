package de.oabidi.pflanzenbestandundlichttest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

/**
 * RecyclerView adapter for displaying {@link SpeciesTarget} entries.
 */
public class SpeciesTargetAdapter extends ListAdapter<SpeciesTarget, SpeciesTargetAdapter.TargetViewHolder> {

    public interface OnTargetClickListener {
        void onTargetClick(SpeciesTarget target);
        void onTargetLongClick(SpeciesTarget target);
    }

    private final OnTargetClickListener listener;

    public SpeciesTargetAdapter(OnTargetClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public TargetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_species_target, parent, false);
        return new TargetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TargetViewHolder holder, int position) {
        SpeciesTarget target = getItem(position);
        holder.bind(target, listener);
    }

    private static final DiffUtil.ItemCallback<SpeciesTarget> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<SpeciesTarget>() {
            @Override
            public boolean areItemsTheSame(@NonNull SpeciesTarget oldItem, @NonNull SpeciesTarget newItem) {
                return Objects.equals(oldItem.getSpeciesKey(), newItem.getSpeciesKey());
            }

            @Override
            public boolean areContentsTheSame(@NonNull SpeciesTarget oldItem, @NonNull SpeciesTarget newItem) {
                return oldItem.getPpfdMin() == newItem.getPpfdMin()
                    && oldItem.getPpfdMax() == newItem.getPpfdMax();
            }
        };

    static class TargetViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        TargetViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.target_text);
        }

        void bind(SpeciesTarget target, OnTargetClickListener listener) {
            String display = target.getSpeciesKey() + ": " + target.getPpfdMin() + " - " + target.getPpfdMax();
            textView.setText(display);
            itemView.setOnClickListener(v -> listener.onTargetClick(target));
            itemView.setOnLongClickListener(v -> {
                listener.onTargetLongClick(target);
                return true;
            });
        }
    }
}
