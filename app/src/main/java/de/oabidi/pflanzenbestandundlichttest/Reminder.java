package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a scheduled reminder notification.
 */
@Entity
public class Reminder {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long triggerAt;
    @NonNull
    private String message;

    /** Default constructor required by Room. */
    public Reminder() {
    }

    /**
     * Convenience constructor to create a reminder instance.
     *
     * @param triggerAt time in milliseconds when the reminder should fire
     * @param message   notification message to display
     */
    @Ignore
    public Reminder(long triggerAt, @NonNull String message) {
        this.triggerAt = triggerAt;
        this.message = message;
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

    @NonNull
    public String getMessage() {
        return message;
    }

    public void setMessage(@NonNull String message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        this.message = message;
    }
}
