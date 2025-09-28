package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.LightSummary;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.system.ExportManager;

/**
 * Presenter for {@link PlantDetailView} handling non-UI logic such as export,
 * text formatting, diary navigation and care recommendations.
 */
public class PlantDetailPresenter {
    private final PlantDetailView view;
    private final ExportManager exportManager;
    private final long plantId;
    private final DateFormat dateFormat = DateFormat.getDateInstance();
    private final PlantRepository repository;
    private final Handler mainHandler;
    private final List<CareRecommendation> currentRecommendations = new ArrayList<>();
    private final PlantRepository.CareRecommendationListener careRecommendationListener =
        new PlantRepository.CareRecommendationListener() {
            @Override
            public void onCareRecommendationsUpdated(long id, List<CareRecommendation> recommendations) {
                if (id != plantId) {
                    return;
                }
                runOnViewThread(() -> deliverRecommendations(recommendations));
            }

            @Override
            public void onCareRecommendationsError(long id, Exception exception) {
                if (id != plantId) {
                    return;
                }
                runOnViewThread(PlantDetailPresenter.this::handleCareRecommendationError);
            }
        };
    private boolean careListenerRegistered;

    public PlantDetailPresenter(PlantDetailView view, long plantId, ExportManager exportManager,
                                PlantRepository repository) {
        this(view, plantId, exportManager, repository, new Handler(Looper.getMainLooper()));
    }

