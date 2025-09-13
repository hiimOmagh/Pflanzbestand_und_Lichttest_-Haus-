package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Fragment displaying a simple onboarding slider.
 */
public class OnboardingFragment extends Fragment {
    private PlantRepository repository;

    public static OnboardingFragment newInstance(PlantRepository repository) {
        OnboardingFragment fragment = new OnboardingFragment();
        fragment.repository = repository;
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewPager2 pager = view.findViewById(R.id.onboarding_pager);
        pager.setAdapter(new OnboardingAdapter(new int[]{
            R.string.onboarding_plant_setup,
            R.string.onboarding_measurement,
            R.string.onboarding_diary
        }));

        Button done = view.findViewById(R.id.onboarding_done);
        done.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        Context ctx = requireContext();
        SharedPreferences prefs = ctx.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(SettingsKeys.KEY_HAS_ONBOARDED, true).apply();
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_nav);
            bottomNav.setSelectedItemId(R.id.nav_plants);
            PlantRepository repo = repository != null ? repository : new PlantRepository(requireContext().getApplicationContext());
            getParentFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, PlantListFragment.newInstance(repo))
                .commit();
        }
    }

    private static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.VH> {
        private final int[] texts;
        OnboardingAdapter(int[] texts) {
            this.texts = texts;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.item_onboarding_page, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.text.setText(texts[position]);
        }

        @Override
        public int getItemCount() {
            return texts.length;
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView text;
            VH(View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.onboarding_text);
            }
        }
    }
}
