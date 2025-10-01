package de.oabidi.pflanzenbestandundlichttest.feature.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Fragment representing a single onboarding slide.
 */
public class OnboardingSlideFragment extends Fragment {
    private static final String ARG_LAYOUT = "layout";
    private static final String ARG_ICON = "icon";
    private static final String ARG_TITLE = "title";
    private static final String ARG_DESCRIPTION = "description";

    public OnboardingSlideFragment() {
        // Default empty constructor.
    }

    @NonNull
    public static OnboardingSlideFragment newInstance(@LayoutRes int layoutResId,
                                                      @DrawableRes int iconResId, @StringRes int titleResId,
                                                      @StringRes int descriptionResId) {
        OnboardingSlideFragment fragment = new OnboardingSlideFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT, layoutResId);
        args.putInt(ARG_ICON, iconResId);
        args.putInt(ARG_TITLE, titleResId);
        args.putInt(ARG_DESCRIPTION, descriptionResId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        int layoutResId = args.getInt(ARG_LAYOUT);
        return inflater.inflate(layoutResId, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        int iconResId = args.getInt(ARG_ICON);
        int titleResId = args.getInt(ARG_TITLE);
        int descriptionResId = args.getInt(ARG_DESCRIPTION);

        ImageView iconView = view.findViewById(R.id.onboarding_icon);
        if (iconView != null && iconResId != 0) {
            iconView.setImageResource(iconResId);
            iconView.setContentDescription(getString(titleResId));
        }

        TextView titleView = view.findViewById(R.id.onboarding_title);
        if (titleView != null && titleResId != 0) {
            titleView.setText(titleResId);
        }

        TextView descriptionView = view.findViewById(R.id.onboarding_description);
        if (descriptionView != null && descriptionResId != 0) {
            descriptionView.setText(descriptionResId);
        }
    }
}
