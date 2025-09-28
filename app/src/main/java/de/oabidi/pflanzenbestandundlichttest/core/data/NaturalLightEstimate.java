package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import java.time.LocalDate;

/**
 * Stores derived daily light integral estimates for a plant zone.
 */
@Entity(
    primaryKeys = {"zoneId", "date"},
    foreignKeys = @ForeignKey(
        entity = PlantZone.class,
        parentColumns = "id",
        childColumns = "zoneId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = {"zoneId", "date"}, unique = true)
)
public class NaturalLightEstimate {
    private long zoneId;
    @NonNull
    private LocalDate date = LocalDate.now();
    private float daylightHours;
    private float dli;
    private float irradianceMj;
    private float cloudCover;
    @ColumnInfo(defaultValue = "0")
    private long createdAt;

    public NaturalLightEstimate() {
    }

    @Ignore
    public NaturalLightEstimate(long zoneId, @NonNull LocalDate date, float daylightHours,
                                float dli, float irradianceMj, float cloudCover, long createdAt) {
        this.zoneId = zoneId;
        this.date = date;
        this.daylightHours = daylightHours;
        this.dli = dli;
        this.irradianceMj = irradianceMj;
        this.cloudCover = cloudCover;
        this.createdAt = createdAt;
    }

    public long getZoneId() {
        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

    @NonNull
    public LocalDate getDate() {
        return date;
    }

    public void setDate(@NonNull LocalDate date) {
        this.date = date;
    }

    public float getDaylightHours() {
        return daylightHours;
    }

    public void setDaylightHours(float daylightHours) {
        this.daylightHours = daylightHours;
    }

    public float getDli() {
        return dli;
    }

    public void setDli(float dli) {
        this.dli = dli;
    }

    public float getIrradianceMj() {
        return irradianceMj;
    }

    public void setIrradianceMj(float irradianceMj) {
        this.irradianceMj = irradianceMj;
    }

    public float getCloudCover() {
        return cloudCover;
    }

    public void setCloudCover(float cloudCover) {
        this.cloudCover = cloudCover;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
