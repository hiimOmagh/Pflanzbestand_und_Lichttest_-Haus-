package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import de.oabidi.pflanzenbestandundlichttest.feature.environment.EnvironmentLogFragment;

/**
 * Adapter providing the detail, gallery and environment tabs.
 */
public class PlantDetailPagerAdapter extends FragmentStateAdapter {
    public static final int POSITION_DETAILS = 0;
    public static final int POSITION_GALLERY = 1;
    public static final int POSITION_ENVIRONMENT = 2;

    private final long plantId;
    private final String nameText;
    private final String descriptionText;
    private final String speciesText;
    private final String locationText;
    private final String acquiredAtText;

    public PlantDetailPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                                   long plantId,
                                   @NonNull String nameText,
                                   @NonNull String descriptionText,
                                   @NonNull String speciesText,
                                   @NonNull String locationText,
                                   @NonNull String acquiredAtText) {
        super(fragmentActivity);
        this.plantId = plantId;
        this.nameText = nameText;
        this.descriptionText = descriptionText;
        this.speciesText = speciesText;
        this.locationText = locationText;
        this.acquiredAtText = acquiredAtText;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == POSITION_DETAILS) {
            return PlantDetailInfoFragment.newInstance(nameText, descriptionText, speciesText, locationText, acquiredAtText);
        } else if (position == POSITION_GALLERY) {
            return PlantGalleryTabFragment.newInstance();
        } else {
            return EnvironmentLogFragment.newInstance(plantId);
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
