package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable view model describing species metadata for presentation in the detail screen.
 */
public class PlantMetadataViewModel {
    /** Represents optional watering guidance. */
    public static class WateringInfo {
        @Nullable
        private final String frequency;
        @Nullable
        private final String soilType;
        @Nullable
        private final String tolerance;

        public WateringInfo(@Nullable String frequency,
                            @Nullable String soilType,
                            @Nullable String tolerance) {
            this.frequency = emptyToNull(frequency);
            this.soilType = emptyToNull(soilType);
            this.tolerance = emptyToNull(tolerance);
        }

        @Nullable
        public String getFrequency() {
            return frequency;
        }

        @Nullable
        public String getSoilType() {
            return soilType;
        }

        @Nullable
        public String getTolerance() {
            return tolerance;
        }

        private static String emptyToNull(@Nullable String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    /** Represents a numeric range for temperature or humidity. */
    public static class RangeInfo {
        @Nullable
        private final Float min;
        @Nullable
        private final Float max;

        public RangeInfo(@Nullable Float min, @Nullable Float max) {
            this.min = min;
            this.max = max;
        }

        @Nullable
        public Float getMin() {
            return min;
        }

        @Nullable
        public Float getMax() {
            return max;
        }

        public boolean hasValues() {
            return min != null || max != null;
        }
    }

    @Nullable
    private final WateringInfo wateringInfo;
    @Nullable
    private final RangeInfo temperatureRange;
    @Nullable
    private final RangeInfo humidityRange;
    @Nullable
    private final Boolean toxicToPets;
    private final List<String> careTips;

    public PlantMetadataViewModel(@Nullable WateringInfo wateringInfo,
                                  @Nullable RangeInfo temperatureRange,
                                  @Nullable RangeInfo humidityRange,
                                  @Nullable Boolean toxicToPets,
                                  @Nullable List<String> careTips) {
        this.wateringInfo = wateringInfo;
        this.temperatureRange = temperatureRange;
        this.humidityRange = humidityRange;
        this.toxicToPets = toxicToPets;
        if (careTips == null || careTips.isEmpty()) {
            this.careTips = Collections.emptyList();
        } else {
            this.careTips = Collections.unmodifiableList(new ArrayList<>(careTips));
        }
    }

    @Nullable
    public WateringInfo getWateringInfo() {
        return wateringInfo;
    }

    @Nullable
    public RangeInfo getTemperatureRange() {
        return temperatureRange;
    }

    @Nullable
    public RangeInfo getHumidityRange() {
        return humidityRange;
    }

    @Nullable
    public Boolean getToxicToPets() {
        return toxicToPets;
    }

    public List<String> getCareTips() {
        return careTips;
    }
}
