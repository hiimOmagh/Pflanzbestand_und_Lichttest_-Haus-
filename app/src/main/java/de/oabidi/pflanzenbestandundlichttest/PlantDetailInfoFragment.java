package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import de.oabidi.pflanzenbestandundlichttest.common.ui.InsetsUtils;

/**
 * Fragment displaying the static details and light meter UI for a plant.
 */
public class PlantDetailInfoFragment extends Fragment {
    private static final String ARG_NAME = "arg_name";
    private static final String ARG_DESCRIPTION = "arg_description";
    private static final String ARG_SPECIES = "arg_species";
    private static final String ARG_LOCATION = "arg_location";
    private static final String ARG_ACQUIRED = "arg_acquired";

    /** Callback interface implemented by {@link PlantDetailActivity}. */
    public interface Callbacks {
        void onDetailInfoViewsReady(@NonNull DetailViews views);

        void onDiaryButtonClicked();

        void onEnvironmentLogButtonClicked();
    }

    /** Convenience holder for the light meter views used by the activity. */
    public static class DetailViews {
        @Nullable
        public final TextView ambientValueView;
        @Nullable
        public final TextView ambientBandView;
        @Nullable
        public final TextView cameraValueView;
        @Nullable
        public final TextView cameraBandView;
        @Nullable
        public final View ambientColumnView;
        @Nullable
        public final View lightMeterSpacerView;

        @Nullable
        public final MaterialCardView careTipsCardView;
        @Nullable
        public final RecyclerView careRecommendationsListView;
        @Nullable
        public final View careRecommendationsLoadingView;
        @Nullable
        public final TextView careRecommendationsEmptyView;

        @Nullable
        public final MaterialCardView speciesMetadataCardView;
        @Nullable
        public final MaterialCardView lightSummaryCardView;
        @Nullable
        public final View wateringSectionView;
        @Nullable
        public final TextView wateringFrequencyView;
        @Nullable
        public final TextView wateringSoilView;
        @Nullable
        public final TextView wateringToleranceView;
        @Nullable
        public final View temperatureSectionView;
        @Nullable
        public final TextView temperatureRangeView;
        @Nullable
        public final View humiditySectionView;
        @Nullable
        public final TextView humidityRangeView;
        @Nullable
        public final View toxicitySectionView;
        @Nullable
        public final TextView toxicityTextView;
        @Nullable
        public final View careTipsSectionView;
        @Nullable
        public final TextView careTipsTextView;
        @Nullable
        public final TextView metadataUnavailableView;
        @Nullable
        public final TextView naturalDliValueView;
        @Nullable
        public final TextView naturalDliTimestampView;
        @Nullable
        public final TextView artificialDliValueView;
        @Nullable
        public final TextView artificialDliTimestampView;
        @Nullable
        public final ImageView wateringIconView;
        @Nullable
        public final ImageView temperatureIconView;
        @Nullable
        public final ImageView humidityIconView;
        @Nullable
        public final ImageView toxicityIconView;
        @Nullable
        public final ImageView careTipsIconView;

        DetailViews(@Nullable TextView ambientValueView,
                    @Nullable TextView ambientBandView,
                    @Nullable TextView cameraValueView,
                    @Nullable TextView cameraBandView,
                    @Nullable View ambientColumnView,
                    @Nullable View lightMeterSpacerView,
                    @Nullable MaterialCardView careTipsCardView,
                    @Nullable RecyclerView careRecommendationsListView,
                    @Nullable View careRecommendationsLoadingView,
                    @Nullable TextView careRecommendationsEmptyView,
                    @Nullable MaterialCardView speciesMetadataCardView,
                    @Nullable MaterialCardView lightSummaryCardView,
                    @Nullable View wateringSectionView,
                    @Nullable TextView wateringFrequencyView,
                    @Nullable TextView wateringSoilView,
                    @Nullable TextView wateringToleranceView,
                    @Nullable View temperatureSectionView,
                    @Nullable TextView temperatureRangeView,
                    @Nullable View humiditySectionView,
                    @Nullable TextView humidityRangeView,
                    @Nullable View toxicitySectionView,
                    @Nullable TextView toxicityTextView,
                    @Nullable View careTipsSectionView,
                    @Nullable TextView careTipsTextView,
                    @Nullable TextView metadataUnavailableView,
                    @Nullable TextView naturalDliValueView,
                    @Nullable TextView naturalDliTimestampView,
                    @Nullable TextView artificialDliValueView,
                    @Nullable TextView artificialDliTimestampView,
                    @Nullable ImageView wateringIconView,
                    @Nullable ImageView temperatureIconView,
                    @Nullable ImageView humidityIconView,
                    @Nullable ImageView toxicityIconView,
                    @Nullable ImageView careTipsIconView) {
            this.ambientValueView = ambientValueView;
            this.ambientBandView = ambientBandView;
            this.cameraValueView = cameraValueView;
            this.cameraBandView = cameraBandView;
            this.ambientColumnView = ambientColumnView;
            this.lightMeterSpacerView = lightMeterSpacerView;
            this.careTipsCardView = careTipsCardView;
            this.careRecommendationsListView = careRecommendationsListView;
            this.careRecommendationsLoadingView = careRecommendationsLoadingView;
            this.careRecommendationsEmptyView = careRecommendationsEmptyView;
            this.speciesMetadataCardView = speciesMetadataCardView;
            this.lightSummaryCardView = lightSummaryCardView;
            this.wateringSectionView = wateringSectionView;
            this.wateringFrequencyView = wateringFrequencyView;
            this.wateringSoilView = wateringSoilView;
            this.wateringToleranceView = wateringToleranceView;
            this.temperatureSectionView = temperatureSectionView;
            this.temperatureRangeView = temperatureRangeView;
            this.humiditySectionView = humiditySectionView;
            this.humidityRangeView = humidityRangeView;
            this.toxicitySectionView = toxicitySectionView;
            this.toxicityTextView = toxicityTextView;
            this.careTipsSectionView = careTipsSectionView;
            this.careTipsTextView = careTipsTextView;
            this.metadataUnavailableView = metadataUnavailableView;
            this.naturalDliValueView = naturalDliValueView;
            this.naturalDliTimestampView = naturalDliTimestampView;
            this.artificialDliValueView = artificialDliValueView;
            this.artificialDliTimestampView = artificialDliTimestampView;
            this.wateringIconView = wateringIconView;
            this.temperatureIconView = temperatureIconView;
            this.humidityIconView = humidityIconView;
            this.toxicityIconView = toxicityIconView;
            this.careTipsIconView = careTipsIconView;
        }
    }

