package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.Reminder;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderDao;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderSuggestionDao;

/**
 * Repository encapsulating {@link Reminder} persistence operations.
 */
public class ReminderRepository extends BaseRepository {
    private static final String TAG = "ReminderRepository";
    private final ReminderDao reminderDao;
    private final ReminderSuggestionDao reminderSuggestionDao;

    public ReminderRepository(Context context, Handler mainHandler, ExecutorService ioExecutor,
                              ReminderDao reminderDao, ReminderSuggestionDao reminderSuggestionDao) {
        super(context, mainHandler, ioExecutor);
        this.reminderDao = Objects.requireNonNull(reminderDao, "reminderDao");
        this.reminderSuggestionDao = Objects.requireNonNull(reminderSuggestionDao, "reminderSuggestionDao");
    }

    public void getAllReminders(Consumer<List<Reminder>> callback, @Nullable Consumer<Exception> errorCallback) {
        queryAsync(reminderDao::getAll, callback, errorCallback);
    }

    public void getAllReminders(Consumer<List<Reminder>> callback) {
        getAllReminders(callback, null);
    }

    public void getRemindersForPlant(long plantId, Consumer<List<Reminder>> callback,
                                     @Nullable Consumer<Exception> errorCallback) {
        queryAsync(() -> reminderDao.getForPlant(plantId), callback, errorCallback);
    }

    public void getRemindersForPlant(long plantId, Consumer<List<Reminder>> callback) {
        getRemindersForPlant(plantId, callback, null);
    }

    public boolean insertReminder(Reminder reminder, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(reminder, "reminder");
        if (!validateReminderPlantId(reminder, errorCallback)) {
            return false;
        }
        runAsync(() -> {
            long id = reminderDao.insert(reminder);
            reminder.setId(id);
        }, callback, errorCallback);
        return true;
    }

    public boolean insertReminder(Reminder reminder, Runnable callback) {
        return insertReminder(reminder, callback, null);
    }

    public boolean updateReminder(Reminder reminder, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(reminder, "reminder");
        if (!validateReminderPlantId(reminder, errorCallback)) {
            return false;
        }
        runAsync(() -> reminderDao.update(reminder), callback, errorCallback);
        return true;
    }

    public boolean updateReminder(Reminder reminder, Runnable callback) {
        return updateReminder(reminder, callback, null);
    }

    public void deleteReminderById(long id, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        runAsync(() -> reminderDao.deleteById(id), callback, errorCallback);
    }

    public void deleteReminderById(long id, Runnable callback) {
        deleteReminderById(id, callback, null);
    }

    public List<Reminder> getRemindersForPlantSync(long plantId) {
        return reminderDao.getForPlant(plantId);
    }

    public ReminderSuggestion getSuggestionForPlantSync(long plantId) {
        return reminderSuggestionDao.getByPlantId(plantId);
    }

    public void saveSuggestionSync(ReminderSuggestion suggestion) {
        reminderSuggestionDao.upsert(suggestion);
    }

    public List<ReminderSuggestion> getAllSuggestionsSync() {
        return reminderSuggestionDao.getAll();
    }

    public void deleteSuggestionSync(long plantId) {
        reminderSuggestionDao.deleteByPlantId(plantId);
    }
    private boolean validateReminderPlantId(Reminder reminder,
                                            @Nullable Consumer<Exception> errorCallback) {
        long plantId = reminder.getPlantId();
        if (plantId > 0) {
            return true;
        }
        IllegalArgumentException exception =
            new IllegalArgumentException("Reminder must reference a valid plant id");
        Log.w(TAG, "Rejecting reminder without valid plant id: " + plantId);
        if (errorCallback != null) {
            mainHandler.post(() -> errorCallback.accept(exception));
            return false;
        }
        throw exception;
    }
}
