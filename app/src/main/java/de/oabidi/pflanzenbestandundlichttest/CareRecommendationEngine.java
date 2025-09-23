package de.oabidi.pflanzenbestandundlichttest;

import android.content.res.Resources;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget.FloatRange;
import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;

/**
 * Analyses recent {@link EnvironmentEntry} values and produces actionable care suggestions.
 */
public class CareRecommendationEngine {

    private static final float TEMPERATURE_CRITICAL_DELTA = 3f;
    private static final float HUMIDITY_CRITICAL_DELTA = 10f;
    private static final float SOIL_DRY_THRESHOLD = 20f;
    private static final float SOIL_WET_THRESHOLD = 80f;
    private static final float MIN_GROWTH_DELTA_CM = 1f;
    private static final int MIN_GROWTH_SAMPLES = 3;
    private static final int GROWTH_WINDOW_SIZE = 5;
    private static final long MIN_GROWTH_TIMESPAN_MS = 14L * 24L * 60L * 60L * 1000L; // 14 days

    /**
     * Evaluates the provided readings and returns applicable care recommendations.
     */
    @NonNull
    public List<CareRecommendation> evaluate(@Nullable PlantProfile profile,
                                             @Nullable List<EnvironmentEntry> entries,
                                             @NonNull Resources res) {
        Objects.requireNonNull(res, "res");
        List<EnvironmentEntry> sortedEntries = entries == null
            ? Collections.emptyList()
            : new ArrayList<>(entries);
        if (!sortedEntries.isEmpty()) {
            sortedEntries.sort(Comparator.comparingLong(EnvironmentEntry::getTimestamp)
                .thenComparingLong(EnvironmentEntry::getId)
                .reversed());
        }

        List<CareRecommendation> recommendations = new ArrayList<>();
        EnvironmentEntry latest = sortedEntries.isEmpty() ? null : sortedEntries.get(0);

        evaluateTemperature(profile, latest, recommendations);
        evaluateHumidity(profile, latest, recommendations);
        evaluateSoilMoisture(latest, recommendations);
        evaluateGrowth(sortedEntries, recommendations);

        return Collections.unmodifiableList(recommendations);
    }

    private void evaluateTemperature(@Nullable PlantProfile profile,
                                     @Nullable EnvironmentEntry latest,
                                     List<CareRecommendation> out) {
        if (profile == null || latest == null) {
            return;
        }
        Float value = latest.getTemperature();
        FloatRange range = profile.getTemperatureRange();
        if (value == null || range == null) {
            return;
        }
        Float min = range.getMin();
        Float max = range.getMax();
        if (min == null && max == null) {
            return;
        }
        if (min != null && value < min) {
            float delta = min - value;
            Severity severity = delta >= TEMPERATURE_CRITICAL_DELTA
                ? Severity.CRITICAL
                : Severity.WARNING;
            @StringRes int resId = max != null
                ? R.string.care_temperature_low_range
                : R.string.care_temperature_low_min;
            Object[] args = max != null
                ? new Object[]{value, min, max}
                : new Object[]{value, min};
            out.add(CareRecommendation.createWithResource("temperature_low", severity,
                R.drawable.ic_care_temperature, resId, args));
            return;
        }
        if (max != null && value > max) {
            float delta = value - max;
            Severity severity = delta >= TEMPERATURE_CRITICAL_DELTA
                ? Severity.CRITICAL
                : Severity.WARNING;
            @StringRes int resId = min != null
                ? R.string.care_temperature_high_range
                : R.string.care_temperature_high_max;
            Object[] args = min != null
                ? new Object[]{value, min, max}
                : new Object[]{value, max};
            out.add(CareRecommendation.createWithResource("temperature_high", severity,
                R.drawable.ic_care_temperature, resId, args));
        }
    }

