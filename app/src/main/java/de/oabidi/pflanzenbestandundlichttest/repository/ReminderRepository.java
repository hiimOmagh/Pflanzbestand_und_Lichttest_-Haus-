package de.oabidi.pflanzenbestandundlichttest.repository;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.Reminder;
import de.oabidi.pflanzenbestandundlichttest.ReminderDao;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestionDao;

/**
 * Repository encapsulating {@link Reminder} persistence operations.
 */
public class ReminderRepository extends BaseRepository {
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

    public void insertReminder(Reminder reminder, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(reminder, "reminder");
        runAsync(() -> {
            long id = reminderDao.insert(reminder);
            reminder.setId(id);
        }, callback, errorCallback);
    }

    public void insertReminder(Reminder reminder, Runnable callback) {
        insertReminder(reminder, callback, null);
    }

    public void updateReminder(Reminder reminder, Runnable callback, @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(reminder, "reminder");
        runAsync(() -> reminderDao.update(reminder), callback, errorCallback);
    }

    public void updateReminder(Reminder reminder, Runnable callback) {
        updateReminder(reminder, callback, null);
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
}
