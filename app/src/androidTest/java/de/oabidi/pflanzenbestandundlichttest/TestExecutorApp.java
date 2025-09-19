package de.oabidi.pflanzenbestandundlichttest;

import java.util.concurrent.ExecutorService;

/**
 * Test application providing deterministic executors for instrumentation tests.
 */
public class TestExecutorApp extends PlantApp {
    private ExecutorService testExecutor;

    @Override
    public synchronized ExecutorService getIoExecutor() {
        if (testExecutor == null || testExecutor.isShutdown()) {
            testExecutor = TestExecutors.newImmediateExecutor();
        }
        return testExecutor;
    }

    @Override
    public synchronized void shutdownIoExecutor() {
        if (testExecutor != null) {
            testExecutor.shutdown();
            testExecutor = null;
        }
        super.shutdownIoExecutor();
    }
}
