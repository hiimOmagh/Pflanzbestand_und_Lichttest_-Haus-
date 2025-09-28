package de.oabidi.pflanzenbestandundlichttest.feature.lighting;

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
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;

/**
 * RecyclerView adapter for displaying {@link LedProfile} entries.
 */
class LedProfileAdapter extends ListAdapter<LedProfile, LedProfileAdapter.ProfileViewHolder> {

    private static final DiffUtil.ItemCallback<LedProfile> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<LedProfile>() {
            @Override
            public boolean areItemsTheSame(@NonNull LedProfile oldItem, @NonNull LedProfile newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull LedProfile oldItem, @NonNull LedProfile newItem) {
                return Objects.equals(oldItem.getName(), newItem.getName())
                    && Objects.equals(oldItem.getType(), newItem.getType())
                    && Objects.equals(oldItem.getMountingDistanceCm(), newItem.getMountingDistanceCm())
                    && Objects.equals(oldItem.getCalibrationFactors(), newItem.getCalibrationFactors())
                    && Objects.equals(oldItem.getSchedule(), newItem.getSchedule());
            }
        };
    private final OnProfileClickListener listener;

    LedProfileAdapter(OnProfileClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_led_profile, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        LedProfile profile = getItem(position);
        holder.bind(profile, listener);
    }

    interface OnProfileClickListener {
        void onProfileClick(LedProfile profile);
    }

    static final class ProfileViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameView;
        private final TextView typeView;
        private final TextView distanceView;
        private final TextView scheduleView;

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.profile_name);
            typeView = itemView.findViewById(R.id.profile_type);
            distanceView = itemView.findViewById(R.id.profile_distance);
            scheduleView = itemView.findViewById(R.id.profile_schedule);
        }

        void bind(LedProfile profile, OnProfileClickListener listener) {
            nameView.setText(profile.getName());

            String type = profile.getType();
            if (type != null && !type.trim().isEmpty()) {
                typeView.setVisibility(View.VISIBLE);
                typeView.setText(itemView.getContext().getString(R.string.format_led_profile_type, type));
            } else {
                typeView.setVisibility(View.GONE);
            }

            Float distance = profile.getMountingDistanceCm();
            if (distance != null) {
                distanceView.setVisibility(View.VISIBLE);
                distanceView.setText(itemView.getContext().getString(R.string.format_led_profile_distance,
                    String.format(Locale.getDefault(), "%.1f", distance)));
            } else {
                distanceView.setVisibility(View.GONE);
            }

            int scheduleCount = profile.getSchedule() != null ? profile.getSchedule().size() : 0;
            if (scheduleCount > 0) {
                scheduleView.setVisibility(View.VISIBLE);
                scheduleView.setText(itemView.getContext().getResources()
                    .getQuantityString(R.plurals.format_led_profile_schedule_count, scheduleCount, scheduleCount));
            } else {
                scheduleView.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onProfileClick(profile));
        }
    }
}
