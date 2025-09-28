package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import de.oabidi.pflanzenbestandundlichttest.R;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.Severity;

/**
 * Adapter displaying care recommendations for a plant.
 */
public class CareRecommendationAdapter
    extends ListAdapter<CareRecommendation, CareRecommendationAdapter.ViewHolder> {

    /** Callback invoked when a recommendation is dismissed. */
    public interface Callbacks {
        void onDismiss(@NonNull CareRecommendation recommendation);
    }

    private static final DiffUtil.ItemCallback<CareRecommendation> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<CareRecommendation>() {
            @Override
            public boolean areItemsTheSame(@NonNull CareRecommendation oldItem,
                                           @NonNull CareRecommendation newItem) {
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull CareRecommendation oldItem,
                                              @NonNull CareRecommendation newItem) {
                if (oldItem.getSeverity() != newItem.getSeverity()) {
                    return false;
                }
                if (oldItem.getIconResId() != newItem.getIconResId()) {
                    return false;
                }
                if (!Objects.equals(oldItem.getMessageResId(), newItem.getMessageResId())) {
                    return false;
                }
                if (!Arrays.equals(oldItem.getFormatArgs(), newItem.getFormatArgs())) {
                    return false;
                }
                return Objects.equals(oldItem.getMessage(), newItem.getMessage());
            }
        };

    private final Callbacks callbacks;

    public CareRecommendationAdapter(@NonNull Callbacks callbacks) {
        super(DIFF_CALLBACK);
        this.callbacks = callbacks;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId().hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_care_recommendation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), callbacks);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View stripView;
        private final ImageView iconView;
        private final TextView severityView;
        private final TextView messageView;
        private final ImageButton dismissButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            stripView = itemView.findViewById(R.id.care_recommendation_strip);
            iconView = itemView.findViewById(R.id.care_recommendation_icon);
            severityView = itemView.findViewById(R.id.care_recommendation_severity);
            messageView = itemView.findViewById(R.id.care_recommendation_message);
            dismissButton = itemView.findViewById(R.id.care_recommendation_dismiss);
        }

        void bind(@NonNull CareRecommendation recommendation, @NonNull Callbacks callbacks) {
            Context context = itemView.getContext();
            stripView.setBackgroundColor(getSeverityColor(context, recommendation.getSeverity()));
            severityView.setText(getSeverityLabel(context, recommendation.getSeverity()));
            if (recommendation.hasIcon()) {
                iconView.setVisibility(View.VISIBLE);
                iconView.setImageResource(recommendation.getIconResId());
            } else {
                iconView.setVisibility(View.GONE);
            }
            messageView.setText(resolveMessage(context, recommendation));
            dismissButton.setContentDescription(
                context.getString(R.string.care_recommendation_dismiss_content_description));
            dismissButton.setOnClickListener(v -> callbacks.onDismiss(recommendation));
        }
    }

    private static CharSequence resolveMessage(@NonNull Context context,
                                               @NonNull CareRecommendation recommendation) {
        Integer resId = recommendation.getMessageResId();
        if (resId != null) {
            Object[] args = recommendation.getFormatArgs();
            if (args != null && args.length > 0) {
                return context.getString(resId, args);
            }
            return context.getString(resId);
        }
        CharSequence message = recommendation.getMessage();
        return message != null ? message : "";
    }

    private static int getSeverityColor(@NonNull Context context, @NonNull Severity severity) {
        switch (severity) {
            case CRITICAL:
                return ContextCompat.getColor(context, R.color.care_severity_critical);
            case WARNING:
                return ContextCompat.getColor(context, R.color.care_severity_warning);
            case INFO:
            default:
                return ContextCompat.getColor(context, R.color.care_severity_info);
        }
    }

    private static CharSequence getSeverityLabel(@NonNull Context context,
                                                 @NonNull Severity severity) {
        switch (severity) {
            case CRITICAL:
                return context.getString(R.string.care_severity_critical);
            case WARNING:
                return context.getString(R.string.care_severity_warning);
            case INFO:
            default:
                return context.getString(R.string.care_severity_info);
        }
    }
}
