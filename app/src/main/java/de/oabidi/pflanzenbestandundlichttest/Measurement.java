package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity capturing a single light measurement for a plant.
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
public class Measurement {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long plantId;
    private long timeEpoch;
    private float luxAvg;
    private float ppfd;
    private float dli;

    /** Default constructor required by Room. */
    public Measurement() {
    }

    /**
     * Convenience constructor to create fully initialised measurement instances.
     */
    @Ignore
    public Measurement(long plantId, long timeEpoch, float luxAvg, float ppfd, float dli) {
        this.plantId = plantId;
        this.timeEpoch = timeEpoch;
        this.luxAvg = luxAvg;
        this.ppfd = ppfd;
        this.dli = dli;
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

    public float getLuxAvg() {
        return luxAvg;
    }

    public void setLuxAvg(float luxAvg) {
        this.luxAvg = luxAvg;
    }

    public float getPpfd() {
        return ppfd;
    }

    public void setPpfd(float ppfd) {
        this.ppfd = ppfd;
    }

    public float getDli() {
        return dli;
    }

    public void setDli(float dli) {
        this.dli = dli;
    }
}
