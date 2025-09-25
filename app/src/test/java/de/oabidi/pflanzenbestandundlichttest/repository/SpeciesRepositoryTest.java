package de.oabidi.pflanzenbestandundlichttest.repository;

import static org.junit.Assert.assertEquals;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTargetDao;

@RunWith(RobolectricTestRunner.class)
public class SpeciesRepositoryTest extends RepositoryTestBase {
    @Mock
    private SpeciesTargetDao speciesTargetDao;

    private SpeciesRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new SpeciesRepository(context, handler, ioExecutor, speciesTargetDao);
    }

    @Test
    public void getPlantProfilesByCategory_filtersNullProfiles() throws Exception {
        SpeciesTarget valid = new SpeciesTarget();
        valid.setSpeciesKey("test");
        valid.setCategory(SpeciesTarget.Category.HERBS);
        List<SpeciesTarget> daoResult = Arrays.asList(valid, null);
        when(speciesTargetDao.getByCategory(SpeciesTarget.Category.HERBS)).thenReturn(daoResult);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<PlantProfile>> result = new AtomicReference<>();

        repository.getPlantProfilesByCategory(SpeciesTarget.Category.HERBS, profiles -> {
            result.set(profiles);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        List<PlantProfile> profiles = result.get();
        assertEquals(1, profiles.size());
        assertEquals("test", profiles.get(0).getSpeciesKey());
        verify(speciesTargetDao).getByCategory(SpeciesTarget.Category.HERBS);
    }

    @Test
    public void getSpeciesTargetSync_delegatesToDao() {
        repository.getSpeciesTargetSync("key");
        verify(speciesTargetDao).findBySpeciesKey("key");
    }
}
