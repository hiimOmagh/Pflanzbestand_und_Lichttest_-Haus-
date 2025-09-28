package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.core.data.ProactiveAlertDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.ProactiveAlertLog;

/**
 * Repository persisting proactive care alert history used for deduplication and UI.
 */
public class ProactiveAlertRepository extends BaseRepository {
    private final ProactiveAlertDao alertDao;

    public ProactiveAlertRepository(Context context,
                                    Handler mainHandler,
                                    ExecutorService ioExecutor,
                                    ProactiveAlertDao alertDao) {
        super(context, mainHandler, ioExecutor);
        this.alertDao = Objects.requireNonNull(alertDao, "alertDao");
    }

    public long insertSync(ProactiveAlertLog log) {
        Objects.requireNonNull(log, "log");
        return alertDao.insert(log);
    }

    public ProactiveAlertLog latestForTrigger(long plantId, String triggerId) {
        return alertDao.latestForTrigger(plantId, triggerId);
    }

    public void getRecentAlerts(int limit, Consumer<List<ProactiveAlertLog>> callback) {
        getRecentAlerts(limit, callback, null);
    }

    public void getRecentAlerts(int limit,
                                Consumer<List<ProactiveAlertLog>> callback,
                                Consumer<Exception> errorCallback) {
        queryAsync(() -> {
            List<ProactiveAlertLog> alerts = alertDao.recent(limit);
            return alerts == null ? Collections.emptyList() : alerts;
        }, callback, errorCallback);
    }

    public void deleteOlderThan(long thresholdMillis) {
        alertDao.deleteOlderThan(thresholdMillis);
    }
}
