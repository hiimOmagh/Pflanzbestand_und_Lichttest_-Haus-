package de.oabidi.pflanzenbestandundlichttest.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import de.oabidi.pflanzenbestandundlichttest.Plant;

/**
 * Room entity representing an additional photo that is associated with a {@link Plant}.
 */
@Entity(
    foreignKeys = @ForeignKey(
        entity = Plant.class,
        parentColumns = "id",
        childColumns = "plantId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index("plantId")
)
public class PlantPhoto {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long plantId;
    @NonNull
    private String uri;
    private long createdAt;

    /** Default constructor required by Room. */
    public PlantPhoto() {
        this.uri = "";
    }

    /**
     * Convenience constructor used when creating a new gallery photo record.
     *
     * @param plantId   identifier of the plant the photo belongs to
     * @param uri       string representation of the stored photo URI
     * @param createdAt timestamp in milliseconds when the photo was persisted
     */
    @Ignore
    public PlantPhoto(long plantId, @NonNull String uri, long createdAt) {
        this.plantId = plantId;
        this.uri = uri;
        this.createdAt = createdAt;
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

    @NonNull
    public String getUri() {
        return uri;
    }

    public void setUri(@NonNull String uri) {
        this.uri = uri;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
