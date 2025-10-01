package de.oabidi.pflanzenbestandundlichttest.feature.onboarding;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Adapter providing onboarding slides for the view pager.
 */
class OnboardingPagerAdapter extends FragmentStateAdapter {
    private static final OnboardingSlide[] SLIDES = new OnboardingSlide[]{
        new OnboardingSlide(R.layout.onboarding_screen1, R.drawable.ic_houseplant,
            R.string.onboarding_slide_welcome_title, R.string.onboarding_overview_description),
        new OnboardingSlide(R.layout.onboarding_screen2, R.drawable.ic_add,
            R.string.onboarding_slide_plants_title, R.string.onboarding_plant_setup),
        new OnboardingSlide(R.layout.onboarding_screen3, R.drawable.ic_light,
            R.string.onboarding_slide_measure_title, R.string.onboarding_measurement),
        new OnboardingSlide(R.layout.onboarding_screen4, R.drawable.ic_book,
            R.string.onboarding_slide_diary_title, R.string.onboarding_diary),
        new OnboardingSlide(R.layout.onboarding_screen5, R.drawable.ic_alarm,
            R.string.onboarding_slide_alerts_title, R.string.onboarding_alerts)
    };

    OnboardingPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        OnboardingSlide slide = SLIDES[position];
        return OnboardingSlideFragment.newInstance(slide.layoutResId, slide.iconResId,
            slide.titleResId, slide.descriptionResId);
    }

    @Override
    public int getItemCount() {
        return SLIDES.length;
    }

    private static final class OnboardingSlide {
        @LayoutRes
        final int layoutResId;
        @DrawableRes
        final int iconResId;
        @StringRes
        final int titleResId;
        @StringRes
        final int descriptionResId;

        OnboardingSlide(@LayoutRes int layoutResId, @DrawableRes int iconResId,
                        @StringRes int titleResId, @StringRes int descriptionResId) {
            this.layoutResId = layoutResId;
            this.iconResId = iconResId;
            this.titleResId = titleResId;
            this.descriptionResId = descriptionResId;
        }
    }
}
