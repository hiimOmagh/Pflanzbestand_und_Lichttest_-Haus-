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
    private static final String DIR_PLANT_PHOTOS = "plant_photos";
    private static final String DIR_ENVIRONMENT_PHOTOS = "environment_photos";

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
        return savePhoto(context, sourceUri, DIR_PLANT_PHOTOS, "plant_");
    }

    @NonNull
    public static Uri saveEnvironmentPhoto(@NonNull Context context, @NonNull Uri sourceUri) throws IOException {
        return savePhoto(context, sourceUri, DIR_ENVIRONMENT_PHOTOS, "environment_");
    }

    public static boolean isEnvironmentPhoto(@NonNull Context context, @Nullable String uriString) {
        return isPhotoInDirectory(context, uriString, DIR_ENVIRONMENT_PHOTOS);
    }

    private static boolean isPhotoInDirectory(@NonNull Context context, @Nullable String uriString,
                                              @NonNull String directoryName) {
        if (uriString == null || uriString.isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(uriString);
        if (!"file".equals(uri.getScheme())) {
            return false;
        }
        String path = uri.getPath();
        if (path == null) {
            return false;
        }
        File directory = new File(context.getFilesDir(), directoryName);
        File file = new File(path);
        try {
            String dirPath = directory.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            return filePath.startsWith(dirPath);
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    private static Uri savePhoto(@NonNull Context context, @NonNull Uri sourceUri,
                                 @NonNull String directoryName, @NonNull String prefix) throws IOException {
        File directory = new File(context.getFilesDir(), directoryName);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create photo directory: " + directory);
        }

        String extension = extractExtension(context, sourceUri);
        if (extension.isEmpty()) {
            extension = ".jpg";
        }

        String baseName = prefix + System.currentTimeMillis();
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
