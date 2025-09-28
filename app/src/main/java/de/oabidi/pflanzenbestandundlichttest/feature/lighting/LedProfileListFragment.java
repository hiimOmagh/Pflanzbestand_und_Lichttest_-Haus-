package de.oabidi.pflanzenbestandundlichttest.feature.lighting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;

/**
 * Displays the list of LED lighting profiles.
 */
public class LedProfileListFragment extends Fragment implements LedProfileAdapter.OnProfileClickListener {

    public static final String RESULT_KEY_PROFILES_UPDATED = "led_profiles_updated";

    private static final String DIALOG_TAG = "LedProfileEditor";

    private PlantRepository repository;
    private LedProfileAdapter adapter;

    public static LedProfileListFragment newInstance(PlantRepository repository) {
        LedProfileListFragment fragment = new LedProfileListFragment();
        fragment.repository = repository;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_led_profile_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }

        InsetsUtils.requestApplyInsetsWhenAttached(view);

        RecyclerView recyclerView = view.findViewById(R.id.profile_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setClipToPadding(false);
        InsetsUtils.applySystemWindowInsetsPadding(recyclerView, false, false, false, true);
        adapter = new LedProfileAdapter(this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_profile);
        InsetsUtils.applySystemWindowInsetsMargin(fab, false, false, false, true);
        fab.setOnClickListener(v -> openEditor(null));

        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_PROFILES_UPDATED,
            this, (requestKey, result) -> loadProfiles());

        loadProfiles();
    }

    private void loadProfiles() {
        repository.getLedProfiles(this::submitProfiles,
            e -> {
                if (isAdded()) {
                    Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show();
                }
            });
    }

    private void submitProfiles(List<LedProfile> profiles) {
        adapter.submitList(new ArrayList<>(profiles));
    }

    private void openEditor(@Nullable LedProfile profile) {
        FragmentManager manager = getParentFragmentManager();
        LedProfileEditorFragment fragment = LedProfileEditorFragment.newInstance(profile);
        fragment.setRepository(repository);
        fragment.show(manager, DIALOG_TAG);
    }

    @Override
    public void onProfileClick(LedProfile profile) {
        openEditor(profile);
    }
}
