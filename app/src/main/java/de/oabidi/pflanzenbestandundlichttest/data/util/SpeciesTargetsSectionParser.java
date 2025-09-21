package de.oabidi.pflanzenbestandundlichttest.data.util;

import static de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager.*;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;
import java.text.ParseException;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.LightMath;

class SpeciesTargetsSectionParser implements SectionParser {
    @VisibleForTesting
    @NonNull
    @Override
    public Section getSection() {
        return Section.SPECIES_TARGETS;
    }

    @VisibleForTesting
    @Override
    public boolean parseSection(@NonNull SectionReader reader,
                                @NonNull SectionContext context) throws IOException {
        boolean imported = false;
        SectionRow row;
        while ((row = reader.nextRow()) != null) {
            List<String> parts = parseCsv(row.line);
            try {
                SpeciesTarget target;
                if (context.version >= 2 && parts.size() >= 15) {
                    target = parseExpandedRow(parts, context);
                } else if (parts.size() >= 3) {
                    target = parseLegacyRow(parts, context);
                } else {
                    throw new ParseException("Not enough columns", parts.size());
                }
                if (target != null) {
                    context.db.speciesTargetDao().insert(target);
                    imported = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Malformed species target row: " + row.line, e);
                context.warnings.add(new ImportWarning("species targets", row.lineNumber, "malformed row"));
            }
        }
        return imported;
    }

    private SpeciesTarget parseExpandedRow(List<String> parts, SectionContext context) throws ParseException {
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
        return new SpeciesTarget(speciesKey, seedling, vegetative, flower, tolerance, source);
    }

    private SpeciesTarget parseLegacyRow(List<String> parts, SectionContext context) throws Exception {
        String speciesKey = parts.get(0);
        float ppfdMin = Objects.requireNonNull(context.numberFormat.parse(parts.get(1))).floatValue();
        float ppfdMax = Objects.requireNonNull(context.numberFormat.parse(parts.get(2))).floatValue();
        float dliMin = LightMath.dliFromPpfd(ppfdMin, 12f);
        float dliMax = LightMath.dliFromPpfd(ppfdMax, 12f);
        SpeciesTarget.StageTarget stage = new SpeciesTarget.StageTarget(ppfdMin, ppfdMax, dliMin, dliMax);
        return new SpeciesTarget(speciesKey, stage, stage, stage, null, null);
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
}
