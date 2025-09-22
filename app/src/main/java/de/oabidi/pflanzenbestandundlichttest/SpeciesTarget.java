package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

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

    public static class StageTarget {
        private Float ppfdMin;
        private Float ppfdMax;
        private Float dliMin;
        private Float dliMax;

        public StageTarget() {
        }

        @Ignore
        public StageTarget(@Nullable Float ppfdMin, @Nullable Float ppfdMax,
                           @Nullable Float dliMin, @Nullable Float dliMax) {
            this.ppfdMin = ppfdMin;
            this.ppfdMax = ppfdMax;
            this.dliMin = dliMin;
            this.dliMax = dliMax;
        }

        @Nullable
        public Float getPpfdMin() {
            return ppfdMin;
        }

        public void setPpfdMin(@Nullable Float ppfdMin) {
            this.ppfdMin = ppfdMin;
        }

        @Nullable
        public Float getPpfdMax() {
            return ppfdMax;
        }

        public void setPpfdMax(@Nullable Float ppfdMax) {
            this.ppfdMax = ppfdMax;
        }

        @Nullable
        public Float getDliMin() {
            return dliMin;
        }

        public void setDliMin(@Nullable Float dliMin) {
            this.dliMin = dliMin;
        }

        @Nullable
        public Float getDliMax() {
            return dliMax;
        }

        public void setDliMax(@Nullable Float dliMax) {
            this.dliMax = dliMax;
        }

        public boolean hasRange() {
            return ppfdMin != null || ppfdMax != null || dliMin != null || dliMax != null;
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
            return Objects.equals(ppfdMin, other.ppfdMin)
                && Objects.equals(ppfdMax, other.ppfdMax)
                && Objects.equals(dliMin, other.dliMin)
                && Objects.equals(dliMax, other.dliMax);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ppfdMin, ppfdMax, dliMin, dliMax);
        }
    }

    @PrimaryKey
    @NonNull
    private String speciesKey;

    @Embedded(prefix = "seedling_")
    private StageTarget seedlingStage;

    @Embedded(prefix = "vegetative_")
    private StageTarget vegetativeStage;

    @Embedded(prefix = "flower_")
    private StageTarget flowerStage;

    @Nullable
    private String tolerance;

    @Nullable
    private String source;

    /**
     * Convenience constructor to create fully initialised instances.
     */
    public SpeciesTarget(@NonNull String speciesKey,
                         @Nullable StageTarget seedlingStage,
                         @Nullable StageTarget vegetativeStage,
                         @Nullable StageTarget flowerStage,
                         @Nullable String tolerance,
                         @Nullable String source) {
        this.speciesKey = speciesKey;
        this.seedlingStage = seedlingStage != null ? seedlingStage : new StageTarget();
        this.vegetativeStage = vegetativeStage != null ? vegetativeStage : new StageTarget();
        this.flowerStage = flowerStage != null ? flowerStage : new StageTarget();
        this.tolerance = tolerance;
        this.source = source;
    }

    @Ignore
    public SpeciesTarget(@NonNull String speciesKey, float ppfdMin, float ppfdMax) {
        this(speciesKey,
            new StageTarget(ppfdMin, ppfdMax, null, null),
            new StageTarget(ppfdMin, ppfdMax, null, null),
            new StageTarget(ppfdMin, ppfdMax, null, null),
            null,
            null);
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
        return tolerance;
    }

    public void setTolerance(@Nullable String tolerance) {
        this.tolerance = tolerance;
    }

    @Nullable
    public String getSource() {
        return source;
    }

    public void setSource(@Nullable String source) {
        this.source = source;
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
}
