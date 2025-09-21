package de.oabidi.pflanzenbestandundlichttest.feature.gallery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.testing.FragmentScenario;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.snackbar.Snackbar;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.oabidi.pflanzenbestandundlichttest.ExecutorProvider;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;

@RunWith(RobolectricTestRunner.class)
@Config(application = PlantPhotoViewerFragmentTest.TestPlantApplication.class)
public class PlantPhotoViewerFragmentTest {
    private static final long PLANT_ID = 42L;

    private PlantRepository repository;
    private List<PlantPhoto> backingPhotos;
    private AtomicInteger deleteCallCount;

    @Before
    public void setUp() {
        backingPhotos = new ArrayList<>();
        deleteCallCount = new AtomicInteger();
        backingPhotos.add(createPhoto(1L));
        backingPhotos.add(createPhoto(2L));

        repository = Mockito.mock(PlantRepository.class);
        Mockito.doAnswer(invocation -> {
                Consumer<List<PlantPhoto>> callback = invocation.getArgument(1);
                callback.accept(new ArrayList<>(backingPhotos));
                return null;
            })
            .when(repository)
            .plantPhotosForPlant(Mockito.anyLong(), Mockito.any(Consumer.class));
        Mockito.doAnswer(invocation -> {
                Consumer<List<PlantPhoto>> callback = invocation.getArgument(1);
                callback.accept(new ArrayList<>(backingPhotos));
                return null;
            })
            .when(repository)
            .plantPhotosForPlant(Mockito.anyLong(), Mockito.any(Consumer.class), Mockito.any(Consumer.class));
        Mockito.doAnswer(invocation -> {
                PlantPhoto photo = invocation.getArgument(0);
                Runnable completion = invocation.getArgument(1);
                Consumer<Exception> error = invocation.getArgument(2);
                performDelete(photo);
                if (completion != null) {
                    completion.run();
                }
                return null;
            })
            .when(repository)
            .deletePlantPhoto(Mockito.any(PlantPhoto.class), Mockito.any(Runnable.class), Mockito.any(Consumer.class));
        Mockito.doAnswer(invocation -> {
                PlantPhoto photo = invocation.getArgument(0);
                Runnable completion = invocation.getArgument(1);
                performDelete(photo);
                if (completion != null) {
                    completion.run();
                }
                return null;
            })
            .when(repository)
            .deletePlantPhoto(Mockito.any(PlantPhoto.class), Mockito.any(Runnable.class));

        TestPlantApplication.setRepository(repository);
    }

    @After
    public void tearDown() {
        TestPlantApplication.setRepository(null);
    }

