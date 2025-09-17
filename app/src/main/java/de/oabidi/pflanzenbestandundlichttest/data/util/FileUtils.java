package de.oabidi.pflanzenbestandundlichttest.data.util;

import androidx.annotation.NonNull;

import java.io.File;

/** Utility methods for working with {@link File} instances. */
public final class FileUtils {
    private FileUtils() {
        // Utility class
    }

    public static void deleteRecursive(@NonNull File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        fileOrDirectory.delete();
    }
}
