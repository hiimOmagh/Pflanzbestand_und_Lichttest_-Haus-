package de.oabidi.pflanzenbestandundlichttest.data.util;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import de.oabidi.pflanzenbestandundlichttest.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;

/**
 * Manager responsible for importing measurements and diary entries from a CSV file.
 */
public class ImportManager {
    private static final String TAG = "ImportManager";

    private final Context context;
    private final PlantRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Callback used to signal completion of the import operation. */
    public interface Callback {
        void onComplete(boolean success);
    }

    public ImportManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.repository = new PlantRepository(this.context);
    }

    /**
     * Imports measurements and diary entries from the given URI.
     *
     * @param uri      CSV source chosen by the user
     * @param callback invoked on the main thread with the result
     */
    public void importData(@NonNull Uri uri, @NonNull Callback callback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            boolean success = false;
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                success = parseAndInsert(reader);
            } catch (IOException e) {
                Log.e(TAG, "Failed to open import file", e);
                success = false;
            }
            final boolean result = success;
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    private boolean parseAndInsert(BufferedReader reader) throws IOException {
        String line;
        Section section = Section.NONE;
        boolean importedAny = false;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.equals("Measurements")) {
                section = Section.MEASUREMENTS;
                reader.readLine(); // skip header
                continue;
            }
            if (line.equals("DiaryEntries")) {
                section = Section.DIARY;
                reader.readLine(); // skip header
                continue;
            }
            List<String> parts = parseCsv(line);
            try {
                if (section == Section.MEASUREMENTS) {
                    if (parts.size() >= 6) {
                        long plantId = Long.parseLong(parts.get(1));
                        long timeEpoch = Long.parseLong(parts.get(2));
                        float luxAvg = Float.parseFloat(parts.get(3));
                        float ppfd = Float.parseFloat(parts.get(4));
                        float dli = Float.parseFloat(parts.get(5));
                        Measurement m = new Measurement(plantId, timeEpoch, luxAvg, ppfd, dli);
                        repository.insertMeasurement(m, null).get();
                        importedAny = true;
                    } else {
                        Log.e(TAG, "Malformed measurement row: " + line);
                    }
                } else if (section == Section.DIARY) {
                    if (parts.size() >= 6) {
                        long plantId = Long.parseLong(parts.get(1));
                        long timeEpoch = Long.parseLong(parts.get(2));
                        String type = parts.get(3);
                        String note = parts.get(4);
                        String photoUri = parts.get(5);
                        DiaryEntry d = new DiaryEntry(plantId, timeEpoch, type, note);
                        if (!photoUri.isEmpty()) {
                            d.setPhotoUri(photoUri);
                        }
                        repository.insertDiaryEntry(d, null).get();
                        importedAny = true;
                    } else {
                        Log.e(TAG, "Malformed diary row: " + line);
                    }
                }
            } catch (NumberFormatException | ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to parse row: " + line, e);
            }
        }
        return importedAny;
    }

    private static List<String> parseCsv(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private enum Section {
        NONE,
        MEASUREMENTS,
        DIARY
    }
}
