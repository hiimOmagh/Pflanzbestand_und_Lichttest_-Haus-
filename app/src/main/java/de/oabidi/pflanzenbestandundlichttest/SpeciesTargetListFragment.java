package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import de.oabidi.pflanzenbestandundlichttest.common.ui.InsetsUtils;

import java.util.ArrayList;

/**
 * Fragment displaying the list of PPFD targets per species.
 */
public class SpeciesTargetListFragment extends Fragment implements SpeciesTargetAdapter.OnTargetClickListener {
    private PlantRepository repository;
    private SpeciesTargetAdapter adapter;

    public static SpeciesTargetListFragment newInstance(PlantRepository repository) {
        SpeciesTargetListFragment fragment = new SpeciesTargetListFragment();
        fragment.repository = repository;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_species_target_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }

        InsetsUtils.requestApplyInsetsWhenAttached(view);
        RecyclerView recyclerView = view.findViewById(R.id.target_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setClipToPadding(false);
        InsetsUtils.applySystemWindowInsetsPadding(recyclerView, false, false, false, true);
        adapter = new SpeciesTargetAdapter(this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_target);
        InsetsUtils.applySystemWindowInsetsMargin(fab, false, false, false, true);
        fab.setOnClickListener(v -> showDialog(null));

        loadTargets();
    }

