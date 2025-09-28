package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;

/**
 * RecyclerView adapter for displaying {@link SpeciesTarget} entries.
 */
public class SpeciesTargetAdapter extends ListAdapter<SpeciesTarget, SpeciesTargetAdapter.TargetViewHolder> {

    private static final DiffUtil.ItemCallback<SpeciesTarget> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<SpeciesTarget>() {
            @Override
            public boolean areItemsTheSame(@NonNull SpeciesTarget oldItem, @NonNull SpeciesTarget newItem) {
                return Objects.equals(oldItem.getSpeciesKey(), newItem.getSpeciesKey());
            }

            @Override
            public boolean areContentsTheSame(@NonNull SpeciesTarget oldItem, @NonNull SpeciesTarget newItem) {
                return Objects.equals(oldItem.getTolerance(), newItem.getTolerance())
                    && Objects.equals(oldItem.getSource(), newItem.getSource())
                    && Objects.equals(oldItem.getSeedlingStage(), newItem.getSeedlingStage())
                    && Objects.equals(oldItem.getVegetativeStage(), newItem.getVegetativeStage())
                    && Objects.equals(oldItem.getFlowerStage(), newItem.getFlowerStage());
            }
        };
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

    public interface OnTargetClickListener {
        void onTargetClick(SpeciesTarget target);

        void onTargetLongClick(SpeciesTarget target);
    }

    static class TargetViewHolder extends RecyclerView.ViewHolder {
        private final TextView speciesView;
        private final TextView toleranceView;
        private final TextView seedlingView;
        private final TextView vegetativeView;
        private final TextView flowerView;
        private final TextView sourceView;

        TargetViewHolder(View itemView) {
            super(itemView);
            speciesView = itemView.findViewById(R.id.target_species_name);
            toleranceView = itemView.findViewById(R.id.target_tolerance);
            seedlingView = itemView.findViewById(R.id.target_stage_seedling);
            vegetativeView = itemView.findViewById(R.id.target_stage_vegetative);
            flowerView = itemView.findViewById(R.id.target_stage_flower);
            sourceView = itemView.findViewById(R.id.target_source);
        }

        void bind(SpeciesTarget target, OnTargetClickListener listener) {
            speciesView.setText(target.getSpeciesKey());

            String tolerance = target.getTolerance();
            if (tolerance != null && !tolerance.trim().isEmpty()) {
                toleranceView.setVisibility(View.VISIBLE);
                toleranceView.setText(itemView.getContext().getString(R.string.format_tolerance, tolerance));
            } else {
                toleranceView.setVisibility(View.GONE);
            }

            bindStage(seedlingView, R.string.label_stage_seedling, target.getSeedlingStage());
            bindStage(vegetativeView, R.string.label_stage_vegetative, target.getVegetativeStage());
            bindStage(flowerView, R.string.label_stage_flower, target.getFlowerStage());

            String source = target.getSource();
            if (source != null && !source.trim().isEmpty()) {
                sourceView.setVisibility(View.VISIBLE);
                sourceView.setText(itemView.getContext().getString(R.string.format_source, source));
            } else {
                sourceView.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onTargetClick(target));
            itemView.setOnLongClickListener(v -> {
                listener.onTargetLongClick(target);
                return true;
            });
        }

        private void bindStage(TextView view, int labelRes, SpeciesTarget.StageTarget stage) {
            if (stage == null || !stage.hasRange()) {
                view.setVisibility(View.GONE);
                return;
            }
            String label = itemView.getContext().getString(labelRes);
            boolean hasPpfd = stage.getPpfdMin() != null || stage.getPpfdMax() != null;
            boolean hasDli = stage.getDliMin() != null || stage.getDliMax() != null;
            String formatted;
            if (hasPpfd && hasDli) {
                formatted = itemView.getContext().getString(R.string.format_stage_range,
                    label,
                    formatValue(stage.getPpfdMin()),
                    formatValue(stage.getPpfdMax()),
                    formatValue(stage.getDliMin()),
                    formatValue(stage.getDliMax()));
            } else if (hasPpfd) {
                formatted = itemView.getContext().getString(R.string.format_stage_range_ppfd_only,
                    label,
                    formatValue(stage.getPpfdMin()),
                    formatValue(stage.getPpfdMax()));
            } else if (hasDli) {
                formatted = itemView.getContext().getString(R.string.format_stage_range_dli_only,
                    label,
                    formatValue(stage.getDliMin()),
                    formatValue(stage.getDliMax()));
            } else {
                view.setVisibility(View.GONE);
                return;
            }
            view.setText(formatted);
            view.setVisibility(View.VISIBLE);
        }

        private String formatValue(Float value) {
            if (value == null) {
                return itemView.getContext().getString(R.string.placeholder_dash);
            }
            return String.format(Locale.getDefault(), "%.1f", value);
        }
    }
}
