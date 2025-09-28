package de.oabidi.pflanzenbestandundlichttest.data;

import androidx.annotation.Nullable;

/**
 * Value object bundling natural and artificial light information for a plant.
 */
public class LightSummary {

    @Nullable
    private final Float naturalDli;
    @Nullable
    private final Long naturalTimestamp;
    @Nullable
    private final Float artificialDli;
    @Nullable
    private final Long artificialTimestamp;

    public LightSummary(@Nullable Float naturalDli, @Nullable Long naturalTimestamp,
                        @Nullable Float artificialDli, @Nullable Long artificialTimestamp) {
        this.naturalDli = naturalDli;
        this.naturalTimestamp = naturalTimestamp;
        this.artificialDli = artificialDli;
        this.artificialTimestamp = artificialTimestamp;
    }

    @Nullable
    public Float getNaturalDli() {
        return naturalDli;
    }

    @Nullable
    public Long getNaturalTimestamp() {
        return naturalTimestamp;
    }

    @Nullable
    public Float getArtificialDli() {
        return artificialDli;
    }

    @Nullable
    public Long getArtificialTimestamp() {
        return artificialTimestamp;
    }
}
