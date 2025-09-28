# Reminder notifications

The reminder system delivers watering and calibration prompts even when the app is not
running. The scheduler behaves differently depending on the Android version to satisfy the
platform's exact alarm policies.

## Scheduling overview

* **Android 12+ (API 31+)** – `ReminderScheduler` routes all requests through
  `ReminderWorkManager`, which uses WorkManager to enqueue one-time workers. When the app has the
  special `SCHEDULE_EXACT_ALARM` permission, the worker requests an exact run time so it fires at
  the
  user-selected minute. If the permission is unavailable, the job is marked expedited when the
  reminder is due immediately and otherwise runs as the next best effort window permitted by the OS.
* **Android 11 and lower** – the scheduler continues to delegate to `AlarmManager` using
  `setExactAndAllowWhileIdle`, matching the historical behaviour.

Regardless of the backend, every scheduling or cancellation triggers the widget refresh broadcast so
home-screen widgets remain in sync.

## Permissions

* `SCHEDULE_EXACT_ALARM` (Android 12+) grants the app access to exact scheduling. The system may
  revoke it during battery optimisations; `ReminderScheduler` automatically adapts without crashing
  and the app surfaces a Settings shortcut so users can re-enable it.
* `POST_NOTIFICATIONS` (Android 13+) remains a runtime permission. When the user denies it the
  `ReminderReceiver` queues the missed reminder and reschedules it for the next day, ensuring the
  task
  is not forgotten.
* `RECEIVE_BOOT_COMPLETED` lets the app reschedule any outstanding reminders after a reboot.

The app handles all permission failures gracefully: reminders are still persisted, widgets update,
`ReminderReceiver` keeps its snooze/update flows, and a denial simply changes how WorkManager fires
the task.
