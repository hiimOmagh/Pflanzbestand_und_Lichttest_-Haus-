package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntryDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.util.PhotoManager;

/**
 * Repository handling {@link EnvironmentEntry} persistence and photo management.
 */
public class EnvironmentRepository extends BaseRepository {
    private final EnvironmentEntryDao environmentEntryDao;
    private final CareRecommendationDelegate careDelegate;
    private final ArtificialLightEstimateSource artificialLightSource;

    public EnvironmentRepository(Context context, Handler mainHandler, ExecutorService ioExecutor,
                                 EnvironmentEntryDao environmentEntryDao,
                                 CareRecommendationDelegate careDelegate,
                                 ArtificialLightEstimateSource artificialLightSource) {
        super(context, mainHandler, ioExecutor);
        this.environmentEntryDao = Objects.requireNonNull(environmentEntryDao, "environmentEntryDao");
        this.careDelegate = Objects.requireNonNull(careDelegate, "careDelegate");
        this.artificialLightSource = Objects.requireNonNull(artificialLightSource, "artificialLightSource");
    }

    public void environmentEntriesForPlant(long plantId, Consumer<List<EnvironmentEntry>> callback,
                                           @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> environmentEntryDao.getForPlantOrdered(plantId), callback, errorCallback);
    }

    public void environmentEntriesForPlant(long plantId, Consumer<List<EnvironmentEntry>> callback) {
        environmentEntriesForPlant(plantId, callback, null);
    }

    public void insertEnvironmentEntry(EnvironmentEntry entry, Runnable callback,
                                       @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(entry, "entry");
        runAsync(() -> {
            attachArtificialLightEstimate(entry);
            entry.setPhotoUri(persistEnvironmentPhoto(entry.getPhotoUri()));
            long id = environmentEntryDao.insert(entry);
            entry.setId(id);
        }, () -> careDelegate.refreshCareRecommendationsAsync(entry.getPlantId()), callback, errorCallback);
    }

    public void insertEnvironmentEntry(EnvironmentEntry entry, Runnable callback) {
        insertEnvironmentEntry(entry, callback, null);
    }

    public void updateEnvironmentEntry(EnvironmentEntry entry, @Nullable String previousPhotoUri, Runnable callback,
                                       @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(entry, "entry");
        runAsync(() -> {
            attachArtificialLightEstimate(entry);
            entry.setPhotoUri(persistEnvironmentPhoto(entry.getPhotoUri()));
            environmentEntryDao.update(entry);
        }, () -> combineEnvironmentPostActions(
            buildEnvironmentPhotoCleanup(previousPhotoUri, entry.getPhotoUri()),
            careDelegate.refreshCareRecommendationsAsync(entry.getPlantId())), callback, errorCallback);
    }

    public void updateEnvironmentEntry(EnvironmentEntry entry, @Nullable String previousPhotoUri, Runnable callback) {
        updateEnvironmentEntry(entry, previousPhotoUri, callback, null);
    }

    public void deleteEnvironmentEntry(EnvironmentEntry entry, Runnable callback,
                                       @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(entry, "entry");
        final String photo = entry.getPhotoUri();
        runAsync(() -> environmentEntryDao.delete(entry),
            () -> combineEnvironmentPostActions(
                buildEnvironmentPhotoCleanup(photo, null),
                careDelegate.refreshCareRecommendationsAsync(entry.getPlantId())), callback, errorCallback);
    }

    public void deleteEnvironmentEntry(EnvironmentEntry entry, Runnable callback) {
        deleteEnvironmentEntry(entry, callback, null);
    }

    public List<EnvironmentEntry> getRecentEntriesForPlantSync(long plantId, int limit) {
        return environmentEntryDao.getRecentForPlant(plantId, limit);
    }

    public void getLatestLight(long plantId, Consumer<EnvironmentEntry> callback,
                               @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> environmentEntryDao.getLatestWithLight(plantId), callback, errorCallback);
    }

    public void getLatestLight(long plantId, Consumer<EnvironmentEntry> callback) {
        getLatestLight(plantId, callback, null);
    }

    @Nullable
    private String persistEnvironmentPhoto(@Nullable String uriString) {
        if (TextUtils.isEmpty(uriString)) {
            return null;
        }
        String normalized = uriString;
        if (!PhotoManager.isEnvironmentPhoto(context, uriString)) {
            try {
                Uri stored = PhotoManager.saveEnvironmentPhoto(context, Uri.parse(uriString));
                normalized = stored.toString();
            } catch (IOException e) {
                throw new RuntimeException("Failed to persist environment photo", e);
            }
        }
        return normalized;
    }

    @Nullable
    private Runnable buildEnvironmentPhotoCleanup(@Nullable String previousPhotoUri, @Nullable String newPhotoUri) {
        if (TextUtils.isEmpty(previousPhotoUri) || TextUtils.equals(previousPhotoUri, newPhotoUri)) {
            return null;
        }
        final String photoToDelete = previousPhotoUri;
        return () -> deleteEnvironmentPhoto(photoToDelete);
    }

    private void deleteEnvironmentPhoto(@Nullable String uriString) {
        if (TextUtils.isEmpty(uriString)) {
            return;
        }
        PhotoManager.deletePhoto(context, uriString);
    }

    private void attachArtificialLightEstimate(EnvironmentEntry entry) {
        if (entry == null) {
            return;
        }
        ArtificialLightEstimateSource.ArtificialLightEstimate estimate =
            artificialLightSource.estimate(entry.getPlantId());
        if (estimate.hasValues()) {
            entry.setArtificialDli(estimate.getDli());
            entry.setArtificialHours(estimate.getHours());
        } else {
            entry.setArtificialDli(null);
            entry.setArtificialHours(null);
        }
    }

    private Runnable combineEnvironmentPostActions(@Nullable Runnable cleanup, Runnable refresh) {
        if (cleanup == null) {
            return refresh;
        }
        return () -> {
            cleanup.run();
            refresh.run();
        };
    }
}
