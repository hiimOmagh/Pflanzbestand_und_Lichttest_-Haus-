package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.core.data.db.BulkReadDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.db.PlantDatabase;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Measurement;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.MeasurementDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.Plant;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.PlantDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExecutorProvider;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.Reminder;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderScheduler;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileAssociation;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileAssociationDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileCalibration;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantCalibrationDao;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.core.data.util.PhotoManager;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantZone;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantZoneDao;
import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.repository.ArtificialLightEstimateSource;
import de.oabidi.pflanzenbestandundlichttest.repository.CalibrationManager;
import de.oabidi.pflanzenbestandundlichttest.repository.CareRecommendationDelegate;
import de.oabidi.pflanzenbestandundlichttest.repository.CareRecommendationService;
import de.oabidi.pflanzenbestandundlichttest.repository.DatabaseArtificialLightEstimateSource;
import de.oabidi.pflanzenbestandundlichttest.repository.DiaryRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.EnvironmentRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.GalleryRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.MeasurementRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.NaturalLightRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.ProactiveAlertRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.ReminderRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.ReminderSuggestionManager;
import de.oabidi.pflanzenbestandundlichttest.repository.SpeciesRepository;
import de.oabidi.pflanzenbestandundlichttest.feature.reminders.ReminderSuggestionFormatter;
import de.oabidi.pflanzenbestandundlichttest.feature.reminders.SmartReminderEngine;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Repository providing asynchronous access to {@link Plant} entities.
 * <p>
 * All database operations are executed on {@link PlantDatabase#databaseWriteExecutor}
 * and results or completion callbacks are delivered on the Android main thread,
 * allowing callers to update the UI directly from these callbacks.
 */
public class PlantRepository implements CareRecommendationDelegate {
    private static final Pattern UNSUPPORTED_CHARS = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern RESERVED_FTS = Pattern.compile("\\b(?:AND|OR|NOT|NEAR)\\b", Pattern.CASE_INSENSITIVE);
    private final PlantDao plantDao;
    private final PlantCalibrationDao plantCalibrationDao;
    private final LedProfileDao ledProfileDao;
    private final LedProfileAssociationDao ledProfileAssociationDao;
    private final PlantZoneDao plantZoneDao;
    private final BulkReadDao bulkDao;
    private final MeasurementRepository measurementRepository;
    private final DiaryRepository diaryRepository;
    private final EnvironmentRepository environmentRepository;
    private final ReminderRepository reminderRepository;
    private final SpeciesRepository speciesRepository;
    private final GalleryRepository galleryRepository;
    private final ProactiveAlertRepository alertRepository;
    private final NaturalLightRepository naturalLightRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context; // This will be the application context
    private final ExecutorService ioExecutor;
    private final SharedPreferences sharedPreferences;
    private final CalibrationManager calibrationManager;
    private final ReminderSuggestionManager reminderSuggestionManager;
    private final CareRecommendationService careRecommendationService;

    /**
     * Creates a new repository instance.
     * <p>
     * The instance can be created on any thread, but its methods must be
     * executed on a background thread.
     *
     * @param context application context used to obtain the database
     */
    public PlantRepository(Context context) {
        this(context.getApplicationContext(), getExecutorFromProvider(context));
    }

    @VisibleForTesting
    PlantRepository(Context appContext, ExecutorService ioExecutor) {
        this.context = appContext; // appContext is already the application context
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.sharedPreferences = this.context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        PlantDatabase db = PlantDatabase.getDatabase(this.context);
        plantDao = db.plantDao();
        plantCalibrationDao = db.plantCalibrationDao();
        ledProfileDao = db.ledProfileDao();
        ledProfileAssociationDao = db.ledProfileAssociationDao();
        plantZoneDao = db.plantZoneDao();
        bulkDao = db.bulkDao();
        speciesRepository = new SpeciesRepository(this.context, mainHandler, this.ioExecutor, db.speciesTargetDao());
        reminderRepository = new ReminderRepository(this.context, mainHandler, this.ioExecutor,
            db.reminderDao(), db.reminderSuggestionDao());
        measurementRepository = new MeasurementRepository(this.context, mainHandler, this.ioExecutor,
            db.measurementDao(), plantDao, db.speciesTargetDao(), db.reminderDao());
        diaryRepository = new DiaryRepository(this.context, mainHandler, this.ioExecutor, db.diaryDao());
        galleryRepository = new GalleryRepository(this.context, mainHandler, this.ioExecutor, db.plantPhotoDao());
        ArtificialLightEstimateSource artificialLightSource =
            new DatabaseArtificialLightEstimateSource(plantDao, ledProfileDao, plantCalibrationDao);
        environmentRepository = new EnvironmentRepository(this.context, mainHandler, this.ioExecutor,
            db.environmentEntryDao(), this, artificialLightSource);
        alertRepository = new ProactiveAlertRepository(this.context, mainHandler, this.ioExecutor,
            db.proactiveAlertDao());
        naturalLightRepository = new NaturalLightRepository(this.context, mainHandler, this.ioExecutor,
            db.naturalLightEstimateDao(), plantZoneDao, sharedPreferences);
        ReminderSuggestionFormatter reminderSuggestionFormatter =
            new ReminderSuggestionFormatter(this.context.getResources());
        calibrationManager = new CalibrationManager(this.context, mainHandler, this.ioExecutor,
            plantDao, plantCalibrationDao, ledProfileDao, ledProfileAssociationDao);
        reminderSuggestionManager = new ReminderSuggestionManager(this.context, mainHandler, this.ioExecutor,
            plantDao, reminderRepository, environmentRepository, speciesRepository,
            new SmartReminderEngine(), reminderSuggestionFormatter);
        careRecommendationService = new CareRecommendationService(this.context, mainHandler, this.ioExecutor,
            plantDao, speciesRepository, environmentRepository, sharedPreferences, reminderSuggestionManager);
    }

    // Helper method to get ExecutorService and perform checks
    private static ExecutorService getExecutorFromProvider(Context originalContext) {
        Context appContext = originalContext.getApplicationContext();
        if (!(appContext instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        return ((ExecutorProvider) appContext).getIoExecutor();
    }

    public MeasurementRepository measurementRepository() {
        return measurementRepository;
    }

    public DiaryRepository diaryRepository() {
        return diaryRepository;
    }

    public EnvironmentRepository environmentRepository() {
        return environmentRepository;
    }

    public ReminderRepository reminderRepository() {
        return reminderRepository;
    }

    public SpeciesRepository speciesRepository() {
        return speciesRepository;
    }

    public GalleryRepository galleryRepository() {
        return galleryRepository;
    }

    public ProactiveAlertRepository alertRepository() {
        return alertRepository;
    }

    public NaturalLightRepository naturalLightRepository() {
        return naturalLightRepository;
    }

    public CalibrationManager calibrationManager() {
        return calibrationManager;
    }

    public ReminderSuggestionManager reminderSuggestionManager() {
        return reminderSuggestionManager;
    }

    public CareRecommendationService careRecommendationService() {
        return careRecommendationService;
    }

    /**
     * Records the last known coarse/fine location for later background usage.
     */
    public void updateLastKnownLocation(double latitude, double longitude, float accuracy, long timestamp) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(SettingsKeys.KEY_LAST_KNOWN_LATITUDE, Double.doubleToRawLongBits(latitude));
        editor.putLong(SettingsKeys.KEY_LAST_KNOWN_LONGITUDE, Double.doubleToRawLongBits(longitude));
        editor.putInt(SettingsKeys.KEY_LAST_KNOWN_LOCATION_ACCURACY, Float.floatToRawIntBits(accuracy));
        editor.putLong(SettingsKeys.KEY_LAST_KNOWN_LOCATION_TIME, timestamp);
        editor.apply();
    }

    /**
     * Returns the most recent location fix recorded by {@link #updateLastKnownLocation}.
     */
    @Nullable
    public LastKnownLocation getLastKnownLocation() {
        if (!sharedPreferences.contains(SettingsKeys.KEY_LAST_KNOWN_LATITUDE)
            || !sharedPreferences.contains(SettingsKeys.KEY_LAST_KNOWN_LONGITUDE)) {
            return null;
        }
        double latitude = Double.longBitsToDouble(sharedPreferences.getLong(SettingsKeys.KEY_LAST_KNOWN_LATITUDE, 0L));
        double longitude = Double.longBitsToDouble(sharedPreferences.getLong(SettingsKeys.KEY_LAST_KNOWN_LONGITUDE, 0L));
        float accuracy = Float.intBitsToFloat(sharedPreferences.getInt(SettingsKeys.KEY_LAST_KNOWN_LOCATION_ACCURACY, 0));
        long timestamp = sharedPreferences.getLong(SettingsKeys.KEY_LAST_KNOWN_LOCATION_TIME, 0L);
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return null;
        }
        return new LastKnownLocation(latitude, longitude, accuracy, timestamp);
    }

    /**
     * Exposes bulk read operations for export and import managers.
     */
    public BulkReadDao bulkDao() {
        return bulkDao;
    }

    /**
     * Returns all LED profiles stored in the database.
     */
    public void getLedProfiles(Consumer<List<LedProfile>> callback) {
        getLedProfiles(callback, null);
    }

    /**
     * Returns all LED profiles stored in the database.
     */
    public void getLedProfiles(Consumer<List<LedProfile>> callback,
                               Consumer<Exception> errorCallback) {
        queryAsync(() -> ledProfileDao.getAll(), callback, errorCallback);
    }

    /**
     * Loads a single LED profile by its identifier.
     */
    public void getLedProfile(long profileId, Consumer<LedProfile> callback) {
        getLedProfile(profileId, callback, null);
    }

    /**
     * Loads a single LED profile by its identifier.
     */
    public void getLedProfile(long profileId, Consumer<LedProfile> callback,
                              Consumer<Exception> errorCallback) {
        queryAsync(() -> ledProfileDao.findById(profileId), callback, errorCallback);
    }

    /**
     * Persists a new LED profile and emits the stored instance including its identifier.
     */
    public void createLedProfile(LedProfile profile, Consumer<LedProfile> callback) {
        createLedProfile(profile, callback, null);
    }

    /**
     * Persists a new LED profile and emits the stored instance including its identifier.
     */
    public void createLedProfile(LedProfile profile, Consumer<LedProfile> callback,
                                 Consumer<Exception> errorCallback) {
        Runnable completion = callback == null ? null : () -> callback.accept(profile);
        runAsync(() -> {
            long id = ledProfileDao.insert(profile);
            profile.setId(id);
        }, completion, errorCallback);
    }

    /**
     * Updates an existing LED profile.
     */
    public void updateLedProfile(LedProfile profile, Runnable callback) {
        updateLedProfile(profile, callback, null);
    }

    /**
     * Updates an existing LED profile.
     */
    public void updateLedProfile(LedProfile profile, Runnable callback,
                                 Consumer<Exception> errorCallback) {
        runAsync(() -> ledProfileDao.update(profile), callback, errorCallback);
    }

    /**
     * Deletes the provided LED profile and detaches it from any assigned plants.
     */
    public void deleteLedProfile(LedProfile profile, Runnable callback) {
        deleteLedProfile(profile, callback, null);
    }

    /**
     * Deletes the provided LED profile and detaches it from any assigned plants.
     */
    public void deleteLedProfile(LedProfile profile, Runnable callback,
                                 Consumer<Exception> errorCallback) {
        runAsync(() -> {
            long profileId = profile.getId();
            List<LedProfileAssociation> associations =
                ledProfileAssociationDao.getAssociationsForProfile(profileId);
            for (LedProfileAssociation association : associations) {
                long plantId = association.getPlantId();
                Plant plant = plantDao.findById(plantId);
                if (plant != null) {
                    plant.setLedProfileId(null);
                    plantDao.update(plant);
                }
                ledProfileAssociationDao.delete(association);
            }
            ledProfileDao.delete(profile);
        }, callback, errorCallback);
    }

    /**
     * Assigns the provided LED profile to the given plant.
     */
    public void assignLedProfileToPlant(long plantId, long profileId, Runnable callback) {
        assignLedProfileToPlant(plantId, profileId, callback, null);
    }

    /**
     * Assigns the provided LED profile to the given plant.
     */
    public void assignLedProfileToPlant(long plantId, long profileId, Runnable callback,
                                        Consumer<Exception> errorCallback) {
        runAsync(() -> {
            Plant plant = plantDao.findById(plantId);
            if (plant == null) {
                throw new IllegalArgumentException("Plant not found: " + plantId);
            }
            LedProfile profile = ledProfileDao.findById(profileId);
            if (profile == null) {
                throw new IllegalArgumentException("LED profile not found: " + profileId);
            }
            plant.setLedProfileId(profileId);
            plantDao.update(plant);
            ledProfileAssociationDao.upsert(new LedProfileAssociation(plantId, profileId));
        }, callback, errorCallback);
    }

    /**
     * Removes any LED profile assignment from the specified plant.
     */
    public void unassignLedProfileFromPlant(long plantId, Runnable callback) {
        unassignLedProfileFromPlant(plantId, callback, null);
    }

    /**
     * Removes any LED profile assignment from the specified plant.
     */
    public void unassignLedProfileFromPlant(long plantId, Runnable callback,
                                            Consumer<Exception> errorCallback) {
        runAsync(() -> {
            Plant plant = plantDao.findById(plantId);
            if (plant == null) {
                throw new IllegalArgumentException("Plant not found: " + plantId);
            }
            Long profileId = plant.getLedProfileId();
            plant.setLedProfileId(null);
            plantDao.update(plant);
            ledProfileAssociationDao.deleteByPlantId(plantId);
        }, callback, errorCallback);
    }

    /**
     * Returns the LED profile currently assigned to the specified plant, if any.
     */
    public void getLedProfileForPlant(long plantId, Consumer<LedProfile> callback) {
        getLedProfileForPlant(plantId, callback, null);
    }

    /**
     * Returns the LED profile currently assigned to the specified plant, if any.
     */
    public void getLedProfileForPlant(long plantId, Consumer<LedProfile> callback,
                                      Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Plant plant = plantDao.findById(plantId);
                LedProfile profile = null;
                if (plant != null) {
                    Long profileId = plant.getLedProfileId();
                    if (profileId != null) {
                        profile = ledProfileDao.findById(profileId);
                    }
                }
                LedProfile finalProfile = profile;
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(finalProfile));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    public void getLedCalibrationForPlant(long plantId, Consumer<LedProfileCalibration> callback) {
        calibrationManager.getLedCalibrationForPlant(plantId, callback);
    }

    public void getLedCalibrationForPlant(long plantId, Consumer<LedProfileCalibration> callback,
                                          Consumer<Exception> errorCallback) {
        calibrationManager.getLedCalibrationForPlant(plantId, callback, errorCallback);
    }

    public void saveLedCalibrationForPlant(long plantId, float ambientFactor, float cameraFactor,
                                           Runnable callback) {
        calibrationManager.saveLedCalibrationForPlant(plantId, ambientFactor, cameraFactor, callback);
    }

    public void saveLedCalibrationForPlant(long plantId, float ambientFactor, float cameraFactor,
                                           Runnable callback, Consumer<Exception> errorCallback) {
        calibrationManager.saveLedCalibrationForPlant(plantId, ambientFactor, cameraFactor, callback, errorCallback);
    }

    @Deprecated
    public void getPlantCalibration(long plantId, Consumer<PlantCalibration> callback) {
        calibrationManager.saveLedCalibrationForPlant(plantId, ambientFactor, cameraFactor, callback, errorCallback);
    }

    @Deprecated
    public void getPlantCalibration(long plantId, Consumer<PlantCalibration> callback,
                                    Consumer<Exception> errorCallback) {
        calibrationManager.getPlantCalibration(plantId, callback, errorCallback);
    }

    @Deprecated
    public void savePlantCalibration(long plantId, float ambientFactor, float cameraFactor,
                                     Runnable callback) {
        calibrationManager.getPlantCalibration(plantId, callback, errorCallback);
    }

    @Deprecated
    public void savePlantCalibration(long plantId, float ambientFactor, float cameraFactor,
                                     Runnable callback, Consumer<Exception> errorCallback) {
        calibrationManager.savePlantCalibration(plantId, ambientFactor, cameraFactor, callback, errorCallback);
    }

    private void runAsync(Runnable action, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(action, (Supplier<Runnable>) null, callback, errorCallback);
    }

    private void runAsync(Runnable action, Runnable postAction, Runnable callback,
                          Consumer<Exception> errorCallback) {
        runAsync(action, postAction == null ? null : () -> postAction, callback, errorCallback);
    }

    private void runAsync(Runnable action, Supplier<Runnable> postActionSupplier, Runnable callback,
                          Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                action.run();
                Runnable suppliedPostAction = null;
                if (postActionSupplier != null) {
                    suppliedPostAction = postActionSupplier.get();
                }
                if (suppliedPostAction == null) {
                    if (callback != null) {
                        mainHandler.post(callback);
                    }
                } else {
                    final Runnable postAction = suppliedPostAction;
                    ioExecutor.execute(() -> {
                        try {
                            postAction.run();
                            if (callback != null) {
                                mainHandler.post(callback);
                            }
                        } catch (Exception e) {
                            if (errorCallback != null) {
                                mainHandler.post(() -> errorCallback.accept(e));
                            }
                        }
                    });
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    private <T> void queryAsync(Supplier<T> query, Consumer<T> callback,
                                Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                T result = query.get();
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(result));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    @Override
    public Runnable refreshCareRecommendationsAsync(long plantId) {
        return careRecommendationService.refreshCareRecommendationsAsync(plantId);
    }

    public void getAllPlants(Consumer<List<Plant>> callback) {
        getAllPlants(callback, null);
    }

    // ... (rest of the class remains the same) ...

    public List<Plant> getAllPlantsSync() {
        return plantDao.getAll();
    }

    public void getAllPlants(Consumer<List<Plant>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Plant> result = plantDao.getAll();
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(result));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    public void searchPlants(String query, Consumer<List<Plant>> callback) {
        searchPlants(query, callback, null);
    }

    public void searchPlants(String query, Consumer<List<Plant>> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Plant> result;
                if (query == null || query.isEmpty()) {
                    result = plantDao.getAll();
                } else {
                    String normalized = UNSUPPORTED_CHARS.matcher(query).replaceAll(" ");
                    normalized = RESERVED_FTS.matcher(normalized).replaceAll(" ");
                    normalized = normalized.trim().replaceAll("\\s+", " ");
                    if (normalized.isEmpty()) {
                        result = Collections.emptyList();
                    } else {
                        String q = normalized + "*";
                        try {
                            result = plantDao.search(q);
                        } catch (SQLiteException e) {
                            result = Collections.emptyList();
                        }
                    }
                }
                if (callback != null) {
                    List<Plant> finalResult = result;
                    mainHandler.post(() -> callback.accept(finalResult));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    public void getPlant(long plantId, Consumer<Plant> callback) {
        getPlant(plantId, callback, null);
    }

    public void getPlant(long plantId, Consumer<Plant> callback, Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Plant plant = plantDao.findById(plantId);
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(plant));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    public void getPlantZone(long plantId, Consumer<PlantZone> callback) {
        getPlantZone(plantId, callback, null);
    }

    public void getPlantZone(long plantId, Consumer<PlantZone> callback,
                             Consumer<Exception> errorCallback) {
        queryAsync(() -> plantZoneDao.getForPlant(plantId), callback, errorCallback);
    }

    public void savePlantZone(long plantId, @Nullable String orientation, @Nullable String notes,
                              Runnable callback, Consumer<Exception> errorCallback) {
        if (plantId <= 0) {
            if (callback != null) {
                mainHandler.post(callback);
            }
            return;
        }
        final String normalizedOrientation = PlantZone.normalizeOrientation(orientation);
        final String normalizedNotes;
        if (notes == null) {
            normalizedNotes = null;
        } else {
            String trimmed = notes.trim();
            normalizedNotes = TextUtils.isEmpty(trimmed) ? null : trimmed;
        }
        runAsync(() -> {
            if (normalizedOrientation == null) {
                plantZoneDao.deleteForPlant(plantId);
                return;
            }
            long now = System.currentTimeMillis();
            PlantZone existing = plantZoneDao.getForPlant(plantId);
            if (existing == null) {
                PlantZone zone = new PlantZone(plantId, normalizedOrientation, normalizedNotes, now, now);
                plantZoneDao.insert(zone);
            } else {
                existing.setOrientation(normalizedOrientation);
                existing.setNotes(normalizedNotes);
                if (existing.getCreatedAt() == 0L) {
                    existing.setCreatedAt(now);
                }
                existing.setUpdatedAt(now);
                plantZoneDao.update(existing);
            }
        }, callback, errorCallback);
    }

    public void insert(Plant plant, Runnable callback) {
        insert(plant, callback, null);
    }

    public void insert(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> {
            final long id = plantDao.insert(plant);
            plant.setId(id);
        }, callback, errorCallback);
    }

    public void update(Plant plant, Runnable callback) {
        update(plant, callback, null);
    }

    public void update(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        runAsync(() -> plantDao.update(plant), callback, errorCallback);
    }

    public void delete(Plant plant, Runnable callback) {
        delete(plant, callback, null);
    }

    public void delete(Plant plant, Runnable callback, Consumer<Exception> errorCallback) {
        AtomicReference<List<Reminder>> remindersRef = new AtomicReference<>(Collections.emptyList());
        AtomicReference<List<PlantPhoto>> plantPhotosRef = new AtomicReference<>(Collections.emptyList());
        runAsync(() -> {
            long plantId = plant.getId();
            List<EnvironmentEntry> entries = environmentRepository.getRecentEntriesForPlantSync(plantId, CARE_RECOMMENDATION_ENTRY_LIMIT);
            remindersRef.set(reminderRepository.getRemindersForPlantSync(plantId));
            List<PlantPhoto> photos = galleryRepository.getPlantPhotosForPlantSync(plantId);
            plantPhotosRef.set(photos);
            plantDao.delete(plant);
            SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getLong(SettingsKeys.KEY_SELECTED_PLANT, -1) == plant.getId()) {
                prefs.edit().remove(SettingsKeys.KEY_SELECTED_PLANT).apply();
            }
        }, () -> {
            PhotoManager.deletePhoto(context, plant.getPhotoUri());
            for (PlantPhoto photo : plantPhotosRef.get()) {
                PhotoManager.deletePhoto(context, photo.getUri());
            }
            for (Reminder reminder : remindersRef.get()) {
                ReminderScheduler.cancelReminder(context, reminder.getId());
            }
        }, callback, errorCallback);
    }

    public void addPlantPhoto(long plantId, Uri sourceUri, Consumer<PlantPhoto> callback) {
        galleryRepository.addPlantPhoto(plantId, sourceUri, callback);
    }

    public void addPlantPhoto(long plantId, Uri sourceUri, Consumer<PlantPhoto> callback,
                              Consumer<Exception> errorCallback) {
        galleryRepository.addPlantPhoto(plantId, sourceUri, callback, errorCallback);
    }

    public void deletePlantPhoto(PlantPhoto photo, Runnable callback) {
        galleryRepository.deletePlantPhoto(photo, callback);
    }

    public void deletePlantPhoto(PlantPhoto photo, Runnable callback, Consumer<Exception> errorCallback) {
        galleryRepository.deletePlantPhoto(photo, callback, errorCallback);
    }

    public void plantPhotosForPlant(long plantId, Consumer<List<PlantPhoto>> callback) {
        galleryRepository.plantPhotosForPlant(plantId, callback);
    }

    public void plantPhotosForPlant(long plantId, Consumer<List<PlantPhoto>> callback,
                                    Consumer<Exception> errorCallback) {
        galleryRepository.plantPhotosForPlant(plantId, callback, errorCallback);
    }

    public void environmentEntriesForPlant(long plantId, Consumer<List<EnvironmentEntry>> callback) {
        environmentRepository.environmentEntriesForPlant(plantId, callback);
    }

    public void environmentEntriesForPlant(long plantId, Consumer<List<EnvironmentEntry>> callback,
                                           Consumer<Exception> errorCallback) {
        environmentRepository.environmentEntriesForPlant(plantId, callback, errorCallback);
    }

    public void insertEnvironmentEntry(EnvironmentEntry entry, Runnable callback) {
        insertEnvironmentEntry(entry, callback, null);
    }

    public void insertEnvironmentEntry(EnvironmentEntry entry, Runnable callback,
                                       Consumer<Exception> errorCallback) {
        environmentRepository.insertEnvironmentEntry(entry, callback, errorCallback);
    }

    public void updateEnvironmentEntry(EnvironmentEntry entry, @Nullable String previousPhotoUri, Runnable callback) {
        environmentRepository.updateEnvironmentEntry(entry, previousPhotoUri, callback);
    }

    public void updateEnvironmentEntry(EnvironmentEntry entry, @Nullable String previousPhotoUri, Runnable callback,
                                       Consumer<Exception> errorCallback) {
        environmentRepository.updateEnvironmentEntry(entry, previousPhotoUri, callback, errorCallback);
    }

    public void deleteEnvironmentEntry(EnvironmentEntry entry, Runnable callback) {
        environmentRepository.deleteEnvironmentEntry(entry, callback);
    }

    public void deleteEnvironmentEntry(EnvironmentEntry entry, Runnable callback,
                                       Consumer<Exception> errorCallback) {
        environmentRepository.deleteEnvironmentEntry(entry, callback, errorCallback);
    }

    public void latestNaturalDliForPlant(long plantId, Consumer<EnvironmentEntry> callback) {
        latestLightForPlant(plantId, callback);
    }

    public void latestNaturalDliForPlant(long plantId, Consumer<EnvironmentEntry> callback,
                                         Consumer<Exception> errorCallback) {
        latestLightForPlant(plantId, callback, errorCallback);
    }

    public void latestLightForPlant(long plantId, Consumer<EnvironmentEntry> callback) {
        environmentRepository.getLatestLight(plantId, callback);
    }

    public void latestLightForPlant(long plantId, Consumer<EnvironmentEntry> callback,
                                    Consumer<Exception> errorCallback) {
        environmentRepository.getLatestLight(plantId, callback, errorCallback);
    }

    public void getCareRecommendations(long plantId,
                                       Consumer<List<CareRecommendation>> callback,
                                       Consumer<Exception> errorCallback) {
        careRecommendationService.getCareRecommendations(plantId, callback, errorCallback);
    }

    public void registerCareRecommendationListener(long plantId,
                                                   CareRecommendationService.CareRecommendationListener listener) {
        careRecommendationService.registerCareRecommendationListener(plantId, listener);
    }

    public void unregisterCareRecommendationListener(long plantId,
                                                     CareRecommendationService.CareRecommendationListener listener) {
        careRecommendationService.unregisterCareRecommendationListener(plantId, listener);
    }

    public void dismissCareRecommendation(long plantId, String recommendationId) {
        careRecommendationService.dismissCareRecommendation(plantId, recommendationId);
    }

    public void dismissCareRecommendation(long plantId, String recommendationId,
                                          @Nullable Runnable callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        careRecommendationService.dismissCareRecommendation(plantId, recommendationId, callback, errorCallback);
    }

    public void restoreCareRecommendation(long plantId, String recommendationId) {
        careRecommendationService.restoreCareRecommendation(plantId, recommendationId);
    }

    public void restoreCareRecommendation(long plantId, String recommendationId,
                                          @Nullable Runnable callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        careRecommendationService.restoreCareRecommendation(plantId, recommendationId);
    }

    public void insertMeasurement(Measurement measurement, Runnable callback) {
        measurementRepository.insertMeasurement(measurement, callback);
    }

    public void insertMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        measurementRepository.insertMeasurement(measurement, callback, errorCallback);
    }

    public void updateMeasurement(Measurement measurement, Runnable callback) {
        measurementRepository.updateMeasurement(measurement, callback);
    }

    public void updateMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        measurementRepository.updateMeasurement(measurement, callback, errorCallback);
    }

    public void deleteMeasurement(Measurement measurement, Runnable callback) {
        measurementRepository.deleteMeasurement(measurement, callback);
    }

    public void deleteMeasurement(Measurement measurement, Runnable callback, Consumer<Exception> errorCallback) {
        measurementRepository.deleteMeasurement(measurement, callback, errorCallback);
    }

    public void insertDiaryEntry(DiaryEntry entry, Runnable callback) {
        diaryRepository.insertDiaryEntry(entry, callback);
    }

    public void insertDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        diaryRepository.insertDiaryEntry(entry, callback, errorCallback);
    }

    public void updateDiaryEntry(DiaryEntry entry, Runnable callback) {
        diaryRepository.updateDiaryEntry(entry, callback);
    }

    public void updateDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        diaryRepository.updateDiaryEntry(entry, callback, errorCallback);
    }

    public void deleteDiaryEntry(DiaryEntry entry, Runnable callback) {
        diaryRepository.deleteDiaryEntry(entry, callback);
    }

    public void deleteDiaryEntry(DiaryEntry entry, Runnable callback, Consumer<Exception> errorCallback) {
        diaryRepository.deleteDiaryEntry(entry, callback, errorCallback);
    }

    public void getAllReminders(Consumer<List<Reminder>> callback) {
        reminderRepository.getAllReminders(callback);
    }

    public void getAllReminders(Consumer<List<Reminder>> callback, Consumer<Exception> errorCallback) {
        reminderRepository.getAllReminders(callback, errorCallback);
    }

    public boolean insertReminder(Reminder reminder, Runnable callback) {
        return reminderRepository.insertReminder(reminder, callback, null);
    }

    public boolean insertReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        return reminderRepository.insertReminder(reminder, callback, errorCallback);
    }

    public boolean updateReminder(Reminder reminder, Runnable callback) {
        return reminderRepository.updateReminder(reminder, callback);
    }

    public boolean updateReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        return reminderRepository.updateReminder(reminder, callback, errorCallback);
    }

    public void deleteReminderById(long id, Runnable callback) {
        reminderRepository.deleteReminderById(id, callback);
    }

    public void deleteReminderById(long id, Runnable callback, Consumer<Exception> errorCallback) {
        reminderRepository.deleteReminderById(id, callback, errorCallback);
    }

    public void getReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback) {
        reminderSuggestionManager.getReminderSuggestion(plantId, callback);
    }

    public void getReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback,
                                      Consumer<Exception> errorCallback) {
        reminderSuggestionManager.getReminderSuggestion(plantId, callback);
    }

    public void computeReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback) {
        reminderSuggestionManager.computeReminderSuggestion(plantId, callback);
    }

    public void computeReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback,
                                          Consumer<Exception> errorCallback) {
        reminderSuggestionManager.computeReminderSuggestion(plantId, callback, errorCallback);
    }

    public ReminderSuggestion refreshReminderSuggestionSync(long plantId) {
        return reminderSuggestionManager.refreshReminderSuggestionSync(plantId);
    }

    public void refreshAllReminderSuggestionsSync() {
        return reminderSuggestionManager.refreshReminderSuggestionSync(plantId);
    }

    public void getSpeciesTarget(String speciesKey, Consumer<SpeciesTarget> callback) {
        speciesRepository.getSpeciesTarget(speciesKey, callback);
    }

    public void getSpeciesTarget(String speciesKey, Consumer<SpeciesTarget> callback, Consumer<Exception> errorCallback) {
        speciesRepository.getSpeciesTarget(speciesKey, callback, errorCallback);
    }

    public void getPlantProfileByCommonName(String commonName, Consumer<PlantProfile> callback) {
        speciesRepository.getPlantProfileByCommonName(commonName, callback);
    }

    public void getPlantProfileByCommonName(String commonName, Consumer<PlantProfile> callback,
                                            Consumer<Exception> errorCallback) {
        speciesRepository.getPlantProfileByCommonName(commonName, callback, errorCallback);
    }

    public void getPlantProfileByScientificName(String scientificName, Consumer<PlantProfile> callback) {
        speciesRepository.getPlantProfileByScientificName(scientificName, callback);
    }

    public void getPlantProfileByScientificName(String scientificName, Consumer<PlantProfile> callback,
                                                Consumer<Exception> errorCallback) {
        speciesRepository.getPlantProfileByScientificName(scientificName, callback, errorCallback);
    }

    public void getAllSpeciesTargets(Consumer<List<SpeciesTarget>> callback) {
        speciesRepository.getAllSpeciesTargets(callback);
    }

    public void getAllSpeciesTargets(Consumer<List<SpeciesTarget>> callback, Consumer<Exception> errorCallback) {
        speciesRepository.getAllSpeciesTargets(callback, errorCallback);
    }

    public void getPlantProfilesByCategory(SpeciesTarget.Category category,
                                           Consumer<List<PlantProfile>> callback) {
        speciesRepository.getPlantProfilesByCategory(category, callback);
    }

    public void getPlantProfilesByCategory(SpeciesTarget.Category category,
                                           Consumer<List<PlantProfile>> callback,
                                           Consumer<Exception> errorCallback) {
        speciesRepository.getPlantProfilesByCategory(category, callback, errorCallback);
    }

    public void getPlantProfilesByGrowthHabit(String growthHabit, Consumer<List<PlantProfile>> callback) {
        speciesRepository.getPlantProfilesByGrowthHabit(growthHabit, callback);
    }

    public void getPlantProfilesByGrowthHabit(String growthHabit, Consumer<List<PlantProfile>> callback,
                                              Consumer<Exception> errorCallback) {
        speciesRepository.getPlantProfilesByGrowthHabit(growthHabit, callback, errorCallback);
    }

    public void getPlantProfilesByToxicity(boolean isToxic, Consumer<List<PlantProfile>> callback) {
        speciesRepository.getPlantProfilesByToxicity(isToxic, callback);
    }

    public void getPlantProfilesByToxicity(boolean isToxic, Consumer<List<PlantProfile>> callback,
                                           Consumer<Exception> errorCallback) {
        speciesRepository.getPlantProfilesByToxicity(isToxic, callback, errorCallback);
    }

    public void getPlantProfilesWithUnknownToxicity(Consumer<List<PlantProfile>> callback) {
        speciesRepository.getPlantProfilesWithUnknownToxicity(callback);
    }

    public void getPlantProfilesWithUnknownToxicity(Consumer<List<PlantProfile>> callback,
                                                    Consumer<Exception> errorCallback) {
        speciesRepository.getPlantProfilesWithUnknownToxicity(callback, errorCallback);
    }

    public void insertSpeciesTarget(SpeciesTarget target, Runnable callback) {
        speciesRepository.insertSpeciesTarget(target, callback);
    }

    public void insertSpeciesTarget(SpeciesTarget target, Runnable callback, Consumer<Exception> errorCallback) {
        speciesRepository.insertSpeciesTarget(target, callback, errorCallback);
    }

    public void deleteSpeciesTarget(String speciesKey, Runnable callback) {
        speciesRepository.deleteSpeciesTarget(speciesKey, callback);
    }

    public void deleteSpeciesTarget(String speciesKey, Runnable callback, Consumer<Exception> errorCallback) {
        speciesRepository.deleteSpeciesTarget(speciesKey, callback, errorCallback);
    }

    public void getMeasurementsForPlant(long plantId, Consumer<List<Measurement>> callback) {
        measurementRepository.getMeasurementsForPlant(plantId, callback);
    }

    public void getMeasurementsForPlant(long plantId, Consumer<List<Measurement>> callback, Consumer<Exception> errorCallback) {
        measurementRepository.getMeasurementsForPlant(plantId, callback, errorCallback);
    }

    public void recentMeasurementsForPlant(long plantId, int limit, Consumer<List<Measurement>> callback) {
        measurementRepository.recentMeasurementsForPlant(plantId, limit, callback);
    }

    public void recentMeasurementsForPlant(long plantId, int limit, Consumer<List<Measurement>> callback, Consumer<Exception> errorCallback) {
        measurementRepository.recentMeasurementsForPlant(plantId, limit, callback, errorCallback);
    }

    public void measurementsForPlantSince(long plantId, long since, Consumer<List<Measurement>> callback) {
        measurementRepository.measurementsForPlantSince(plantId, since, callback);
    }

    public void measurementsForPlantSince(long plantId, long since, Consumer<List<Measurement>> callback, Consumer<Exception> errorCallback) {
        measurementRepository.measurementsForPlantSince(plantId, since, callback, errorCallback);
    }

    public void measurementsForPlantInRange(long plantId, long start, long end, Consumer<List<Measurement>> callback) {
        measurementRepository.measurementsForPlantInRange(plantId, start, end, callback);
    }

    public void measurementsForPlantInRange(long plantId, long start, long end, Consumer<List<Measurement>> callback, Consumer<Exception> errorCallback) {
        measurementRepository.measurementsForPlantInRange(plantId, start, end, callback, errorCallback);
    }

    public void sumPpfdForRange(long plantId, long start, long end, Consumer<Float> callback) {
        measurementRepository.sumPpfdForRange(plantId, start, end, callback);
    }

    public void sumPpfdForRange(long plantId, long start, long end, Consumer<Float> callback, Consumer<Exception> errorCallback) {
        measurementRepository.sumPpfdForRange(plantId, start, end, callback);
    }

    public void countDaysWithData(long plantId, long start, long end, Consumer<Integer> callback) {
        measurementRepository.countDaysWithData(plantId, start, end, callback);
    }

    public void countDaysWithData(long plantId, long start, long end, Consumer<Integer> callback, Consumer<Exception> errorCallback) {
        measurementRepository.countDaysWithData(plantId, start, end, callback, errorCallback);
    }

    public void sumPpfdAndCountDays(long plantId, long start, long end, Consumer<MeasurementDao.SumAndDays> callback) {
        measurementRepository.sumPpfdAndCountDays(plantId, start, end, callback);
    }

    public void sumPpfdAndCountDays(long plantId, long start, long end, Consumer<MeasurementDao.SumAndDays> callback, Consumer<Exception> errorCallback) {
        measurementRepository.sumPpfdAndCountDays(plantId, start, end, callback, errorCallback);
    }

    public void diaryEntriesForPlant(long plantId, Consumer<List<DiaryEntry>> callback) {
        diaryRepository.diaryEntriesForPlant(plantId, callback);
    }

    public void diaryEntriesForPlant(long plantId, Consumer<List<DiaryEntry>> callback, Consumer<Exception> errorCallback) {
        diaryRepository.diaryEntriesForPlant(plantId, callback, errorCallback);
    }

    public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback) {
        diaryRepository.searchDiaryEntries(plantId, query, callback);
    }

    public void searchDiaryEntries(long plantId, String query, Consumer<List<DiaryEntry>> callback, Consumer<Exception> errorCallback) {
        diaryRepository.searchDiaryEntries(plantId, query, callback, errorCallback);
    }

    /**
     * Lightweight holder describing a cached location fix.
     */
    public static final class LastKnownLocation {
        private final double latitude;
        private final double longitude;
        private final float accuracy;
        private final long timestamp;

        LastKnownLocation(double latitude, double longitude, float accuracy, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.timestamp = timestamp;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public float getAccuracy() {
            return accuracy;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
