package de.oabidi.pflanzenbestandundlichttest.feature.reminders;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;

/**
 * Calculates smart reminder intervals based on species metadata and recent environment readings.
 */
public class SmartReminderEngine {
    public static final int HISTORY_LIMIT = 8;
    public static final String ALGORITHM_VERSION = "1";

    private static final int DEFAULT_BASELINE_DAYS = 7;
    private static final int MIN_INTERVAL_DAYS = 1;
    private static final int MAX_INTERVAL_DAYS = 60;
    private static final float DRY_THRESHOLD = 35f;
    private static final float WET_THRESHOLD = 75f;
    private static final float ADJUSTMENT_RATIO = 0.35f;
    private static final int MAX_ADJUSTMENT_DAYS = 14;
    private static final int SOIL_SAMPLE_LIMIT = 4;
    private static final Pattern FREQUENCY_PATTERN = Pattern.compile(
        "(?:every|per)?\\s*(\\d+)(?:\\s*[-to]+\\s*(\\d+))?\\s*(day|days|week|weeks|month|months)",
        Pattern.CASE_INSENSITIVE
    );

    public enum BaselineSource {
        SPECIES_FREQUENCY,
        SPECIES_TOLERANCE,
        CATEGORY,
        DEFAULT
    }

    public enum EnvironmentSignal {
        DRY,
        WET,
        BALANCED,
        NO_DATA
    }

    /**
     * Result of the smart reminder calculation.
     */
    public static final class Suggestion {
        private final int baselineDays;
        private final int suggestedDays;
        private final int adjustmentDays;
        @NonNull
        private final BaselineSource baselineSource;
        @NonNull
        private final EnvironmentSignal environmentSignal;
        @Nullable
        private final Float averageSoilMoisture;
        @Nullable
        private final Float latestSoilMoisture;

        Suggestion(int baselineDays,
                   int suggestedDays,
                   int adjustmentDays,
                   @NonNull BaselineSource baselineSource,
                   @NonNull EnvironmentSignal environmentSignal,
                   @Nullable Float averageSoilMoisture,
                   @Nullable Float latestSoilMoisture) {
            this.baselineDays = baselineDays;
            this.suggestedDays = suggestedDays;
            this.adjustmentDays = adjustmentDays;
            this.baselineSource = baselineSource;
            this.environmentSignal = environmentSignal;
            this.averageSoilMoisture = averageSoilMoisture;
            this.latestSoilMoisture = latestSoilMoisture;
        }

        public int getBaselineDays() {
            return baselineDays;
        }

        public int getSuggestedDays() {
            return suggestedDays;
        }

        public int getAdjustmentDays() {
            return adjustmentDays;
        }

        @NonNull
        public BaselineSource getBaselineSource() {
            return baselineSource;
        }

        @NonNull
        public EnvironmentSignal getEnvironmentSignal() {
            return environmentSignal;
        }

        @Nullable
        public Float getAverageSoilMoisture() {
            return averageSoilMoisture;
        }

        @Nullable
        public Float getLatestSoilMoisture() {
            return latestSoilMoisture;
        }
    }

    /**
     * Generates a reminder suggestion for the provided plant profile and environment readings.
     */
    @NonNull
    public Suggestion suggest(@Nullable PlantProfile profile, @Nullable List<EnvironmentEntry> entries) {
        BaselineSource baselineSource = BaselineSource.DEFAULT;
        int baselineDays = determineBaselineDays(profile);
        if (profile != null) {
            SpeciesTarget.WateringInfo info = profile.getWateringInfo();
            String frequency = info != null ? trimToNull(info.getFrequency()) : null;
            int frequencyDays = parseFrequencyDays(frequency);
            if (frequencyDays > 0) {
                baselineDays = frequencyDays;
                baselineSource = BaselineSource.SPECIES_FREQUENCY;
            } else {
                String tolerance = info != null ? trimToNull(info.getTolerance()) : null;
                int toleranceDays = toleranceToDays(tolerance);
                if (toleranceDays > 0) {
                    baselineDays = toleranceDays;
                    baselineSource = BaselineSource.SPECIES_TOLERANCE;
                } else {
                    int categoryDays = categoryToDays(profile);
                    if (categoryDays > 0) {
                        baselineDays = categoryDays;
                        baselineSource = BaselineSource.CATEGORY;
                    }
                }
            }
        } else {
            baselineDays = DEFAULT_BASELINE_DAYS;
        }
        baselineDays = clamp(baselineDays, MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS);

        List<Float> soilSamples = collectSoilSamples(entries);
        Float averageSoil = soilSamples.isEmpty() ? null : average(soilSamples);
        Float latestSoil = soilSamples.isEmpty() ? null : soilSamples.get(0);

        EnvironmentSignal environmentSignal = determineEnvironmentSignal(averageSoil);
        int adjustment = computeAdjustment(baselineDays, environmentSignal, averageSoil);
        int suggested = clamp(baselineDays + adjustment, MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS);
        int finalAdjustment = suggested - baselineDays;

        if (finalAdjustment == 0 && environmentSignal != EnvironmentSignal.NO_DATA) {
            environmentSignal = EnvironmentSignal.BALANCED;
        }

        return new Suggestion(baselineDays, suggested, finalAdjustment, baselineSource,
            environmentSignal, averageSoil, latestSoil);
    }

