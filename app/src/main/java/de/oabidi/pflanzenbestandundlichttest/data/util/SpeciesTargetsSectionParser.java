package de.oabidi.pflanzenbestandundlichttest.data.util;

import static de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager.*;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.LightMath;
import de.oabidi.pflanzenbestandundlichttest.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.Converters;

class SpeciesTargetsSectionParser implements SectionParser {
    private static final int LEGACY_EXPANDED_COLUMNS = 15;
    private static final int PROFILE_COLUMNS = 27;

    @VisibleForTesting
    @NonNull
    @Override
    public Section getSection() {
        return Section.SPECIES_TARGETS;
    }

    @VisibleForTesting
    @Override
    public boolean parseSection(@NonNull SectionChunk chunk,
                                @NonNull SectionContext context) throws IOException {
        boolean imported = false;
        for (SectionRow row : chunk.getRows()) {
            List<String> parts = parseCsv(row.line);
            try {
                SpeciesTarget target;
                if (context.version >= 2 && parts.size() >= PROFILE_COLUMNS) {
                    target = parseProfileRow(parts, context);
                } else if (context.version >= 2 && parts.size() >= LEGACY_EXPANDED_COLUMNS) {
                    target = parseLegacyExpandedRow(parts, context);
                } else if (parts.size() >= 3) {
                    target = parseLegacyRow(parts, context);
                } else {
                    throw new ParseException("Not enough columns", parts.size());
                }
                context.db.speciesTargetDao().insert(target);
                imported = true;
            } catch (Exception e) {
                Log.e(TAG, "Malformed species target row: " + row.line, e);
                context.warnings.add(new ImportWarning("species targets", row.lineNumber, "malformed row"));
            }
        }
        return imported;
    }

    private SpeciesTarget parseLegacyExpandedRow(List<String> parts, SectionContext context) throws ParseException {
        String speciesKey = parts.get(0);
        SpeciesTarget.StageTarget seedling = new SpeciesTarget.StageTarget(
            parseFloat(parts.get(1), context),
            parseFloat(parts.get(2), context),
            parseFloat(parts.get(3), context),
            parseFloat(parts.get(4), context));
        SpeciesTarget.StageTarget vegetative = new SpeciesTarget.StageTarget(
            parseFloat(parts.get(5), context),
            parseFloat(parts.get(6), context),
            parseFloat(parts.get(7), context),
            parseFloat(parts.get(8), context));
        SpeciesTarget.StageTarget flower = new SpeciesTarget.StageTarget(
            parseFloat(parts.get(9), context),
            parseFloat(parts.get(10), context),
            parseFloat(parts.get(11), context),
            parseFloat(parts.get(12), context));
        String tolerance = emptyToNull(parts.size() > 13 ? parts.get(13) : null);
        String source = emptyToNull(parts.size() > 14 ? parts.get(14) : null);
        SpeciesTarget target = new SpeciesTarget(speciesKey, seedling, vegetative, flower, tolerance, source);
        SpeciesTarget profile = PlantProfile.fromTarget(target);
        return profile;
    }

    private SpeciesTarget parseLegacyRow(List<String> parts, SectionContext context) throws Exception {
        String speciesKey = parts.get(0);
        float ppfdMin = Objects.requireNonNull(context.numberFormat.parse(parts.get(1))).floatValue();
        float ppfdMax = Objects.requireNonNull(context.numberFormat.parse(parts.get(2))).floatValue();
        float dliMin = LightMath.dliFromPpfd(ppfdMin, 12f);
        float dliMax = LightMath.dliFromPpfd(ppfdMax, 12f);
        SpeciesTarget.StageTarget stage = new SpeciesTarget.StageTarget(ppfdMin, ppfdMax, dliMin, dliMax);
        SpeciesTarget target = new SpeciesTarget(speciesKey, stage, stage, stage, null, null);
        SpeciesTarget profile = PlantProfile.fromTarget(target);
        return profile;
    }

    private SpeciesTarget parseProfileRow(List<String> parts, SectionContext context) throws ParseException {
        int index = 0;
        String speciesKey = parts.get(index++);
        String commonName = emptyToNull(parts.get(index++));
        String scientificName = emptyToNull(parts.get(index++));
        SpeciesTarget.Category category = parseCategory(parts.get(index++));
        SpeciesTarget.StageTarget seedling = new SpeciesTarget.StageTarget(
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context));
        SpeciesTarget.StageTarget vegetative = new SpeciesTarget.StageTarget(
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context));
        SpeciesTarget.StageTarget flower = new SpeciesTarget.StageTarget(
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context),
            parseFloat(parts.get(index++), context));

        String wateringFrequency = emptyToNull(parts.get(index++));
        String wateringSoilType = emptyToNull(parts.get(index++));
        String wateringTolerance = emptyToNull(parts.get(index++));
        SpeciesTarget.WateringInfo wateringInfo = null;
        if (!isNullOrEmpty(wateringFrequency) || !isNullOrEmpty(wateringSoilType)
            || isNullOrEmpty(wateringTolerance)) {
            wateringInfo = new SpeciesTarget.WateringInfo(wateringFrequency, wateringSoilType, wateringTolerance);
        }

        Float temperatureMin = parseFloat(parts.get(index++), context);
        Float temperatureMax = parseFloat(parts.get(index++), context);
        SpeciesTarget.FloatRange temperatureRange = null;
        if (temperatureMin != null || temperatureMax != null) {
            temperatureRange = new SpeciesTarget.FloatRange(temperatureMin, temperatureMax);
        }

        Float humidityMin = parseFloat(parts.get(index++), context);
        Float humidityMax = parseFloat(parts.get(index++), context);
        SpeciesTarget.FloatRange humidityRange = null;
        if (humidityMin != null || humidityMax != null) {
            humidityRange = new SpeciesTarget.FloatRange(humidityMin, humidityMax);
        }

        String growthHabit = emptyToNull(parts.get(index++));
        Boolean toxicToPets = parseBoolean(parts.get(index++));
        List<String> careTips = parseList(parts.get(index++));
        List<String> sources = parseList(parts.get(index++));

        SpeciesTarget target = new SpeciesTarget(speciesKey,
            commonName,
            scientificName,
            category,
            seedling,
            vegetative,
            flower,
            wateringInfo,
            temperatureRange,
            humidityRange,
            growthHabit,
            toxicToPets,
            careTips,
            sources);
        SpeciesTarget profile = PlantProfile.fromTarget(target);
        return profile;
    }

    private Float parseFloat(String token, SectionContext context) throws ParseException {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return Objects.requireNonNull(context.numberFormat.parse(token)).floatValue();
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private SpeciesTarget.Category parseCategory(String value) {
        if (value == null || value.trim().isEmpty()) {
            return SpeciesTarget.Category.OTHER;
        }
        String normalized = value.trim().toUpperCase(Locale.US);
        try {
            return SpeciesTarget.Category.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return SpeciesTarget.Category.OTHER;
        }
    }

    private Boolean parseBoolean(String value) {
        String normalized = value != null ? value.trim() : null;
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized)
            || "yes".equalsIgnoreCase(normalized)) {
            return Boolean.TRUE;
        }
        if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized)
            || "no".equalsIgnoreCase(normalized)) {
            return Boolean.FALSE;
        }
        return Boolean.parseBoolean(normalized);
    }

    private List<String> parseList(String value) throws ParseException {
        String normalized = value != null ? value.trim() : null;
        if (normalized == null || normalized.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = Converters.fromJsonToStringList(normalized);
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new ParseException("Invalid list value", 0);
        }
    }

    private boolean isNullOrEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
