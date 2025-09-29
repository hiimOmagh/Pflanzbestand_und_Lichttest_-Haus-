package de.oabidi.pflanzenbestandundlichttest.feature.onboarding;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;

/**
 * Activity hosting the onboarding view pager.
 */
public class OnboardingActivity extends AppCompatActivity {
    private ViewPager2 pager;
    private MaterialButton nextButton;
    private MaterialButton skipButton;
    private OnboardingPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        MaterialToolbar toolbar = findViewById(R.id.onboarding_toolbar);
        InsetsUtils.applySystemWindowInsetsPadding(toolbar, true, true, true, false);
        toolbar.setTitle(R.string.app_name);

        View actions = findViewById(R.id.onboarding_actions);
        InsetsUtils.applySystemWindowInsetsPadding(actions, true, false, true, true);

        pager = findViewById(R.id.onboarding_pager);
        pagerAdapter = new OnboardingPagerAdapter();
        pager.setAdapter(pagerAdapter);

        skipButton = findViewById(R.id.onboarding_skip);
        nextButton = findViewById(R.id.onboarding_next);

        skipButton.setOnClickListener(v -> completeOnboarding());
        nextButton.setOnClickListener(v -> {
            int nextItem = pager.getCurrentItem() + 1;
            if (nextItem < pagerAdapter.getItemCount()) {
                pager.setCurrentItem(nextItem, true);
            } else {
                completeOnboarding();
            }
        });

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateButtonState(position);
            }
        });

        updateButtonState(0);
    }

    private void updateButtonState(int position) {
        boolean isLastPage = position == pagerAdapter.getItemCount() - 1;
        nextButton.setText(isLastPage
            ? R.string.onboarding_action_get_started
            : R.string.onboarding_action_next);
        skipButton.setVisibility(isLastPage ? View.GONE : View.VISIBLE);
    }

    private void completeOnboarding() {
        SharedPreferences preferences = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE);
        preferences.edit()
            .putBoolean(SettingsKeys.KEY_HAS_ONBOARDED, true)
            .putBoolean(SettingsKeys.KEY_ONBOARDING_COMPLETE, true)
            .apply();
        setResult(Activity.RESULT_OK);
        finish();
    }
}
