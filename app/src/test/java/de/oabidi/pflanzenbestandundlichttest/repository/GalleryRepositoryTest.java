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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhotoDao;

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

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<PlantPhoto>> result = new AtomicReference<>();

        repository.plantPhotosForPlant(4L, list -> {
            result.set(list);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals(photos.size(), result.get().size());
        verify(plantPhotoDao).getForPlant(4L);
    }

    @Test
    public void addPlantPhoto_withNullSourceReportsError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        repository.addPlantPhoto(1L, null, photo -> { }, e -> {
            error.set(e);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(error.get() instanceof IllegalArgumentException);
    }

    @Test
    public void deletePlantPhoto_invokesDaoAndCallback() throws Exception {
        PlantPhoto photo = new PlantPhoto(2L, "", System.currentTimeMillis());
        photo.setId(10L);
        CountDownLatch latch = new CountDownLatch(1);

        repository.deletePlantPhoto(photo, latch::countDown, null);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(plantPhotoDao).deleteForPlant(2L, 10L);
    }
}
