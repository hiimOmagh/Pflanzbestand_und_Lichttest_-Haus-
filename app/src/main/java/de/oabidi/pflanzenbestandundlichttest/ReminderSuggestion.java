package de.oabidi.pflanzenbestandundlichttest;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity storing the most recent smart reminder suggestion for a plant.
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
public class ReminderSuggestion {
    @PrimaryKey
    private long plantId;
    private int suggestedIntervalDays;
    private int baselineIntervalDays;
    private int adjustmentDays;
    private long updatedAt;
    @Nullable
    private String baselineSource;
    @Nullable
    private String environmentSignal;
    @Nullable
    private Float averageSoilMoisture;
    @Nullable
    private String explanation;
    @Nullable
    private String algorithmVersion;

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

    public int getBaselineIntervalDays() {
        return baselineIntervalDays;
    }

    public void setBaselineIntervalDays(int baselineIntervalDays) {
        this.baselineIntervalDays = baselineIntervalDays;
    }

    public int getAdjustmentDays() {
        return adjustmentDays;
    }

    public void setAdjustmentDays(int adjustmentDays) {
        this.adjustmentDays = adjustmentDays;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Nullable
    public String getBaselineSource() {
        return baselineSource;
    }

    public void setBaselineSource(@Nullable String baselineSource) {
        this.baselineSource = baselineSource;
    }

    @Nullable
    public String getEnvironmentSignal() {
        return environmentSignal;
    }

    public void setEnvironmentSignal(@Nullable String environmentSignal) {
        this.environmentSignal = environmentSignal;
    }

    @Nullable
    public Float getAverageSoilMoisture() {
        return averageSoilMoisture;
    }

    public void setAverageSoilMoisture(@Nullable Float averageSoilMoisture) {
        this.averageSoilMoisture = averageSoilMoisture;
    }

    @Nullable
    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(@Nullable String explanation) {
        this.explanation = explanation;
    }

    @Nullable
    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(@Nullable String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }
}
