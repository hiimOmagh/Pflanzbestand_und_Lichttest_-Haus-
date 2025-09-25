package de.oabidi.pflanzenbestandundlichttest.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.DiaryDao;
import de.oabidi.pflanzenbestandundlichttest.DiaryEntry;

@RunWith(RobolectricTestRunner.class)
public class DiaryRepositoryTest extends RepositoryTestBase {
    @Mock
    private DiaryDao diaryDao;

    private DiaryRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new DiaryRepository(context, handler, ioExecutor, diaryDao);
    }

    @Test
    public void searchDiaryEntries_emptyQueryFallsBackToAll() throws Exception {
        List<DiaryEntry> allEntries = Collections.singletonList(new DiaryEntry());
        when(diaryDao.entriesForPlant(5L)).thenReturn(allEntries);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<DiaryEntry>> result = new AtomicReference<>();

        repository.searchDiaryEntries(5L, "", entries -> {
            result.set(entries);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertSame(allEntries, result.get());
        verify(diaryDao).entriesForPlant(5L);
    }

    @Test
    public void searchDiaryEntries_nonEmptyQueryUsesSearch() throws Exception {
        List<DiaryEntry> searchResults = Arrays.asList(new DiaryEntry(), new DiaryEntry());
        when(diaryDao.searchDiaryEntries(7L, "note*"))
            .thenReturn(searchResults);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<DiaryEntry>> result = new AtomicReference<>();

        repository.searchDiaryEntries(7L, "note", entries -> {
            result.set(entries);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(searchResults.size(), result.get().size());
        verify(diaryDao).searchDiaryEntries(7L, "note*");
    }
}
