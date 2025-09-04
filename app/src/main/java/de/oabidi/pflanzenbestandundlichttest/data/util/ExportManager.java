package de.oabidi.pflanzenbestandundlichttest.data.util;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;

/**
 * Manager responsible for exporting measurements and diary entries to a CSV file.
 */
public class ExportManager {
    private final Context context;
    private final PlantRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Callback used to signal completion of the export operation. */
    public interface Callback {
        void onComplete(boolean success);
    }

    public ExportManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.repository = new PlantRepository(this.context);
    }

    /**
     * Exports all measurements and diary entries to the given destination URI.
     *
     * @param uri      destination chosen by the user
     * @param callback invoked on the main thread with the result
     */
    public void export(@NonNull Uri uri, @NonNull Callback callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            boolean success = false;
            try (OutputStream os = context.getContentResolver().openOutputStream(uri);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os))) {
                List<Plant> plants = repository.getAllPlantsSync();
                List<SpeciesTarget> targets = repository.getAllSpeciesTargetsSync();
                List<Measurement> measurements = repository.getAllMeasurementsSync();
                List<DiaryEntry> diaryEntries = repository.getAllDiaryEntriesSync();

                writer.write("Plants\n");
                writer.write("id,name,description,species,locationHint,acquiredAtEpoch,photoUri\n");
                for (Plant p : plants) {
                    writer.write(p.getId() + "," + escape(p.getName()) + "," +
                        escape(p.getDescription()) + "," + escape(p.getSpecies()) + "," +
                        escape(p.getLocationHint()) + "," + p.getAcquiredAtEpoch() + "," +
                        escape(p.getPhotoUri() != null ? p.getPhotoUri().toString() : null) + "\n");
                }

                writer.write("\nSpeciesTargets\n");
                writer.write("speciesKey,ppfdMin,ppfdMax\n");
                for (SpeciesTarget t : targets) {
                    writer.write(escape(t.getSpeciesKey()) + "," + t.getPpfdMin() + "," + t.getPpfdMax() + "\n");
                }

                writer.write("\nMeasurements\n");
                writer.write("id,plantId,timeEpoch,luxAvg,ppfd,dli\n");
                for (Measurement m : measurements) {
                    writer.write(m.getId() + "," + m.getPlantId() + "," + m.getTimeEpoch() + "," +
                        m.getLuxAvg() + "," + m.getPpfd() + "," + m.getDli() + "\n");
                }

                writer.write("\nDiaryEntries\n");
                writer.write("id,plantId,timeEpoch,type,note,photoUri\n");
                for (DiaryEntry d : diaryEntries) {
                    writer.write(d.getId() + "," + d.getPlantId() + "," + d.getTimeEpoch() + "," +
                        escape(d.getType()) + "," + escape(d.getNote()) + "," + escape(d.getPhotoUri()) + "\n");
                }
                writer.flush();
                success = true;
            } catch (IOException e) {
                success = false;
            }
            final boolean result = success;
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n")) {
            escaped = "\"" + escaped + "\"";
        }
        return escaped;
    }
}
