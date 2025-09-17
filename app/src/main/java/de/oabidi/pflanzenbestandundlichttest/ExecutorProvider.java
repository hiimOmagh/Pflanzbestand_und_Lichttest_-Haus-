package de.oabidi.pflanzenbestandundlichttest;

import java.util.concurrent.ExecutorService;

/** Provides access to shared {@link ExecutorService} instances. */
public interface ExecutorProvider {
    ExecutorService getIoExecutor();
}
