package de.oabidi.pflanzenbestandundlichttest.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntryDao;

@RunWith(RobolectricTestRunner.class)
public class EnvironmentRepositoryTest extends RepositoryTestBase {
    @Mock
    private EnvironmentEntryDao environmentEntryDao;
    @Mock
    private CareRecommendationDelegate careDelegate;

    private EnvironmentRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new EnvironmentRepository(context, handler, ioExecutor, environmentEntryDao, careDelegate);
    }

    @Test
    public void environmentEntriesForPlant_returnsDaoValues() throws Exception {
        List<EnvironmentEntry> entries = Arrays.asList(new EnvironmentEntry(), new EnvironmentEntry());
        when(environmentEntryDao.getForPlantOrdered(9L)).thenReturn(entries);

        AtomicReference<List<EnvironmentEntry>> result = new AtomicReference<>();

        repository.environmentEntriesForPlant(9L, list -> {
            result.set(list);
        });

        drainMainLooper();

        assertEquals(entries.size(), result.get().size());
        verify(environmentEntryDao).getForPlantOrdered(9L);
    }

    @Test
    public void insertEnvironmentEntry_invokesDelegateAndSetsId() throws Exception {
        EnvironmentEntry entry = new EnvironmentEntry(3L, System.currentTimeMillis(), null, null, null, null, null, null, null);
        when(environmentEntryDao.insert(entry)).thenReturn(15L);
        AtomicBoolean refreshed = new AtomicBoolean(false);
        when(careDelegate.refreshCareRecommendationsAsync(anyLong())).thenReturn(() -> refreshed.set(true));

        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        repository.insertEnvironmentEntry(entry, () -> callbackInvoked.set(true), null);

        drainMainLooper();

        assertTrue(callbackInvoked.get());
        assertEquals(15L, entry.getId());
        assertTrue(refreshed.get());
        verify(environmentEntryDao).insert(entry);
    }
}
