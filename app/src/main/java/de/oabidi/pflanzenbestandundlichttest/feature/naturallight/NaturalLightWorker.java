package de.oabidi.pflanzenbestandundlichttest.feature.naturallight;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.LightMath;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository.LastKnownLocation;
import de.oabidi.pflanzenbestandundlichttest.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.data.NaturalLightEstimate;
import de.oabidi.pflanzenbestandundlichttest.data.PlantZone;
import de.oabidi.pflanzenbestandundlichttest.repository.NaturalLightRepository;
import de.oabidi.pflanzenbestandundlichttest.weather.WeatherClient;
import de.oabidi.pflanzenbestandundlichttest.weather.WeatherSnapshot;

/**
 * Periodic worker computing natural light availability for plant zones.
 */
public class NaturalLightWorker extends Worker {
    private final WeatherClient weatherClient = new WeatherClient();

    public NaturalLightWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        PlantRepository repository = RepositoryProvider.getRepository(context);
        LastKnownLocation lastKnownLocation = repository.getLastKnownLocation();
        if (lastKnownLocation == null) {
            return Result.retry();
        }
        NaturalLightRepository naturalLightRepository = repository.naturalLightRepository();
        NaturalLightRepository.FetchMetadata previous = naturalLightRepository.getLastFetchMetadata();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (previous != null
            && previous.getDate().equals(today)
            && Math.abs(previous.getLatitude() - lastKnownLocation.getLatitude()) < 0.0001
            && Math.abs(previous.getLongitude() - lastKnownLocation.getLongitude()) < 0.0001) {
            return Result.success();
        }

        WeatherSnapshot snapshot;
        try {
            snapshot = weatherClient.fetchDailySnapshot(lastKnownLocation.getLatitude(),
                lastKnownLocation.getLongitude(), today);
        } catch (IOException | JSONException e) {
            return Result.retry();
        }

        List<PlantZone> zones = naturalLightRepository.getAllZonesSync();
        if (zones.isEmpty()) {
            naturalLightRepository.updateFetchMetadata(today, lastKnownLocation.getLatitude(),
                lastKnownLocation.getLongitude(), System.currentTimeMillis());
            return Result.success();
        }

        float baseDli = LightMath.dliFromShortwaveRadiation(snapshot.getShortwaveRadiationMj());
        baseDli = LightMath.applyCloudCover(baseDli, snapshot.getMeanCloudCover());
        double daylightHours = LightMath.daylightDurationHours(lastKnownLocation.getLatitude(), today);
        long now = System.currentTimeMillis();
        for (PlantZone zone : zones) {
            float orientationFactor = LightMath.orientationModifier(zone.getOrientation());
            NaturalLightEstimate estimate = new NaturalLightEstimate(
                zone.getId(), today, (float) daylightHours,
                baseDli * orientationFactor,
                snapshot.getShortwaveRadiationMj(),
                snapshot.getMeanCloudCover(),
                now);
            naturalLightRepository.insertEstimateSync(estimate);
        }
        naturalLightRepository.updateFetchMetadata(today, lastKnownLocation.getLatitude(),
            lastKnownLocation.getLongitude(), now);
        return Result.success();
    }
}
