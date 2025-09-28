package de.oabidi.pflanzenbestandundlichttest.core.system;

import java.util.concurrent.ExecutorService;

/** Provides access to shared {@link ExecutorService} instances. */
public interface ExecutorProvider {
    ExecutorService getIoExecutor();
}
