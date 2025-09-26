package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import de.oabidi.pflanzenbestandundlichttest.repository.DiaryRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.EnvironmentRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.GalleryRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.MeasurementRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.ProactiveAlertRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.ReminderRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.SpeciesRepository;

/**
 * Interface providing access to a {@link PlantRepository} instance.
 */
public interface RepositoryProvider {
    /**
     * Returns the shared {@link PlantRepository} instance for the application.
     */
    PlantRepository getRepository();

    default MeasurementRepository getMeasurementRepository() {
        return getRepository().measurementRepository();
    }

    default DiaryRepository getDiaryRepository() {
        return getRepository().diaryRepository();
    }

    default EnvironmentRepository getEnvironmentRepository() {
        return getRepository().environmentRepository();
    }

    default ReminderRepository getReminderRepository() {
        return getRepository().reminderRepository();
    }

    default SpeciesRepository getSpeciesRepository() {
        return getRepository().speciesRepository();
    }

    default GalleryRepository getGalleryRepository() {
        return getRepository().galleryRepository();
    }

    default ProactiveAlertRepository getAlertRepository() {
        return getRepository().alertRepository();
    }

    /**
     * Convenience method returning the repository associated with the given context.
     *
     * @param context any context whose application implements {@link RepositoryProvider}
     * @return the shared {@link PlantRepository} instance
     * @throws IllegalStateException if the application context is not a {@link RepositoryProvider}
     */
    static PlantRepository getRepository(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof RepositoryProvider) {
            return ((RepositoryProvider) appContext).getRepository();
        }
        throw new IllegalStateException("Application context does not implement RepositoryProvider");
    }

    static MeasurementRepository getMeasurementRepository(Context context) {
        return getRepository(context).measurementRepository();
    }

    static DiaryRepository getDiaryRepository(Context context) {
        return getRepository(context).diaryRepository();
    }

    static EnvironmentRepository getEnvironmentRepository(Context context) {
        return getRepository(context).environmentRepository();
    }

    static ReminderRepository getReminderRepository(Context context) {
        return getRepository(context).reminderRepository();
    }

    static SpeciesRepository getSpeciesRepository(Context context) {
        return getRepository(context).speciesRepository();
    }

    static GalleryRepository getGalleryRepository(Context context) {
        return getRepository(context).galleryRepository();
    }

    static ProactiveAlertRepository getAlertRepository(Context context) {
        return getRepository(context).alertRepository();
    }
}
