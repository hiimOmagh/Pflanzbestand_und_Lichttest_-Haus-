package de.oabidi.pflanzenbestandundlichttest.core.data.util;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;

@VisibleForTesting
class EnvironmentEntriesSectionParser implements ImportManager.SectionParser {
    @NonNull
    @Override
    public ImportManager.Section getSection() {
        return ImportManager.Section.ENVIRONMENT_ENTRIES;
    }

    @Override
    public boolean parseSection(@NonNull ImportManager.SectionChunk chunk,
                                @NonNull ImportManager.SectionContext context) throws IOException {
        boolean imported = false;
        for (ImportManager.SectionRow row : chunk.getRows()) {
            List<String> parts = ImportManager.parseCsv(row.line);
            if (context.manager.insertEnvironmentEntryRow(parts, context.mode, context.baseDir,
                context.plantIdMap, context.warnings, row.lineNumber, context.restoredUris,
                context.numberFormat, context.db)) {
                imported = true;
            }
        }
        return imported;
    }
}
