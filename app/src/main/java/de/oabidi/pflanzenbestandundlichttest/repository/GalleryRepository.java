package de.oabidi.pflanzenbestandundlichttest.repository;

import static de.oabidi.pflanzenbestandundlichttest.data.util.PhotoManager.*;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhotoDao;
import de.oabidi.pflanzenbestandundlichttest.data.util.PhotoManager;

/**
 * Repository handling plant gallery operations.
 */
public class GalleryRepository extends BaseRepository {
    private final PlantPhotoDao plantPhotoDao;

    public GalleryRepository(Context context, Handler mainHandler, ExecutorService ioExecutor,
                             PlantPhotoDao plantPhotoDao) {
        super(context, mainHandler, ioExecutor);
        this.plantPhotoDao = Objects.requireNonNull(plantPhotoDao, "plantPhotoDao");
    }

    public void addPlantPhoto(long plantId, Uri sourceUri, Consumer<PlantPhoto> callback,
                              @Nullable Consumer<Exception> errorCallback) {
        if (sourceUri == null) {
            if (errorCallback != null) {
                mainHandler.post(() -> errorCallback.accept(new IllegalArgumentException("sourceUri")));
            }
            return;
        }
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Uri stored = savePlantPhoto(context, sourceUri);
                PlantPhoto photo = new PlantPhoto(plantId, stored.toString(), System.currentTimeMillis());
                long id = plantPhotoDao.insert(photo);
                photo.setId(id);
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(photo));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    public void addPlantPhoto(long plantId, Uri sourceUri, Consumer<PlantPhoto> callback) {
        addPlantPhoto(plantId, sourceUri, callback, null);
    }

    public void deletePlantPhoto(PlantPhoto photo, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        if (photo == null) {
            if (errorCallback != null) {
                mainHandler.post(() -> errorCallback.accept(new IllegalArgumentException("photo")));
            }
            return;
        }
        final String uri = photo.getUri();
        runAsync(() -> plantPhotoDao.deleteForPlant(photo.getPlantId(), photo.getId()),
            () -> deletePhoto(context, uri), callback, errorCallback);
    }

    public void deletePlantPhoto(PlantPhoto photo, Runnable callback) {
        deletePlantPhoto(photo, callback, null);
    }

    public void plantPhotosForPlant(long plantId, Consumer<List<PlantPhoto>> callback,
                                    @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> plantPhotoDao.getForPlant(plantId), callback, errorCallback);
    }

    public void plantPhotosForPlant(long plantId, Consumer<List<PlantPhoto>> callback) {
        plantPhotosForPlant(plantId, callback, null);
    }

    public List<PlantPhoto> getPlantPhotosForPlantSync(long plantId) {
        return plantPhotoDao.getForPlant(plantId);
    }
}
