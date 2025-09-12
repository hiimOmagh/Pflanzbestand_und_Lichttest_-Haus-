package de.oabidi.pflanzenbestandundlichttest.data.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * Utility class for managing photo files associated with plants or diary entries.
 * Provides methods to delete images referenced by {@link Uri} or string paths.
 */
public final class PhotoManager {
    private static final String TAG = "PhotoManager";

    private PhotoManager() {
    }

    /**
     * Deletes the image referenced by the given {@link Uri} if possible.
     * Supports both {@code content://} and {@code file://} schemes.
     *
     * @param context context used to resolve the URI
     * @param uri     uri of the image to delete, may be {@code null}
     */
    public static void deletePhoto(@NonNull Context context, Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            String scheme = uri.getScheme();
            if ("content".equals(scheme)) {
                ContentResolver resolver = context.getContentResolver();
                resolver.delete(uri, null, null);
            } else {
                File f = new File(Objects.requireNonNull(uri.getPath()));
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to delete photo " + uri, e);
        }
    }

    /**
     * Deletes the image referenced by the string representation of a URI.
     *
     * @param context   context used to resolve the URI
     * @param uriString string representation of the photo URI, may be {@code null}
     */
    public static void deletePhoto(@NonNull Context context, String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            return;
        }
        deletePhoto(context, Uri.parse(uriString));
    }
}
