package de.oabidi.pflanzenbestandundlichttest.data.util;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;

/**
 * Parses the PlantCalibrations section from an import CSV.
 */
@VisibleForTesting
class PlantCalibrationsSectionParser implements ImportManager.SectionParser {
    @NonNull
    @Override
    public ImportManager.Section getSection() {
        return ImportManager.Section.PLANT_CALIBRATIONS;
    }

    @Override
    public boolean parseSection(@NonNull ImportManager.SectionChunk chunk,
                                @NonNull ImportManager.SectionContext context) throws IOException {
        boolean imported = false;
        for (ImportManager.SectionRow row : chunk.getRows()) {
            List<String> parts = ImportManager.parseCsv(row.line);
            if (context.manager.insertCalibrationRow(parts, context.mode, context.plantIdMap,
                context.warnings, row.lineNumber, context.db)) {
                imported = true;
            }
        }
        return imported;
    }
}
