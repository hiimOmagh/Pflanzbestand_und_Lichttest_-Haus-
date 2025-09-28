package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Locale;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;

/**
 * Room entity storing metadata about a plant's zone and its orientation.
 */
@Entity(
    foreignKeys = @ForeignKey(
        entity = Plant.class,
        parentColumns = "id",
        childColumns = "plantId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = "plantId", unique = true)
)
public class PlantZone {
    public static final String ORIENTATION_NORTH = "N";
    public static final String ORIENTATION_SOUTH = "S";
    public static final String ORIENTATION_EAST = "E";
    public static final String ORIENTATION_WEST = "W";

    @PrimaryKey(autoGenerate = true)
    private long id;
    private long plantId;
    @NonNull
    private String orientation = ORIENTATION_NORTH;
    @Nullable
    private String notes;
    private long createdAt;
    private long updatedAt;

    /**
     * Default constructor required by Room.
     */
    public PlantZone() {
    }

    /**
     * Creates a new zone entry for the provided plant.
     */
    @Ignore
    public PlantZone(long plantId, @NonNull String orientation, @Nullable String notes,
                     long createdAt, long updatedAt) {
        this.plantId = plantId;
        setOrientation(orientation);
        setNotes(notes);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Normalises the provided value to a supported orientation code.
     *
     * @param value user supplied orientation, may be {@code null}
     * @return canonical orientation code or {@code null} if none matches
     */
    @Nullable
    public static String normalizeOrientation(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.US);
        switch (upper) {
            case ORIENTATION_NORTH:
            case "NORTH":
                return ORIENTATION_NORTH;
            case ORIENTATION_SOUTH:
            case "SOUTH":
                return ORIENTATION_SOUTH;
            case ORIENTATION_EAST:
            case "EAST":
                return ORIENTATION_EAST;
            case ORIENTATION_WEST:
            case "WEST":
                return ORIENTATION_WEST;
            default:
                return null;
        }
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
    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(@NonNull String orientation) {
        String normalized = normalizeOrientation(orientation);
        if (normalized == null) {
            throw new IllegalArgumentException("Unsupported orientation: " + orientation);
        }
        this.orientation = normalized;
    }

    @Nullable
    public String getNotes() {
        return notes;
    }

    public void setNotes(@Nullable String notes) {
        if (notes == null) {
            this.notes = null;
            return;
        }
        String trimmed = notes.trim();
        this.notes = trimmed.isEmpty() ? null : trimmed;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
