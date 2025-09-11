package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.net.Uri;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Unit tests for {@link PlantEditPresenterImpl} verifying validation and persistence behaviour.
 */
@RunWith(RobolectricTestRunner.class)
public class PlantEditPresenterTest {
    private FakeView view;
    private RecordingRepository repository;
    private PlantEditPresenter presenter;

    private static class FakeView implements PlantEditView {
        String name = "";
        String species = null;
        String location = null;
        String notes = null;
        long acquired = 123L;
        Uri photo;
        long id;
        boolean nameErrorShown;
        Plant finished;
        String errorMessage;

        @Override
        public String getName() { return name; }
        @Override
        public String getSpecies() { return species; }
        @Override
        public String getLocation() { return location; }
        @Override
        public String getNotes() { return notes; }
        @Override
        public long getAcquiredEpoch() { return acquired; }
        @Override
        public Uri getPhotoUri() { return photo; }
        @Override
        public long getPlantId() { return id; }
        @Override
        public void showNameError() { nameErrorShown = true; }
        @Override
        public void finishWithResult(Plant plant) { finished = plant; }
        @Override
        public void showError(String message) { errorMessage = message; }
    }

    private static class RecordingRepository extends PlantRepository {
        Plant inserted;
        Plant updated;
        RecordingRepository(Context context) { super(context); }
        @Override
        public Future<?> insert(Plant plant, Runnable cb) {
            inserted = plant;
            if (cb != null) { cb.run(); }
            return CompletableFuture.completedFuture(null);
        }
        @Override
        public Future<?> update(Plant plant, Runnable cb) {
            updated = plant;
            if (cb != null) { cb.run(); }
            return CompletableFuture.completedFuture(null);
        }
    }

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        view = new FakeView();
        repository = new RecordingRepository(context);
        presenter = new PlantEditPresenterImpl(view, repository, context);
    }

    @Test
    public void savePlant_emptyName_showsError() {
        view.name = "";
        presenter.savePlant();
        assertTrue(view.nameErrorShown);
        assertNull(repository.inserted);
        assertNull(repository.updated);
    }

    @Test
    public void savePlant_newPlant_inserts() {
        view.name = "Aloe";
        presenter.savePlant();
        assertNotNull(repository.inserted);
        assertNull(repository.updated);
        assertNotNull(view.finished);
        assertEquals(repository.inserted, view.finished);
    }

    @Test
    public void savePlant_existingPlant_updates() {
        view.name = "Aloe";
        view.id = 5L;
        presenter.savePlant();
        assertNotNull(repository.updated);
        assertNull(repository.inserted);
        assertNotNull(view.finished);
        assertEquals(5L, view.finished.getId());
    }
}
