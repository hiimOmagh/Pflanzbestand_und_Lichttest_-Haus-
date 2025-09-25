package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class RepositoryTestBase {
    protected Context context;
    protected Handler handler;
    protected ExecutorService ioExecutor;

    @Before
    public void setUpBase() {
        context = ApplicationProvider.getApplicationContext();
        handler = new ImmediateHandler();
        ioExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDownBase() {
        ioExecutor.shutdownNow();
    }

    protected static class ImmediateHandler extends Handler {
        ImmediateHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public boolean post(Runnable r) {
            r.run();
            return true;
        }
    }
}
