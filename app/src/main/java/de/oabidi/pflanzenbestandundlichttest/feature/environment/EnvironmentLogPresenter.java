package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.LightSummary;
import de.oabidi.pflanzenbestandundlichttest.repository.EnvironmentRepository;

/**
 * Presenter coordinating the environment log between the repository and the view.
 */
public class EnvironmentLogPresenter {
    public static final String EVENT_SAVED = "saved";
    public static final String EVENT_UPDATED = "updated";
    public static final String EVENT_DELETED = "deleted";

    private final EnvironmentLogView view;
    private final EnvironmentRepository environmentRepository;
    private final long plantId;
    private final Context context;
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    @Nullable
    private EnvironmentEntry editingEntry;
    @Nullable
    private String pendingPhotoUri;
    @Nullable
    private String editingOriginalPhotoUri;

    public EnvironmentLogPresenter(@NonNull EnvironmentLogView view, @NonNull EnvironmentRepository environmentRepository,
                                   long plantId, @NonNull Context context) {
        this.view = view;
        this.environmentRepository = environmentRepository;
        this.plantId = plantId;
        this.context = context.getApplicationContext();
    }

    /**
     * Loads all environment entries for the current plant.
     */
    public void loadEntries() {
        if (plantId <= 0) {
            view.showEmptyState(true);
            view.showGrowthChart(null);
            view.showClimateChart(null);
            view.showError(context.getString(R.string.error_select_plant));
            view.showLoading(false);
            return;
        }
        view.showLoading(true);
        environmentRepository.environmentEntriesForPlant(plantId, entries -> {
            List<EnvironmentLogItem> items = new ArrayList<>(entries.size());
            for (EnvironmentEntry entry : entries) {
                items.add(toItem(entry));
            }
            view.showEntries(items);
            view.showEmptyState(items.isEmpty());
            view.showPhotoHighlights(buildPhotoHighlights(entries));
            refreshCharts(entries);
            view.showLightSummary(extractLightSummary(entries));
            view.showLoading(false);
        }, e -> {
            view.showLoading(false);
            view.showError(context.getString(R.string.error_database));
        });
    }

    /**
     * Handles form submission either inserting a new entry or updating the current one.
     */
    public void onSubmit(EnvironmentLogFormData data) {
        boolean hasValues = data != null && data.hasAnyValue();
        if (!hasValues && TextUtils.isEmpty(pendingPhotoUri)) {
            view.showEmptyFormError();
            return;
        }
        if (plantId <= 0) {
            view.showError(context.getString(R.string.error_select_plant));
            return;
        }
        if (editingEntry == null) {
            EnvironmentEntry entry = new EnvironmentEntry();
            entry.setPlantId(plantId);
            entry.setTimestamp(System.currentTimeMillis());
            applyFormData(entry, data);
            view.showLoading(true);
            environmentRepository.insertEnvironmentEntry(entry, () -> {
                view.showMessage(context.getString(R.string.environment_log_saved));
                view.clearForm();
                view.showEditingState(false);
                view.notifyLogEvent(EVENT_SAVED, entry.getId());
                pendingPhotoUri = null;
                editingOriginalPhotoUri = null;
                loadEntries();
            }, e -> {
                view.showLoading(false);
                view.showError(context.getString(R.string.error_database));
            });
        } else {
            String previousPhoto = editingOriginalPhotoUri;
            applyFormData(editingEntry, data);
            view.showLoading(true);
            environmentRepository.updateEnvironmentEntry(editingEntry, previousPhoto, () -> {
                view.showMessage(context.getString(R.string.environment_log_updated));
                view.clearForm();
                view.showEditingState(false);
                view.notifyLogEvent(EVENT_UPDATED, editingEntry.getId());
                editingEntry = null;
                pendingPhotoUri = null;
                editingOriginalPhotoUri = null;
                loadEntries();
            }, e -> {
                view.showLoading(false);
                view.showError(context.getString(R.string.error_database));
            });
        }
    }

