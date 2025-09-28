package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;

/**
 * Room entity capturing periodic environment measurements for a {@link Plant}.
 */
@Entity(
    foreignKeys = @ForeignKey(
        entity = Plant.class,
        parentColumns = "id",
        childColumns = "plantId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index("plantId"),
        @Index("timestamp")
    }
)
public class EnvironmentEntry {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long plantId;
    private long timestamp;
    @Nullable
    private Float temperature;
    @Nullable
    private Float humidity;
    @Nullable
    private Float soilMoisture;
    @Nullable
    private Float height;
    @Nullable
    private Float width;
    @Nullable
    private Float naturalDli;
    @Nullable
    private Float artificialDli;
    @Nullable
    private Float artificialHours;
    @Nullable
    private String notes;
    @Nullable
    private String photoUri;

    /**
     * Default constructor required by Room.
     */
    public EnvironmentEntry() {
    }

    /**
     * Convenience constructor to create a fully initialised environment entry.
     */
    @Ignore
    public EnvironmentEntry(long plantId, long timestamp, @Nullable Float temperature,
                            @Nullable Float humidity, @Nullable Float soilMoisture,
                            @Nullable Float height, @Nullable Float width,
                            @Nullable Float naturalDli, @Nullable Float artificialDli,
                            @Nullable Float artificialHours,
                            @Nullable String notes, @Nullable String photoUri) {
        this.plantId = plantId;
        this.timestamp = timestamp;
        this.temperature = temperature;
        this.humidity = humidity;
        this.soilMoisture = soilMoisture;
        this.height = height;
        this.width = width;
        this.naturalDli = naturalDli;
        this.artificialDli = artificialDli;
        this.artificialHours = artificialHours;
        this.notes = notes;
        this.photoUri = photoUri;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPlantId() {
        return plantId;
    }

    public void setPlantId(long plantId) {
        this.plantId = plantId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Nullable
    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(@Nullable Float temperature) {
        this.temperature = temperature;
    }

    @Nullable
    public Float getHumidity() {
        return humidity;
    }

    public void setHumidity(@Nullable Float humidity) {
        this.humidity = humidity;
    }

    @Nullable
    public Float getSoilMoisture() {
        return soilMoisture;
    }

    public void setSoilMoisture(@Nullable Float soilMoisture) {
        this.soilMoisture = soilMoisture;
    }

    @Nullable
    public Float getHeight() {
        return height;
    }

    public void setHeight(@Nullable Float height) {
        this.height = height;
    }

    @Nullable
    public Float getWidth() {
        return width;
    }

    public void setWidth(@Nullable Float width) {
        this.width = width;
    }

    @Nullable
    public Float getNaturalDli() {
        return naturalDli;
    }

    public void setNaturalDli(@Nullable Float naturalDli) {
        this.naturalDli = naturalDli;
    }

    @Nullable
    public Float getArtificialDli() {
        return artificialDli;
    }

    public void setArtificialDli(@Nullable Float artificialDli) {
        this.artificialDli = artificialDli;
    }

    @Nullable
    public Float getArtificialHours() {
        return artificialHours;
    }

    public void setArtificialHours(@Nullable Float artificialHours) {
        this.artificialHours = artificialHours;
    }

    @Nullable
    public String getNotes() {
        return notes;
    }

    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }

    @Nullable
    public String getPhotoUri() {
        return photoUri;
    }

    public void setPhotoUri(@Nullable String photoUri) {
        this.photoUri = photoUri;
    }
}
