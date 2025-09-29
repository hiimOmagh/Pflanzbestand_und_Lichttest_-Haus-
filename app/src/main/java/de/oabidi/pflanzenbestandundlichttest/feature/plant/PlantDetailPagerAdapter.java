package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import de.oabidi.pflanzenbestandundlichttest.feature.environment.EnvironmentLogFragment;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderListFragment;

/**
 * Adapter providing the detail, gallery, environment, measurement and reminder tabs.
 */
public class PlantDetailPagerAdapter extends FragmentStateAdapter {
    public static final int POSITION_DETAILS = 0;
    public static final int POSITION_GALLERY = 1;
    public static final int POSITION_ENVIRONMENT = 2;
    public static final int POSITION_MEASUREMENTS = 3;
    public static final int POSITION_REMINDERS = 4;

    private final long plantId;
    private final PlantRepository repository;
    private final String nameText;
    private final String descriptionText;
    private final String speciesText;
    private final String locationText;
    private final String acquiredAtText;

    public PlantDetailPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                                   long plantId,
                                   @NonNull PlantRepository repository,
                                   @NonNull String nameText,
                                   @NonNull String descriptionText,
                                   @NonNull String speciesText,
                                   @NonNull String locationText,
                                   @NonNull String acquiredAtText) {
        super(fragmentActivity);
        this.plantId = plantId;
        this.repository = repository;
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
        } else if (position == POSITION_ENVIRONMENT) {
            return EnvironmentLogFragment.newInstance(plantId);
        } else if (position == POSITION_MEASUREMENTS) {
            return MeasurementListFragment.newInstance(plantId, repository);
        } else {
            return ReminderListFragment.newInstance(repository);
        }
    }

    @Override
    public int getItemCount() {
        return 5;
    }
}
