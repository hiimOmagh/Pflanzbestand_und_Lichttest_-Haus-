package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Room entity defining the acceptable PPFD range for a plant species.
 */
@Entity
public class SpeciesTarget {
    public enum GrowthStage {
        SEEDLING,
        VEGETATIVE,
        FLOWER
    }

    public static class FloatRange {
        @Nullable
        private Float min;
        @Nullable
        private Float max;

        public FloatRange() {
        }

        @Ignore
        public FloatRange(@Nullable Float min, @Nullable Float max) {
            this.min = min;
            this.max = max;
        }

        @Nullable
        public Float getMin() {
            return min;
        }

        public void setMin(@Nullable Float min) {
            this.min = min;
        }

        @Nullable
        public Float getMax() {
            return max;
        }

        public void setMax(@Nullable Float max) {
            this.max = max;
        }

        public boolean hasValues() {
            return min != null || max != null;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof FloatRange)) {
                return false;
            }
            FloatRange other = (FloatRange) obj;
            return Objects.equals(min, other.min) && Objects.equals(max, other.max);
        }

        @Override
        public int hashCode() {
            return Objects.hash(min, max);
        }
    }

    public static class StageTarget {
        @Embedded(prefix = "ppfd_")
        private FloatRange ppfdRange;

        @Embedded(prefix = "dli_")
        private FloatRange dliRange;

        public StageTarget() {
            this.ppfdRange = new FloatRange();
            this.dliRange = new FloatRange();
        }

        @Ignore
        public StageTarget(@Nullable Float ppfdMin, @Nullable Float ppfdMax,
                           @Nullable Float dliMin, @Nullable Float dliMax) {
            this.ppfdRange = new FloatRange(ppfdMin, ppfdMax);
            this.dliRange = new FloatRange(dliMin, dliMax);
        }

        @Nullable
        public Float getPpfdMin() {
            return ppfdRange != null ? ppfdRange.getMin() : null;
        }

        public void setPpfdMin(@Nullable Float ppfdMin) {
            ensurePpfdRange().setMin(ppfdMin);
        }

        @Nullable
        public Float getPpfdMax() {
            return ppfdRange != null ? ppfdRange.getMax() : null;
        }

        public void setPpfdMax(@Nullable Float ppfdMax) {
            ensurePpfdRange().setMax(ppfdMax);
        }

        @Nullable
        public Float getDliMin() {
            return dliRange != null ? dliRange.getMin() : null;
        }

        public void setDliMin(@Nullable Float dliMin) {
            ensureDliRange().setMin(dliMin);
        }

        @Nullable
        public Float getDliMax() {
            return dliRange != null ? dliRange.getMax() : null;
        }

        public void setDliMax(@Nullable Float dliMax) {
            ensureDliRange().setMax(dliMax);
        }

        @Nullable
        public FloatRange getPpfdRange() {
            return ppfdRange;
        }

        public void setPpfdRange(@Nullable FloatRange ppfdRange) {
            this.ppfdRange = ppfdRange != null ? ppfdRange : new FloatRange();
        }

        @Nullable
        public FloatRange getDliRange() {
            return dliRange;
        }

        public void setDliRange(@Nullable FloatRange dliRange) {
            this.dliRange = dliRange != null ? dliRange : new FloatRange();
        }

        public boolean hasRange() {
            return (ppfdRange != null && ppfdRange.hasValues())
                || (dliRange != null && dliRange.hasValues());
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StageTarget)) {
                return false;
            }
            StageTarget other = (StageTarget) obj;
            return Objects.equals(ppfdRange, other.ppfdRange)
                && Objects.equals(dliRange, other.dliRange);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ppfdRange, dliRange);
        }

        private FloatRange ensurePpfdRange() {
            if (ppfdRange == null) {
                ppfdRange = new FloatRange();
            }
            return ppfdRange;
        }

        private FloatRange ensureDliRange() {
            if (dliRange == null) {
                dliRange = new FloatRange();
            }
            return dliRange;
        }
    }

    public static class WateringInfo {
        @Nullable
        private String schedule;

        @Nullable
        private String soil;

        @Nullable
        private String tolerance;

        public WateringInfo() {
        }

        @Ignore
        public WateringInfo(@Nullable String schedule,
                            @Nullable String soil,
                            @Nullable String tolerance) {
            this.schedule = schedule;
            this.soil = soil;
            this.tolerance = tolerance;
        }

        @Nullable
        public String getSchedule() {
            return schedule;
        }

        public void setSchedule(@Nullable String schedule) {
            this.schedule = schedule;
        }

        @Nullable
        public String getSoil() {
            return soil;
        }

        public void setSoil(@Nullable String soil) {
            this.soil = soil;
        }

        @Nullable
        public String getTolerance() {
            return tolerance;
        }

        public void setTolerance(@Nullable String tolerance) {
            this.tolerance = tolerance;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof WateringInfo)) {
                return false;
            }
            WateringInfo other = (WateringInfo) obj;
            return Objects.equals(schedule, other.schedule)
                && Objects.equals(soil, other.soil)
                && Objects.equals(tolerance, other.tolerance);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schedule, soil, tolerance);
        }
    }

    public enum Category {
        HOUSEPLANT,
        HERB,
        VEGETABLE,
        FRUIT,
        FLOWER,
        SUCCULENT,
        TREE,
        SHRUB,
        FERN,
        CACTUS,
        GRASS,
        OTHER
    }

    @PrimaryKey
    @NonNull
    private String speciesKey;

    @Nullable
    private String commonName;

    @Nullable
    private String scientificName;

    @NonNull
    private Category category = Category.OTHER;

    @Embedded(prefix = "seedling_")
    private StageTarget seedlingStage;

    @Embedded(prefix = "vegetative_")
    private StageTarget vegetativeStage;

    @Embedded(prefix = "flower_")
    private StageTarget flowerStage;

    @Embedded(prefix = "watering_")
    private WateringInfo wateringInfo;

    @Embedded(prefix = "temperature_")
    private FloatRange temperatureRange;

    @Embedded(prefix = "humidity_")
    private FloatRange humidityRange;

    @Nullable
    private String growthHabit;

    @Nullable
    private Boolean toxicToPets;

    @Nullable
    private List<String> careTips;

    @Nullable
    private List<String> sources;

    /**
     * Convenience constructor to create fully initialised instances.
     */
    public SpeciesTarget(@NonNull String speciesKey,
                         @Nullable String commonName,
                         @Nullable String scientificName,
                         @NonNull Category category,
                         @Nullable StageTarget seedlingStage,
                         @Nullable StageTarget vegetativeStage,
                         @Nullable StageTarget flowerStage,
                         @Nullable WateringInfo wateringInfo,
                         @Nullable FloatRange temperatureRange,
                         @Nullable FloatRange humidityRange,
                         @Nullable String growthHabit,
                         @Nullable Boolean toxicToPets,
                         @Nullable List<String> careTips,
                         @Nullable List<String> sources) {
        this.speciesKey = speciesKey;
        this.commonName = commonName;
        this.scientificName = scientificName;
        this.category = category != null ? category : Category.OTHER;
        this.seedlingStage = seedlingStage != null ? seedlingStage : new StageTarget();
        this.vegetativeStage = vegetativeStage != null ? vegetativeStage : new StageTarget();
        this.flowerStage = flowerStage != null ? flowerStage : new StageTarget();
        this.wateringInfo = wateringInfo != null ? wateringInfo : new WateringInfo();
        this.temperatureRange = temperatureRange != null ? temperatureRange : new FloatRange();
        this.humidityRange = humidityRange != null ? humidityRange : new FloatRange();
        this.growthHabit = growthHabit;
        this.toxicToPets = toxicToPets;
        this.careTips = sanitizeList(careTips);
        this.sources = sanitizeList(sources);
    }

    @Ignore
    public SpeciesTarget(@NonNull String speciesKey, float ppfdMin, float ppfdMax) {
        this(speciesKey,
            null,
            null,
            Category.OTHER,
            new StageTarget(ppfdMin, ppfdMax, null, null),
            new StageTarget(ppfdMin, ppfdMax, null, null),
            new StageTarget(ppfdMin, ppfdMax, null, null),
            null,
            null,
            null,
            null,
            null,
            null);
    }

    @Ignore
    public SpeciesTarget(@NonNull String speciesKey,
                         @Nullable StageTarget seedlingStage,
                         @Nullable StageTarget vegetativeStage,
                         @Nullable StageTarget flowerStage,
                         @Nullable String tolerance,
                         @Nullable String source) {
        this(speciesKey,
            null,
            null,
            Category.OTHER,
            seedlingStage,
            vegetativeStage,
            flowerStage,
            tolerance != null || source != null ? new WateringInfo(null, null, tolerance) : null,
            null,
            null,
            null,
            null,
            null,
            source != null ? Collections.singletonList(source) : null);
    }

    /**
     * @return identifier for the plant species.
     */
    @NonNull
    public String getSpeciesKey() {
        return speciesKey;
    }

    /**
     * Updates the species identifier.
     */
    public void setSpeciesKey(@NonNull String speciesKey) {
        this.speciesKey = speciesKey;
    }

    @Nullable
    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(@Nullable String commonName) {
        this.commonName = commonName;
    }

    @Nullable
    public String getScientificName() {
        return scientificName;
    }

    public void setScientificName(@Nullable String scientificName) {
        this.scientificName = scientificName;
    }

    @NonNull
    public Category getCategory() {
        return category;
    }

    public void setCategory(@NonNull Category category) {
        this.category = category != null ? category : Category.OTHER;
    }

    @Nullable
    public StageTarget getSeedlingStage() {
        return seedlingStage;
    }

    public void setSeedlingStage(@Nullable StageTarget seedlingStage) {
        this.seedlingStage = seedlingStage;
    }

    @Nullable
    public StageTarget getVegetativeStage() {
        return vegetativeStage;
    }

    public void setVegetativeStage(@Nullable StageTarget vegetativeStage) {
        this.vegetativeStage = vegetativeStage;
    }

    @Nullable
    public StageTarget getFlowerStage() {
        return flowerStage;
    }

    public void setFlowerStage(@Nullable StageTarget flowerStage) {
        this.flowerStage = flowerStage;
    }

    @Nullable
    public String getTolerance() {
        return wateringInfo != null ? wateringInfo.getTolerance() : null;
    }

    public void setTolerance(@Nullable String tolerance) {
        ensureWateringInfo().setTolerance(tolerance);
    }

    @Nullable
    public String getSource() {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        return sources.get(0);
    }

    public void setSource(@Nullable String source) {
        if (source == null || source.isEmpty()) {
            this.sources = Collections.emptyList();
        } else {
            this.sources = new ArrayList<>();
            this.sources.add(source);
        }
    }

    @Nullable
    public WateringInfo getWateringInfo() {
        return wateringInfo;
    }

    public void setWateringInfo(@Nullable WateringInfo wateringInfo) {
        this.wateringInfo = wateringInfo != null ? wateringInfo : new WateringInfo();
    }

    @Nullable
    public FloatRange getTemperatureRange() {
        return temperatureRange;
    }

    public void setTemperatureRange(@Nullable FloatRange temperatureRange) {
        this.temperatureRange = temperatureRange != null ? temperatureRange : new FloatRange();
    }

    @Nullable
    public FloatRange getHumidityRange() {
        return humidityRange;
    }

    public void setHumidityRange(@Nullable FloatRange humidityRange) {
        this.humidityRange = humidityRange != null ? humidityRange : new FloatRange();
    }

    @Nullable
    public String getGrowthHabit() {
        return growthHabit;
    }

    public void setGrowthHabit(@Nullable String growthHabit) {
        this.growthHabit = growthHabit;
    }

    @Nullable
    public Boolean getToxicToPets() {
        return toxicToPets;
    }

    public void setToxicToPets(@Nullable Boolean toxicToPets) {
        this.toxicToPets = toxicToPets;
    }

    @Nullable
    public List<String> getCareTips() {
        return careTips;
    }

    public void setCareTips(@Nullable List<String> careTips) {
        this.careTips = sanitizeList(careTips);
    }

    @Nullable
    public List<String> getSources() {
        return sources;
    }

    public void setSources(@Nullable List<String> sources) {
        this.sources = sanitizeList(sources);
    }

    @Nullable
    public StageTarget getStage(GrowthStage stage) {
        if (stage == null) {
            return null;
        }
        switch (stage) {
            case SEEDLING:
                return seedlingStage;
            case VEGETATIVE:
                return vegetativeStage;
            case FLOWER:
                return flowerStage;
            default:
                return null;
        }
    }

    public boolean hasStage(GrowthStage stage) {
        StageTarget target = getStage(stage);
        return target != null && target.hasRange();
    }

    public GrowthStage getDefaultStage() {
        if (hasStage(GrowthStage.VEGETATIVE)) {
            return GrowthStage.VEGETATIVE;
        }
        if (hasStage(GrowthStage.SEEDLING)) {
            return GrowthStage.SEEDLING;
        }
        if (hasStage(GrowthStage.FLOWER)) {
            return GrowthStage.FLOWER;
        }
        return GrowthStage.VEGETATIVE;
    }

    @Nullable
    public StageTarget getStageOrFallback(GrowthStage stage) {
        StageTarget preferred = getStage(stage);
        if (preferred != null && preferred.hasRange()) {
            return preferred;
        }
        GrowthStage defaultStage = getDefaultStage();
        return getStage(defaultStage);
    }

    /**
     * Legacy accessors returning the vegetative PPFD range if available.
     */
    public float getPpfdMin() {
        StageTarget target = getStageOrFallback(GrowthStage.VEGETATIVE);
        return target != null && target.getPpfdMin() != null ? target.getPpfdMin() : 0f;
    }

    public float getPpfdMax() {
        StageTarget target = getStageOrFallback(GrowthStage.VEGETATIVE);
        return target != null && target.getPpfdMax() != null ? target.getPpfdMax() : 0f;
    }

    private WateringInfo ensureWateringInfo() {
        if (wateringInfo == null) {
            wateringInfo = new WateringInfo();
        }
        return wateringInfo;
    }

    private static List<String> sanitizeList(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                copy.add(value);
            }
        }
        return copy.isEmpty() ? Collections.emptyList() : copy;
    }
}
