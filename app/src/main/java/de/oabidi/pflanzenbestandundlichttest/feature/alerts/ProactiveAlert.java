package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import androidx.annotation.NonNull;

import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.data.ProactiveAlertLog;

/**
 * Value object describing a proactive alert for a specific plant.
 */
public class ProactiveAlert {
    private final Plant plant;
    private final ProactiveAlertTrigger trigger;
    private final Severity severity;
    private final String message;
    private final long createdAt;
    public ProactiveAlert(@NonNull Plant plant,
                          @NonNull ProactiveAlertTrigger trigger,
                          @NonNull Severity severity,
                          @NonNull String message,
                          long createdAt) {
        this.plant = plant;
        this.trigger = trigger;
        this.severity = severity;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Plant getPlant() {
        return plant;
    }

    public ProactiveAlertTrigger getTrigger() {
        return trigger;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public ProactiveAlertLog toLog() {
        return new ProactiveAlertLog(plant.getId(), trigger.getId(), severity.name(), message, createdAt);
    }

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL;

        public static Severity fromCareSeverity(
            de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.Severity severity) {
            if (severity == null) {
                return INFO;
            }
            switch (severity) {
                case CRITICAL:
                    return CRITICAL;
                case WARNING:
                    return WARNING;
                default:
                    return INFO;
            }
        }
    }
}
