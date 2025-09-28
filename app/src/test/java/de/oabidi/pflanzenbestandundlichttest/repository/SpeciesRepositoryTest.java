package de.oabidi.pflanzenbestandundlichttest.repository;

import static org.junit.Assert.assertEquals;
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
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;
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
        valid.setCategory(SpeciesTarget.Category.HERB);
        List<SpeciesTarget> daoResult = Arrays.asList(valid, null);
        when(speciesTargetDao.getByCategory(SpeciesTarget.Category.HERB)).thenReturn(daoResult);

        AtomicReference<List<PlantProfile>> result = new AtomicReference<>();

        repository.getPlantProfilesByCategory(SpeciesTarget.Category.HERB, profiles -> {
            result.set(profiles);
        });

        drainMainLooper();

        List<PlantProfile> profiles = result.get();
        assertEquals(1, profiles.size());
        assertEquals("test", profiles.get(0).getSpeciesKey());
        verify(speciesTargetDao).getByCategory(SpeciesTarget.Category.HERB);
    }

    @Test
    public void getSpeciesTargetSync_delegatesToDao() {
        repository.getSpeciesTargetSync("key");
        verify(speciesTargetDao).findBySpeciesKey("key");
    }
}
