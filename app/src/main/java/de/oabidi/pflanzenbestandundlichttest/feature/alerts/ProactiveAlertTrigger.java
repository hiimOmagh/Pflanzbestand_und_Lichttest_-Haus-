package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumerates proactive alert triggers and maps care recommendation identifiers.
 */
public enum ProactiveAlertTrigger {
    TEMPERATURE_LOW("temperature_low"),
    TEMPERATURE_HIGH("temperature_high"),
    HUMIDITY_LOW("humidity_low"),
    HUMIDITY_HIGH("humidity_high"),
    DIARY_INACTIVITY("diary_inactivity");

    private static final Map<String, ProactiveAlertTrigger> BY_ID = new HashMap<>();

    static {
        for (ProactiveAlertTrigger trigger : values()) {
            BY_ID.put(trigger.id, trigger);
        }
    }

    private final String id;

    ProactiveAlertTrigger(String id) {
        this.id = id;
    }

    @Nullable
    public static ProactiveAlertTrigger fromRecommendationId(String recommendationId) {
        if (recommendationId == null) {
            return null;
        }
        return BY_ID.get(recommendationId);
    }

    public String getId() {
        return id;
    }
}
