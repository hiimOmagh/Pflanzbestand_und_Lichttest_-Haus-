package de.oabidi.pflanzenbestandundlichttest.data.util;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;

@VisibleForTesting
class PlantsSectionParser implements ImportManager.SectionParser {
    @NonNull
    @Override
    public ImportManager.Section getSection() {
        return ImportManager.Section.PLANTS;
    }

    @Override
    public boolean parseSection(@NonNull ImportManager.SectionReader reader,
                                @NonNull ImportManager.SectionContext context) throws IOException {
        boolean imported = false;
        ImportManager.SectionRow row;
        while ((row = reader.nextRow()) != null) {
            List<String> parts = ImportManager.parseCsv(row.line);
            if (context.manager.parsePlantRow(parts, context.mode, context.baseDir,
                context.plantIdMap, context.warnings, row.lineNumber,
                context.restoredUris, context.db)) {
                imported = true;
            }
        }
        return imported;
    }
}
