package de.oabidi.pflanzenbestandundlichttest;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.repository.ReminderRepository;

/**
 * Home screen widget provider showing the next pending reminder.
 */
public class ReminderWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_MARK_DONE =
        "de.oabidi.pflanzenbestandundlichttest.widget.ACTION_MARK_DONE";
    private static final String EXTRA_REMINDER_ID = "extra_reminder_id";
    private static final String PREFS_NAME = "ReminderWidgetProvider";
    private static final String PREF_STATUS_PREFIX = "status_";

    private ReminderRepository reminderRepository;

    public ReminderWidgetProvider() { }

    public ReminderWidgetProvider(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_MARK_DONE.equals(action)) {
            handleMarkDone(context, intent);
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = manager.getAppWidgetIds(
                new ComponentName(context, ReminderWidgetProvider.class));
            onUpdate(context, manager, appWidgetIds);
        }
    }
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int appWidgetId : appWidgetIds) {
            clearStatus(context, appWidgetId);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_reminder);

        Intent measureIntent = new Intent(context, MainActivity.class);
        measureIntent.putExtra(MainActivity.EXTRA_NAVIGATE_MEASURE, true);
        PendingIntent measurePending = PendingIntent.getActivity(context, 0, measureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_measure_button, measurePending);

        Intent logIntent = new Intent(context, MainActivity.class);
        logIntent.putExtra(MainActivity.EXTRA_NAVIGATE_DIARY, true);
        PendingIntent logPending = PendingIntent.getActivity(context, 1, logIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_log_button, logPending);

        ReminderRepository repo = reminderRepository != null
            ? reminderRepository
            : RepositoryProvider.getReminderRepository(context);
        final long now = System.currentTimeMillis();
        final String statusMessage = consumeStatus(context, appWidgetId);
        repo.getAllReminders(reminders -> {
            Reminder next = null;
            for (Reminder r : reminders) {
                if (r.getTriggerAt() >= now && (next == null || r.getTriggerAt() < next.getTriggerAt())) {
                    next = r;
                }
            }

            if (statusMessage != null) {
                views.setTextViewText(R.id.widget_reminder_text, statusMessage);
                views.setViewVisibility(R.id.widget_mark_done_button, View.GONE);
            } else if (next != null) {
                views.setTextViewText(R.id.widget_reminder_text, next.getMessage());
                views.setViewVisibility(R.id.widget_mark_done_button, View.VISIBLE);

                Intent doneIntent = new Intent(context, ReminderWidgetProvider.class);
                doneIntent.setAction(ACTION_MARK_DONE);
                doneIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                doneIntent.putExtra(EXTRA_REMINDER_ID, next.getId());
                doneIntent.setData(Uri.parse("widget://reminder/" + appWidgetId + "/" + next.getId()));
                PendingIntent donePending = PendingIntent.getBroadcast(
                    context,
                    0,
                    doneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                views.setOnClickPendingIntent(R.id.widget_mark_done_button, donePending);
            } else {
                views.setTextViewText(R.id.widget_reminder_text, context.getString(R.string.widget_no_reminders));
                views.setViewVisibility(R.id.widget_mark_done_button, View.GONE);
            }

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }, e -> {
            if (statusMessage != null) {
                views.setTextViewText(R.id.widget_reminder_text, statusMessage);
            } else {
                views.setTextViewText(R.id.widget_reminder_text, context.getString(R.string.widget_no_reminders));
            }
            views.setViewVisibility(R.id.widget_mark_done_button, View.GONE);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        });
    }

    private void handleMarkDone(Context context, Intent intent) {
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        long reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || reminderId < 0) {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                saveStatus(context, appWidgetId,
                    context.getString(R.string.widget_reminder_mark_done_error));
                requestWidgetUpdate(context, appWidgetId);
            }
            return;
        }

        ReminderRepository repo = reminderRepository != null
            ? reminderRepository
            : RepositoryProvider.getReminderRepository(context);
        repo.deleteReminderById(reminderId, () -> {
            saveStatus(context, appWidgetId, context.getString(R.string.widget_reminder_marked_done));
            ReminderScheduler.cancelReminder(context, reminderId);
        }, e -> {
            saveStatus(context, appWidgetId,
                context.getString(R.string.widget_reminder_mark_done_error));
            requestWidgetUpdate(context, appWidgetId);
        });
    }

    private void requestWidgetUpdate(Context context, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        updateAppWidget(context, manager, appWidgetId);
    }

    private void saveStatus(Context context, int appWidgetId, String status) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || status == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_STATUS_PREFIX + appWidgetId, status).apply();
    }

    private String consumeStatus(Context context, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = PREF_STATUS_PREFIX + appWidgetId;
        String status = prefs.getString(key, null);
        if (status != null) {
            prefs.edit().remove(key).apply();
        }
        return status;
    }

    private void clearStatus(Context context, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_STATUS_PREFIX + appWidgetId).apply();
    }
}
