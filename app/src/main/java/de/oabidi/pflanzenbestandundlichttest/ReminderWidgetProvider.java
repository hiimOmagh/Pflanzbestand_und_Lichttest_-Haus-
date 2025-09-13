package de.oabidi.pflanzenbestandundlichttest;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Home screen widget provider showing the next pending reminder.
 */
public class ReminderWidgetProvider extends AppWidgetProvider {
    private PlantRepository repository;

    public ReminderWidgetProvider() { }

    public ReminderWidgetProvider(PlantRepository repository) {
        this.repository = repository;
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
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

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_reminder);

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_NAVIGATE_MEASURE, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_measure_button, pendingIntent);

        PlantRepository repo = repository != null ? repository : new PlantRepository(context.getApplicationContext());
        final long now = System.currentTimeMillis();
        repo.getAllReminders(reminders -> {
            Reminder next = null;
            for (Reminder r : reminders) {
                if (r.getTriggerAt() >= now && (next == null || r.getTriggerAt() < next.getTriggerAt())) {
                    next = r;
                }
            }
            String message = next != null ? next.getMessage()
                : context.getString(R.string.widget_no_reminders);
            views.setTextViewText(R.id.widget_reminder_text, message);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }, e -> {
            views.setTextViewText(R.id.widget_reminder_text, context.getString(R.string.widget_no_reminders));
            appWidgetManager.updateAppWidget(appWidgetId, views);
        });
    }
}
