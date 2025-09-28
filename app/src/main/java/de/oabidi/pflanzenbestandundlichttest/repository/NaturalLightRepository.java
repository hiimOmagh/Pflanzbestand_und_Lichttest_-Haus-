package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.core.data.NaturalLightEstimate;
import de.oabidi.pflanzenbestandundlichttest.core.data.NaturalLightEstimateDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantZone;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantZoneDao;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Repository for persisting {@link NaturalLightEstimate} entries and caching metadata
 * about background fetches.
 */
public class NaturalLightRepository extends BaseRepository {
    private final NaturalLightEstimateDao naturalLightEstimateDao;
    private final PlantZoneDao plantZoneDao;
    private final SharedPreferences sharedPreferences;

    public NaturalLightRepository(@NonNull Context context,
                                  @NonNull android.os.Handler mainHandler,
                                  @NonNull java.util.concurrent.ExecutorService ioExecutor,
                                  @NonNull NaturalLightEstimateDao naturalLightEstimateDao,
                                  @NonNull PlantZoneDao plantZoneDao,
                                  @NonNull SharedPreferences sharedPreferences) {
        super(context, mainHandler, ioExecutor);
        this.naturalLightEstimateDao = Objects.requireNonNull(naturalLightEstimateDao, "naturalLightEstimateDao");
        this.plantZoneDao = Objects.requireNonNull(plantZoneDao, "plantZoneDao");
        this.sharedPreferences = Objects.requireNonNull(sharedPreferences, "sharedPreferences");
    }

    public void insertEstimateAsync(@NonNull NaturalLightEstimate estimate, @Nullable Runnable callback,
                                    @Nullable java.util.function.Consumer<Exception> errorCallback) {
        Objects.requireNonNull(estimate, "estimate");
        runAsync(() -> naturalLightEstimateDao.insertOrUpdate(estimate), callback, errorCallback);
    }

    public void insertEstimateSync(@NonNull NaturalLightEstimate estimate) {
        Objects.requireNonNull(estimate, "estimate");
        naturalLightEstimateDao.insertOrUpdate(estimate);
    }

    public List<PlantZone> getAllZonesSync() {
        return plantZoneDao.getAll();
    }

    public void deleteOlderThan(@NonNull LocalDate cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        naturalLightEstimateDao.deleteOlderThan(cutoff);
    }

    public void updateFetchMetadata(@NonNull LocalDate date, double latitude, double longitude, long timestamp) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_DATE, date.toEpochDay());
        editor.putLong(SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_LATITUDE, Double.doubleToRawLongBits(latitude));
        editor.putLong(SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_LONGITUDE, Double.doubleToRawLongBits(longitude));
        editor.putLong(SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_TIME, timestamp);
        editor.apply();
    }

    @Nullable
    public FetchMetadata getLastFetchMetadata() {
        if (!sharedPreferences.contains(SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_DATE)) {
            return null;
        }
        long epochDay = sharedPreferences.getLong(SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_DATE, Long.MIN_VALUE);
        double latitude = Double.longBitsToDouble(sharedPreferences.getLong(
            SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_LATITUDE, 0L));
        double longitude = Double.longBitsToDouble(sharedPreferences.getLong(
            SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_LONGITUDE, 0L));
        long timestamp = sharedPreferences.getLong(SettingsKeys.KEY_LAST_NATURAL_LIGHT_FETCH_TIME, 0L);
        return new FetchMetadata(LocalDate.ofEpochDay(epochDay), latitude, longitude, timestamp);
    }

    public static final class FetchMetadata {
        private final LocalDate date;
        private final double latitude;
        private final double longitude;
        private final long timestamp;

        FetchMetadata(LocalDate date, double latitude, double longitude, long timestamp) {
            this.date = date;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }

        public LocalDate getDate() {
            return date;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