    private void evaluateHumidity(@Nullable PlantProfile profile,
                                  @Nullable EnvironmentEntry latest,
                                  List<CareRecommendation> out) {
        if (profile == null || latest == null) {
            return;
        }
        Float value = latest.getHumidity();
        FloatRange range = profile.getHumidityRange();
        if (value == null || range == null) {
            return;
        }
        Float min = range.getMin();
        Float max = range.getMax();
        if (min == null && max == null) {
            return;
        }
        if (min != null && value < min) {
            float delta = min - value;
            Severity severity = delta >= HUMIDITY_CRITICAL_DELTA
                ? Severity.CRITICAL
                : Severity.WARNING;
            @StringRes int resId = max != null
                ? R.string.care_humidity_low_range
                : R.string.care_humidity_low_min;
            Object[] args = max != null
                ? new Object[]{value, min, max}
                : new Object[]{value, min};
            out.add(CareRecommendation.createWithResource("humidity_low", severity,
                R.drawable.ic_care_water_drop, resId, args));
            return;
        }
        if (max != null && value > max) {
            float delta = value - max;
            Severity severity = delta >= HUMIDITY_CRITICAL_DELTA
                ? Severity.CRITICAL
                : Severity.WARNING;
            @StringRes int resId = min != null
                ? R.string.care_humidity_high_range
                : R.string.care_humidity_high_max;
            Object[] args = min != null
                ? new Object[]{value, min, max}
                : new Object[]{value, max};
            out.add(CareRecommendation.createWithResource("humidity_high", severity,
                R.drawable.ic_care_water_drop, resId, args));
        }
    }

    private void evaluateSoilMoisture(@Nullable EnvironmentEntry latest,
                                      List<CareRecommendation> out) {
        if (latest == null) {
            return;
        }
        Float soil = latest.getSoilMoisture();
        if (soil == null) {
            return;
        }
        if (soil <= SOIL_DRY_THRESHOLD) {
            out.add(CareRecommendation.createWithResource("soil_dry", Severity.WARNING,
                R.drawable.ic_care_water_drop, R.string.care_soil_dry, soil));
        } else if (soil >= SOIL_WET_THRESHOLD) {
            out.add(CareRecommendation.createWithResource("soil_wet", Severity.WARNING,
                R.drawable.ic_care_water_drop, R.string.care_soil_wet, soil));
        }
    }

    private void evaluateGrowth(List<EnvironmentEntry> entries, List<CareRecommendation> out) {
        List<Sample> heightSamples = collectSamples(entries, EnvironmentEntry::getHeight);
        List<Sample> widthSamples = collectSamples(entries, EnvironmentEntry::getWidth);

        boolean heightStalled = isGrowthStalled(heightSamples);
        boolean widthStalled = isGrowthStalled(widthSamples);

        if (!heightStalled && !widthStalled) {
            return;
        }

        if (heightStalled && widthStalled) {
            int count = Math.min(heightSamples.size(), widthSamples.size());
            if (count == 0) {
                count = Math.max(heightSamples.size(), widthSamples.size());
            }
            out.add(CareRecommendation.createWithResource("growth_stalled",
                Severity.INFO, R.drawable.ic_care_trending_up,
                R.string.care_growth_stalled_height_width,
                count,
                Math.abs(delta(heightSamples)),
                Math.abs(delta(widthSamples))));
        } else if (heightStalled) {
            out.add(CareRecommendation.createWithResource("growth_height_stalled",
                Severity.INFO, R.drawable.ic_care_trending_up,
                R.string.care_growth_stalled_height,
                heightSamples.size(), Math.abs(delta(heightSamples))));
        } else {
            out.add(CareRecommendation.createWithResource("growth_width_stalled",
                Severity.INFO, R.drawable.ic_care_trending_up,
                R.string.care_growth_stalled_width,
                widthSamples.size(), Math.abs(delta(widthSamples))));
        }
    }

