package de.oabidi.pflanzenbestandundlichttest.data.util;

import static de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager.*;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;

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
            if (parts.size() >= 3) {
                try {
                    String speciesKey = parts.get(0);
                    float ppfdMin = Objects.requireNonNull(context.numberFormat.parse(parts.get(1))).floatValue();
                    float ppfdMax = Objects.requireNonNull(context.numberFormat.parse(parts.get(2))).floatValue();
                    SpeciesTarget target = new SpeciesTarget(speciesKey, ppfdMin, ppfdMax);
                    context.db.speciesTargetDao().insert(target);
                    imported = true;
                } catch (Exception e) {
                    Log.e(TAG, "Malformed species target row: " + row.line, e);
                    context.warnings.add(new ImportWarning("species targets", row.lineNumber, "malformed row"));
                }
            } else {
                Log.e(TAG, "Malformed species target row: " + row.line);
                context.warnings.add(new ImportWarning("species targets", row.lineNumber, "malformed row"));
            }
        }
        return imported;
    }
}
