package de.oabidi.pflanzenbestandundlichttest.data.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@VisibleForTesting
class SpeciesTargetsSectionParser implements ImportManager.SectionParser {
    @NonNull
    @Override
    public ImportManager.Section getSection() {
        return ImportManager.Section.SPECIES_TARGETS;
    }

    @Override
    public boolean parseSection(@NonNull ImportManager.SectionReader reader,
                                @NonNull ImportManager.SectionContext context) throws IOException {
        boolean imported = false;
        ImportManager.SectionRow row;
        while ((row = reader.nextRow()) != null) {
            List<String> parts = ImportManager.parseCsv(row.line);
            if (parts.size() >= 3) {
                try {
                    String speciesKey = parts.get(0);
                    float ppfdMin = Objects.requireNonNull(context.numberFormat.parse(parts.get(1))).floatValue();
                    float ppfdMax = Objects.requireNonNull(context.numberFormat.parse(parts.get(2))).floatValue();
                    SpeciesTarget target = new SpeciesTarget(speciesKey, ppfdMin, ppfdMax);
                    context.db.speciesTargetDao().insert(target);
                    imported = true;
                } catch (Exception e) {
                    Log.e(ImportManager.TAG, "Malformed species target row: " + row.line, e);
                    context.warnings.add(new ImportManager.ImportWarning("species targets", row.lineNumber, "malformed row"));
                }
            } else {
                Log.e(ImportManager.TAG, "Malformed species target row: " + row.line);
                context.warnings.add(new ImportManager.ImportWarning("species targets", row.lineNumber, "malformed row"));
            }
        }
        return imported;
    }
}
