package de.oabidi.pflanzenbestandundlichttest.data.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    /**
     * Copies the content referenced by {@code sourceUri} into the application's private gallery
     * directory and returns a {@link Uri} pointing to the stored file.
     *
     * @param context   context used to resolve the input URI
     * @param sourceUri original image URI that should be persisted
     * @return {@link Uri} of the copied image within the app's storage
     * @throws IOException if the source cannot be read or the destination cannot be written
     */
    @NonNull
    public static Uri savePlantPhoto(@NonNull Context context, @NonNull Uri sourceUri) throws IOException {
        File directory = new File(context.getFilesDir(), "plant_photos");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create plant photo directory: " + directory);
        }

        String extension = extractExtension(context, sourceUri);
        if (extension == null || extension.isEmpty()) {
            extension = ".jpg";
        }

        String baseName = "plant_" + System.currentTimeMillis();
        File destination;
        int suffix = 0;
        do {
            String candidate = suffix == 0 ? baseName + extension : baseName + "_" + suffix + extension;
            destination = new File(directory, candidate);
            suffix++;
        } while (destination.exists());

        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destination)) {
            if (in == null) {
                throw new IOException("Cannot open source URI: " + sourceUri);
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }

        return Uri.fromFile(destination);
    }

    @NonNull
    private static String extractFileName(@NonNull Uri uri) {
        String name = uri.getLastPathSegment();
        return name != null ? name : "";
    }

    private static String extractExtension(@NonNull Context context, @NonNull Uri uri) {
        String name = extractFileName(uri);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot);
        }
        String type = context.getContentResolver().getType(uri);
        if (type != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            if (ext != null && !ext.isEmpty()) {
                return "." + ext;
            }
        }
        return "";
    }
}
