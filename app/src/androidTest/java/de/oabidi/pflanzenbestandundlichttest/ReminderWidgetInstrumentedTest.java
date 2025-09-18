package de.oabidi.pflanzenbestandundlichttest;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAppWidgetManager;
import org.robolectric.shadows.ShadowApplication;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link ReminderWidgetProvider}.
 */
@RunWith(RobolectricTestRunner.class)
public class ReminderWidgetInstrumentedTest {

    @Test
    public void widgetUpdatesWithScheduledReminderAndButtonLaunchesMeasure() throws Exception {
        Application application = ApplicationProvider.getApplicationContext();
        Context context = application;

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ShadowAppWidgetManager shadowAppWidgetManager = Shadows.shadowOf(appWidgetManager);
        int widgetId = shadowAppWidgetManager.createWidget(ReminderWidgetProvider.class, R.layout.widget_reminder);

        long triggerAt = System.currentTimeMillis() + 60000;
        Reminder reminder = new Reminder(triggerAt, "Water widget", 1);
        PlantDatabase db = PlantDatabase.getDatabase(context);
        final long[] reminderIdHolder = new long[1];
        CountDownLatch insertLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            reminderIdHolder[0] = db.reminderDao().insert(reminder);
            insertLatch.countDown();
        });
        assertTrue(insertLatch.await(2, TimeUnit.SECONDS));
        long reminderId = reminderIdHolder[0];
        ReminderScheduler.scheduleReminderAt(context, triggerAt, reminder.getMessage(), reminderId, reminder.getPlantId());

        CountDownLatch latch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        Intent update = new Intent(context, ReminderWidgetProvider.class);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        new ReminderWidgetProvider().onReceive(context, update);

        CountDownLatch updateLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(updateLatch::countDown);
        assertTrue(updateLatch.await(2, TimeUnit.SECONDS));

        android.view.View widgetView = shadowAppWidgetManager.getViewFor(widgetId);
        assertNotNull(widgetView);

        TextView reminderText = widgetView.findViewById(R.id.widget_reminder_text);
        assertNotNull(reminderText);
        assertEquals("Water widget", reminderText.getText().toString());

        Button measureButton = widgetView.findViewById(R.id.widget_measure_button);
        assertNotNull(measureButton);
        measureButton.performClick();

        ShadowApplication shadowApplication = Shadows.shadowOf(application);
        Intent launchIntent = shadowApplication.getNextStartedActivity();
        assertNotNull(launchIntent);
        assertEquals(MainActivity.class.getName(), Objects.requireNonNull(launchIntent.getComponent()).getClassName());
        assertTrue(launchIntent.getBooleanExtra(MainActivity.EXTRA_NAVIGATE_MEASURE, false));
    }
}
