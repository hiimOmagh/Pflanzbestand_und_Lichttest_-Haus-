package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;

import androidx.room.TypeConverter;

public class Converters {
    @TypeConverter
    public static Uri fromString(String value) {
        return (value == null || value.isEmpty()) ? null : Uri.parse(value);
    }

    @TypeConverter
    public static String uriToString(Uri uri) {
        return (uri == null || uri.toString().isEmpty()) ? null : uri.toString();
    }
}
