package de.oabidi.pflanzenbestandundlichttest.data.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicInteger totalSteps;
    private final AtomicBoolean cancelled;

    SectionCoordinator(@NonNull ImportManager manager,
                       @NonNull ImportManager.SectionReader reader,
                       @NonNull List<ImportManager.SectionParser> parserList,
                       @NonNull ImportManager.SectionContext context,
                       @NonNull AtomicInteger progress,
                       @Nullable ImportManager.ProgressCallback progressCallback,
                       @NonNull AtomicInteger totalSteps,
                       @NonNull AtomicBoolean cancelled) {
        this.manager = manager;
        this.reader = reader;
        this.context = context;
        this.progress = progress;
        this.progressCallback = progressCallback;
        this.totalSteps = totalSteps;
        this.cancelled = cancelled;
        this.parsers = new EnumMap<>(ImportManager.Section.class);
        for (ImportManager.SectionParser parser : parserList) {
            this.parsers.put(parser.getSection(), parser);
        }
    }

    boolean process() throws IOException {
        List<SectionTask> tasks = new ArrayList<>();
        ImportManager.SectionChunk chunk;
        while ((chunk = reader.nextSectionChunk(manager)) != null) {
            ImportManager.SectionParser parser = parsers.get(chunk.getSection());
            if (parser == null) {
                throw new IOException("No parser registered for section " + chunk.getSection().getHeader());
            }
            int workUnits = Math.max(0, parser.estimateWorkUnits(chunk));
            tasks.add(new SectionTask(parser, chunk, workUnits));
        }

        int archiveProgress = progress.get();
        int aggregated = archiveProgress;
        for (SectionTask task : tasks) {
            aggregated += task.workUnits;
        }
        if (aggregated < archiveProgress) {
            aggregated = archiveProgress;
        }
        if (aggregated != totalSteps.get()) {
            totalSteps.set(aggregated);
            manager.publishProgress(progress, progressCallback, totalSteps.get());
        } else if (progressCallback != null) {
            manager.publishProgress(progress, progressCallback, totalSteps.get());
        }

        boolean importedAny = false;
        for (SectionTask task : tasks) {
            if (cancelled.get()) {
                break;
            }
            if (task.parser.parseSection(task.chunk, context)) {
                importedAny = true;
            }
            if (task.workUnits > 0) {
                manager.stepProgress(progress, progressCallback, totalSteps.get(), task.workUnits);
            }
        }
        return importedAny;
    }

    private static final class SectionTask {
        final ImportManager.SectionParser parser;
        final ImportManager.SectionChunk chunk;
        final int workUnits;

        SectionTask(ImportManager.SectionParser parser,
                    ImportManager.SectionChunk chunk,
                    int workUnits) {
            this.parser = parser;
            this.chunk = chunk;
            this.workUnits = workUnits;
        }
    }
}