    @Nullable
    private Callbacks callbacks;

    /** Creates a new instance configured with the provided details. */
    public static PlantDetailInfoFragment newInstance(@NonNull String name,
                                                      @NonNull String description,
                                                      @NonNull String species,
                                                      @NonNull String location,
                                                      @NonNull String acquiredAt) {
        PlantDetailInfoFragment fragment = new PlantDetailInfoFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putString(ARG_DESCRIPTION, description);
        args.putString(ARG_SPECIES, species);
        args.putString(ARG_LOCATION, location);
        args.putString(ARG_ACQUIRED, acquiredAt);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Callbacks) {
            callbacks = (Callbacks) context;
        } else {
            throw new IllegalStateException("Host activity must implement Callbacks");
        }
    }

    @Override
    public void onDetach() {
        callbacks = null;
        super.onDetach();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plant_detail_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.applySystemWindowInsetsPadding(view, false, false, false, true);

        TextView nameView = view.findViewById(R.id.detail_name);
        TextView descriptionView = view.findViewById(R.id.detail_description);
        TextView speciesView = view.findViewById(R.id.detail_species);
        TextView locationView = view.findViewById(R.id.detail_location_hint);
        TextView acquiredView = view.findViewById(R.id.detail_acquired_at);

        Bundle args = getArguments();
        if (args != null) {
            nameView.setText(args.getString(ARG_NAME, ""));
            descriptionView.setText(args.getString(ARG_DESCRIPTION, ""));
            speciesView.setText(args.getString(ARG_SPECIES, ""));
            locationView.setText(args.getString(ARG_LOCATION, ""));
            acquiredView.setText(args.getString(ARG_ACQUIRED, ""));
        }

        Button diaryButton = view.findViewById(R.id.detail_diary);
        if (diaryButton != null) {
            diaryButton.setOnClickListener(v -> {
                if (callbacks != null) {
                    callbacks.onDiaryButtonClicked();
                }
            });
        }

        Button environmentButton = view.findViewById(R.id.detail_environment_log);
        if (environmentButton != null) {
            environmentButton.setOnClickListener(v -> {
                if (callbacks != null) {
                    callbacks.onEnvironmentLogButtonClicked();
                }
            });
        }

        if (callbacks != null) {
            callbacks.onDetailInfoViewsReady(new DetailViews(
                view.findViewById(R.id.detail_ambient_value),
                view.findViewById(R.id.detail_ambient_band),
                view.findViewById(R.id.detail_camera_value),
                view.findViewById(R.id.detail_camera_band),
                view.findViewById(R.id.detail_ambient_column),
                view.findViewById(R.id.detail_light_meter_spacer),
                view.findViewById(R.id.detail_care_card),
                view.findViewById(R.id.detail_care_list),
                view.findViewById(R.id.detail_care_loading),
                view.findViewById(R.id.detail_care_empty),
                view.findViewById(R.id.detail_species_metadata_card),
                view.findViewById(R.id.detail_light_card),
                view.findViewById(R.id.detail_watering_section),
                view.findViewById(R.id.detail_watering_frequency),
                view.findViewById(R.id.detail_watering_soil),
                view.findViewById(R.id.detail_watering_tolerance),
                view.findViewById(R.id.detail_temperature_section),
                view.findViewById(R.id.detail_temperature_range),
                view.findViewById(R.id.detail_humidity_section),
                view.findViewById(R.id.detail_humidity_range),
                view.findViewById(R.id.detail_toxicity_section),
                view.findViewById(R.id.detail_toxicity_text),
                view.findViewById(R.id.detail_care_tips_section),
                view.findViewById(R.id.detail_care_tips_text),
                view.findViewById(R.id.detail_metadata_unavailable),
                view.findViewById(R.id.detail_light_natural_value),
                view.findViewById(R.id.detail_light_natural_timestamp),
                view.findViewById(R.id.detail_light_artificial_value),
                view.findViewById(R.id.detail_light_artificial_timestamp),
                view.findViewById(R.id.detail_watering_icon),
                view.findViewById(R.id.detail_temperature_icon),
                view.findViewById(R.id.detail_humidity_icon),
                view.findViewById(R.id.detail_toxicity_icon),
                view.findViewById(R.id.detail_care_tips_icon)
            ));
        }
    }
}
