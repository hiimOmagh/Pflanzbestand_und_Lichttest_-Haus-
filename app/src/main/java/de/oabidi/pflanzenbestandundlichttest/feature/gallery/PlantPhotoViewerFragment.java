package de.oabidi.pflanzenbestandundlichttest.feature.gallery;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.github.chrisbanes.photoview.PhotoView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import de.oabidi.pflanzenbestandundlichttest.ExecutorProvider;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;

/**
 * Full-screen fragment that allows swiping through plant gallery photos and deleting them.
 */
public class PlantPhotoViewerFragment extends Fragment {
    public static final String RESULT_KEY = "plant_photo_viewer_result";
    public static final String EXTRA_REFRESH_GALLERY = "refresh_gallery";

    private static final String TAG = "PlantPhotoViewer";
    private static final String ARG_PLANT_ID = "arg_plant_id";
    private static final String ARG_PHOTO_ID = "arg_photo_id";
    private static final String ARG_PLANT_NAME = "arg_plant_name";

    public static void show(@NonNull FragmentManager manager, long plantId, long initialPhotoId,
                            @Nullable String plantName) {
        PlantPhotoViewerFragment fragment = new PlantPhotoViewerFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLANT_ID, plantId);
        args.putLong(ARG_PHOTO_ID, initialPhotoId);
        args.putString(ARG_PLANT_NAME, plantName);
        fragment.setArguments(args);
        manager.beginTransaction()
            .add(android.R.id.content, fragment, TAG)
            .addToBackStack(TAG)
            .commit();
    }

    private PlantRepository repository;
    private PlantPhotoLoader photoLoader;
    private ViewPager2 pager;
    private PhotoPagerAdapter pagerAdapter;
    private long plantId;
    private long initialPhotoId;
    @Nullable
    private String plantName;
    private boolean initialPositionApplied;
    private boolean refreshRequested;
    private int currentIndex;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private ItemTouchHelper swipeToDeleteHelper;
    private ItemTouchHelper.SimpleCallback swipeCallback;
    @Nullable
    private PendingDeletion pendingDeletion;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = requireArguments();
        plantId = args.getLong(ARG_PLANT_ID, -1L);
        initialPhotoId = args.getLong(ARG_PHOTO_ID, -1L);
        plantName = args.getString(ARG_PLANT_NAME);

        Application application = requireActivity().getApplication();
        if (!(application instanceof RepositoryProvider) || !(application instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application must provide repository and executor");
        }
        repository = ((RepositoryProvider) application).getRepository();
        ExecutorService executor = ((ExecutorProvider) application).getIoExecutor();
        photoLoader = new PlantPhotoLoader(requireContext(), executor);
        pagerAdapter = new PhotoPagerAdapter(photoLoader);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plant_photo_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pager = view.findViewById(R.id.photo_pager);
        pager.setAdapter(pagerAdapter);
        pagerAdapter.setHasStableIds(true);
        RecyclerView pagerRecycler = (RecyclerView) pager.getChildAt(0);
        if (pagerRecycler != null) {
            swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.UP | ItemTouchHelper.DOWN) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder viewHolder,
                                      @NonNull RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    handlePhotoSwiped(viewHolder.getBindingAdapterPosition());
                }
            };
            swipeToDeleteHelper = new ItemTouchHelper(swipeCallback);
            swipeToDeleteHelper.attachToRecyclerView(pagerRecycler);
        }
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentIndex = position;
                updateSubtitle(position);
            }
        };
        pager.registerOnPageChangeCallback(pageChangeCallback);

        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.photo_viewer_toolbar);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationContentDescription(R.string.action_close);
        toolbar.inflateMenu(R.menu.plant_photo_viewer_menu);
        toolbar.setNavigationOnClickListener(v -> closeSelf());
        toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);
        updateTitle();

        loadPhotos();
    }

    @Override
    public void onDestroyView() {
        if (pageChangeCallback != null && pager != null) {
            pager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        if (swipeToDeleteHelper != null) {
            swipeToDeleteHelper.attachToRecyclerView(null);
            swipeToDeleteHelper = null;
        }
        swipeCallback = null;
        if (pendingDeletion != null) {
            pendingDeletion.snackbar.dismiss();
            pendingDeletion = null;
        }
        pageChangeCallback = null;
        pager = null;
        super.onDestroyView();
    }

    private boolean onMenuItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_delete_photo) {
            confirmDeleteCurrent();
            return true;
        }
        return false;
    }

    private void loadPhotos() {
        if (plantId <= 0) {
            if (null != null) {
                ((Runnable) null).run();
            }
            return;
        }
        repository.plantPhotosForPlant(plantId, photos -> {
            pagerAdapter.submit(photos);
            if (!initialPositionApplied && initialPhotoId > 0) {
                int index = pagerAdapter.indexOf(initialPhotoId);
                if (index >= 0) {
                    currentIndex = index;
                    pager.setCurrentItem(index, false);
                }
                initialPositionApplied = true;
            } else if (currentIndex >= pagerAdapter.getItemCount()) {
                currentIndex = Math.max(0, pagerAdapter.getItemCount() - 1);
                pager.setCurrentItem(currentIndex, false);
            }
            updateSubtitle(currentIndex);
            updateTitle();
            if (null != null) {
                ((Runnable) null).run();
            }
            if (pagerAdapter.getItemCount() == 0) {
                closeSelf();
            }
        }, e -> {
            Toast.makeText(requireContext(), R.string.plant_photo_delete_failed, Toast.LENGTH_SHORT).show();
            if (null != null) {
                ((Runnable) null).run();
            }
        });
    }

    private void updateTitle() {
        androidx.appcompat.widget.Toolbar toolbar = getToolbar();
        if (toolbar == null) {
            return;
        }
        toolbar.setTitle(plantName != null && !plantName.isEmpty()
            ? plantName
            : getString(R.string.plant_photo_viewer_title));
    }

    private void updateSubtitle(int position) {
        androidx.appcompat.widget.Toolbar toolbar = getToolbar();
        if (toolbar == null) {
            return;
        }
        int count = pagerAdapter.getItemCount();
        if (count > 0) {
            toolbar.setSubtitle(getString(R.string.plant_photo_position, position + 1, count));
        } else {
            toolbar.setSubtitle(null);
        }
    }

    @Nullable
    private androidx.appcompat.widget.Toolbar getToolbar() {
        View view = getView();
        if (view == null) {
            return null;
        }
        return view.findViewById(R.id.photo_viewer_toolbar);
    }

    private void confirmDeleteCurrent() {
        PlantPhoto photo = pagerAdapter.getItem(currentIndex);
        if (photo == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.plant_photo_delete_title)
            .setMessage(R.string.plant_photo_delete_message)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> deletePhoto(photo))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deletePhoto(@NonNull PlantPhoto photo) {
        repository.deletePlantPhoto(photo, () -> {
            Toast.makeText(requireContext(), R.string.plant_photo_delete_success, Toast.LENGTH_SHORT).show();
            refreshRequested = true;
            loadPhotos();
        }, e -> Toast.makeText(requireContext(), R.string.plant_photo_delete_failed, Toast.LENGTH_SHORT).show());
    }

    private void handlePhotoSwiped(int position) {
        if (position == RecyclerView.NO_POSITION) {
            pagerAdapter.notifyDataSetChanged();
            return;
        }
        PlantPhoto photo = pagerAdapter.getItem(position);
        if (photo == null) {
            pagerAdapter.notifyItemChanged(position);
            return;
        }
        pagerAdapter.removeAt(position);
        if (pendingDeletion != null) {
            pendingDeletion.snackbar.dismiss();
        }
        int itemCount = pagerAdapter.getItemCount();
        if (itemCount > 0) {
            currentIndex = Math.min(position, itemCount - 1);
            if (pager != null) {
                pager.setCurrentItem(currentIndex, false);
            }
        } else {
            currentIndex = 0;
        }
        updateSubtitle(currentIndex);
        showUndoSnackbar(photo, position);
    }

    private void showUndoSnackbar(@NonNull PlantPhoto photo, int position) {
        View root = getView();
        if (root == null) {
            deletePhoto(photo);
            return;
        }
        Snackbar snackbar = Snackbar.make(root, R.string.plant_photo_pending_delete, Snackbar.LENGTH_LONG);
        pendingDeletion = new PendingDeletion(photo, position, snackbar);
        snackbar.setAction(R.string.action_undo, v -> {
            if (pendingDeletion != null && pendingDeletion.photo.getId() == photo.getId()) {
                pendingDeletion.undone = true;
                pagerAdapter.insertAt(position, photo);
                currentIndex = position;
                if (pager != null) {
                    pager.setCurrentItem(position, false);
                }
                updateSubtitle(currentIndex);
            }
        });
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (pendingDeletion != null && pendingDeletion.photo.getId() == photo.getId()) {
                    PendingDeletion dismissed = pendingDeletion;
                    pendingDeletion = null;
                    if (!dismissed.undone) {
                        deletePhoto(photo);
                    }
                }
            }
        });
        snackbar.show();
    }

    @VisibleForTesting
    @Nullable
    ItemTouchHelper.SimpleCallback getSwipeCallbackForTesting() {
        return swipeCallback;
    }

    @VisibleForTesting
    @Nullable
    PendingDeletion getPendingDeletionForTesting() {
        return pendingDeletion;
    }

    static final class PendingDeletion {
        final PlantPhoto photo;
        final int position;
        final Snackbar snackbar;
        boolean undone;

        PendingDeletion(@NonNull PlantPhoto photo, int position, @NonNull Snackbar snackbar) {
            this.photo = photo;
            this.position = position;
            this.snackbar = snackbar;
        }
    }

    private void closeSelf() {
        if (refreshRequested) {
            Bundle result = new Bundle();
            result.putBoolean(EXTRA_REFRESH_GALLERY, true);
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        }
        getParentFragmentManager().popBackStack(TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private static class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.ViewHolder> {
        private final List<PlantPhoto> items = new ArrayList<>();
        private final PlantPhotoLoader loader;

        PhotoPagerAdapter(@NonNull PlantPhotoLoader loader) {
            this.loader = loader;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_plant_photo_pager, parent, false);
            return new ViewHolder(view, loader);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
            prefetchAround(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).getId();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void submit(@NonNull List<PlantPhoto> photos) {
            items.clear();
            items.addAll(photos);
            notifyDataSetChanged();
        }

        void removeAt(int index) {
            if (index < 0 || index >= items.size()) {
                return;
            }
            items.remove(index);
            notifyItemRemoved(index);
        }

        void insertAt(int index, @NonNull PlantPhoto photo) {
            int clamped = Math.max(0, Math.min(index, items.size()));
            items.add(clamped, photo);
            notifyItemInserted(clamped);
        }

        int indexOf(long photoId) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getId() == photoId) {
                    return i;
                }
            }
            return -1;
        }

        @Nullable
        PlantPhoto getItem(int index) {
            if (index < 0 || index >= items.size()) {
                return null;
            }
            return items.get(index);
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            super.onViewRecycled(holder);
            holder.recycle();
        }

        private void prefetchAround(int position) {
            int next = position + 1;
            if (next < items.size()) {
                loader.prefetch(items.get(next).getUri());
            }
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final PhotoView imageView;
            private final PlantPhotoLoader loader;

            ViewHolder(@NonNull View itemView, @NonNull PlantPhotoLoader loader) {
                super(itemView);
                this.loader = loader;
                imageView = itemView.findViewById(R.id.pager_photo_image);
            }

            void bind(@NonNull PlantPhoto photo) {
                imageView.setScale(1.0f, false);
                loader.loadInto(imageView, photo.getUri());
            }

            void recycle() {
                loader.clear(imageView);
                imageView.setScale(1.0f, false);
            }
        }
    }
}
