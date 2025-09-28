package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import de.oabidi.pflanzenbestandundlichttest.R;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;

/**
 * Fragment hosting the plant photo gallery content.
 */
public class PlantGalleryTabFragment extends Fragment {
    @Nullable
    private Callbacks callbacks;

    public static PlantGalleryTabFragment newInstance() {
        return new PlantGalleryTabFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Callbacks) {
            callbacks = (Callbacks) context;
        } else {
            throw new IllegalStateException("Host activity must implement Callbacks");
        }
    }

    @Override
    public void onDetach() {
        callbacks = null;
        super.onDetach();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plant_detail_gallery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.applySystemWindowInsetsPadding(view, false, false, false, true);

        RecyclerView photoGrid = view.findViewById(R.id.detail_photo_grid);
        TextView emptyView = view.findViewById(R.id.detail_photo_empty);
        MaterialButton addPhotoButton = view.findViewById(R.id.detail_add_photo);

        if (callbacks != null && photoGrid != null && emptyView != null && addPhotoButton != null) {
            callbacks.onGalleryViewsReady(photoGrid, emptyView, addPhotoButton);
        }
    }

    /**
     * Callback interface implemented by {@link PlantDetailActivity}.
     */
    public interface Callbacks {
        void onGalleryViewsReady(@NonNull RecyclerView photoGrid,
                                 @NonNull View emptyView,
                                 @NonNull MaterialButton addPhotoButton);
    }
}
