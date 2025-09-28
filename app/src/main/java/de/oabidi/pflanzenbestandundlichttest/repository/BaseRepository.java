package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import de.oabidi.pflanzenbestandundlichttest.core.data.db.PlantDatabase;

/**
 * Base class providing asynchronous execution helpers for repositories.
 */
abstract class BaseRepository {
    protected final Context context;
    protected final Handler mainHandler;
    protected final ExecutorService ioExecutor;

    BaseRepository(Context context, Handler mainHandler, ExecutorService ioExecutor) {
        this.context = context.getApplicationContext();
        this.mainHandler = mainHandler;
        this.ioExecutor = ioExecutor;
    }

    protected void runAsync(Runnable action, @Nullable Runnable callback,
                            @Nullable Consumer<Exception> errorCallback) {
        runAsync(action, (Supplier<Runnable>) null, callback, errorCallback);
    }

    protected void runAsync(Runnable action, @Nullable Supplier<Runnable> postActionSupplier,
                            @Nullable Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                action.run();
                Runnable suppliedPostAction = null;
                if (postActionSupplier != null) {
                    suppliedPostAction = postActionSupplier.get();
                }
                if (suppliedPostAction == null) {
                    if (callback != null) {
                        mainHandler.post(callback);
                    }
                } else {
                    final Runnable postAction = suppliedPostAction;
                    ioExecutor.execute(() -> {
                        try {
                            postAction.run();
                            if (callback != null) {
                                mainHandler.post(callback);
                            }
                        } catch (Exception e) {
                            if (errorCallback != null) {
                                mainHandler.post(() -> errorCallback.accept(e));
                            }
                        }
                    });
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    protected <T> void queryAsync(Supplier<T> query, @Nullable Consumer<T> callback,
                                  @Nullable Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                T result = query.get();
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(result));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }
}
