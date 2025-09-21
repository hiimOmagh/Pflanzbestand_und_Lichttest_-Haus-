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
    public boolean parseSection(@NonNull ImportManager.SectionChunk chunk,
                                @NonNull ImportManager.SectionContext context) throws IOException {
        boolean imported = false;
        for (ImportManager.SectionRow row : chunk.getRows()) {
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
