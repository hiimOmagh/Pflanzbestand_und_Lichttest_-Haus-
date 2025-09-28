package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity capturing dispatched proactive care alerts for history and deduplication.
 */
@Entity(indices = @Index(value = {"plantId", "triggerId"}))
public class ProactiveAlertLog {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long plantId;
    @NonNull
    private String triggerId;
    @NonNull
    private String severity;
    @NonNull
    private String message;
    private long createdAt;

    @Ignore
    public ProactiveAlertLog() {
    }

    public ProactiveAlertLog(long plantId,
                             @NonNull String triggerId,
                             @NonNull String severity,
                             @NonNull String message,
                             long createdAt) {
        this.plantId = plantId;
        this.triggerId = triggerId;
        this.severity = severity;
        this.message = message;
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
    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(@NonNull String triggerId) {
        this.triggerId = triggerId;
    }

    @NonNull
    public String getSeverity() {
        return severity;
    }

    public void setSeverity(@NonNull String severity) {
        this.severity = severity;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    public void setMessage(@NonNull String message) {
        this.message = message;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
