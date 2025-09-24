package de.oabidi.pflanzenbestandundlichttest;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
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
    public void widgetButtonsLaunchExpectedActivities() throws Exception {
        Application application = ApplicationProvider.getApplicationContext();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(application);
        ShadowAppWidgetManager shadowAppWidgetManager = Shadows.shadowOf(appWidgetManager);
        int widgetId = shadowAppWidgetManager.createWidget(ReminderWidgetProvider.class, R.layout.widget_reminder);

        long triggerAt = System.currentTimeMillis() + 60000;
        Reminder reminder = new Reminder(triggerAt, "Water widget", 1);
        PlantDatabase db = PlantDatabase.getDatabase(application);
        final long[] reminderIdHolder = new long[1];
        CountDownLatch insertLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            reminderIdHolder[0] = db.reminderDao().insert(reminder);
            insertLatch.countDown();
        });
        assertTrue(insertLatch.await(2, TimeUnit.SECONDS));
        long reminderId = reminderIdHolder[0];
        ReminderScheduler.scheduleReminderAt(application, triggerAt, reminder.getMessage(), reminderId, reminder.getPlantId());

        CountDownLatch latch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        Intent update = new Intent(application, ReminderWidgetProvider.class);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        new ReminderWidgetProvider().onReceive(application, update);

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

        Button logButton = widgetView.findViewById(R.id.widget_log_button);
        assertNotNull(logButton);
        logButton.performClick();

        Intent logIntent = shadowApplication.getNextStartedActivity();
        assertNotNull(logIntent);
        assertEquals(MainActivity.class.getName(), Objects.requireNonNull(logIntent.getComponent()).getClassName());
        assertTrue(logIntent.getBooleanExtra(MainActivity.EXTRA_NAVIGATE_DIARY, false));

        Button markDoneButton = widgetView.findViewById(R.id.widget_mark_done_button);
        assertNotNull(markDoneButton);
        assertEquals(View.VISIBLE, markDoneButton.getVisibility());
    }

    @Test
    public void markDoneBroadcastUpdatesWidgetState() throws Exception {

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ApplicationProvider.<Application>getApplicationContext());
        ShadowAppWidgetManager shadowAppWidgetManager = Shadows.shadowOf(appWidgetManager);
        int widgetId = shadowAppWidgetManager.createWidget(ReminderWidgetProvider.class, R.layout.widget_reminder);

        long triggerAt = System.currentTimeMillis() + 60000;
        Reminder reminder = new Reminder(triggerAt, "Mist plants", 2);
        PlantDatabase db = PlantDatabase.getDatabase(ApplicationProvider.<Application>getApplicationContext());
        final long[] reminderIdHolder = new long[1];
        CountDownLatch insertLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            reminderIdHolder[0] = db.reminderDao().insert(reminder);
            insertLatch.countDown();
        });
        assertTrue(insertLatch.await(2, TimeUnit.SECONDS));
        long reminderId = reminderIdHolder[0];
        ReminderScheduler.scheduleReminderAt(ApplicationProvider.<Application>getApplicationContext(), triggerAt, reminder.getMessage(), reminderId, reminder.getPlantId());

        CountDownLatch readyLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(readyLatch::countDown);
        assertTrue(readyLatch.await(2, TimeUnit.SECONDS));

        Intent update = new Intent(ApplicationProvider.<Application>getApplicationContext(), ReminderWidgetProvider.class);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        new ReminderWidgetProvider().onReceive(ApplicationProvider.<Application>getApplicationContext(), update);

        Shadows.shadowOf(Looper.getMainLooper()).idle();
        CountDownLatch initialUpdateLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(initialUpdateLatch::countDown);
        assertTrue(initialUpdateLatch.await(2, TimeUnit.SECONDS));

        android.view.View widgetView = shadowAppWidgetManager.getViewFor(widgetId);
        assertNotNull(widgetView);

        Button markDoneButton = widgetView.findViewById(R.id.widget_mark_done_button);
        assertNotNull(markDoneButton);
        markDoneButton.performClick();

        Shadows.shadowOf(Looper.getMainLooper()).idle();

        CountDownLatch completionLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(completionLatch::countDown);
        assertTrue(completionLatch.await(2, TimeUnit.SECONDS));

        android.view.View updatedView = shadowAppWidgetManager.getViewFor(widgetId);
        assertNotNull(updatedView);
        TextView reminderText = updatedView.findViewById(R.id.widget_reminder_text);
        assertNotNull(reminderText);
        assertEquals(ApplicationProvider.<Application>getApplicationContext().getString(R.string.widget_reminder_marked_done), reminderText.getText().toString());

        CountDownLatch queryLatch = new CountDownLatch(1);
        final boolean[] hasReminder = new boolean[1];
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            hasReminder[0] = !db.reminderDao().getAll().isEmpty();
            queryLatch.countDown();
        });
        assertTrue(queryLatch.await(2, TimeUnit.SECONDS));
        assertFalse(hasReminder[0]);

        Intent refreshIntent = new Intent(ApplicationProvider.<Application>getApplicationContext(), ReminderWidgetProvider.class);
        refreshIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        new ReminderWidgetProvider().onReceive(ApplicationProvider.<Application>getApplicationContext(), refreshIntent);

        Shadows.shadowOf(Looper.getMainLooper()).idle();
        CountDownLatch refreshLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(refreshLatch::countDown);
        assertTrue(refreshLatch.await(2, TimeUnit.SECONDS));

        android.view.View refreshedView = shadowAppWidgetManager.getViewFor(widgetId);
        assertNotNull(refreshedView);
        TextView refreshedText = refreshedView.findViewById(R.id.widget_reminder_text);
        assertNotNull(refreshedText);
        assertEquals(ApplicationProvider.<Application>getApplicationContext().getString(R.string.widget_no_reminders), refreshedText.getText().toString());
    }

    @Test
    public void statusMessageHidesMarkDoneButtonAfterQuickAction() throws Exception {

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ApplicationProvider.<Application>getApplicationContext());
        ShadowAppWidgetManager shadowAppWidgetManager = Shadows.shadowOf(appWidgetManager);
        int widgetId = shadowAppWidgetManager.createWidget(ReminderWidgetProvider.class, R.layout.widget_reminder);

        long triggerAt = System.currentTimeMillis() + 60000;
        Reminder reminder = new Reminder(triggerAt, "Fertilize", 3);
        PlantDatabase db = PlantDatabase.getDatabase(ApplicationProvider.<Application>getApplicationContext());
        CountDownLatch insertLatch = new CountDownLatch(1);
        final long[] reminderIdHolder = new long[1];
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            reminderIdHolder[0] = db.reminderDao().insert(reminder);
            insertLatch.countDown();
        });
        assertTrue(insertLatch.await(2, TimeUnit.SECONDS));
        long reminderId = reminderIdHolder[0];
        ReminderScheduler.scheduleReminderAt(ApplicationProvider.<Application>getApplicationContext(), triggerAt, reminder.getMessage(), reminderId, reminder.getPlantId());

        CountDownLatch readyLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(readyLatch::countDown);
        assertTrue(readyLatch.await(2, TimeUnit.SECONDS));

        Intent update = new Intent(ApplicationProvider.<Application>getApplicationContext(), ReminderWidgetProvider.class);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        new ReminderWidgetProvider().onReceive(ApplicationProvider.<Application>getApplicationContext(), update);

        Shadows.shadowOf(Looper.getMainLooper()).idle();
        CountDownLatch initialLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(initialLatch::countDown);
        assertTrue(initialLatch.await(2, TimeUnit.SECONDS));

        android.view.View widgetView = shadowAppWidgetManager.getViewFor(widgetId);
        assertNotNull(widgetView);
        Button markDoneButton = widgetView.findViewById(R.id.widget_mark_done_button);
        assertNotNull(markDoneButton);
        markDoneButton.performClick();

        Shadows.shadowOf(Looper.getMainLooper()).idle();
        CountDownLatch completionLatch = new CountDownLatch(1);
        PlantDatabase.databaseWriteExecutor.execute(completionLatch::countDown);
        assertTrue(completionLatch.await(2, TimeUnit.SECONDS));

        android.view.View updatedView = shadowAppWidgetManager.getViewFor(widgetId);
        assertNotNull(updatedView);
        TextView reminderText = updatedView.findViewById(R.id.widget_reminder_text);
        assertNotNull(reminderText);
        assertEquals(ApplicationProvider.<Application>getApplicationContext().getString(R.string.widget_reminder_marked_done), reminderText.getText().toString());

        Button markDoneUpdated = updatedView.findViewById(R.id.widget_mark_done_button);
        assertNotNull(markDoneUpdated);
        assertEquals(View.GONE, markDoneUpdated.getVisibility());

        Button measureButton = updatedView.findViewById(R.id.widget_measure_button);
        Button logButton = updatedView.findViewById(R.id.widget_log_button);
        assertNotNull(measureButton);
        assertNotNull(logButton);
        assertEquals(View.VISIBLE, measureButton.getVisibility());
        assertEquals(View.VISIBLE, logButton.getVisibility());
    }
}