    private int determineBaselineDays(@Nullable PlantProfile profile) {
        if (profile == null) {
            return DEFAULT_BASELINE_DAYS;
        }
        SpeciesTarget.WateringInfo info = profile.getWateringInfo();
        String frequency = info != null ? trimToNull(info.getFrequency()) : null;
        int frequencyDays = parseFrequencyDays(frequency);
        if (frequencyDays > 0) {
            return frequencyDays;
        }
        String tolerance = info != null ? trimToNull(info.getTolerance()) : null;
        int toleranceDays = toleranceToDays(tolerance);
        if (toleranceDays > 0) {
            return toleranceDays;
        }
        int categoryDays = categoryToDays(profile);
        if (categoryDays > 0) {
            return categoryDays;
        }
        return DEFAULT_BASELINE_DAYS;
    }

    private List<Float> collectSoilSamples(@Nullable List<EnvironmentEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<EnvironmentEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(EnvironmentEntry::getTimestamp)
            .thenComparingLong(EnvironmentEntry::getId)
            .reversed());
        List<Float> soil = new ArrayList<>();
        for (EnvironmentEntry entry : sorted) {
            Float value = entry.getSoilMoisture();
            if (value != null) {
                soil.add(value);
                if (soil.size() >= SOIL_SAMPLE_LIMIT) {
                    break;
                }
            }
        }
        return soil;
    }

    private EnvironmentSignal determineEnvironmentSignal(@Nullable Float averageSoil) {
        if (averageSoil == null) {
            return EnvironmentSignal.NO_DATA;
        }
        if (averageSoil <= DRY_THRESHOLD) {
            return EnvironmentSignal.DRY;
        }
        if (averageSoil >= WET_THRESHOLD) {
            return EnvironmentSignal.WET;
        }
        return EnvironmentSignal.BALANCED;
    }

    private int computeAdjustment(int baselineDays, EnvironmentSignal signal, @Nullable Float averageSoil) {
        if (signal == EnvironmentSignal.NO_DATA || signal == EnvironmentSignal.BALANCED) {
            return 0;
        }
        int delta = Math.max(1, Math.round(baselineDays * ADJUSTMENT_RATIO));
        delta = Math.min(delta, MAX_ADJUSTMENT_DAYS);
        if (signal == EnvironmentSignal.DRY) {
            if (averageSoil != null) {
                float severity = clampFloat((DRY_THRESHOLD - averageSoil) / DRY_THRESHOLD, 0f, 1f);
                delta += Math.round(severity * 2f);
            }
            delta = Math.min(delta, baselineDays - MIN_INTERVAL_DAYS);
            if (delta <= 0) {
                return 0;
            }
            return -delta;
        }
        if (signal == EnvironmentSignal.WET) {
            if (averageSoil != null) {
                float severity = clampFloat((averageSoil - WET_THRESHOLD) / (100f - WET_THRESHOLD), 0f, 1f);
                delta += Math.round(severity * 2f);
            }
            return delta;
        }
        return 0;
    }

    private int parseFrequencyDays(@Nullable String frequency) {
        if (frequency == null) {
            return 0;
        }
        String normalized = frequency.toLowerCase(Locale.US);
        Matcher matcher = FREQUENCY_PATTERN.matcher(normalized);
        List<Integer> values = new ArrayList<>();
        while (matcher.find()) {
            int first = safeParseInt(matcher.group(1));
            String unit = matcher.group(3);
            int converted = convertToDays(first, unit);
            if (converted > 0) {
                values.add(converted);
            }
            String secondGroup = matcher.group(2);
            if (secondGroup != null) {
                int second = safeParseInt(secondGroup.trim());
                int convertedSecond = convertToDays(second, unit);
                if (convertedSecond > 0) {
                    values.add(convertedSecond);
                }
            }
        }
        if (!values.isEmpty()) {
            float sum = 0f;
            for (Integer value : values) {
                sum += value;
            }
            return Math.max(1, Math.round(sum / values.size()));
        }
        if (normalized.contains("every other day")) {
            return 2;
        }
        if (normalized.contains("every other week") || normalized.contains("biweekly")) {
            return 14;
        }
        if (normalized.contains("daily") || normalized.contains("every day")) {
            return 1;
        }
        if (normalized.contains("weekly")) {
            return 7;
        }
        if (normalized.contains("fortnight")) {
            return 14;
        }
        if (normalized.contains("monthly")) {
            return 30;
        }
        return 0;
    }

    private int convertToDays(int value, String unit) {
        if (value <= 0) {
            return 0;
        }
        if (unit == null) {
            return value;
        }
        String normalized = unit.toLowerCase(Locale.US);
        if (normalized.contains("week")) {
            return value * 7;
        }
        if (normalized.contains("month")) {
            return value * 30;
        }
        return value;
    }

    private int toleranceToDays(@Nullable String tolerance) {
        if (tolerance == null) {
            return 0;
        }
        String normalized = tolerance.toLowerCase(Locale.US);
        if (normalized.contains("drought") || normalized.contains("high")) {
            return 14;
        }
        if (normalized.contains("moderate")) {
            return 7;
        }
        if (normalized.contains("evenly") || normalized.contains("consistent")
            || normalized.contains("requires") || normalized.contains("low")) {
            return 4;
        }
        return 0;
    }

    private int categoryToDays(@Nullable PlantProfile profile) {
        if (profile == null) {
            return 0;
        }
        SpeciesTarget.Category category = profile.getCategory();
        if (category == null) {
            return 0;
        }
        switch (category) {
            case SUCCULENT:
            case CACTUS:
                return 18;
            case HERB:
            case VEGETABLE:
            case FRUIT:
                return 5;
            default:
                return 0;
        }
    }

    private float average(List<Float> values) {
        if (values.isEmpty()) {
            return 0f;
        }
        float sum = 0f;
        for (Float value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int safeParseInt(@Nullable String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
