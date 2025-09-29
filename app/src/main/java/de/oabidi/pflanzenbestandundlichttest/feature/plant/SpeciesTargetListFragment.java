package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.repository.SpeciesRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying the list of PPFD targets per species.
 */
public class SpeciesTargetListFragment extends Fragment implements SpeciesTargetAdapter.OnTargetClickListener, SpeciesSearchView {
    private PlantRepository repository;
    private SpeciesRepository speciesRepository;
    private SpeciesTargetAdapter adapter;
    private RecyclerView recyclerView;
    private View emptyStateView;
    private View progressView;
    private SpeciesSearchPresenter searchPresenter;
    private String currentQuery = "";

    public static SpeciesTargetListFragment newInstance(PlantRepository repository) {
        SpeciesTargetListFragment fragment = new SpeciesTargetListFragment();
        fragment.repository = repository;
        fragment.speciesRepository = repository.speciesRepository();
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.species_target_list_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search_species);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.species_search_hint));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    handleSearchQuery(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    handleSearchQuery(newText);
                    return true;
                }
            });
            searchView.setOnCloseListener(() -> {
                if (!TextUtils.isEmpty(currentQuery)) {
                    currentQuery = "";
                    if (searchPresenter != null) {
                        searchPresenter.onSearchQueryChanged(currentQuery);
                    }
                    loadTargets();
                }
                return false;
            });
        }
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                currentQuery = "";
                if (searchPresenter != null) {
                    searchPresenter.onSearchQueryChanged(currentQuery);
                }
                loadTargets();
                return true;
            }
        });
    }

    private void handleSearchQuery(@Nullable String query) {
        String normalized = query == null ? "" : query.trim();
        if (TextUtils.equals(currentQuery, normalized) && !normalized.isEmpty()) {
            return;
        }
        currentQuery = normalized;
        if (emptyStateView instanceof TextView && TextUtils.isEmpty(normalized)) {
            ((TextView) emptyStateView).setText(getString(R.string.species_search_empty_prompt));
        }
        if (searchPresenter == null) {
            return;
        }
        if (normalized.isEmpty()) {
            searchPresenter.onSearchQueryChanged(normalized);
            loadTargets();
        } else {
            searchPresenter.onSearchQueryChanged(normalized);
        }
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_species_target_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PlantRepository repo = repository;
        if (repo == null) {
            repo = RepositoryProvider.getRepository(requireContext());
            repository = repo;
        }
        SpeciesRepository speciesRepo = speciesRepository;
        if (speciesRepo == null) {
            speciesRepo = repo.speciesRepository();
        }
        speciesRepository = speciesRepo;

        InsetsUtils.requestApplyInsetsWhenAttached(view);
        recyclerView = view.findViewById(R.id.target_list);
        emptyStateView = view.findViewById(R.id.empty_state);
        progressView = view.findViewById(R.id.search_progress);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setClipToPadding(false);
        InsetsUtils.applySystemWindowInsetsPadding(recyclerView, false, false, false, true);
        adapter = new SpeciesTargetAdapter(this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_target);
        InsetsUtils.applySystemWindowInsetsMargin(fab, false, false, false, true);
        fab.setOnClickListener(v -> showDialog(null));

        searchPresenter = new SpeciesSearchPresenterImpl(this, speciesRepo, requireContext());

        loadTargets();
    }

    private void loadTargets() {
        if (searchPresenter != null && !TextUtils.isEmpty(currentQuery)) {
            searchPresenter.onSearchQueryChanged(currentQuery);
            return;
        }
        repository.getAllSpeciesTargets(targets -> {
                if (!isAdded()) {
                    return;
                }
                hideLoading();
                if (emptyStateView instanceof TextView) {
                    ((TextView) emptyStateView).setText(getString(R.string.species_search_empty_prompt));
                }
                if (emptyStateView != null) {
                    emptyStateView.setVisibility(View.GONE);
                }
                recyclerView.setVisibility(View.VISIBLE);
                adapter.submitList(new ArrayList<>(targets));
            },
            e -> {
                if (isAdded()) {
                    showError(getString(R.string.error_database));
                }
            });
    }


    @Override
    public void showLoading() {
        if (!isAdded() || progressView == null) {
            return;
        }
        progressView.setVisibility(View.VISIBLE);
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    @Override
    public void hideLoading() {
        if (progressView != null) {
            progressView.setVisibility(View.GONE);
        }
    }

    @Override
    public void showResults(List<SpeciesTarget> results) {
        if (!isAdded()) {
            return;
        }
        hideLoading();
        if (emptyStateView instanceof TextView) {
            ((TextView) emptyStateView).setText(getString(R.string.species_search_empty_prompt));
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
        recyclerView.setVisibility(View.VISIBLE);
        adapter.submitList(new ArrayList<>(results));
    }

    @Override
    public void showEmptyState() {
        if (!isAdded()) {
            return;
        }
        hideLoading();
        if (TextUtils.isEmpty(currentQuery)) {
            if (emptyStateView instanceof TextView) {
                ((TextView) emptyStateView).setText(getString(R.string.species_search_empty_prompt));
            }
            if (emptyStateView != null) {
                emptyStateView.setVisibility(View.GONE);
            }
            recyclerView.setVisibility(View.VISIBLE);
            return;
        }
        adapter.submitList(new ArrayList<>());
        recyclerView.setVisibility(View.GONE);
        if (emptyStateView instanceof TextView) {
            ((TextView) emptyStateView).setText(getString(R.string.species_search_no_results));
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showError(String message) {
        if (!isAdded()) {
            return;
        }
        hideLoading();
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        if (searchPresenter != null) {
            searchPresenter.onDestroy();
            searchPresenter = null;
        }
        recyclerView = null;
        emptyStateView = null;
        progressView = null;
        super.onDestroyView();
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
        EditText wateringFrequencyEdit = dialogView.findViewById(R.id.edit_watering_frequency);
        EditText wateringSoilEdit = dialogView.findViewById(R.id.edit_watering_soil);
        Spinner toxicitySpinner = dialogView.findViewById(R.id.spinner_toxicity);
        EditText temperatureMinEdit = dialogView.findViewById(R.id.edit_temperature_min);
        EditText temperatureMaxEdit = dialogView.findViewById(R.id.edit_temperature_max);
        EditText humidityMinEdit = dialogView.findViewById(R.id.edit_humidity_min);
        EditText humidityMaxEdit = dialogView.findViewById(R.id.edit_humidity_max);
        EditText careTipsEdit = dialogView.findViewById(R.id.edit_care_tips);

        ArrayAdapter<CharSequence> toxicityAdapter = ArrayAdapter.createFromResource(requireContext(),
            R.array.metadata_toxicity_options, android.R.layout.simple_spinner_item);
        toxicityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        toxicitySpinner.setAdapter(toxicityAdapter);

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
            SpeciesTarget.WateringInfo wateringInfo = target.getWateringInfo();
            if (wateringInfo != null) {
                if (!TextUtils.isEmpty(wateringInfo.getFrequency())) {
                    wateringFrequencyEdit.setText(wateringInfo.getFrequency());
                }
                if (!TextUtils.isEmpty(wateringInfo.getSoilType())) {
                    wateringSoilEdit.setText(wateringInfo.getSoilType());
                }
            }
            toxicitySpinner.setSelection(toxicitySelectionForValue(target.getToxicToPets()));
            SpeciesTarget.FloatRange temperatureRange = target.getTemperatureRange();
            if (temperatureRange != null) {
                if (temperatureRange.getMin() != null) {
                    temperatureMinEdit.setText(formatFloat(temperatureRange.getMin()));
                }
                if (temperatureRange.getMax() != null) {
                    temperatureMaxEdit.setText(formatFloat(temperatureRange.getMax()));
                }
            }
            SpeciesTarget.FloatRange humidityRange = target.getHumidityRange();
            if (humidityRange != null) {
                if (humidityRange.getMin() != null) {
                    humidityMinEdit.setText(formatFloat(humidityRange.getMin()));
                }
                if (humidityRange.getMax() != null) {
                    humidityMaxEdit.setText(formatFloat(humidityRange.getMax()));
                }
            }
            List<String> careTips = target.getCareTips();
            if (careTips != null && !careTips.isEmpty()) {
                careTipsEdit.setText(TextUtils.join("\n", careTips));
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
                    SpeciesTarget.WateringInfo wateringInfo = newTarget.getWateringInfo();
                    if (wateringInfo == null) {
                        wateringInfo = new SpeciesTarget.WateringInfo();
                    }
                    wateringInfo.setFrequency(emptyToNull(wateringFrequencyEdit.getText().toString()));
                    wateringInfo.setSoilType(emptyToNull(wateringSoilEdit.getText().toString()));
                    wateringInfo.setTolerance(tolerance);
                    newTarget.setWateringInfo(wateringInfo);
                    Float tempMin = parseNullable(temperatureMinEdit.getText().toString());
                    Float tempMax = parseNullable(temperatureMaxEdit.getText().toString());
                    if (tempMin != null || tempMax != null) {
                        newTarget.setTemperatureRange(new SpeciesTarget.FloatRange(tempMin, tempMax));
                    } else {
                        newTarget.setTemperatureRange(null);
                    }
                    Float humidityMin = parseNullable(humidityMinEdit.getText().toString());
                    Float humidityMax = parseNullable(humidityMaxEdit.getText().toString());
                    if (humidityMin != null || humidityMax != null) {
                        newTarget.setHumidityRange(new SpeciesTarget.FloatRange(humidityMin, humidityMax));
                    } else {
                        newTarget.setHumidityRange(null);
                    }
                    newTarget.setToxicToPets(parseToxicitySelection(toxicitySpinner.getSelectedItemPosition()));
                    List<String> careTips = parseCareTipsInput(careTipsEdit.getText().toString());
                    newTarget.setCareTips(careTips.isEmpty() ? null : careTips);
                    repository.insertSpeciesTarget(newTarget, this::loadTargets,
                        e -> {
                            if (isAdded())
                                showError(getString(R.string.error_database));
                        });
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

    private int toxicitySelectionForValue(@Nullable Boolean value) {
        if (value == null) {
            return 0;
        }
        return value ? 2 : 1;
    }

    @Nullable
    private Boolean parseToxicitySelection(int position) {
        if (position <= 0) {
            return null;
        }
        return position == 2;
    }

    private List<String> parseCareTipsInput(@Nullable String value) {
        List<String> tips = new ArrayList<>();
        if (value == null) {
            return tips;
        }
        String[] lines = value.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                tips.add(trimmed);
            }
        }
        return tips;
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
                    e -> {
                        if (isAdded())
                            showError(getString(R.string.error_database));
                    }))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
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
}
