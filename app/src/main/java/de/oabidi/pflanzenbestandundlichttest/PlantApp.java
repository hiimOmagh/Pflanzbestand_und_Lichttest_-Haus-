package de.oabidi.pflanzenbestandundlichttest;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.material.color.DynamicColors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.common.util.ThemeUtils;

/**
 * Application subclass providing a single {@link PlantRepository} instance.
 */
public class PlantApp extends Application implements RepositoryProvider, ExecutorProvider {
    private static final int MIN_IO_THREAD_COUNT = 2;
    private PlantRepository repository;
    private ExecutorService ioExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        SharedPreferences prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE);
        String theme = prefs.getString(SettingsKeys.KEY_THEME, "system");
        ThemeUtils.applyNightMode(theme);

        // Ensure the repository is created and supplied to activities at startup.
        getRepository();
    }

    /**
     * Returns the shared {@link PlantRepository} instance for the application.
     */
    @Override
    public synchronized PlantRepository getRepository() {
        if (repository == null) {
            repository = new PlantRepository(this, getIoExecutor());
        }
        return repository;
    }

    /** Returns the shared executor used by import/export components. */
    @Override
    public synchronized ExecutorService getIoExecutor() {
        if (ioExecutor == null || ioExecutor.isShutdown()) {
            ioExecutor = createIoExecutor();
        }
        return ioExecutor;
    }

    /** Shuts down the shared executor service. */
    public synchronized void shutdownIoExecutor() {
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                    ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                ioExecutor = null;
            }
        }
    }

    private ExecutorService createIoExecutor() {
        int threadCount = Math.max(MIN_IO_THREAD_COUNT, Runtime.getRuntime().availableProcessors());
        return Executors.newFixedThreadPool(threadCount);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        shutdownIoExecutor();
    }

    /** Returns the {@link PlantApp} instance associated with the given context. */
    public static PlantApp from(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof PlantApp) {
            return (PlantApp) appContext;
        }
        throw new IllegalStateException("Application context is not PlantApp");
    }
}
