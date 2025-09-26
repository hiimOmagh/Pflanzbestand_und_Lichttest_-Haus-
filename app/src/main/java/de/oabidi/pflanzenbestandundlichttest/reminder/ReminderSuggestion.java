package de.oabidi.pflanzenbestandundlichttest.reminder;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import de.oabidi.pflanzenbestandundlichttest.Plant;

/**
 * Room entity storing the most recent smart reminder suggestion for a plant.
 */
@Entity(
    tableName = "ReminderSuggestion",
    foreignKeys = @ForeignKey(
        entity = Plant.class,
        parentColumns = "id",
        childColumns = "plantId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "plantId", unique = true)}
)
public class ReminderSuggestion {
    @PrimaryKey
    private long plantId;
    private int suggestedIntervalDays;
    private long lastEvaluatedAt;
    private float confidenceScore;
    @Nullable
    private String explanation;

    public ReminderSuggestion() {
    }

    public long getPlantId() {
        return plantId;
    }

    public void setPlantId(long plantId) {
        this.plantId = plantId;
    }

    public int getSuggestedIntervalDays() {
        return suggestedIntervalDays;
    }

    public void setSuggestedIntervalDays(int suggestedIntervalDays) {
        this.suggestedIntervalDays = suggestedIntervalDays;
    }

    public long getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(long lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public float getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(float confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    @Nullable
    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(@Nullable String explanation) {
        this.explanation = explanation;
    }
}
