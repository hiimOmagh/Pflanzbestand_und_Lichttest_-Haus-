package de.oabidi.pflanzenbestandundlichttest;

/**
 * Simple value object holding aggregated DLI information for a time range.
 */
public class DliAggregate {
    private int dayCount;
    private float totalDli;

    /** Default constructor required by Room. */
    public DliAggregate() {
    }

    public int getDayCount() {
        return dayCount;
    }

    public void setDayCount(int dayCount) {
        this.dayCount = dayCount;
    }

    public float getTotalDli() {
        return totalDli;
    }

    public void setTotalDli(float totalDli) {
        this.totalDli = totalDli;
    }
}
