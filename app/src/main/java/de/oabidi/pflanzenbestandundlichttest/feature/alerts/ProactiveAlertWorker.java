package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Background worker evaluating proactive care alerts on a schedule.
 */
public class ProactiveAlertWorker extends Worker {
    private static final String TAG = "ProactiveAlertWorker";

    public ProactiveAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
            if (!prefs.getBoolean(SettingsKeys.KEY_PROACTIVE_ALERTS_ENABLED, true)) {
                return Result.success();
            }
            PlantRepository repository = RepositoryProvider.getRepository(context);
            ProactiveAlertManager manager = new ProactiveAlertManager(context,
                repository,
                repository.environmentRepository(),
                repository.diaryRepository(),
                repository.speciesRepository(),
                repository.alertRepository());
            List<ProactiveAlert> alerts = manager.evaluateNewAlerts();
            repository.reminderSuggestionManager().refreshAllReminderSuggestionsSync();
            new ProactiveAlertNotifier(context).dispatch(alerts);
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "Failed to evaluate proactive alerts", e);
            return Result.retry();
        }
    }
}
