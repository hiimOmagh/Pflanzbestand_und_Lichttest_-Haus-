package de.oabidi.pflanzenbestandundlichttest.repository;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.Reminder;
import de.oabidi.pflanzenbestandundlichttest.ReminderDao;

@RunWith(RobolectricTestRunner.class)
public class ReminderRepositoryTest extends RepositoryTestBase {
    @Mock
    private ReminderDao reminderDao;

    private ReminderRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new ReminderRepository(context, handler, ioExecutor, reminderDao);
    }

    @Test
    public void getAllReminders_deliversResults() throws Exception {
        List<Reminder> reminders = Collections.singletonList(new Reminder(1000L, "Water", 1));
        when(reminderDao.getAll()).thenReturn(reminders);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Reminder>> result = new AtomicReference<>();

        repository.getAllReminders(list -> {
            result.set(list);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertSame(reminders, result.get());
        verify(reminderDao).getAll();
    }

    @Test
    public void deleteReminderById_propagatesError() throws Exception {
        RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(reminderDao).deleteById(42L);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        repository.deleteReminderById(42L, null, e -> {
            error.set(e);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertSame(failure, error.get());
        verify(reminderDao).deleteById(42L);
    }
}
