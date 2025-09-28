package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import de.oabidi.pflanzenbestandundlichttest.Plant;

/**
 * Stores calibration factors associated with a specific plant.
 */
@Entity(
    tableName = "PlantCalibration",
    foreignKeys = @ForeignKey(
        entity = Plant.class,
        parentColumns = "id",
        childColumns = "plantId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "plantId", unique = true)}
)
public class PlantCalibration {
    @PrimaryKey
    @ColumnInfo(name = "plantId")
    private long plantId;

    @ColumnInfo(name = "ambientFactor")
    private float ambientFactor;

    @ColumnInfo(name = "cameraFactor")
    private float cameraFactor;

    public PlantCalibration(long plantId, float ambientFactor, float cameraFactor) {
        this.plantId = plantId;
        this.ambientFactor = ambientFactor;
        this.cameraFactor = cameraFactor;
    }

    public long getPlantId() {
        return plantId;
    }

    public void setPlantId(long plantId) {
        this.plantId = plantId;
    }

    public float getAmbientFactor() {
        return ambientFactor;
    }

    public void setAmbientFactor(float ambientFactor) {
        this.ambientFactor = ambientFactor;
    }

    public float getCameraFactor() {
        return cameraFactor;
    }

    public void setCameraFactor(float cameraFactor) {
        this.cameraFactor = cameraFactor;
    }

    @NonNull
    @Override
    public String toString() {
        return "PlantCalibration{" +
            "plantId=" + plantId +
            ", ambientFactor=" + ambientFactor +
            ", cameraFactor=" + cameraFactor +
            '}';
    }
}
