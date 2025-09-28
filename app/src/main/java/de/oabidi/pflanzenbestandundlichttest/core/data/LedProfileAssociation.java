package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;

/**
 * Join table linking LED profiles to the plants that use them.
 */
@Entity(
    tableName = "LedProfileAssociation",
    primaryKeys = {"plantId", "ledProfileId"},
    foreignKeys = {
        @ForeignKey(
            entity = Plant.class,
            parentColumns = "id",
            childColumns = "plantId",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = LedProfile.class,
            parentColumns = "id",
            childColumns = "ledProfileId",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index(value = "plantId"),
        @Index(value = "ledProfileId")
    }
)
public class LedProfileAssociation {
    private long plantId;
    private long ledProfileId;

    public LedProfileAssociation() {
    }

    @Ignore
    public LedProfileAssociation(long plantId, long ledProfileId) {
        this.plantId = plantId;
        this.ledProfileId = ledProfileId;
    }

    public long getPlantId() {
        return plantId;
    }

    public void setPlantId(long plantId) {
        this.plantId = plantId;
    }

    public long getLedProfileId() {
        return ledProfileId;
    }

    public void setLedProfileId(long ledProfileId) {
        this.ledProfileId = ledProfileId;
    }
}
