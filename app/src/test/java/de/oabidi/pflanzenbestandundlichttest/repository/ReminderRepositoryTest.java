package de.oabidi.pflanzenbestandundlichttest.repository;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.Reminder;
import de.oabidi.pflanzenbestandundlichttest.ReminderDao;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestionDao;

@RunWith(RobolectricTestRunner.class)
public class ReminderRepositoryTest extends RepositoryTestBase {
    @Mock
    private ReminderDao reminderDao;
    @Mock
    private ReminderSuggestionDao reminderSuggestionDao;

    private ReminderRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new ReminderRepository(context, handler, ioExecutor, reminderDao, reminderSuggestionDao);
    }

    @Test
    public void getAllReminders_deliversResults() throws Exception {
        List<Reminder> reminders = Collections.singletonList(new Reminder(1000L, "Water", 1));
        when(reminderDao.getAll()).thenReturn(reminders);

        AtomicReference<List<Reminder>> result = new AtomicReference<>();

        repository.getAllReminders(list -> {
            result.set(list);
        });

        drainMainLooper();

        assertSame(reminders, result.get());
        verify(reminderDao).getAll();
    }

    @Test
    public void deleteReminderById_propagatesError() throws Exception {
        RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(reminderDao).deleteById(42L);

        AtomicReference<Exception> error = new AtomicReference<>();

        repository.deleteReminderById(42L, null, e -> {
            error.set(e);
        });

        drainMainLooper();

        assertSame(failure, error.get());
        verify(reminderDao).deleteById(42L);
    }

    @Test
    public void saveSuggestionSync_delegatesToDao() {
        ReminderSuggestion suggestion = new ReminderSuggestion();
        repository.saveSuggestionSync(suggestion);
        verify(reminderSuggestionDao).upsert(suggestion);
    }

    @Test
    public void getSuggestionForPlantSync_queriesDao() {
        ReminderSuggestion suggestion = new ReminderSuggestion();
        when(reminderSuggestionDao.getByPlantId(7L)).thenReturn(suggestion);

        ReminderSuggestion result = repository.getSuggestionForPlantSync(7L);

        assertSame(suggestion, result);
        verify(reminderSuggestionDao).getByPlantId(7L);
    }

    @Test
    public void getAllSuggestionsSync_queriesDao() {
        List<ReminderSuggestion> suggestions = Collections.singletonList(new ReminderSuggestion());
        when(reminderSuggestionDao.getAll()).thenReturn(suggestions);

        List<ReminderSuggestion> result = repository.getAllSuggestionsSync();

        assertSame(suggestions, result);
        verify(reminderSuggestionDao).getAll();
    }
}
