package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import android.content.res.ColorStateList;
import android.widget.ImageView;

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
    private TextInputEditText searchInput;
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

        searchInput = view.findViewById(R.id.input_species_search);
        resultsView = view.findViewById(R.id.species_search_results);
        emptyStateView = view.findViewById(R.id.species_search_empty_state);
        progressBar = view.findViewById(R.id.species_search_progress);

        if (resultsView != null) {
            Context context = requireContext();
            resultsView.setLayoutManager(new LinearLayoutManager(context));
            resultsView.setClipToPadding(false);
            InsetsUtils.applySystemWindowInsetsPadding(resultsView, false, false, false, true);
            adapter = new SpeciesSearchAdapter(this::showDetailDialog);
            resultsView.setAdapter(adapter);
        }

        if (savedInstanceState != null) {
            currentQuery = savedInstanceState.getString(STATE_QUERY, "");
        }

        if (searchInput != null && !currentQuery.isEmpty()) {
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
        String message = buildDetailMessage(target);
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    @NonNull
    private String buildDetailMessage(@NonNull SpeciesTarget target) {
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(target.getScientificName())) {
            builder.append(getString(R.string.species_search_scientific_label, target.getScientificName()));
            builder.append('\n');
        }
        builder.append(getString(R.string.species_search_category_label, formatCategory(target.getCategory())));

        if (!TextUtils.isEmpty(target.getGrowthHabit())) {
            builder.append('\n');
            builder.append(getString(R.string.species_search_growth_habit, target.getGrowthHabit()));
        }

        SpeciesTarget.WateringInfo wateringInfo = target.getWateringInfo();
        if (hasWateringInfo(wateringInfo)) {
            builder.append('\n');
            if (!TextUtils.isEmpty(wateringInfo.getFrequency())) {
                builder.append('\n');
                builder.append(getString(R.string.species_search_watering_label, wateringInfo.getFrequency()));
            }
            if (!TextUtils.isEmpty(wateringInfo.getSoilType())) {
                builder.append('\n');
                builder.append(getString(R.string.species_search_soil_label, wateringInfo.getSoilType()));
            }
        }

        SpeciesTarget.FloatRange temperatureRange = target.getTemperatureRange();
        if (hasRange(temperatureRange)) {
            builder.append('\n');
            builder.append('\n');
            builder.append(getString(R.string.species_search_temperature_label,
                formatRangeValue(temperatureRange.getMin()),
                formatRangeValue(temperatureRange.getMax())));
        }

        SpeciesTarget.FloatRange humidityRange = target.getHumidityRange();
        if (hasRange(humidityRange)) {
            builder.append('\n');
            builder.append(getString(R.string.species_search_humidity_label,
                formatRangeValue(humidityRange.getMin()),
                formatRangeValue(humidityRange.getMax())));
        }

        Boolean toxic = target.getToxicToPets();
        if (toxic != null) {
            builder.append('\n');
            builder.append('\n');
            builder.append(getString(toxic ? R.string.species_search_toxic_label : R.string.species_search_non_toxic_label));
        } else if (builder.length() > 0) {
            builder.append('\n');
            builder.append('\n');
            builder.append(getString(R.string.species_search_unknown_toxicity_label));
        }

        List<String> careTips = target.getCareTips();
        if (careTips != null && !careTips.isEmpty()) {
            String tips = formatBulletedList(careTips);
            if (!tips.isEmpty()) {
                builder.append('\n');
                builder.append('\n');
                builder.append(getString(R.string.species_search_care_tips, tips));
            }
        }

        List<String> sources = target.getSources();
        if (sources != null && !sources.isEmpty()) {
            String formattedSources = formatBulletedList(sources);
            if (!formattedSources.isEmpty()) {
                builder.append('\n');
                builder.append('\n');
                builder.append(getString(R.string.species_search_sources, formattedSources));
            }
        }

        if (builder.length() == 0) {
            builder.append(getString(R.string.species_search_detail_title_fallback));
        }
        return builder.toString();
    }

    private boolean hasWateringInfo(@Nullable SpeciesTarget.WateringInfo info) {
        return info != null && (!TextUtils.isEmpty(info.getFrequency()) || !TextUtils.isEmpty(info.getSoilType()));
    }

    private boolean hasRange(@Nullable SpeciesTarget.FloatRange range) {
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
    private String formatBulletedList(@NonNull List<String> values) {
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

    private int resolveCategoryIcon(@Nullable SpeciesTarget.Category category) {
        if (category == null) {
            return R.drawable.ic_book;
        }
        switch (category) {
            case HOUSEPLANT:
            case TREE:
            case SHRUB:
                return R.drawable.ic_home;
            case HERB:
            case VEGETABLE:
                return R.drawable.ic_care_trending_up;
            case FRUIT:
                return R.drawable.ic_insights;
            case FLOWER:
                return R.drawable.ic_light;
            case SUCCULENT:
            case CACTUS:
                return R.drawable.ic_care_humidity;
            case FERN:
            case GRASS:
                return R.drawable.ic_care_water_drop;
            case OTHER:
            default:
                return R.drawable.ic_book;
        }
    }

    private void bindItemView(@NonNull SpeciesSearchAdapter.ResultViewHolder holder, @NonNull SpeciesTarget target) {
        holder.bind(target,
            formatCategory(target.getCategory()),
            resolveCategoryIcon(target.getCategory()));
    }

    private abstract class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // no-op
        }

        @Override
        public void afterTextChanged(Editable s) {
            // no-op
        }
    }

    private class SpeciesSearchAdapter extends ListAdapter<SpeciesTarget, SpeciesSearchAdapter.ResultViewHolder> {
        private final DiffUtil.ItemCallback<SpeciesTarget> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SpeciesTarget>() {
                @Override
                public boolean areItemsTheSame(@NonNull SpeciesTarget oldItem, @NonNull SpeciesTarget newItem) {
                    return Objects.equals(oldItem.getSpeciesKey(), newItem.getSpeciesKey());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SpeciesTarget oldItem, @NonNull SpeciesTarget newItem) {
                    return Objects.equals(oldItem.getCommonName(), newItem.getCommonName())
                        && Objects.equals(oldItem.getScientificName(), newItem.getScientificName())
                        && oldItem.getCategory() == newItem.getCategory()
                        && Objects.equals(oldItem.getWateringInfo(), newItem.getWateringInfo())
                        && Objects.equals(oldItem.getTemperatureRange(), newItem.getTemperatureRange())
                        && Objects.equals(oldItem.getHumidityRange(), newItem.getHumidityRange())
                        && Objects.equals(oldItem.getToxicToPets(), newItem.getToxicToPets());
                }
            };

        private final OnSpeciesClickListener listener;

        SpeciesSearchAdapter(OnSpeciesClickListener listener) {
            super(DIFF_CALLBACK);
            this.listener = Objects.requireNonNull(listener);
        }

        @NonNull
        @Override
        public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.item_species_search_result, parent, false);
            return new ResultViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
            SpeciesTarget target = getItem(position);
            bindItemView(holder, target);
        }

        class ResultViewHolder extends RecyclerView.ViewHolder {
            private final TextView commonNameView;
            private final TextView scientificNameView;
            private final Chip categoryChip;
            private final ImageView categoryIconView;
            private final ImageView wateringIcon;
            private final ImageView temperatureIcon;
            private final ImageView humidityIcon;
            private final ImageView toxicityIcon;

            ResultViewHolder(@NonNull View itemView) {
                super(itemView);
                commonNameView = itemView.findViewById(R.id.species_common_name);
                scientificNameView = itemView.findViewById(R.id.species_scientific_name);
                categoryChip = itemView.findViewById(R.id.species_category_chip);
                categoryIconView = itemView.findViewById(R.id.species_category_icon);
                wateringIcon = itemView.findViewById(R.id.species_icon_watering);
                temperatureIcon = itemView.findViewById(R.id.species_icon_temperature);
                humidityIcon = itemView.findViewById(R.id.species_icon_humidity);
                toxicityIcon = itemView.findViewById(R.id.species_icon_toxicity);
            }

            void bind(@NonNull SpeciesTarget target, @NonNull String categoryLabel, int categoryIconRes) {
                String commonName = target.getCommonName();
                if (TextUtils.isEmpty(commonName)) {
                    commonName = target.getSpeciesKey();
                }
                commonNameView.setText(commonName);

                String scientificName = target.getScientificName();
                scientificNameView.setText(!TextUtils.isEmpty(scientificName)
                    ? scientificName
                    : itemView.getContext().getString(R.string.species_search_unknown_scientific));

                categoryChip.setText(categoryLabel);
                categoryIconView.setImageResource(categoryIconRes);

                SpeciesTarget.WateringInfo watering = target.getWateringInfo();
                boolean hasWatering = watering != null
                    && (!TextUtils.isEmpty(watering.getFrequency()) || !TextUtils.isEmpty(watering.getSoilType()));
                wateringIcon.setVisibility(hasWatering ? View.VISIBLE : View.GONE);

                SpeciesTarget.FloatRange temperatureRange = target.getTemperatureRange();
                boolean hasTemperature = hasRange(temperatureRange);
                temperatureIcon.setVisibility(hasTemperature ? View.VISIBLE : View.GONE);

                SpeciesTarget.FloatRange humidityRange = target.getHumidityRange();
                boolean hasHumidity = hasRange(humidityRange);
                humidityIcon.setVisibility(hasHumidity ? View.VISIBLE : View.GONE);

                Boolean toxic = target.getToxicToPets();
                if (toxic != null) {
                    toxicityIcon.setVisibility(View.VISIBLE);
                    int colorAttr = toxic
                        ? com.google.android.material.R.attr.colorError
                        : com.google.android.material.R.attr.colorSecondary;
                    int tintColor = MaterialColors.getColor(itemView, colorAttr);
                    toxicityIcon.setImageTintList(ColorStateList.valueOf(tintColor));
                    toxicityIcon.setContentDescription(itemView.getContext().getString(
                        toxic ? R.string.species_search_toxic_label : R.string.species_search_non_toxic_label));
                } else {
                    toxicityIcon.setVisibility(View.GONE);
                }

                itemView.setOnClickListener(v -> listener.onSpeciesClick(target));
            }
        }
    }

    private interface OnSpeciesClickListener {
        void onSpeciesClick(SpeciesTarget target);
    }
}
