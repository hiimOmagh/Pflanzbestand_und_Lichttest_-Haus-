package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;

import androidx.room.TypeConverter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;

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

    @TypeConverter
    public static Map<String, Float> fromJsonToCalibrationMap(String json) {
        Map<String, Float> factors = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return factors;
        }
        try {
            JSONObject object = new JSONObject(json);
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key == null || key.isEmpty()) {
                    continue;
                }
                if (!object.isNull(key)) {
                    double value = object.optDouble(key, Double.NaN);
                    if (!Double.isNaN(value)) {
                        factors.put(key, (float) value);
                    }
                }
            }
            return factors;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to parse calibration factor map", e);
        }
    }

    @TypeConverter
    public static String fromCalibrationMapToJson(Map<String, Float> factors) {
        if (factors == null || factors.isEmpty()) {
            return null;
        }
        JSONObject object = new JSONObject();
        try {
            for (Map.Entry<String, Float> entry : factors.entrySet()) {
                String key = entry.getKey();
                Float value = entry.getValue();
                if (key == null || key.isEmpty() || value == null) {
                    continue;
                }
                object.put(key, value.doubleValue());
            }
            return object.length() == 0 ? null : object.toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to serialise calibration factor map", e);
        }
    }

    @TypeConverter
    public static List<LedProfile.ScheduleEntry> fromJsonToSchedule(String json) {
        List<LedProfile.ScheduleEntry> schedule = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return schedule;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                LedProfile.ScheduleEntry entry = new LedProfile.ScheduleEntry();
                if (!object.isNull("startTime")) {
                    entry.setStartTime(object.optString("startTime", null));
                }
                if (!object.isNull("endTime")) {
                    entry.setEndTime(object.optString("endTime", null));
                }
                entry.setIntensityPercent(object.optInt("intensityPercent", 0));
                schedule.add(entry);
            }
            return schedule;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to parse LED schedule", e);
        }
    }

    @TypeConverter
    public static String fromScheduleToJson(List<LedProfile.ScheduleEntry> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return null;
        }
        JSONArray array = new JSONArray();
        try {
            for (LedProfile.ScheduleEntry entry : schedule) {
                if (entry == null) {
                    continue;
                }
                JSONObject object = new JSONObject();
                if (entry.getStartTime() != null && !entry.getStartTime().isEmpty()) {
                    object.put("startTime", entry.getStartTime());
                }
                if (entry.getEndTime() != null && !entry.getEndTime().isEmpty()) {
                    object.put("endTime", entry.getEndTime());
                }
                object.put("intensityPercent", entry.getIntensityPercent());
                array.put(object);
            }
            return array.length() == 0 ? null : array.toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to serialise LED schedule", e);
        }
    }
}
