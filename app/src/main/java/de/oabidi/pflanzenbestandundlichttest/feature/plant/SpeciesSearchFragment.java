package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.repository.SpeciesRepository;

/**
 * Fragment allowing users to search for plant species metadata.
 */
public class SpeciesSearchFragment extends Fragment implements SpeciesSearchView {
    private static final String STATE_QUERY = "state_query";

    @Nullable
    private SpeciesRepository speciesRepository;
    @Nullable
    private SpeciesSearchPresenter presenter;
    @Nullable
    private RecyclerView resultsView;
    @Nullable
    private View emptyStateView;
    @Nullable
    private ProgressBar progressBar;
    @Nullable
    private TextInputLayout searchInputLayout;
    @Nullable
    private EditText searchInput;
    @Nullable
    private TextWatcher searchWatcher;
    @Nullable
    private SpeciesSearchAdapter adapter;

    @NonNull
    private String currentQuery = "";

    /**
     * Factory method for creating a new instance.
     */
    public static SpeciesSearchFragment newInstance() {
        return new SpeciesSearchFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_species_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.requestApplyInsetsWhenAttached(view);

        searchInputLayout = view.findViewById(R.id.species_search_bar);
        if (searchInputLayout != null) {
            searchInput = searchInputLayout.getEditText();
            InsetsUtils.applySystemWindowInsetsMargin(searchInputLayout, false, true, false, false);
        } else {
            searchInput = null;
        }
        if (searchInput == null) {
            searchInput = view.findViewById(R.id.species_search_input);
        }
        resultsView = view.findViewById(R.id.species_search_results);
        emptyStateView = view.findViewById(R.id.species_search_empty_state);
        progressBar = view.findViewById(R.id.species_search_progress);

        if (resultsView != null) {
            Context context = requireContext();
            resultsView.setLayoutManager(new LinearLayoutManager(context));
            resultsView.setClipToPadding(false);
            InsetsUtils.applySystemWindowInsetsPadding(resultsView, false, false, false, true);
            adapter = new SpeciesSearchAdapter(this, this::showDetailDialog);
            resultsView.setAdapter(adapter);
        }

        if (savedInstanceState != null) {
            currentQuery = savedInstanceState.getString(STATE_QUERY, "");
        }

        if (!currentQuery.isEmpty() && searchInput != null) {
            searchInput.setText(currentQuery);
            searchInput.setSelection(currentQuery.length());
        }

        speciesRepository = RepositoryProvider.getSpeciesRepository(requireContext());
        presenter = new SpeciesSearchPresenterImpl(this, speciesRepository, requireContext());

        if (searchInput != null) {
            searchWatcher = new SimpleTextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String normalized = s == null ? "" : s.toString().trim();
                    if (TextUtils.equals(currentQuery, normalized)) {
                        return;
                    }
                    currentQuery = normalized;
                    if (presenter != null) {
                        presenter.onSearchQueryChanged(normalized);
                    }
                }
            };
            searchInput.addTextChangedListener(searchWatcher);
        }

        if (presenter != null) {
            if (!currentQuery.isEmpty()) {
                presenter.onSearchQueryChanged(currentQuery);
            } else {
                showEmptyState();
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, currentQuery);
    }

    @Override
    public void onDestroyView() {
        if (searchInput != null && searchWatcher != null) {
            searchInput.removeTextChangedListener(searchWatcher);
        }
        searchWatcher = null;
        searchInput = null;
        searchInputLayout = null;

        if (resultsView != null) {
            resultsView.setAdapter(null);
        }
        adapter = null;
        resultsView = null;
        emptyStateView = null;
        progressBar = null;

        if (presenter != null) {
            presenter.onDestroy();
        }
        presenter = null;
        speciesRepository = null;

        super.onDestroyView();
    }

    @Override
    public void showLoading() {
        if (!isAdded()) {
            return;
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    @Override
    public void hideLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void showResults(List<SpeciesTarget> results) {
        if (!isAdded()) {
            return;
        }
        hideLoading();
        if (resultsView != null) {
            resultsView.setVisibility(View.VISIBLE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
        if (adapter != null) {
            adapter.submitList(new ArrayList<>(results));
        }
    }

    @Override
    public void showEmptyState() {
        if (!isAdded()) {
            return;
        }
        hideLoading();
        if (adapter != null) {
            adapter.submitList(Collections.emptyList());
        }
        if (emptyStateView instanceof TextView) {
            int messageRes = TextUtils.isEmpty(currentQuery)
                ? R.string.species_search_empty_prompt
                : R.string.species_search_no_results;
            ((TextView) emptyStateView).setText(getString(messageRes));
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.VISIBLE);
        }
        if (resultsView != null) {
            resultsView.setVisibility(View.GONE);
        }
    }

    @Override
    public void showError(String message) {
        if (!isAdded()) {
            return;
        }
        hideLoading();
        View view = getView();
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }
    }

    private void showDetailDialog(@NonNull SpeciesTarget target) {
        if (!isAdded()) {
            return;
        }
        String title = !TextUtils.isEmpty(target.getCommonName())
            ? target.getCommonName()
            : !TextUtils.isEmpty(target.getScientificName())
            ? target.getScientificName()
            : getString(R.string.species_search_detail_title_fallback);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View contentView = inflater.inflate(R.layout.dialog_species_detail, null);

        TextView scientificNameView = contentView.findViewById(R.id.species_detail_scientific_name);
        setTextOrHide(scientificNameView,
            !TextUtils.isEmpty(target.getScientificName())
                ? getString(R.string.species_search_scientific_label, target.getScientificName())
                : null);

        TextView categoryView = contentView.findViewById(R.id.species_detail_category);
        categoryView.setText(getString(R.string.species_search_category_label, formatCategory(target.getCategory())));

        TextView growthHabitView = contentView.findViewById(R.id.species_detail_growth_habit);
        setTextOrHide(growthHabitView,
            !TextUtils.isEmpty(target.getGrowthHabit())
                ? getString(R.string.species_search_growth_habit, target.getGrowthHabit())
                : null);

        TextView seedlingView = contentView.findViewById(R.id.species_detail_light_seedling);
        TextView vegetativeView = contentView.findViewById(R.id.species_detail_light_vegetative);
        TextView flowerView = contentView.findViewById(R.id.species_detail_light_flower);
        TextView lightPlaceholder = contentView.findViewById(R.id.species_detail_light_placeholder);
        boolean hasLight = bindStageDetail(seedlingView, target.getSeedlingStage(), R.string.label_stage_seedling)
            | bindStageDetail(vegetativeView, target.getVegetativeStage(), R.string.label_stage_vegetative)
            | bindStageDetail(flowerView, target.getFlowerStage(), R.string.label_stage_flower);
        lightPlaceholder.setVisibility(hasLight ? View.GONE : View.VISIBLE);

        TextView wateringScheduleView = contentView.findViewById(R.id.species_detail_watering_schedule);
        TextView wateringSoilView = contentView.findViewById(R.id.species_detail_watering_soil);
        TextView wateringToleranceView = contentView.findViewById(R.id.species_detail_watering_tolerance);
        TextView wateringPlaceholder = contentView.findViewById(R.id.species_detail_watering_placeholder);
        SpeciesTarget.WateringInfo wateringInfo = target.getWateringInfo();
        boolean hasWatering = false;
        if (wateringInfo != null && !TextUtils.isEmpty(wateringInfo.getFrequency())) {
            wateringScheduleView.setText(getString(R.string.species_search_watering_label, wateringInfo.getFrequency()));
            wateringScheduleView.setVisibility(View.VISIBLE);
            hasWatering = true;
        } else {
            wateringScheduleView.setVisibility(View.GONE);
        }
        if (wateringInfo != null && !TextUtils.isEmpty(wateringInfo.getSoilType())) {
            wateringSoilView.setText(getString(R.string.species_search_soil_label, wateringInfo.getSoilType()));
            wateringSoilView.setVisibility(View.VISIBLE);
            hasWatering = true;
        } else {
            wateringSoilView.setVisibility(View.GONE);
        }
        if (wateringInfo != null && !TextUtils.isEmpty(wateringInfo.getTolerance())) {
            wateringToleranceView.setText(getString(R.string.metadata_watering_tolerance, wateringInfo.getTolerance()));
            wateringToleranceView.setVisibility(View.VISIBLE);
            hasWatering = true;
        } else {
            wateringToleranceView.setVisibility(View.GONE);
        }
        wateringPlaceholder.setVisibility(hasWatering ? View.GONE : View.VISIBLE);

        TextView temperatureView = contentView.findViewById(R.id.species_detail_temperature_range);
        SpeciesTarget.FloatRange temperatureRange = target.getTemperatureRange();
        if (hasRange(temperatureRange)) {
            temperatureView.setText(getString(R.string.species_search_temperature_label,
                formatRangeValue(temperatureRange.getMin()),
                formatRangeValue(temperatureRange.getMax())));
        } else {
            temperatureView.setText(R.string.metadata_temperature_range_fallback);
        }

        TextView humidityView = contentView.findViewById(R.id.species_detail_humidity_range);
        SpeciesTarget.FloatRange humidityRange = target.getHumidityRange();
        if (hasRange(humidityRange)) {
            humidityView.setText(getString(R.string.species_search_humidity_label,
                formatRangeValue(humidityRange.getMin()),
                formatRangeValue(humidityRange.getMax())));
        } else {
            humidityView.setText(R.string.metadata_humidity_range_fallback);
        }

        TextView petSafetyView = contentView.findViewById(R.id.species_detail_pet_safety);
        Boolean toxic = target.getToxicToPets();
        if (toxic != null) {
            petSafetyView.setText(getString(toxic
                ? R.string.species_search_toxic_label
                : R.string.species_search_non_toxic_label));
        } else {
            petSafetyView.setText(R.string.species_search_unknown_toxicity_label);
        }

        TextView careTipsView = contentView.findViewById(R.id.species_detail_care_tips);
        String careTips = formatBulletedList(target.getCareTips());
        if (!TextUtils.isEmpty(careTips)) {
            careTipsView.setText(careTips);
        } else {
            careTipsView.setText(R.string.metadata_care_tips_fallback);
        }

        TextView sourcesView = contentView.findViewById(R.id.species_detail_sources);
        String sources = formatBulletedList(target.getSources());
        if (!TextUtils.isEmpty(sources)) {
            sourcesView.setText(getString(R.string.species_search_sources, "\n" + sources));
            sourcesView.setVisibility(View.VISIBLE);
        } else {
            sourcesView.setVisibility(View.GONE);
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(contentView)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private static boolean hasRange(@Nullable SpeciesTarget.FloatRange range) {
        return range != null && (range.getMin() != null || range.getMax() != null);
    }

    @NonNull
    private String formatRangeValue(@Nullable Float value) {
        if (value == null) {
            return getString(R.string.placeholder_dash);
        }
        return String.format(Locale.getDefault(), value % 1 == 0 ? "%.0f" : "%.1f", value);
    }

    @NonNull
    private String formatBulletedList(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("• ");
            builder.append(value.trim());
        }
        return builder.toString();
    }

    @NonNull
    private String formatCategory(@Nullable SpeciesTarget.Category category) {
        if (category == null) {
            return getString(R.string.species_category_other);
        }
        switch (category) {
            case HOUSEPLANT:
                return getString(R.string.species_category_houseplant);
            case HERB:
                return getString(R.string.species_category_herb);
            case VEGETABLE:
                return getString(R.string.species_category_vegetable);
            case FRUIT:
                return getString(R.string.species_category_fruit);
            case FLOWER:
                return getString(R.string.species_category_flower);
            case SUCCULENT:
                return getString(R.string.species_category_succulent);
            case TREE:
                return getString(R.string.species_category_tree);
            case SHRUB:
                return getString(R.string.species_category_shrub);
            case FERN:
                return getString(R.string.species_category_fern);
            case CACTUS:
                return getString(R.string.species_category_cactus);
            case GRASS:
                return getString(R.string.species_category_grass);
            case OTHER:
            default:
                return getString(R.string.species_category_other);
        }
    }

    private void setTextOrHide(@NonNull TextView view, @Nullable CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }

    private boolean bindStageDetail(@NonNull TextView view,
                                    @Nullable SpeciesTarget.StageTarget stage,
                                    @StringRes int stageNameRes) {
        String detail = formatStageDetail(stage, stageNameRes);
        if (TextUtils.isEmpty(detail)) {
            view.setVisibility(View.GONE);
            return false;
        }
        view.setText(detail);
        view.setVisibility(View.VISIBLE);
        return true;
    }

    @Nullable
    private String formatStageDetail(@Nullable SpeciesTarget.StageTarget stage,
                                     @StringRes int stageNameRes) {
        if (stage == null) {
            return null;
        }
        SpeciesTarget.FloatRange ppfdRange = stage.getPpfdRange();
        SpeciesTarget.FloatRange dliRange = stage.getDliRange();
        boolean hasPpfd = hasRange(ppfdRange);
        boolean hasDli = hasRange(dliRange);
        if (!hasPpfd && !hasDli) {
            return null;
        }
        String stageName = getString(stageNameRes);
        if (hasPpfd && hasDli) {
            SpeciesTarget.FloatRange nonNullPpfdRange = Objects.requireNonNull(ppfdRange);
            SpeciesTarget.FloatRange nonNullDliRange = Objects.requireNonNull(dliRange);
            return getString(R.string.format_stage_range,
                stageName,
                formatRangeValue(nonNullPpfdRange.getMin()),
                formatRangeValue(nonNullPpfdRange.getMax()),
                formatRangeValue(nonNullDliRange.getMin()),
                formatRangeValue(nonNullDliRange.getMax()));
        }
        if (hasPpfd) {
            SpeciesTarget.FloatRange nonNullPpfdRange = Objects.requireNonNull(ppfdRange);
            return getString(R.string.format_stage_range_ppfd_only,
                stageName,
                formatRangeValue(nonNullPpfdRange.getMin()),
                formatRangeValue(nonNullPpfdRange.getMax()));
        }
        SpeciesTarget.FloatRange nonNullDliRange = Objects.requireNonNull(dliRange);
        getString(R.string.format_stage_range_dli_only,
            stageName,
            formatRangeValue(nonNullDliRange.getMin()),
            formatRangeValue(nonNullDliRange.getMax()));
        return stageName;
    }

    private static boolean hasLightDetails(@NonNull SpeciesTarget target) {
        return hasStageLightInfo(target.getSeedlingStage())
            || hasStageLightInfo(target.getVegetativeStage())
            || hasStageLightInfo(target.getFlowerStage());
    }

    private static boolean hasStageLightInfo(@Nullable SpeciesTarget.StageTarget stage) {
        return stage != null && (hasRange(stage.getPpfdRange()) || hasRange(stage.getDliRange()));
    }

    private void bindItemView(@NonNull SpeciesSearchAdapter.ResultViewHolder holder, @NonNull SpeciesTarget target) {
        holder.bind(target, formatCategory(target.getCategory()));
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // no-op
        }

        @Override
        public void afterTextChanged(Editable s) {
            // no-op
        }
    }

    private static class SpeciesSearchAdapter extends ListAdapter<SpeciesTarget, SpeciesSearchAdapter.ResultViewHolder> {
        private static final DiffUtil.ItemCallback<SpeciesTarget> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SpeciesTarget>() {
                @Override
                public boolean areItemsTheSame(@NonNull SpeciesTarget oldItem, @NonNull SpeciesTarget newItem) {
                    return Objects.equals(oldItem.getSpeciesKey(), newItem.getSpeciesKey());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SpeciesTarget oldItem, @NonNull SpeciesTarget newItem) {
                    return Objects.equals(oldItem.getCommonName(), newItem.getCommonName())
                        && Objects.equals(oldItem.getScientificName(), newItem.getScientificName())
                        && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                        && Objects.equals(oldItem.getSeedlingStage(), newItem.getSeedlingStage())
                        && Objects.equals(oldItem.getVegetativeStage(), newItem.getVegetativeStage())
                        && Objects.equals(oldItem.getFlowerStage(), newItem.getFlowerStage())
                        && Objects.equals(oldItem.getWateringInfo(), newItem.getWateringInfo())
                        && Objects.equals(oldItem.getTemperatureRange(), newItem.getTemperatureRange())
                        && Objects.equals(oldItem.getHumidityRange(), newItem.getHumidityRange())
                        && Objects.equals(oldItem.getToxicToPets(), newItem.getToxicToPets());
                }
            };
        private final SpeciesSearchFragment fragment;
        private final OnSpeciesClickListener listener;

        SpeciesSearchAdapter(@NonNull SpeciesSearchFragment fragment, OnSpeciesClickListener listener) {
            super(DIFF_CALLBACK);
            this.fragment = Objects.requireNonNull(fragment);
            this.listener = Objects.requireNonNull(listener);
        }

        @NonNull
        @Override
        public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.item_species_search, parent, false);
            return new ResultViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
            SpeciesTarget target = getItem(position);
            fragment.bindItemView(holder, target);
        }

        class ResultViewHolder extends RecyclerView.ViewHolder {
            private final TextView commonNameView;
            private final TextView scientificNameView;
            private final TextView categoryView;
            private final TextView lightIconView;
            private final TextView waterIconView;
            private final TextView petIconView;

            ResultViewHolder(@NonNull View itemView) {
                super(itemView);
                commonNameView = itemView.findViewById(R.id.species_common_name);
                scientificNameView = itemView.findViewById(R.id.species_scientific_name);
                categoryView = itemView.findViewById(R.id.species_category);
                lightIconView = itemView.findViewById(R.id.species_icon_light);
                waterIconView = itemView.findViewById(R.id.species_icon_water);
                petIconView = itemView.findViewById(R.id.species_icon_pets);
            }

            void bind(@NonNull SpeciesTarget target, @NonNull String categoryLabel) {
                String commonName = target.getCommonName();
                if (TextUtils.isEmpty(commonName)) {
                    commonName = target.getSpeciesKey();
                }
                commonNameView.setText(commonName);

                String scientificName = target.getScientificName();
                scientificNameView.setText(!TextUtils.isEmpty(scientificName)
                    ? scientificName
                    : itemView.getContext().getString(R.string.species_search_unknown_scientific));

                categoryView.setText(categoryLabel);

                boolean hasLight = hasLightDetails(target);
                lightIconView.setVisibility(hasLight ? View.VISIBLE : View.GONE);

                SpeciesTarget.WateringInfo watering = target.getWateringInfo();
                boolean hasWatering = watering != null
                    && (!TextUtils.isEmpty(watering.getFrequency())
                    || !TextUtils.isEmpty(watering.getSoilType())
                    || !TextUtils.isEmpty(watering.getTolerance()));
                waterIconView.setVisibility(hasWatering ? View.VISIBLE : View.GONE);

                Boolean toxic = target.getToxicToPets();
                if (toxic != null) {
                    petIconView.setVisibility(View.VISIBLE);
                    petIconView.setContentDescription(itemView.getContext().getString(
                        toxic ? R.string.species_search_toxic_label : R.string.species_search_non_toxic_label));
                    int colorAttr = toxic
                        ? R.attr.colorError
                        : R.attr.colorSecondary;
                    int tintColor = MaterialColors.getColor(petIconView, colorAttr);
                    petIconView.setTextColor(tintColor);
                } else {
                    petIconView.setVisibility(View.GONE);
                    petIconView.setContentDescription(itemView.getContext().getString(R.string.species_search_unknown_toxicity_label));
                }

                itemView.setOnClickListener(v -> listener.onSpeciesClick(target));
            }
        }
    }

    private interface OnSpeciesClickListener {
        void onSpeciesClick(SpeciesTarget target);
    }

}
