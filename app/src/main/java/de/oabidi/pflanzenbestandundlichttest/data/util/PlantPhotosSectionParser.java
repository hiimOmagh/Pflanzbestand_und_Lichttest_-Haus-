package de.oabidi.pflanzenbestandundlichttest.data.util;

import static de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager.*;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;

class PlantPhotosSectionParser implements SectionParser {
    @NonNull
    @Override
    public Section getSection() {
        return Section.PLANT_PHOTOS;
    }

    @Override
    public boolean parseSection(@NonNull SectionReader reader,
                                @NonNull SectionContext context) throws IOException {
        boolean imported = false;
        SectionRow row;
        while ((row = reader.nextRow()) != null) {
            List<String> parts = parseCsv(row.line);
            if (context.manager.insertPlantPhotoRow(parts, context.mode, context.baseDir,
                context.plantIdMap, context.warnings, row.lineNumber,
                context.restoredUris, context.db)) {
                imported = true;
            }
        }
        return imported;
    }
}