    /**
     * Triggered when the user chooses an entry to edit.
     */
    public void onEntrySelected(EnvironmentLogItem item) {
        if (item == null) {
            return;
        }
        editingEntry = copyEntry(item.getEntry());
        editingOriginalPhotoUri = editingEntry.getPhotoUri();
        pendingPhotoUri = editingOriginalPhotoUri;
        view.populateForm(editingEntry);
        view.showPhotoPreview(pendingPhotoUri);
        view.showEditingState(true);
    }

    /**
     * Cancels the current edit flow.
     */
    public void onCancelEdit() {
        editingEntry = null;
        pendingPhotoUri = null;
        editingOriginalPhotoUri = null;
        view.clearForm();
        view.showPhotoPreview(null);
        view.showEditingState(false);
    }

    /**
     * Registers a newly selected photo for the current form.
     */
    public void onPhotoSelected(@NonNull String uri) {
        pendingPhotoUri = uri;
        view.showPhotoPreview(uri);
    }

    /**
     * Clears any pending photo from the form.
     */
    public void onPhotoRemoved() {
        pendingPhotoUri = null;
        view.showPhotoPreview(null);
    }

    /**
     * Restores the pending photo after configuration changes.
     */
    public void restorePendingPhoto(@Nullable String uri) {
        pendingPhotoUri = TextUtils.isEmpty(uri) ? null : uri;
        if (editingEntry == null) {
            editingOriginalPhotoUri = null;
        }
        view.showPhotoPreview(pendingPhotoUri);
    }

    /**
     * Deletes the given entry.
     */
    public void onDeleteEntry(EnvironmentLogItem item) {
        if (item == null) {
            return;
        }
        EnvironmentEntry entry = item.getEntry();
        view.showLoading(true);
        environmentRepository.deleteEnvironmentEntry(entry, () -> {
            if (editingEntry != null && editingEntry.getId() == entry.getId()) {
                onCancelEdit();
            }
            view.showMessage(context.getString(R.string.environment_log_deleted));
            view.notifyLogEvent(EVENT_DELETED, entry.getId());
            loadEntries();
        }, e -> {
            view.showLoading(false);
            view.showError(context.getString(R.string.error_database));
        });
    }

    private void applyFormData(EnvironmentEntry entry, EnvironmentLogFormData data) {
        entry.setTemperature(data.getTemperature());
        entry.setHumidity(data.getHumidity());
        entry.setSoilMoisture(data.getSoilMoisture());
        entry.setHeight(data.getHeight());
        entry.setWidth(data.getWidth());
        entry.setNotes(data.getNotes());
        entry.setPhotoUri(pendingPhotoUri);
    }

    private EnvironmentLogItem toItem(EnvironmentEntry entry) {
        String timestamp = dateFormat.format(new Date(entry.getTimestamp()));
        String metrics = buildMetricsSummary(entry);
        String notes = entry.getNotes();
        return new EnvironmentLogItem(copyEntry(entry), timestamp, metrics, notes, entry.getPhotoUri());
    }

