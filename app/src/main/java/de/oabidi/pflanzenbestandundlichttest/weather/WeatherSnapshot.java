package de.oabidi.pflanzenbestandundlichttest.weather;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Immutable weather response containing irradiance and cloud cover information.
 */
public final class WeatherSnapshot {
    private final LocalDate date;
    private final float shortwaveRadiationMj;
    private final float meanCloudCover;

    public WeatherSnapshot(@NonNull LocalDate date, float shortwaveRadiationMj, float meanCloudCover) {
        this.date = date;
        this.shortwaveRadiationMj = shortwaveRadiationMj;
        this.meanCloudCover = meanCloudCover;
    }

    @NonNull
    public LocalDate getDate() {
        return date;
    }

    /** Short-wave radiation sum in MJ/m² for the given day. */
    public float getShortwaveRadiationMj() {
        return shortwaveRadiationMj;
    }

    /** Mean cloud cover percentage for the day. */
    public float getMeanCloudCover() {
        return meanCloudCover;
    }
}
