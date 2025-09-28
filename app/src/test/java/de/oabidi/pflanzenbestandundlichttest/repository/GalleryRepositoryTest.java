package de.oabidi.pflanzenbestandundlichttest.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import de.oabidi.pflanzenbestandundlichttest.core.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantPhotoDao;

@RunWith(RobolectricTestRunner.class)
public class GalleryRepositoryTest extends RepositoryTestBase {
    @Mock
    private PlantPhotoDao plantPhotoDao;

    private GalleryRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new GalleryRepository(context, handler, ioExecutor, plantPhotoDao);
    }

    @Test
    public void plantPhotosForPlant_returnsDaoValues() throws Exception {
        List<PlantPhoto> photos = Arrays.asList(new PlantPhoto(), new PlantPhoto());
        when(plantPhotoDao.getForPlant(4L)).thenReturn(photos);

        AtomicReference<List<PlantPhoto>> result = new AtomicReference<>();

        repository.plantPhotosForPlant(4L, list -> {
            result.set(list);
        });

        drainMainLooper();

        assertNotNull(result.get());
        assertEquals(photos.size(), result.get().size());
        verify(plantPhotoDao).getForPlant(4L);
    }

    @Test
    public void addPlantPhoto_withNullSourceReportsError() throws Exception {
        AtomicReference<Exception> error = new AtomicReference<>();

        repository.addPlantPhoto(1L, null, photo -> { }, e -> {
            error.set(e);
        });

        drainMainLooper();

        assertTrue(error.get() instanceof IllegalArgumentException);
    }

    @Test
    public void deletePlantPhoto_invokesDaoAndCallback() throws Exception {
        PlantPhoto photo = new PlantPhoto(2L, "", System.currentTimeMillis());
        photo.setId(10L);
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        repository.deletePlantPhoto(photo, () -> callbackInvoked.set(true), null);

        drainMainLooper();

        assertTrue(callbackInvoked.get());
        verify(plantPhotoDao).deleteForPlant(2L, 10L);
    }
}
