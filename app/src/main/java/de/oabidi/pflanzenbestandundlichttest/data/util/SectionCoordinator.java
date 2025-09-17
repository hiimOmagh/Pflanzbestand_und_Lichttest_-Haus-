package de.oabidi.pflanzenbestandundlichttest.data.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@VisibleForTesting
class SectionCoordinator {
    private final ImportManager manager;
    private final ImportManager.SectionReader reader;
    private final Map<ImportManager.Section, ImportManager.SectionParser> parsers;
    private final ImportManager.SectionContext context;
    private final AtomicInteger progress;
    @Nullable
    private final ImportManager.ProgressCallback progressCallback;
    private final int totalSteps;

    SectionCoordinator(@NonNull ImportManager manager,
                       @NonNull ImportManager.SectionReader reader,
                       @NonNull List<ImportManager.SectionParser> parserList,
                       @NonNull ImportManager.SectionContext context,
                       @NonNull AtomicInteger progress,
                       @Nullable ImportManager.ProgressCallback progressCallback,
                       int totalSteps) {
        this.manager = manager;
        this.reader = reader;
        this.context = context;
        this.progress = progress;
        this.progressCallback = progressCallback;
        this.totalSteps = totalSteps;
        this.parsers = new EnumMap<>(ImportManager.Section.class);
        for (ImportManager.SectionParser parser : parserList) {
            this.parsers.put(parser.getSection(), parser);
        }
    }

    boolean process() throws IOException {
        boolean importedAny = false;
        ImportManager.Section section;
        while ((section = reader.nextSection(manager)) != null) {
            ImportManager.SectionParser parser = parsers.get(section);
            if (parser == null) {
                throw new IOException("No parser registered for section " + section.getHeader());
            }
            if (parser.parseSection(reader, context)) {
                importedAny = true;
            }
            manager.stepProgress(progress, progressCallback, totalSteps);
        }
        return importedAny;
    }
}
