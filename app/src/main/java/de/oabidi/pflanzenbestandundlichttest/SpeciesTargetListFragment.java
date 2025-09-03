package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

/**
 * Fragment displaying the list of PPFD targets per species.
 */
public class SpeciesTargetListFragment extends Fragment implements SpeciesTargetAdapter.OnTargetClickListener {
    private PlantRepository repository;
    private SpeciesTargetAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_species_target_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new PlantRepository(requireContext().getApplicationContext());

        RecyclerView recyclerView = view.findViewById(R.id.target_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SpeciesTargetAdapter(this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_target);
        fab.setOnClickListener(v -> showDialog(null));

        loadTargets();
    }

    private void loadTargets() {
        repository.getAllSpeciesTargets(targets -> adapter.submitList(new ArrayList<>(targets)));
    }

    private void showDialog(@Nullable SpeciesTarget target) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_species_target, null);
        EditText keyEdit = dialogView.findViewById(R.id.edit_species_key);
        EditText minEdit = dialogView.findViewById(R.id.edit_ppfd_min);
        EditText maxEdit = dialogView.findViewById(R.id.edit_ppfd_max);

        if (target != null) {
            keyEdit.setText(target.getSpeciesKey());
            minEdit.setText(String.valueOf(target.getPpfdMin()));
            maxEdit.setText(String.valueOf(target.getPpfdMax()));
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(target == null ? R.string.action_add_target : R.string.action_edit_target)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String key = keyEdit.getText().toString();
                float min = parseFloat(minEdit.getText().toString());
                float max = parseFloat(maxEdit.getText().toString());
                SpeciesTarget newTarget = new SpeciesTarget(key, min, max);
                repository.insertSpeciesTarget(newTarget, this::loadTargets);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private float parseFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    @Override
    public void onTargetClick(SpeciesTarget target) {
        showDialog(target);
    }
}
