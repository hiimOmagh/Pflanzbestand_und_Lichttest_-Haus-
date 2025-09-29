package de.oabidi.pflanzenbestandundlichttest.feature.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Adapter providing onboarding slides for the view pager.
 */
class OnboardingPagerAdapter extends RecyclerView.Adapter<OnboardingPagerAdapter.SlideViewHolder> {
    private static final Slide[] SLIDES = new Slide[]{
        new Slide(R.drawable.ic_home, R.string.onboarding_slide_welcome_title,
            R.string.onboarding_overview_description),
        new Slide(R.drawable.ic_add, R.string.onboarding_slide_plants_title,
            R.string.onboarding_plant_setup),
        new Slide(R.drawable.ic_light, R.string.onboarding_slide_measure_title,
            R.string.onboarding_measurement),
        new Slide(R.drawable.ic_book, R.string.onboarding_slide_diary_title,
            R.string.onboarding_diary)
    };

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_onboarding_page, parent, false);
        return new SlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        Slide slide = SLIDES[position];
        holder.icon.setImageResource(slide.iconResId);
        holder.title.setText(slide.titleResId);
        holder.description.setText(slide.descriptionResId);
    }

    @Override
    public int getItemCount() {
        return SLIDES.length;
    }

    static class SlideViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView description;

        SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.onboarding_icon);
            title = itemView.findViewById(R.id.onboarding_title);
            description = itemView.findViewById(R.id.onboarding_description);
        }
    }

    private static class Slide {
        final int iconResId;
        final int titleResId;
        final int descriptionResId;

        Slide(int iconResId, int titleResId, int descriptionResId) {
            this.iconResId = iconResId;
            this.titleResId = titleResId;
            this.descriptionResId = descriptionResId;
        }
    }
}