    private void loadTargets() {
        repository.getAllSpeciesTargets(targets -> adapter.submitList(new ArrayList<>(targets)),
            e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); });
    }

    private void showDialog(@Nullable SpeciesTarget target) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_species_target, null);
        EditText keyEdit = dialogView.findViewById(R.id.edit_species_key);
        EditText seedlingPpfdMin = dialogView.findViewById(R.id.edit_seedling_ppfd_min);
        EditText seedlingPpfdMax = dialogView.findViewById(R.id.edit_seedling_ppfd_max);
        EditText seedlingDliMin = dialogView.findViewById(R.id.edit_seedling_dli_min);
        EditText seedlingDliMax = dialogView.findViewById(R.id.edit_seedling_dli_max);
        EditText vegetativePpfdMin = dialogView.findViewById(R.id.edit_vegetative_ppfd_min);
        EditText vegetativePpfdMax = dialogView.findViewById(R.id.edit_vegetative_ppfd_max);
        EditText vegetativeDliMin = dialogView.findViewById(R.id.edit_vegetative_dli_min);
        EditText vegetativeDliMax = dialogView.findViewById(R.id.edit_vegetative_dli_max);
        EditText flowerPpfdMin = dialogView.findViewById(R.id.edit_flower_ppfd_min);
        EditText flowerPpfdMax = dialogView.findViewById(R.id.edit_flower_ppfd_max);
        EditText flowerDliMin = dialogView.findViewById(R.id.edit_flower_dli_min);
        EditText flowerDliMax = dialogView.findViewById(R.id.edit_flower_dli_max);
        EditText toleranceEdit = dialogView.findViewById(R.id.edit_tolerance);
        EditText sourceEdit = dialogView.findViewById(R.id.edit_source);

        StageFields seedlingFields = new StageFields(seedlingPpfdMin, seedlingPpfdMax, seedlingDliMin, seedlingDliMax);
        StageFields vegetativeFields = new StageFields(vegetativePpfdMin, vegetativePpfdMax, vegetativeDliMin, vegetativeDliMax);
        StageFields flowerFields = new StageFields(flowerPpfdMin, flowerPpfdMax, flowerDliMin, flowerDliMax);

        if (target != null) {
            keyEdit.setText(target.getSpeciesKey());
            populateStage(seedlingFields, target.getSeedlingStage());
            populateStage(vegetativeFields, target.getVegetativeStage());
            populateStage(flowerFields, target.getFlowerStage());
            if (target.getTolerance() != null) {
                toleranceEdit.setText(target.getTolerance());
            }
            if (target.getSource() != null) {
                sourceEdit.setText(target.getSource());
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setTitle(target == null ? R.string.action_add_target : R.string.action_edit_target)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            SimpleTextWatcher watcher = new SimpleTextWatcher(() -> positive.setEnabled(isInputValid(
                keyEdit.getText().toString(),
                seedlingFields,
                vegetativeFields,
                flowerFields)));

            keyEdit.addTextChangedListener(watcher);
            seedlingFields.attachWatcher(watcher);
            vegetativeFields.attachWatcher(watcher);
            flowerFields.attachWatcher(watcher);

            positive.setOnClickListener(v -> {
                if (validateInputs(keyEdit, seedlingFields, vegetativeFields, flowerFields)) {
                    String key = keyEdit.getText().toString();
                    SpeciesTarget.StageTarget seedling = buildStage(seedlingFields);
                    SpeciesTarget.StageTarget vegetative = buildStage(vegetativeFields);
                    SpeciesTarget.StageTarget flower = buildStage(flowerFields);
                    String tolerance = emptyToNull(toleranceEdit.getText().toString());
                    String source = emptyToNull(sourceEdit.getText().toString());
                    SpeciesTarget newTarget = new SpeciesTarget(key, seedling, vegetative, flower, tolerance, source);
                    repository.insertSpeciesTarget(newTarget, this::loadTargets,
                        e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); });
                    dialog.dismiss();
                }
            });

            // initialize button state
            positive.setEnabled(isInputValid(
                keyEdit.getText().toString(),
                seedlingFields,
                vegetativeFields,
                flowerFields));
        });

        dialog.show();
    }

    static boolean isInputValid(String key, StageFields... stages) {
        if (key.trim().isEmpty()) {
            return false;
        }
        boolean hasStage = false;
        for (StageFields fields : stages) {
            ValidationResult result = validateStage(fields, false);
            if (!result.valid) {
                return false;
            }
            hasStage |= result.hasValues;
        }
        return hasStage;
    }

    private boolean validateInputs(EditText keyEdit, StageFields... stages) {
        String key = keyEdit.getText().toString();
        boolean valid = true;
        if (key.trim().isEmpty()) {
            keyEdit.setError(getString(R.string.error_required));
            valid = false;
        } else {
            keyEdit.setError(null);
        }
        boolean hasStage = false;
        for (StageFields fields : stages) {
            ValidationResult result = validateStage(fields, true);
            valid &= result.valid;
            hasStage |= result.hasValues;
        }
        if (!hasStage) {
            Snackbar.make(requireView(), R.string.error_stage_required, Snackbar.LENGTH_LONG).show();
            valid = false;
        }
        return valid;
    }

    private void populateStage(StageFields fields, @Nullable SpeciesTarget.StageTarget stage) {
        if (stage == null) {
            return;
        }
        if (stage.getPpfdMin() != null) {
            fields.ppfdMin.setText(formatFloat(stage.getPpfdMin()));
        }
        if (stage.getPpfdMax() != null) {
            fields.ppfdMax.setText(formatFloat(stage.getPpfdMax()));
        }
        if (stage.getDliMin() != null) {
            fields.dliMin.setText(formatFloat(stage.getDliMin()));
        }
        if (stage.getDliMax() != null) {
            fields.dliMax.setText(formatFloat(stage.getDliMax()));
        }
    }

    private String formatFloat(float value) {
        if ((long) value == value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private SpeciesTarget.StageTarget buildStage(StageFields fields) {
        Float ppfdMin = parseNullable(fields.ppfdMin.getText().toString());
        Float ppfdMax = parseNullable(fields.ppfdMax.getText().toString());
        Float dliMin = parseNullable(fields.dliMin.getText().toString());
        Float dliMax = parseNullable(fields.dliMax.getText().toString());
        return new SpeciesTarget.StageTarget(ppfdMin, ppfdMax, dliMin, dliMax);
    }

    private Float parseNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static ValidationResult validateStage(StageFields fields, boolean showErrors) {
        boolean valid = true;
        boolean hasValues = false;

        ValidationResult ppfd = validateRange(fields.ppfdMin, fields.ppfdMax, showErrors);
        valid &= ppfd.valid;
        hasValues |= ppfd.hasValues;

        ValidationResult dli = validateRange(fields.dliMin, fields.dliMax, showErrors);
        valid &= dli.valid;
        hasValues |= dli.hasValues;

        return new ValidationResult(valid, hasValues);
    }

    private static ValidationResult validateRange(EditText minField, EditText maxField, boolean showErrors) {
        String minText = minField.getText().toString().trim();
        String maxText = maxField.getText().toString().trim();
        boolean hasMin = !minText.isEmpty();
        boolean hasMax = !maxText.isEmpty();
        if (!hasMin && !hasMax) {
            if (showErrors) {
                minField.setError(null);
                maxField.setError(null);
            }
            return new ValidationResult(true, false);
        }
        if (!hasMin || !hasMax) {
            if (showErrors) {
                String error = minField.getContext().getString(R.string.error_required);
                if (!hasMin) {
                    minField.setError(error);
                }
                if (!hasMax) {
                    maxField.setError(error);
                }
            }
            return new ValidationResult(false, false);
        }
        try {
            float min = Float.parseFloat(minText);
            float max = Float.parseFloat(maxText);
            if (min >= max) {
                if (showErrors) {
                    String error = minField.getContext().getString(R.string.error_ppfd_range);
                    minField.setError(error);
                    maxField.setError(error);
                }
                return new ValidationResult(false, true);
            }
        } catch (NumberFormatException e) {
            if (showErrors) {
                String error = minField.getContext().getString(R.string.error_positive_number);
                minField.setError(error);
                maxField.setError(error);
            }
            return new ValidationResult(false, false);
        }
        if (showErrors) {
            minField.setError(null);
            maxField.setError(null);
        }
        return new ValidationResult(true, true);
    }

    private static class StageFields {
        final EditText ppfdMin;
        final EditText ppfdMax;
        final EditText dliMin;
        final EditText dliMax;

        StageFields(EditText ppfdMin, EditText ppfdMax, EditText dliMin, EditText dliMax) {
            this.ppfdMin = ppfdMin;
            this.ppfdMax = ppfdMax;
            this.dliMin = dliMin;
            this.dliMax = dliMax;
        }

        void attachWatcher(TextWatcher watcher) {
            ppfdMin.addTextChangedListener(watcher);
            ppfdMax.addTextChangedListener(watcher);
            dliMin.addTextChangedListener(watcher);
            dliMax.addTextChangedListener(watcher);
        }
    }

    private static class ValidationResult {
        final boolean valid;
        final boolean hasValues;

        ValidationResult(boolean valid, boolean hasValues) {
            this.valid = valid;
            this.hasValues = hasValues;
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleTextWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChange.run();
        }
    }

    @Override
    public void onTargetClick(SpeciesTarget target) {
        showDialog(target);
    }

    @Override
    public void onTargetLongClick(SpeciesTarget target) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete_target)
            .setMessage(R.string.confirm_delete_target)
            .setPositiveButton(android.R.string.ok, (d, which) ->
                repository.deleteSpeciesTarget(target.getSpeciesKey(), this::loadTargets,
                    e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); }))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
