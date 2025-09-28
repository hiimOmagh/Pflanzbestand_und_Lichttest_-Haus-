package de.oabidi.pflanzenbestandundlichttest.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Abstraction for resolving artificial light estimates associated with a plant.
 */
public interface ArtificialLightEstimateSource {

    /**
     * Returns the latest artificial light estimate for the supplied plant identifier.
     */
    @NonNull
    ArtificialLightEstimate estimate(long plantId);

    /** Value object describing artificial light metrics. */
    final class ArtificialLightEstimate {
        @Nullable
        private final Float dli;
        @Nullable
        private final Float hours;

        public ArtificialLightEstimate(@Nullable Float dli, @Nullable Float hours) {
            this.dli = dli;
            this.hours = hours;
        }

        /** Returns the estimated artificial daily light integral, if available. */
        @Nullable
        public Float getDli() {
            return dli;
        }

        /** Returns the weighted photon hours emitted by the artificial lighting schedule. */
        @Nullable
        public Float getHours() {
            return hours;
        }

        /** Convenience factory returning an empty estimate. */
        @NonNull
        public static ArtificialLightEstimate empty() {
            return new ArtificialLightEstimate(null, null);
        }

        /** Indicates whether the estimate contains meaningful values. */
        public boolean hasValues() {
            return dli != null || hours != null;
        }
    }
}
