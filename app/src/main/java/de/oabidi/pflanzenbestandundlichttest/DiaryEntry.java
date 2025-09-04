package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a single diary entry for a plant.
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
public class DiaryEntry {
    public static final String TYPE_WATER = "WATER";
    public static final String TYPE_FERTILIZE = "FERTILIZE";
    public static final String TYPE_PRUNE = "PRUNE";
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long plantId;
    private long timeEpoch;
    @NonNull
    private String type;
    private String note;
    private String photoUri;

    /** Default constructor required by Room. */
    public DiaryEntry() {
    }

    /**
     * Convenience constructor to create a fully initialised diary entry.
     */
    @Ignore
    public DiaryEntry(long plantId, long timeEpoch, @NonNull String type, String note) {
        this.plantId = plantId;
        this.timeEpoch = timeEpoch;
        this.type = type;
        this.note = note;
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

    public long getTimeEpoch() {
        return timeEpoch;
    }

    public void setTimeEpoch(long timeEpoch) {
        this.timeEpoch = timeEpoch;
    }

    @NonNull
    public String getType() {
        return type;
    }

    public void setType(@NonNull String type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPhotoUri() {
        return photoUri;
    }

    public void setPhotoUri(String photoUri) {
        this.photoUri = photoUri;
    }
}
