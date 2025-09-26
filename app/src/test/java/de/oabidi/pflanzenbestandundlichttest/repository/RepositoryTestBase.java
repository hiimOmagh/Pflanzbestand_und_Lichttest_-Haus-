package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class RepositoryTestBase {
    protected Context context;
    protected Handler handler;
    protected ExecutorService ioExecutor;

    @Before
    public void setUpBase() {
        context = ApplicationProvider.getApplicationContext();
        handler = new Handler(Looper.getMainLooper());
        ioExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDownBase() {
        ioExecutor.shutdownNow();
    }

    protected void drainMainLooper() {
        ShadowLooper shadowLooper = Shadows.shadowOf(Looper.getMainLooper());
        shadowLooper.runToEndOfTasks();
    }
}
