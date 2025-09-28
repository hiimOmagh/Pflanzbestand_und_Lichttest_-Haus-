package de.oabidi.pflanzenbestandundlichttest.feature.gallery;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;

import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Utility responsible for loading {@link Uri}-backed images on a background executor and delivering
 * them to an {@link ImageView} once decoded. Instances cache bitmaps in-memory to avoid repeatedly
 * decoding the same file while users scroll through the gallery.
 */
public class PlantPhotoLoader {
    private static final int MAX_TEXTURE_SIZE = 2048;
    private static final int CACHE_SIZE_BYTES = 8 * 1024 * 1024; // 8 MiB

    private final Context appContext;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> bitmapCache;
    private final Set<String> inflightPrefetches = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PlantPhotoLoader(@NonNull Context context, @NonNull ExecutorService executor) {
        this.appContext = context.getApplicationContext();
        this.executor = executor;
        this.bitmapCache = new LruCache<>(CACHE_SIZE_BYTES) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    private static int calculateSampleSize(@NonNull BitmapFactory.Options options) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > PlantPhotoLoader.MAX_TEXTURE_SIZE || width > PlantPhotoLoader.MAX_TEXTURE_SIZE) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= PlantPhotoLoader.MAX_TEXTURE_SIZE
                && (halfWidth / inSampleSize) >= PlantPhotoLoader.MAX_TEXTURE_SIZE) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    /**
     * Loads the provided photo asynchronously into the given target view.
     */
    public void loadInto(@NonNull ImageView target, @NonNull String uriString) {
        target.setTag(R.id.tag_photo_uri, uriString);
        Bitmap cached = bitmapCache.get(uriString);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageDrawable(null);
        executor.execute(() -> {
            Bitmap bitmap = decodeBitmap(uriString);
            if (bitmap == null) {
                return;
            }
            bitmapCache.put(uriString, bitmap);
            mainHandler.post(() -> {
                Object tag = target.getTag(R.id.tag_photo_uri);
                if (Objects.equals(tag, uriString)) {
                    target.setImageBitmap(bitmap);
                }
            });
        });
    }

    /**
     * Preloads the provided photo into the in-memory cache to speed up subsequent binds.
     */
    public void prefetch(@NonNull String uriString) {
        if (bitmapCache.get(uriString) != null || !inflightPrefetches.add(uriString)) {
            return;
        }
        executor.execute(() -> {
            try {
                Bitmap bitmap = decodeBitmap(uriString);
                if (bitmap != null) {
                    bitmapCache.put(uriString, bitmap);
                }
            } finally {
                inflightPrefetches.remove(uriString);
            }
        });
    }

    /**
     * Clears any pending association with the supplied target.
     */
    public void clear(@NonNull ImageView target) {
        target.setTag(R.id.tag_photo_uri, null);
        target.setImageDrawable(null);
    }

    @Nullable
    private Bitmap decodeBitmap(@NonNull String uriString) {
        Uri uri = Uri.parse(uriString);
        ContentResolver resolver = appContext.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (IOException | SecurityException e) {
            return null;
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        decode.inSampleSize = calculateSampleSize(bounds);
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            return BitmapFactory.decodeStream(in, null, decode);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
