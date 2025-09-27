package de.oabidi.pflanzenbestandundlichttest.weather;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Lightweight HTTP client retrieving daily irradiance and cloud cover data from Open-Meteo.
 */
public class WeatherClient {
    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";

    @NonNull
    public WeatherSnapshot fetchDailySnapshot(double latitude, double longitude, @NonNull LocalDate date)
        throws IOException, JSONException {
        Uri uri = Uri.parse(BASE_URL).buildUpon()
            .appendQueryParameter("latitude", String.format(Locale.US, "%.4f", latitude))
            .appendQueryParameter("longitude", String.format(Locale.US, "%.4f", longitude))
            .appendQueryParameter("daily", "shortwave_radiation_sum,cloud_cover_mean")
            .appendQueryParameter("timezone", "auto")
            .appendQueryParameter("start_date", date.toString())
            .appendQueryParameter("end_date", date.toString())
            .build();
        HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        int responseCode = connection.getResponseCode();
        InputStream stream = responseCode >= 200 && responseCode < 300
            ? connection.getInputStream()
            : connection.getErrorStream();
        if (stream == null) {
            throw new IOException("Weather service returned status " + responseCode);
        }
        try (InputStream inputStream = stream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            if (responseCode >= 200 && responseCode < 300) {
                return parseResponse(response.toString(), date);
            }
            throw new IOException("Weather service error: " + response);
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private WeatherSnapshot parseResponse(@NonNull String json, @NonNull LocalDate date)
        throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject daily = root.getJSONObject("daily");
        JSONArray radiation = daily.getJSONArray("shortwave_radiation_sum");
        JSONArray cloudCover = daily.getJSONArray("cloud_cover_mean");
        if (radiation.length() == 0) {
            throw new JSONException("No radiation data returned");
        }
        float radiationValue = (float) radiation.getDouble(0);
        float cloudValue = cloudCover.length() > 0 ? (float) cloudCover.getDouble(0) : 0f;
        return new WeatherSnapshot(date, radiationValue, cloudValue);
    }
}