    @Test
    public void photoViewPinchGesture_doesNotCrash() {
        FragmentScenario<PlantPhotoViewerFragment> scenario = launchFragment();
        scenario.onFragment(fragment -> {
            ViewPager2 pager = fragment.requireView().findViewById(R.id.photo_pager);
            RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
            assertNotNull(recyclerView);
            layoutRecyclerView(recyclerView);

            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            PhotoView photoView = holder.itemView.findViewById(R.id.pager_photo_image);
            photoView.setImageBitmap(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888));

            float initialScale = photoView.getScale();
            dispatchPinchGesture(photoView);
            assertTrue(photoView.getScale() >= initialScale);
        });
    }

    @Test
    public void swipeDelete_removesPhotoAndHonorsUndo() {
        FragmentScenario<PlantPhotoViewerFragment> scenario = launchFragment();
        scenario.onFragment(fragment -> {
            ViewPager2 pager = fragment.requireView().findViewById(R.id.photo_pager);
            RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
            assertNotNull(recyclerView);
            layoutRecyclerView(recyclerView);

            ItemTouchHelper.SimpleCallback callback = fragment.getSwipeCallbackForTesting();
            assertNotNull(callback);

            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            callback.onSwiped(holder, ItemTouchHelper.UP);
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            assertEquals(1, recyclerView.getAdapter().getItemCount());
            assertEquals(0, deleteCallCount.get());
            PlantPhotoViewerFragment.PendingDeletion pending = fragment.getPendingDeletionForTesting();
            assertNotNull(pending);

            View undo = pending.snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_action);
            assertNotNull(undo);
            undo.performClick();
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            assertEquals(2, recyclerView.getAdapter().getItemCount());
            assertEquals(0, deleteCallCount.get());
            assertNull(fragment.getPendingDeletionForTesting());

            layoutRecyclerView(recyclerView);
            holder = recyclerView.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            callback.onSwiped(holder, ItemTouchHelper.DOWN);
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            PlantPhotoViewerFragment.PendingDeletion second = fragment.getPendingDeletionForTesting();
            assertNotNull(second);
            Snackbar snackbar = second.snackbar;
            snackbar.dismiss();
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            assertEquals(1, deleteCallCount.get());
            assertEquals(Collections.singletonList(2L), collectPhotoIds(backingPhotos));
        });
    }

    private void performDelete(@NonNull PlantPhoto photo) {
        backingPhotos.removeIf(item -> item.getId() == photo.getId());
        deleteCallCount.incrementAndGet();
    }

    private FragmentScenario<PlantPhotoViewerFragment> launchFragment() {
        Bundle args = new Bundle();
        args.putLong("arg_plant_id", PLANT_ID);
        args.putLong("arg_photo_id", 1L);
        args.putString("arg_plant_name", "Test Plant");
        FragmentScenario<PlantPhotoViewerFragment> scenario = FragmentScenario.launchInContainer(
            PlantPhotoViewerFragment.class,
            args,
            R.style.Theme_Pflanzenbestand,
            (FragmentFactory) null
        );
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        return scenario;
    }

    private void layoutRecyclerView(@NonNull RecyclerView recyclerView) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        recyclerView.measure(widthSpec, heightSpec);
        recyclerView.layout(0, 0, 1080, 1920);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private void dispatchPinchGesture(@NonNull PhotoView photoView) {
        long downTime = SystemClock.uptimeMillis();

        MotionEvent.PointerProperties p0 = new MotionEvent.PointerProperties();
        p0.id = 0;
        p0.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerProperties p1 = new MotionEvent.PointerProperties();
        p1.id = 1;
        p1.toolType = MotionEvent.TOOL_TYPE_FINGER;

        MotionEvent.PointerCoords c0 = new MotionEvent.PointerCoords();
        c0.x = 100f;
        c0.y = 100f;
        c0.pressure = 1f;
        c0.size = 1f;
        MotionEvent.PointerCoords c1 = new MotionEvent.PointerCoords();
        c1.x = 200f;
        c1.y = 200f;
        c1.pressure = 1f;
        c1.size = 1f;

        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 1,
            new MotionEvent.PointerProperties[] {p0}, new MotionEvent.PointerCoords[] {c0},
            0, 0, 1f, 1f, 0, 0, 0, 0);
        photoView.dispatchTouchEvent(down);

        MotionEvent pointerDown = MotionEvent.obtain(downTime, downTime + 10,
            MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
            new MotionEvent.PointerProperties[] {p0, p1},
            new MotionEvent.PointerCoords[] {c0, c1}, 0, 0, 1f, 1f, 0, 0, 0, 0);
        photoView.dispatchTouchEvent(pointerDown);

        MotionEvent.PointerCoords c0Move = new MotionEvent.PointerCoords();
        c0Move.x = 80f;
        c0Move.y = 80f;
        c0Move.pressure = 1f;
        c0Move.size = 1f;
        MotionEvent.PointerCoords c1Move = new MotionEvent.PointerCoords();
        c1Move.x = 220f;
        c1Move.y = 220f;
        c1Move.pressure = 1f;
        c1Move.size = 1f;
        MotionEvent move = MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_MOVE, 2,
            new MotionEvent.PointerProperties[] {p0, p1},
            new MotionEvent.PointerCoords[] {c0Move, c1Move}, 0, 0, 1f, 1f, 0, 0, 0, 0);
        photoView.dispatchTouchEvent(move);

        MotionEvent pointerUp = MotionEvent.obtain(downTime, downTime + 60,
            MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
            new MotionEvent.PointerProperties[] {p0, p1},
            new MotionEvent.PointerCoords[] {c0Move, c1Move}, 0, 0, 1f, 1f, 0, 0, 0, 0);
        photoView.dispatchTouchEvent(pointerUp);

        MotionEvent up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, 1,
            new MotionEvent.PointerProperties[] {p0}, new MotionEvent.PointerCoords[] {c0Move},
            0, 0, 1f, 1f, 0, 0, 0, 0);
        photoView.dispatchTouchEvent(up);

        down.recycle();
        pointerDown.recycle();
        move.recycle();
        pointerUp.recycle();
        up.recycle();
    }

    private PlantPhoto createPhoto(long id) {
        PlantPhoto photo = new PlantPhoto();
        photo.setId(id);
        photo.setPlantId(PLANT_ID);
        photo.setUri(resourceUri());
        photo.setCreatedAt(SystemClock.uptimeMillis());
        return photo;
    }

    private String resourceUri() {
        Application context = ApplicationProvider.getApplicationContext();
        return "android.resource://" + context.getPackageName() + "/" + R.drawable.ic_launcher_foreground;
    }

    private List<Long> collectPhotoIds(List<PlantPhoto> photos) {
        List<Long> ids = new ArrayList<>();
        for (PlantPhoto photo : photos) {
            ids.add(photo.getId());
        }
        return ids;
    }

    public static class TestPlantApplication extends Application implements RepositoryProvider, ExecutorProvider {
        private static PlantRepository repository;
        private final ExecutorService executor = new DirectExecutorService();

        @Override
        public PlantRepository getRepository() {
            return repository;
        }

        static void setRepository(PlantRepository repo) {
            repository = repo;
        }

        @Override
        public ExecutorService getIoExecutor() {
            return executor;
        }
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @NonNull
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(@NonNull Runnable command) {
            if (!shutdown) {
                command.run();
            }
        }
    }
}
