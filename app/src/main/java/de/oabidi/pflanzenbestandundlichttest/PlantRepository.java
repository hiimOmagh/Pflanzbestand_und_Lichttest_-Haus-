package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.reminder.ReminderSuggestion;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibrationDao;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;
import de.oabidi.pflanzenbestandundlichttest.data.util.PhotoManager;
import de.oabidi.pflanzenbestandundlichttest.repository.CareRecommendationDelegate;
import de.oabidi.pflanzenbestandundlichttest.repository.DiaryRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.EnvironmentRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.GalleryRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.MeasurementRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.ProactiveAlertRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.ReminderRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.SpeciesRepository;
import de.oabidi.pflanzenbestandundlichttest.feature.reminders.ReminderSuggestionFormatter;
import de.oabidi.pflanzenbestandundlichttest.feature.reminders.SmartReminderEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final PlantDao plantDao;
    private final PlantCalibrationDao plantCalibrationDao;
    private final BulkReadDao bulkDao;
    private final MeasurementRepository measurementRepository;
    private final DiaryRepository diaryRepository;
    private final EnvironmentRepository environmentRepository;
    private final ReminderRepository reminderRepository;
    private final SpeciesRepository speciesRepository;
    private final GalleryRepository galleryRepository;
    private final ProactiveAlertRepository alertRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context; // This will be the application context
    private final ExecutorService ioExecutor;
    private final SharedPreferences sharedPreferences;
    private final CareRecommendationEngine careRecommendationEngine = new CareRecommendationEngine();
    private final SmartReminderEngine smartReminderEngine = new SmartReminderEngine();
    private final ReminderSuggestionFormatter reminderSuggestionFormatter;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<CareRecommendationListener>> careRecommendationListeners = new ConcurrentHashMap<>();
    private static final String TAG = "PlantRepository";
    private static final Pattern UNSUPPORTED_CHARS = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern RESERVED_FTS = Pattern.compile("\\b(?:AND|OR|NOT|NEAR)\\b", Pattern.CASE_INSENSITIVE);
    private static final int CARE_RECOMMENDATION_ENTRY_LIMIT = 30;

    // Helper method to get ExecutorService and perform checks
    private static ExecutorService getExecutorFromProvider(Context originalContext) {
        Context appContext = originalContext.getApplicationContext();
        if (!(appContext instanceof ExecutorProvider)) {
            throw new IllegalStateException("Application context does not implement ExecutorProvider");
        }
        return ((ExecutorProvider) appContext).getIoExecutor();
    }

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
        bulkDao = db.bulkDao();
        speciesRepository = new SpeciesRepository(this.context, mainHandler, this.ioExecutor, db.speciesTargetDao());
        reminderRepository = new ReminderRepository(this.context, mainHandler, this.ioExecutor,
            db.reminderDao(), db.reminderSuggestionDao());
        measurementRepository = new MeasurementRepository(this.context, mainHandler, this.ioExecutor,
            db.measurementDao(), plantDao, db.speciesTargetDao(), db.reminderDao());
        diaryRepository = new DiaryRepository(this.context, mainHandler, this.ioExecutor, db.diaryDao());
        galleryRepository = new GalleryRepository(this.context, mainHandler, this.ioExecutor, db.plantPhotoDao());
        environmentRepository = new EnvironmentRepository(this.context, mainHandler, this.ioExecutor,
            db.environmentEntryDao(), this);
        alertRepository = new ProactiveAlertRepository(this.context, mainHandler, this.ioExecutor,
            db.proactiveAlertDao());
        reminderSuggestionFormatter = new ReminderSuggestionFormatter(this.context.getResources());
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

    /** Exposes bulk read operations for export and import managers. */
    public BulkReadDao bulkDao() {
        return bulkDao;
    }

    /** Retrieves the calibration entry for the specified plant. */
    public void getPlantCalibration(long plantId, Consumer<PlantCalibration> callback) {
        getPlantCalibration(plantId, callback, null);
    }

    /** Retrieves the calibration entry for the specified plant. */
    public void getPlantCalibration(long plantId, Consumer<PlantCalibration> callback,
                                    Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                PlantCalibration calibration = plantCalibrationDao.getForPlant(plantId);
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(calibration));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    /** Stores the calibration for the specified plant. */
    public void savePlantCalibration(long plantId, float ambientFactor, float cameraFactor,
                                     Runnable callback) {
        savePlantCalibration(plantId, ambientFactor, cameraFactor, callback, null);
    }

    /** Stores the calibration for the specified plant. */
    public void savePlantCalibration(long plantId, float ambientFactor, float cameraFactor,
                                     Runnable callback, Consumer<Exception> errorCallback) {
        PlantCalibration calibration = new PlantCalibration(plantId, ambientFactor, cameraFactor);
        runAsync(() -> plantCalibrationDao.insertOrUpdate(calibration), callback, errorCallback);
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

    private Supplier<Runnable> careRecommendationRefreshSupplier(long plantId) {
        return () -> refreshCareRecommendationsAsync(plantId);
    }

    @Override
    public Runnable refreshCareRecommendationsAsync(long plantId) {
        return () -> {
            try {
                List<CareRecommendation> recommendations = computeCareRecommendations(plantId);
                notifyCareRecommendationListeners(plantId, recommendations);
            } catch (Exception e) {
                notifyCareRecommendationError(plantId, e);
            }
            try {
                refreshReminderSuggestionSync(plantId, System.currentTimeMillis());
            } catch (Exception e) {
                Log.w(TAG, "Failed to refresh reminder suggestion", e);
            }
        };
    }

    private List<CareRecommendation> computeCareRecommendations(long plantId) {
        Plant plant = plantDao.findById(plantId);
        if (plant == null) {
            clearDismissedCareRecommendations(plantId);
            return Collections.emptyList();
        }
        PlantProfile profile = null;
        String speciesKey = plant.getSpecies();
        if (speciesKey != null && !speciesKey.isEmpty()) {
            SpeciesTarget target = speciesRepository.getSpeciesTargetSync(speciesKey);
            profile = PlantProfile.fromTarget(target);
        }
        List<EnvironmentEntry> entries = environmentRepository.getRecentEntriesForPlantSync(plantId, CARE_RECOMMENDATION_ENTRY_LIMIT);
        if (entries == null) {
            entries = Collections.emptyList();
        }
        List<CareRecommendation> evaluated = careRecommendationEngine.evaluate(profile, entries, context.getResources());
        return applyDismissals(plantId, evaluated);
    }

    private List<CareRecommendation> applyDismissals(long plantId, List<CareRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            clearDismissedCareRecommendations(plantId);
            return Collections.emptyList();
        }
        Set<String> dismissed = loadDismissedIds(plantId);
        List<CareRecommendation> filtered = new ArrayList<>();
        for (CareRecommendation recommendation : recommendations) {
            if (!dismissed.contains(recommendation.getId())) {
                filtered.add(recommendation);
            }
        }
        pruneDismissedCareRecommendations(plantId, dismissed, recommendations);
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(filtered);
    }

    private void notifyCareRecommendationListeners(long plantId, List<CareRecommendation> recommendations) {
        CopyOnWriteArrayList<CareRecommendationListener> listeners = careRecommendationListeners.get(plantId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        List<CareRecommendation> payload = recommendations.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(recommendations));
        mainHandler.post(() -> {
            for (CareRecommendationListener listener : listeners) {
                listener.onCareRecommendationsUpdated(plantId, payload);
            }
        });
    }

    private void notifyCareRecommendationError(long plantId, Exception exception) {
        CopyOnWriteArrayList<CareRecommendationListener> listeners = careRecommendationListeners.get(plantId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            for (CareRecommendationListener listener : listeners) {
                listener.onCareRecommendationsError(plantId, exception);
            }
        });
    }

    private Set<String> loadDismissedIds(long plantId) {
        Set<String> stored = sharedPreferences.getStringSet(getDismissedPrefKey(plantId), Collections.emptySet());
        return new HashSet<>(stored);
    }

    private void saveDismissedCareRecommendations(long plantId, Set<String> ids) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String key = getDismissedPrefKey(plantId);
        if (ids == null || ids.isEmpty()) {
            editor.remove(key);
        } else {
            editor.putStringSet(key, new HashSet<>(ids));
        }
        editor.apply();
    }

    private void clearDismissedCareRecommendations(long plantId) {
        sharedPreferences.edit().remove(getDismissedPrefKey(plantId)).apply();
    }

    private void pruneDismissedCareRecommendations(long plantId, Set<String> dismissed,
                                                   List<CareRecommendation> active) {
        if (dismissed.isEmpty()) {
            return;
        }
        Set<String> activeIds = new HashSet<>();
        for (CareRecommendation recommendation : active) {
            activeIds.add(recommendation.getId());
        }
        if (dismissed.retainAll(activeIds)) {
            saveDismissedCareRecommendations(plantId, dismissed);
        }
    }

    private void addDismissedId(long plantId, String recommendationId) {
        Set<String> dismissed = loadDismissedIds(plantId);
        if (dismissed.add(recommendationId)) {
            saveDismissedCareRecommendations(plantId, dismissed);
        }
    }

    private void removeDismissedId(long plantId, String recommendationId) {
        Set<String> dismissed = loadDismissedIds(plantId);
        if (dismissed.remove(recommendationId)) {
            saveDismissedCareRecommendations(plantId, dismissed);
        }
    }

    private String getDismissedPrefKey(long plantId) {
        return SettingsKeys.KEY_DISMISSED_CARE_RECOMMENDATIONS + "_" + plantId;
    }

    // ... (rest of the class remains the same) ...

    public void getAllPlants(Consumer<List<Plant>> callback) {
        getAllPlants(callback, null);
    }

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

    /**
     * Retrieves the current care recommendations for the supplied plant.
     */
    public void getCareRecommendations(long plantId,
                                       Consumer<List<CareRecommendation>> callback,
                                       Consumer<Exception> errorCallback) {
        PlantDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<CareRecommendation> recommendations = computeCareRecommendations(plantId);
                if (callback != null) {
                    List<CareRecommendation> payload = recommendations.isEmpty()
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(new ArrayList<>(recommendations));
                    mainHandler.post(() -> callback.accept(payload));
                }
            } catch (Exception e) {
                if (errorCallback != null) {
                    mainHandler.post(() -> errorCallback.accept(e));
                }
            }
        });
    }

    /** Registers a listener that will receive recommendation updates for the given plant. */
    public void registerCareRecommendationListener(long plantId, CareRecommendationListener listener) {
        if (listener == null) {
            return;
        }
        careRecommendationListeners
            .computeIfAbsent(plantId, id -> new CopyOnWriteArrayList<>())
            .addIfAbsent(listener);
    }

    /** Unregisters a previously registered care recommendation listener. */
    public void unregisterCareRecommendationListener(long plantId, CareRecommendationListener listener) {
        if (listener == null) {
            return;
        }
        CopyOnWriteArrayList<CareRecommendationListener> listeners = careRecommendationListeners.get(plantId);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                careRecommendationListeners.remove(plantId, listeners);
            }
        }
    }

    /** Marks a recommendation as dismissed for the supplied plant. */
    public void dismissCareRecommendation(long plantId, String recommendationId) {
        dismissCareRecommendation(plantId, recommendationId, null, null);
    }

    /** Marks a recommendation as dismissed for the supplied plant. */
    public void dismissCareRecommendation(long plantId, String recommendationId,
                                          @Nullable Runnable callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        if (recommendationId == null || recommendationId.isEmpty()) {
            if (errorCallback != null) {
                mainHandler.post(() -> errorCallback.accept(new IllegalArgumentException("recommendationId")));
            }
            return;
        }
        runAsync(() -> addDismissedId(plantId, recommendationId),
            careRecommendationRefreshSupplier(plantId), callback, errorCallback);
    }

    /** Removes a dismissal marker for the supplied recommendation. */
    public void restoreCareRecommendation(long plantId, String recommendationId) {
        restoreCareRecommendation(plantId, recommendationId, null, null);
    }

    /** Removes a dismissal marker for the supplied recommendation. */
    public void restoreCareRecommendation(long plantId, String recommendationId,
                                          @Nullable Runnable callback,
                                          @Nullable Consumer<Exception> errorCallback) {
        if (recommendationId == null || recommendationId.isEmpty()) {
            if (errorCallback != null) {
                mainHandler.post(() -> errorCallback.accept(new IllegalArgumentException("recommendationId")));
            }
            return;
        }
        runAsync(() -> removeDismissedId(plantId, recommendationId),
            careRecommendationRefreshSupplier(plantId), callback, errorCallback);
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

    public void insertReminder(Reminder reminder, Runnable callback) {
        reminderRepository.insertReminder(reminder, callback, null);
    }

    public void insertReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        reminderRepository.insertReminder(reminder, callback, errorCallback);
    }

    public void updateReminder(Reminder reminder, Runnable callback) {
        reminderRepository.updateReminder(reminder, callback);
    }

    public void updateReminder(Reminder reminder, Runnable callback, Consumer<Exception> errorCallback) {
        reminderRepository.updateReminder(reminder, callback, errorCallback);
    }

    public void deleteReminderById(long id, Runnable callback) {
        reminderRepository.deleteReminderById(id, callback);
    }

    public void deleteReminderById(long id, Runnable callback, Consumer<Exception> errorCallback) {
        reminderRepository.deleteReminderById(id, callback, errorCallback);
    }

    public void getReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback) {
        getReminderSuggestion(plantId, callback, null);
    }

    public void getReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback,
                                      Consumer<Exception> errorCallback) {
        queryAsync(() -> reminderRepository.getSuggestionForPlantSync(plantId), callback, errorCallback);
    }

    public void computeReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback) {
        computeReminderSuggestion(plantId, callback, null);
    }

    public void computeReminderSuggestion(long plantId, Consumer<ReminderSuggestion> callback,
                                          Consumer<Exception> errorCallback) {
        queryAsync(() -> refreshReminderSuggestionSync(plantId, System.currentTimeMillis()), callback, errorCallback);
    }

    public ReminderSuggestion refreshReminderSuggestionSync(long plantId) {
        return refreshReminderSuggestionSync(plantId, System.currentTimeMillis());
    }

    public void refreshAllReminderSuggestionsSync() {
        List<Plant> plants = plantDao.getAll();
        if (plants == null || plants.isEmpty()) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        for (Plant plant : plants) {
            try {
                refreshReminderSuggestionForPlant(plant, timestamp);
            } catch (Exception e) {
                Log.w(TAG, "Failed to refresh reminder suggestion for plant " + plant.getId(), e);
            }
        }
    }

    private ReminderSuggestion refreshReminderSuggestionSync(long plantId, long timestamp) {
        Plant plant = plantDao.findById(plantId);
        if (plant == null) {
            reminderRepository.deleteSuggestionSync(plantId);
            return null;
        }
        ReminderSuggestion suggestion = buildReminderSuggestion(plant, timestamp);
        reminderRepository.saveSuggestionSync(suggestion);
        return suggestion;
    }

    private void refreshReminderSuggestionForPlant(Plant plant, long timestamp) {
        if (plant == null) {
            return;
        }
        ReminderSuggestion suggestion = buildReminderSuggestion(plant, timestamp);
        reminderRepository.saveSuggestionSync(suggestion);
    }

    private ReminderSuggestion buildReminderSuggestion(Plant plant, long timestamp) {
        long plantId = plant.getId();
        PlantProfile profile = resolveProfileForPlant(plant);
        List<EnvironmentEntry> entries = environmentRepository.getRecentEntriesForPlantSync(plantId,
            SmartReminderEngine.HISTORY_LIMIT);
        if (entries == null) {
            entries = Collections.emptyList();
        }
        SmartReminderEngine.Suggestion suggestion = smartReminderEngine.suggest(profile, entries);
        ReminderSuggestion entity = new ReminderSuggestion();
        entity.setPlantId(plantId);
        entity.setSuggestedIntervalDays(suggestion.getSuggestedDays());
        entity.setLastEvaluatedAt(timestamp);
        entity.setConfidenceScore(computeConfidenceScore(suggestion));
        entity.setExplanation(reminderSuggestionFormatter.format(plant, profile, suggestion));
        return entity;
    }

    private float computeConfidenceScore(SmartReminderEngine.Suggestion suggestion) {
        float confidence = 0.3f;
        if (suggestion.getBaselineDays() > 0) {
            confidence += 0.3f;
        }
        if (suggestion.getEnvironmentSignal() != SmartReminderEngine.EnvironmentSignal.NO_DATA) {
            confidence += 0.2f;
        }
        if (suggestion.getAverageSoilMoisture() != null) {
            confidence += 0.2f;
        }
        if (suggestion.getLatestSoilMoisture() != null) {
            confidence += 0.1f;
        }
        return Math.max(0f, Math.min(1f, confidence));
    }

    private PlantProfile resolveProfileForPlant(Plant plant) {
        if (plant == null) {
            return null;
        }
        String speciesKey = plant.getSpecies();
        if (!TextUtils.isEmpty(speciesKey)) {
            SpeciesTarget target = speciesRepository.getSpeciesTargetSync(speciesKey);
            return PlantProfile.fromTarget(target);
        }
        return null;
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

    /** Listener receiving automatic care recommendation updates. */
    public interface CareRecommendationListener {
        /** Invoked when a new list of care recommendations is available. */
        void onCareRecommendationsUpdated(long plantId, List<CareRecommendation> recommendations);

        /** Invoked when the recommendation engine encounters an error. */
        void onCareRecommendationsError(long plantId, Exception exception);
    }
}
