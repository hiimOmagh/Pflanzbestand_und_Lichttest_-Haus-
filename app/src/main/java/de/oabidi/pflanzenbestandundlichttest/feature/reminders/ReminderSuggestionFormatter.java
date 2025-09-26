package de.oabidi.pflanzenbestandundlichttest.feature.reminders;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;

/**
 * Formats {@link SmartReminderEngine.Suggestion} instances for presentation to the user.
 */
public class ReminderSuggestionFormatter {
    private final Resources resources;

    public ReminderSuggestionFormatter(@NonNull Resources resources) {
        this.resources = resources;
    }

    @NonNull
    public String format(@Nullable Plant plant,
                         @Nullable PlantProfile profile,
                         @NonNull SmartReminderEngine.Suggestion suggestion) {
        StringBuilder builder = new StringBuilder();
        builder.append(resources.getString(R.string.reminder_suggestion_summary,
            suggestion.getSuggestedDays()));

        String baseline = buildBaselineText(plant, profile, suggestion);
        if (!baseline.isEmpty()) {
            builder.append('\n').append(baseline);
        }

        String environment = buildEnvironmentText(suggestion);
        if (!environment.isEmpty()) {
            if (baseline.isEmpty()) {
                builder.append('\n');
            } else {
                builder.append(' ');
            }
            builder.append(environment);
        }

        return builder.toString();
    }

    private String buildBaselineText(@Nullable Plant plant,
                                     @Nullable PlantProfile profile,
                                     @NonNull SmartReminderEngine.Suggestion suggestion) {
        SmartReminderEngine.BaselineSource source = suggestion.getBaselineSource();
        int baselineDays = suggestion.getBaselineDays();
        String plantLabel = derivePlantLabel(plant, profile);
        SpeciesTarget.WateringInfo info = profile != null ? profile.getWateringInfo() : null;
        switch (source) {
            case SPECIES_FREQUENCY:
                String frequency = info != null ? safeTrim(info.getFrequency()) : null;
                if (frequency == null) {
                    frequency = resources.getString(R.string.reminder_suggestion_frequency_unknown);
                }
                return resources.getString(R.string.reminder_suggestion_baseline_frequency,
                    plantLabel, frequency, baselineDays);
            case SPECIES_TOLERANCE:
                String tolerance = info != null ? safeTrim(info.getTolerance()) : null;
                if (tolerance == null) {
                    tolerance = resources.getString(R.string.reminder_suggestion_tolerance_unknown);
                }
                return resources.getString(R.string.reminder_suggestion_baseline_tolerance,
                    tolerance, baselineDays);
            case CATEGORY:
                SpeciesTarget.Category category = profile != null ? profile.getCategory() : null;
                String categoryLabel = category != null
                    ? capitalize(category.name().replace('_', ' '))
                    : resources.getString(R.string.reminder_suggestion_category_generic);
                return resources.getString(R.string.reminder_suggestion_baseline_category,
                    categoryLabel, baselineDays);
            case DEFAULT:
            default:
                return resources.getString(R.string.reminder_suggestion_baseline_default,
                    baselineDays);
        }
    }

    private String buildEnvironmentText(@NonNull SmartReminderEngine.Suggestion suggestion) {
        SmartReminderEngine.EnvironmentSignal signal = suggestion.getEnvironmentSignal();
        int adjustment = Math.abs(suggestion.getAdjustmentDays());
        Float average = suggestion.getAverageSoilMoisture();
        switch (signal) {
            case DRY:
                if (adjustment <= 0 || average == null) {
                    return resources.getString(R.string.reminder_suggestion_env_balanced);
                }
                return resources.getString(R.string.reminder_suggestion_env_dry,
                    average, adjustment);
            case WET:
                if (adjustment <= 0 || average == null) {
                    return resources.getString(R.string.reminder_suggestion_env_balanced);
                }
                return resources.getString(R.string.reminder_suggestion_env_wet,
                    average, adjustment);
            case BALANCED:
                return resources.getString(R.string.reminder_suggestion_env_balanced);
            case NO_DATA:
            default:
                return resources.getString(R.string.reminder_suggestion_env_missing);
        }
    }

    private String derivePlantLabel(@Nullable Plant plant, @Nullable PlantProfile profile) {
        if (profile != null) {
            String common = safeTrim(profile.getCommonName());
            if (common != null) {
                return common;
            }
            String scientific = safeTrim(profile.getScientificName());
            if (scientific != null) {
                return scientific;
            }
        }
        if (plant != null) {
            String name = safeTrim(plant.getName());
            if (name != null) {
                return name;
            }
        }
        return resources.getString(R.string.reminder_suggestion_unknown_species);
    }

    @Nullable
    private String safeTrim(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String capitalize(String value) {
        String trimmed = safeTrim(value);
        if (trimmed == null) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.getDefault());
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
