package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a scheduled reminder notification.
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
public class Reminder {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long triggerAt;
    private long plantId;
    private String message;

    /** Default constructor required by Room. */
    public Reminder() {
    }

    /**
     * Convenience constructor to create a reminder instance.
     *
     * @param triggerAt time in milliseconds when the reminder should fire
     * @param message   notification message to display
     * @param plantId   identifier of the related plant
     */
    @Ignore
    public Reminder(long triggerAt, @NonNull String message, long plantId) {
        this.triggerAt = triggerAt;
        this.message = message;
        this.plantId = plantId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTriggerAt() {
        return triggerAt;
    }

    public void setTriggerAt(long triggerAt) {
        this.triggerAt = triggerAt;
    }

    public long getPlantId() {
        return plantId;
    }

    public void setPlantId(long plantId) {
        this.plantId = plantId;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    public void setMessage(@NonNull String message) {
        this.message = message;
    }
}
