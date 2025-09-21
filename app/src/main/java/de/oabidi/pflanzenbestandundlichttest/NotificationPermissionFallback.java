package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Persists information about reminders that could not be shown because the
 * POST_NOTIFICATIONS permission was missing. The app can use this data to
 * prompt the user later to grant the permission when it is safe to do so.
 */
public final class NotificationPermissionFallback {
    private static final String PREFS_NAME = "notification_permission_fallback";
    private static final String KEY_PENDING = "pending";
    private static final int MAX_ENTRIES = 10;

    private NotificationPermissionFallback() {
    }

    /**
     * Record a reminder that failed to post due to missing notification
     * permission. Only the last {@value #MAX_ENTRIES} entries are retained to
     * avoid unbounded growth.
     */
    public static void recordMissingPermission(Context context, long reminderId,
                                               long plantId, String message) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray existing = readExisting(prefs);
        JSONArray trimmed = new JSONArray();
        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item == null) {
                continue;
            }
            long storedId = item.optLong("reminderId", -1);
            if (storedId != reminderId) {
                trimmed.put(item);
            }
        }
        JSONObject entry = new JSONObject();
        try {
            entry.put("timestamp", System.currentTimeMillis());
            entry.put("reminderId", reminderId);
            entry.put("plantId", plantId);
            entry.put("message", message == null ? "" : message);
            trimmed.put(entry);
            trimmed = trimToMax(trimmed);
            prefs.edit().putString(KEY_PENDING, trimmed.toString()).apply();
        } catch (JSONException e) {
            Log.w("ReminderReceiver", "Failed to persist blocked reminder metadata", e);
        }
    }

    private static JSONArray readExisting(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_PENDING, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            Log.w("ReminderReceiver", "Corrupt blocked reminder metadata, resetting", e);
            return new JSONArray();
        }
    }

    private static JSONArray trimToMax(JSONArray input) {
        if (input.length() <= MAX_ENTRIES) {
            return input;
        }
        JSONArray result = new JSONArray();
        int start = Math.max(0, input.length() - MAX_ENTRIES);
        for (int i = start; i < input.length(); i++) {
            result.put(input.opt(i));
        }
        return result;
    }
}
