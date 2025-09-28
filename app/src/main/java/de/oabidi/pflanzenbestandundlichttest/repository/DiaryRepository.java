package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.util.PhotoManager;

/**
 * Repository for {@link DiaryEntry} persistence.
 */
public class DiaryRepository extends BaseRepository {
    private final DiaryDao diaryDao;

    public DiaryRepository(Context context, Handler mainHandler, ExecutorService ioExecutor, DiaryDao diaryDao) {
        super(context, mainHandler, ioExecutor);
        this.diaryDao = Objects.requireNonNull(diaryDao, "diaryDao");
    }

    public void insertDiaryEntry(DiaryEntry entry, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(entry, "entry");
        runAsync(() -> diaryDao.insert(entry), callback, errorCallback);
    }

    public void insertDiaryEntry(DiaryEntry entry, Runnable callback) {
        insertDiaryEntry(entry, callback, null);
    }

    public void updateDiaryEntry(DiaryEntry entry, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(entry, "entry");
        runAsync(() -> diaryDao.update(entry), callback, errorCallback);
    }

    public void updateDiaryEntry(DiaryEntry entry, Runnable callback) {
        updateDiaryEntry(entry, callback, null);
    }

    public void deleteDiaryEntry(DiaryEntry entry, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(entry, "entry");
        final String photoUri = entry.getPhotoUri();
        runAsync(() -> diaryDao.delete(entry), () -> PhotoManager.deletePhoto(context, photoUri), callback, errorCallback);
    }

    public void deleteDiaryEntry(DiaryEntry entry, Runnable callback) {
        deleteDiaryEntry(entry, callback, null);
    }

    public void diaryEntriesForPlant(long plantId, Consumer<List<DiaryEntry>> callback,
                                     @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> diaryDao.entriesForPlant(plantId), callback, errorCallback);
    }

    public void diaryEntriesForPlant(long plantId, Consumer<List<DiaryEntry>> callback) {
        diaryEntriesForPlant(plantId, callback, null);
    }

    public DiaryEntry getLatestDiaryEntrySync(long plantId) {
        return diaryDao.latestForPlant(plantId);
    }

    public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback,
                                   @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> {
            if (query == null || query.isEmpty()) {
                return diaryDao.entriesForPlant(plantId);
            }
            return diaryDao.searchDiaryEntries(plantId, query + "*");
        }, callback, errorCallback);
    }

    public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback) {
        searchDiaryEntries(plantId, query, callback, null);
    }
}
