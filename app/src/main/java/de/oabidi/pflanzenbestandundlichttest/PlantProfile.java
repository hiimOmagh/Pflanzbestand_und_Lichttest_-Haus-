package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of a {@link SpeciesTarget} with all nested structures fully populated.
 */
public class PlantProfile extends SpeciesTarget {
    private PlantProfile(@NonNull String speciesKey,
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
        super(speciesKey,
            commonName,
            scientificName,
            category,
            seedlingStage,
            vegetativeStage,
            flowerStage,
            wateringInfo,
            temperatureRange,
            humidityRange,
            growthHabit,
            toxicToPets,
            careTips,
            sources);
    }

    /**
     * Create a {@link PlantProfile} from a {@link SpeciesTarget} entity, ensuring defensive copies of
     * nested value objects and collection fields.
     */
    @Nullable
    public static PlantProfile fromTarget(@Nullable SpeciesTarget target) {
        if (target == null) {
            return null;
        }
        return new PlantProfile(target.getSpeciesKey(),
            target.getCommonName(),
            target.getScientificName(),
            target.getCategory(),
            copyStage(target.getSeedlingStage()),
            copyStage(target.getVegetativeStage()),
            copyStage(target.getFlowerStage()),
            copyWatering(target.getWateringInfo()),
            copyRange(target.getTemperatureRange()),
            copyRange(target.getHumidityRange()),
            target.getGrowthHabit(),
            target.getToxicToPets(),
            copyList(target.getCareTips()),
            copyList(target.getSources()));
    }

    private static StageTarget copyStage(@Nullable StageTarget stage) {
        if (stage == null) {
            return new StageTarget();
        }
        if (stage.getPpfdMin() == null && stage.getPpfdMax() == null
            && stage.getDliMin() == null && stage.getDliMax() == null) {
            return new StageTarget();
        }
        return new StageTarget(stage.getPpfdMin(), stage.getPpfdMax(), stage.getDliMin(), stage.getDliMax());
    }

    private static FloatRange copyRange(@Nullable FloatRange range) {
        if (range == null) {
            return new FloatRange();
        }
        return new FloatRange(range.getMin(), range.getMax());
    }

    private static WateringInfo copyWatering(@Nullable WateringInfo info) {
        if (info == null) {
            return new WateringInfo();
        }
        return new WateringInfo(info.getFrequency(), info.getSoilType(), info.getTolerance());
    }

    private static List<String> copyList(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                copy.add(value);
            }
        }
        return copy;
    }
}
