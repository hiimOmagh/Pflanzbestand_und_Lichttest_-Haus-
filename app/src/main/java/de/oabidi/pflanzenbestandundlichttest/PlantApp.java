package de.oabidi.pflanzenbestandundlichttest;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Application subclass providing a single {@link PlantRepository} instance.
 */
public class PlantApp extends Application implements RepositoryProvider {
    private static final int IO_THREAD_COUNT = 2;
    private PlantRepository repository;
    private ExecutorService ioExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        SharedPreferences prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE);
        String theme = prefs.getString(SettingsKeys.KEY_THEME, "system");
        applyTheme(theme);

        // Ensure the repository is created and supplied to activities at startup.
        getRepository();
    }

    private void applyTheme(String value) {
        int mode;
        switch (value) {
            case "light":
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case "dark":
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /**
     * Returns the shared {@link PlantRepository} instance for the application.
     */
    @Override
    public synchronized PlantRepository getRepository() {
        if (repository == null) {
            repository = new PlantRepository(this);
        }
        return repository;
    }

    /** Returns the shared executor used by import/export components. */
    public synchronized ExecutorService getIoExecutor() {
        if (ioExecutor == null || ioExecutor.isShutdown()) {
            ioExecutor = Executors.newFixedThreadPool(IO_THREAD_COUNT);
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