    private void refreshCharts(List<EnvironmentEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            view.showGrowthChart(null);
            view.showClimateChart(null);
            return;
        }
        List<EnvironmentEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(EnvironmentEntry::getTimestamp));
        ChartData growth = buildChartData(sorted,
            new MetricSpec(context.getString(R.string.environment_log_chart_label_height), EnvironmentEntry::getHeight),
            new MetricSpec(context.getString(R.string.environment_log_chart_label_width), EnvironmentEntry::getWidth));
        ChartData climate = buildChartData(sorted,
            new MetricSpec(context.getString(R.string.environment_log_chart_label_temperature), EnvironmentEntry::getTemperature),
            new MetricSpec(context.getString(R.string.environment_log_chart_label_humidity), EnvironmentEntry::getHumidity),
            new MetricSpec(context.getString(R.string.environment_log_chart_label_natural_dli), EnvironmentEntry::getNaturalDli),
            new MetricSpec(context.getString(R.string.environment_log_chart_label_artificial_dli), EnvironmentEntry::getArtificialDli));
        view.showGrowthChart(growth);
        view.showClimateChart(climate);
    }

    @Nullable
    private ChartData buildChartData(List<EnvironmentEntry> entries, MetricSpec... specs) {
        List<ChartSeries> series = new ArrayList<>();
        for (MetricSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            List<ChartPoint> points = new ArrayList<>();
            for (EnvironmentEntry entry : entries) {
                Float value = spec.extractor.extract(entry);
                if (value != null) {
                    points.add(new ChartPoint(entry.getTimestamp(), value));
                }
            }
            if (points.size() >= 2) {
                series.add(new ChartSeries(spec.label, points));
            }
        }
        if (series.isEmpty()) {
            return null;
        }
        return new ChartData(series);
    }

    private String buildMetricsSummary(EnvironmentEntry entry) {
        List<String> parts = new ArrayList<>();
        if (entry.getTemperature() != null) {
            parts.add(context.getString(R.string.environment_log_metric_temperature, entry.getTemperature()));
        }
        if (entry.getHumidity() != null) {
            parts.add(context.getString(R.string.environment_log_metric_humidity, entry.getHumidity()));
        }
        if (entry.getSoilMoisture() != null) {
            parts.add(context.getString(R.string.environment_log_metric_soil_moisture, entry.getSoilMoisture()));
        }
        if (entry.getHeight() != null) {
            parts.add(context.getString(R.string.environment_log_metric_height, entry.getHeight()));
        }
        if (entry.getWidth() != null) {
            parts.add(context.getString(R.string.environment_log_metric_width, entry.getWidth()));
        }
        if (entry.getNaturalDli() != null) {
            parts.add(context.getString(R.string.environment_log_metric_natural_dli, entry.getNaturalDli()));
        }
        if (entry.getArtificialDli() != null) {
            parts.add(context.getString(R.string.environment_log_metric_artificial_dli, entry.getArtificialDli()));
        }
        if (parts.isEmpty()) {
            return context.getString(R.string.environment_log_metrics_empty);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(context.getString(R.string.environment_log_metrics_separator));
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private EnvironmentEntry copyEntry(EnvironmentEntry entry) {
        EnvironmentEntry copy = new EnvironmentEntry();
        copy.setId(entry.getId());
        copy.setPlantId(entry.getPlantId());
        copy.setTimestamp(entry.getTimestamp());
        copy.setTemperature(entry.getTemperature());
        copy.setHumidity(entry.getHumidity());
        copy.setSoilMoisture(entry.getSoilMoisture());
        copy.setHeight(entry.getHeight());
        copy.setWidth(entry.getWidth());
        copy.setNaturalDli(entry.getNaturalDli());
        copy.setArtificialDli(entry.getArtificialDli());
        copy.setArtificialHours(entry.getArtificialHours());
        copy.setNotes(entry.getNotes());
        copy.setPhotoUri(entry.getPhotoUri());
        return copy;
    }

    private LightSummary extractLightSummary(List<EnvironmentEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new LightSummary(null, null, null, null);
        }
        EnvironmentEntry latestNatural = null;
        EnvironmentEntry latestArtificial = null;
        for (EnvironmentEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            Float natural = entry.getNaturalDli();
            if (natural != null && isNewer(entry, latestNatural)) {
                latestNatural = entry;
            }
            Float artificial = entry.getArtificialDli();
            if (artificial != null && isNewer(entry, latestArtificial)) {
                latestArtificial = entry;
            }
        }
        Float naturalValue = latestNatural != null ? latestNatural.getNaturalDli() : null;
        Long naturalTimestamp = latestNatural != null ? latestNatural.getTimestamp() : null;
        Float artificialValue = latestArtificial != null ? latestArtificial.getArtificialDli() : null;
        Long artificialTimestamp = latestArtificial != null ? latestArtificial.getTimestamp() : null;
        return new LightSummary(naturalValue, naturalTimestamp, artificialValue, artificialTimestamp);
    }

    private boolean isNewer(EnvironmentEntry candidate, @Nullable EnvironmentEntry current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (candidate.getTimestamp() > current.getTimestamp()) {
            return true;
        }
        return candidate.getTimestamp() == current.getTimestamp()
            && candidate.getId() > current.getId();
    }

    private List<PhotoHighlight> buildPhotoHighlights(List<EnvironmentEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<PhotoHighlight> highlights = new ArrayList<>();
        for (EnvironmentEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String uri = entry.getPhotoUri();
            if (uri == null || uri.isEmpty()) {
                continue;
            }
            highlights.add(new PhotoHighlight(entry.getId(), entry.getTimestamp(),
                dateFormat.format(new Date(entry.getTimestamp())), uri));
        }
        if (highlights.size() <= 1) {
            return highlights;
        }
        highlights.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return highlights;
    }

    private interface ValueExtractor {
        @Nullable
        Float extract(EnvironmentEntry entry);
    }

    private static class MetricSpec {
        final String label;
        final ValueExtractor extractor;

        MetricSpec(String label, ValueExtractor extractor) {
            this.label = label;
            this.extractor = extractor;
        }
    }

    /**
     * Representation of chart data to be consumed by the view layer.
     */
    public static class ChartData {
        private final List<ChartSeries> series;

        ChartData(List<ChartSeries> series) {
            this.series = Collections.unmodifiableList(new ArrayList<>(series));
        }

        public List<ChartSeries> getSeries() {
            return series;
        }
    }

    /**
     * Single series of chart data points.
     */
    public static class ChartSeries {
        private final String label;
        private final List<ChartPoint> points;

        ChartSeries(String label, List<ChartPoint> points) {
            this.label = label;
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
        }

        public String getLabel() {
            return label;
        }

        public List<ChartPoint> getPoints() {
            return points;
        }
    }

    /**
     * Individual point within a chart series.
     */
    public static class ChartPoint {
        private final long timestamp;
        private final float value;

        ChartPoint(long timestamp, float value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public float getValue() {
            return value;
        }
    }

    /**
     * Representation of an entry adapted for the RecyclerView.
     */
    public static class EnvironmentLogItem {
        private final EnvironmentEntry entry;
        private final String timestampText;
        private final String metricsText;
        @Nullable
        private final String notes;
        @Nullable
        private final String photoUri;

        EnvironmentLogItem(EnvironmentEntry entry, String timestampText, String metricsText,
                           @Nullable String notes, @Nullable String photoUri) {
            this.entry = entry;
            this.timestampText = timestampText;
            this.metricsText = metricsText;
            this.notes = notes;
            this.photoUri = photoUri;
        }

        public EnvironmentEntry getEntry() {
            return entry;
        }

        public String getTimestampText() {
            return timestampText;
        }

        public String getMetricsText() {
            return metricsText;
        }

        @Nullable
        public String getNotes() {
            return notes;
        }

        @Nullable
        public String getPhotoUri() {
            return photoUri;
        }
    }

    /**
     * Highlight representing a logged photo for quick browsing.
     */
    public static class PhotoHighlight {
        private final long entryId;
        private final long timestamp;
        private final String label;
        private final String photoUri;

        PhotoHighlight(long entryId, long timestamp, String label, String photoUri) {
            this.entryId = entryId;
            this.timestamp = timestamp;
            this.label = label;
            this.photoUri = photoUri;
        }

        public long getEntryId() {
            return entryId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getLabel() {
            return label;
        }

        public String getPhotoUri() {
            return photoUri;
        }
    }
}
