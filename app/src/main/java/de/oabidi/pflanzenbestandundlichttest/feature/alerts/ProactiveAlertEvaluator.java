package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryEntry;

/**
 * Evaluates environment readings and diary activity to derive proactive alerts.
 */
public class ProactiveAlertEvaluator {
    private static final long DIARY_INACTIVITY_THRESHOLD_MS = TimeUnit.DAYS.toMillis(7);

    private final CareRecommendationEngine careEngine = new CareRecommendationEngine();

    public List<ProactiveAlert> evaluate(@NonNull Plant plant,
                                         @Nullable PlantProfile profile,
                                         @Nullable List<EnvironmentEntry> environmentEntries,
                                         @Nullable DiaryEntry latestDiary,
                                         @NonNull Resources resources) {
        List<ProactiveAlert> alerts = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<EnvironmentEntry> safeEntries = environmentEntries == null
            ? Collections.emptyList()
            : environmentEntries;

        if (!safeEntries.isEmpty()) {
            List<CareRecommendationEngine.CareRecommendation> recommendations =
                careEngine.evaluate(profile, safeEntries, resources);
            for (CareRecommendationEngine.CareRecommendation recommendation : recommendations) {
                if (recommendation == null) {
                    continue;
                }
                ProactiveAlertTrigger trigger =
                    ProactiveAlertTrigger.fromRecommendationId(recommendation.getId());
                if (trigger == null) {
                    continue;
                }
                ProactiveAlert.Severity severity =
                    ProactiveAlert.Severity.fromCareSeverity(recommendation.getSeverity());
                if (severity == ProactiveAlert.Severity.INFO) {
                    continue;
                }
                CharSequence message = resolveMessage(recommendation, resources, plant.getName());
                if (message == null) {
                    continue;
                }
                alerts.add(new ProactiveAlert(plant, trigger, severity, message.toString(), now));
            }
        }

        if (shouldFlagDiaryInactivity(plant, latestDiary, now)) {
            String message = resources.getString(R.string.alert_diary_inactive_message, plant.getName());
            alerts.add(new ProactiveAlert(plant, ProactiveAlertTrigger.DIARY_INACTIVITY,
                ProactiveAlert.Severity.WARNING, message, now));
        }

        return alerts;
    }

    private boolean shouldFlagDiaryInactivity(@NonNull Plant plant,
                                              @Nullable DiaryEntry latestDiary,
                                              long now) {
        long mostRecent = latestDiary != null ? latestDiary.getTimeEpoch() : plant.getAcquiredAtEpoch();
        if (mostRecent <= 0L) {
            return false;
        }
        long delta = now - mostRecent;
        return delta >= DIARY_INACTIVITY_THRESHOLD_MS;
    }

    @Nullable
    private CharSequence resolveMessage(CareRecommendationEngine.CareRecommendation recommendation,
                                        Resources resources,
                                        String plantName) {
        Integer resId = recommendation.getMessageResId();
        Object[] args = recommendation.getFormatArgs();
        if (resId != null) {
            if (args != null && args.length > 0) {
                return resources.getString(resId, args);
            }
            return resources.getString(resId);
        }
        CharSequence message = recommendation.getMessage();
        if (message == null || message.length() == 0) {
            return resources.getString(R.string.alert_generic_message, plantName);
        }
        return message;
    }
}