    @VisibleForTesting
    PlantDetailPresenter(PlantDetailView view, long plantId, ExportManager exportManager,
                         PlantRepository repository, Handler handler) {
        this.view = Objects.requireNonNull(view, "view");
        this.plantId = plantId;
        this.exportManager = Objects.requireNonNull(exportManager, "exportManager");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.mainHandler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Returns the given text or a placeholder when {@code null} or empty.
     */
    public String getTextOrFallback(String text) {
        return (text == null || text.isEmpty()) ? view.getPlaceholderDash() : text;
    }

    /**
     * Formats the acquisition epoch using the current locale or an unknown date string.
     */
    public String formatAcquiredAt(long epoch) {
        return epoch == 0 ? view.getUnknownDateText() : dateFormat.format(new Date(epoch));
    }

    /**
     * Handles diary button clicks.
     */
    public void onDiaryClicked() {
        view.navigateToDiary(plantId);
    }

    /**
     * Handles environment log button clicks.
     */
    public void onEnvironmentLogClicked() {
        view.navigateToEnvironmentLog(plantId);
    }

    /**
     * Loads the current care recommendations.
     */
    public void loadCareRecommendations() {
        if (plantId <= 0) {
            runOnViewThread(() -> {
                view.setCareRecommendationsLoading(false);
                view.showCareRecommendationsEmpty();
            });
            return;
        }
        ensureCareListenerRegistered();
        runOnViewThread(() -> view.setCareRecommendationsLoading(true));
        repository.getCareRecommendations(plantId,
            recommendations -> runOnViewThread(() -> deliverRecommendations(recommendations)),
            error -> runOnViewThread(this::handleCareRecommendationError));
    }

    /**
     * Loads the latest light summary for the current plant.
     */
    public void loadLatestLightSummary() {
        if (plantId <= 0) {
            runOnViewThread(() -> view.showLightSummary(new LightSummary(null, null, null, null)));
            return;
        }
        repository.latestLightForPlant(plantId, entry -> runOnViewThread(() -> {
            if (entry == null) {
                view.showLightSummary(new LightSummary(null, null, null, null));
                return;
            }
            Float natural = entry.getNaturalDli();
            Float artificial = entry.getArtificialDli();
            Long timestamp = entry.getTimestamp();
            view.showLightSummary(new LightSummary(natural, natural != null ? timestamp : null,
                artificial, artificial != null ? timestamp : null));
        }), error -> runOnViewThread(() -> view.showLightSummary(new LightSummary(null, null, null, null))));
    }

    /**
     * Loads metadata associated with the supplied species key.
     */
    public void loadSpeciesMetadata(@Nullable String speciesKey) {
        String normalized = speciesKey != null ? speciesKey.trim() : null;
        if (normalized == null || normalized.isEmpty()) {
            runOnViewThread(() -> view.showSpeciesMetadataUnavailable(view.getSpeciesMetadataUnavailableText()));
            return;
        }
        repository.getSpeciesTarget(normalized,
            target -> runOnViewThread(() -> {
                if (target == null) {
                    view.showSpeciesMetadataUnavailable(view.getSpeciesMetadataUnavailableText());
                    return;
                }
                view.showSpeciesMetadata(createMetadataViewModel(target));
            }),
            error -> runOnViewThread(() -> view.showSpeciesMetadataUnavailable(view.getSpeciesMetadataUnavailableText())));
    }

    /**
     * Dismisses a recommendation so it will no longer be shown.
     */
    public void dismissRecommendation(String recommendationId) {
        if (recommendationId == null || recommendationId.isEmpty()) {
            runOnViewThread(this::handleCareRecommendationError);
            return;
        }
        repository.dismissCareRecommendation(plantId, recommendationId,
            () -> runOnViewThread(() -> handleDismissSuccess(recommendationId)),
            error -> runOnViewThread(this::handleCareRecommendationError));
    }

    /**
     * Initiates the export flow.
     */
    public void onExportRequested() {
        view.launchExport();
    }

    /**
     * Handles the URI returned from the export document picker.
     *
     * @param uri the target URI or {@code null} when user cancelled
     */
    public void onExportUriSelected(Uri uri) {
        if (uri == null) {
            view.showExportFailure();
            return;
        }
        exportManager.export(uri, plantId, success -> {
            if (success) {
                view.showExportSuccess();
            } else {
                view.showExportFailure();
            }
        });
    }

    /**
     * Cleans up resources when the presenter is destroyed.
     */
    public void onDestroy() {
        if (careListenerRegistered) {
            repository.unregisterCareRecommendationListener(plantId, careRecommendationListener);
            careListenerRegistered = false;
        }
    }

    private void ensureCareListenerRegistered() {
        if (careListenerRegistered) {
            return;
        }
        repository.registerCareRecommendationListener(plantId, careRecommendationListener);
        careListenerRegistered = true;
    }

    private void deliverRecommendations(List<CareRecommendation> recommendations) {
        List<CareRecommendation> copy = recommendations == null
            ? Collections.emptyList()
            : new ArrayList<>(recommendations);
        currentRecommendations.clear();
        currentRecommendations.addAll(copy);
        view.setCareRecommendationsLoading(false);
        if (copy.isEmpty()) {
            view.showCareRecommendationsEmpty();
        } else {
            view.showCareRecommendations(copy);
        }
    }

    private void handleCareRecommendationError() {
        view.setCareRecommendationsLoading(false);
        view.showCareRecommendationError();
    }

    private void handleDismissSuccess(String recommendationId) {
        boolean removed = removeFromCurrent(recommendationId);
        view.onCareRecommendationDismissed(recommendationId);
        if (removed && currentRecommendations.isEmpty()) {
            view.showCareRecommendationsEmpty();
        }
    }

    private boolean removeFromCurrent(String recommendationId) {
        Iterator<CareRecommendation> iterator = currentRecommendations.iterator();
        while (iterator.hasNext()) {
            CareRecommendation recommendation = iterator.next();
            if (recommendation.getId().equals(recommendationId)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private void runOnViewThread(Runnable runnable) {
        if (Looper.myLooper() == mainHandler.getLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private PlantMetadataViewModel createMetadataViewModel(SpeciesTarget target) {
        SpeciesTarget.WateringInfo wateringInfo = target.getWateringInfo();
        String frequency = wateringInfo != null ? sanitize(wateringInfo.getFrequency()) : null;
        if (frequency == null && wateringInfo != null) {
            frequency = sanitize(wateringInfo.getSchedule());
        }
        String soil = wateringInfo != null ? sanitize(firstNonEmpty(wateringInfo.getSoilType(), wateringInfo.getSoil())) : null;
        String tolerance = wateringInfo != null ? sanitize(wateringInfo.getTolerance()) : null;
        PlantMetadataViewModel.WateringInfo watering = null;
        if (frequency != null || soil != null || tolerance != null) {
            watering = new PlantMetadataViewModel.WateringInfo(frequency, soil, tolerance);
        }

        SpeciesTarget.FloatRange temperatureRange = target.getTemperatureRange();
        PlantMetadataViewModel.RangeInfo temperature = createRangeInfo(temperatureRange);

        SpeciesTarget.FloatRange humidityRange = target.getHumidityRange();
        PlantMetadataViewModel.RangeInfo humidity = createRangeInfo(humidityRange);

        List<String> careTips = sanitizeList(target.getCareTips());

        return new PlantMetadataViewModel(watering, temperature, humidity, target.getToxicToPets(), careTips);
    }

    private PlantMetadataViewModel.RangeInfo createRangeInfo(@Nullable SpeciesTarget.FloatRange range) {
        if (range == null) {
            return null;
        }
        Float min = range.getMin();
        Float max = range.getMax();
        if (min == null && max == null) {
            return null;
        }
        return new PlantMetadataViewModel.RangeInfo(min, max);
    }

    private List<String> sanitizeList(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> sanitized = new ArrayList<>();
        for (String value : values) {
            String normalized = sanitize(value);
            if (normalized != null) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    @Nullable
    private String sanitize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private String firstNonEmpty(@Nullable String primary, @Nullable String secondary) {
        String primarySanitized = sanitize(primary);
        if (primarySanitized != null) {
            return primarySanitized;
        }
        return sanitize(secondary);
    }
}
