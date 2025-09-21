package de.oabidi.pflanzenbestandundlichttest.data.util;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;

@VisibleForTesting
class RemindersSectionParser implements ImportManager.SectionParser {
    @NonNull
    @Override
    public ImportManager.Section getSection() {
        return ImportManager.Section.REMINDERS;
    }

    @Override
    public boolean parseSection(@NonNull ImportManager.SectionChunk chunk,
                                @NonNull ImportManager.SectionContext context) throws IOException {
        boolean imported = false;
        for (ImportManager.SectionRow row : chunk.getRows()) {
            List<String> parts = ImportManager.parseCsv(row.line);
            if (context.manager.insertReminderRow(parts, context.mode,
                context.plantIdMap, context.warnings, row.lineNumber, context.db)) {
                imported = true;
            }
        }
        return imported;
    }
}
