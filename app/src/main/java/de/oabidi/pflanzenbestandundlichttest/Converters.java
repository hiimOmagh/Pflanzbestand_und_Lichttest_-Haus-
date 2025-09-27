package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;

import androidx.room.TypeConverter;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Converters {
    @TypeConverter
    public static Uri fromString(String value) {
        return (value == null || value.isEmpty()) ? null : Uri.parse(value);
    }

    @TypeConverter
    public static String uriToString(Uri uri) {
        return (uri == null || uri.toString().isEmpty()) ? null : uri.toString();
    }

    @TypeConverter
    public static List<String> fromJsonToStringList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(json);
            List<String> values = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                if (!array.isNull(i)) {
                    String value = array.optString(i, null);
                    if (value != null && !value.isEmpty()) {
                        values.add(value);
                    }
                }
            }
            return values;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to parse JSON list", e);
        }
    }

    @TypeConverter
    public static String fromStringListToJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        return array.toString();
    }

    @TypeConverter
    public static String fromCategory(SpeciesTarget.Category category) {
        return category != null ? category.name() : null;
    }

    @TypeConverter
    public static SpeciesTarget.Category toCategory(String value) {
        if (value == null || value.isEmpty()) {
            return SpeciesTarget.Category.OTHER;
        }
        try {
            return SpeciesTarget.Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            return SpeciesTarget.Category.OTHER;
        }
    }

    @TypeConverter
    public static Long fromLocalDate(LocalDate date) {
        return date != null ? date.toEpochDay() : null;
    }

    @TypeConverter
    public static LocalDate toLocalDate(Long epochDay) {
        return epochDay != null ? LocalDate.ofEpochDay(epochDay) : null;
    }
}