    private List<Sample> collectSamples(List<EnvironmentEntry> entries,
                                        Function<EnvironmentEntry, Float> extractor) {
        List<Sample> samples = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return samples;
        }
        for (EnvironmentEntry entry : entries) {
            Float value = extractor.apply(entry);
            if (value != null) {
                samples.add(new Sample(value, entry.getTimestamp()));
                if (samples.size() >= GROWTH_WINDOW_SIZE) {
                    break;
                }
            }
        }
        return samples;
    }

    private boolean isGrowthStalled(List<Sample> samples) {
        if (samples.size() < MIN_GROWTH_SAMPLES) {
            return false;
        }
        Sample newest = samples.get(0);
        Sample oldest = samples.get(samples.size() - 1);
        long span = newest.timestamp - oldest.timestamp;
        if (span < MIN_GROWTH_TIMESPAN_MS) {
            return false;
        }
        float change = newest.value - oldest.value;
        return Math.abs(change) < MIN_GROWTH_DELTA_CM;
    }

    private float delta(List<Sample> samples) {
        if (samples.isEmpty()) {
            return 0f;
        }
        Sample newest = samples.get(0);
        Sample oldest = samples.get(samples.size() - 1);
        return newest.value - oldest.value;
    }

    private static final class Sample {
        final float value;
        final long timestamp;

        Sample(float value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    /** Severity levels for recommendations. */
    public enum Severity {
        INFO(R.drawable.ic_care_trending_up),
        WARNING(R.drawable.ic_care_water_drop),
        CRITICAL(R.drawable.ic_care_temperature);

        @DrawableRes
        private final int defaultIconResId;

        Severity(@DrawableRes int defaultIconResId) {
            this.defaultIconResId = defaultIconResId;
        }

        @DrawableRes
        public int getDefaultIconResId() {
            return defaultIconResId;
        }
    }

    /**
     * Immutable representation of a single care recommendation.
     */
    public static final class CareRecommendation {
        private final String id;
        private final Severity severity;
        @DrawableRes
        private final int iconResId;
        @StringRes
        @Nullable
        private final Integer messageResId;
        @Nullable
        private final Object[] formatArgs;
        @Nullable
        private final CharSequence message;

        private CareRecommendation(@NonNull String id,
                                   @NonNull Severity severity,
                                   @DrawableRes int iconResId,
                                   @Nullable Integer messageResId,
                                   @Nullable Object[] formatArgs,
                                   @Nullable CharSequence message) {
            this.id = Objects.requireNonNull(id, "id");
            this.severity = Objects.requireNonNull(severity, "severity");
            this.iconResId = iconResId;
            this.messageResId = messageResId;
            this.formatArgs = formatArgs;
            this.message = message;
        }

        /** Creates a recommendation backed by a string resource. */
        @NonNull
        public static CareRecommendation createWithResource(@NonNull String id,
                                                            @NonNull Severity severity,
                                                            @DrawableRes int iconResId,
                                                            @StringRes int messageRes,
                                                            Object... args) {
            int resolvedIcon = iconResId != 0 ? iconResId : severity.getDefaultIconResId();
            return new CareRecommendation(id, severity, resolvedIcon, messageRes,
                args != null && args.length > 0 ? args : null, null);
        }

        /** Creates a recommendation with a pre-built message. */
        @NonNull
        public static CareRecommendation createWithMessage(@NonNull String id,
                                                           @NonNull Severity severity,
                                                           @DrawableRes int iconResId,
                                                           @NonNull CharSequence message) {
            int resolvedIcon = iconResId != 0 ? iconResId : severity.getDefaultIconResId();
            return new CareRecommendation(id, severity, resolvedIcon, null, null, message);
        }

        @NonNull
        public String getId() {
            return id;
        }

        @NonNull
        public Severity getSeverity() {
            return severity;
        }

        @DrawableRes
        public int getIconResId() {
            return iconResId;
        }

        public boolean hasIcon() {
            return iconResId != 0;
        }

        @Nullable
        public Integer getMessageResId() {
            return messageResId;
        }

        @Nullable
        public Object[] getFormatArgs() {
            return formatArgs;
        }

        @Nullable
        public CharSequence getMessage() {
            return message;
        }
    }
}
