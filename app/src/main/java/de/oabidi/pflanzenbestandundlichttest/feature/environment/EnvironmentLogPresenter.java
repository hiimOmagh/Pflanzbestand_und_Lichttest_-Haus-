package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;

/**
 * Presenter coordinating the environment log between the repository and the view.
 */
public class EnvironmentLogPresenter {
    private final EnvironmentLogView view;
    private final PlantRepository repository;
    private final long plantId;
    private final Context context;
    @Nullable
    private EnvironmentEntry editingEntry;
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

    public EnvironmentLogPresenter(@NonNull EnvironmentLogView view, @NonNull PlantRepository repository,
                                   long plantId, @NonNull Context context) {
        this.view = view;
        this.repository = repository;
        this.plantId = plantId;
        this.context = context.getApplicationContext();
    }

    /** Loads all environment entries for the current plant. */
    public void loadEntries() {
        if (plantId <= 0) {
            view.showEmptyState(true);
            view.updateChartPlaceholder(0);
            view.showError(context.getString(R.string.error_select_plant));
            view.showLoading(false);
            return;
        }
        view.showLoading(true);
        repository.environmentEntriesForPlant(plantId, entries -> {
            List<EnvironmentLogItem> items = new ArrayList<>(entries.size());
            for (EnvironmentEntry entry : entries) {
                items.add(toItem(entry));
            }
            view.showEntries(items);
            view.showEmptyState(items.isEmpty());
            view.updateChartPlaceholder(items.size());
            view.showLoading(false);
        }, e -> {
            view.showLoading(false);
            view.showError(context.getString(R.string.error_database));
        });
    }

    /** Handles form submission either inserting a new entry or updating the current one. */
    public void onSubmit(EnvironmentLogFormData data) {
        if (data == null || !data.hasAnyValue()) {
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
            repository.insertEnvironmentEntry(entry, () -> {
                view.showMessage(context.getString(R.string.environment_log_saved));
                view.clearForm();
                view.showEditingState(false);
                loadEntries();
            }, e -> {
                view.showLoading(false);
                view.showError(context.getString(R.string.error_database));
            });
        } else {
            applyFormData(editingEntry, data);
            view.showLoading(true);
            repository.updateEnvironmentEntry(editingEntry, () -> {
                view.showMessage(context.getString(R.string.environment_log_updated));
                view.clearForm();
                view.showEditingState(false);
                editingEntry = null;
                loadEntries();
            }, e -> {
                view.showLoading(false);
                view.showError(context.getString(R.string.error_database));
            });
        }
    }

    /** Triggered when the user chooses an entry to edit. */
    public void onEntrySelected(EnvironmentLogItem item) {
        if (item == null) {
            return;
        }
        editingEntry = copyEntry(item.getEntry());
        view.populateForm(editingEntry);
        view.showEditingState(true);
    }

    /** Cancels the current edit flow. */
    public void onCancelEdit() {
        editingEntry = null;
        view.clearForm();
        view.showEditingState(false);
    }

    /** Deletes the given entry. */
    public void onDeleteEntry(EnvironmentLogItem item) {
        if (item == null) {
            return;
        }
        EnvironmentEntry entry = item.getEntry();
        view.showLoading(true);
        repository.deleteEnvironmentEntry(entry, () -> {
            if (editingEntry != null && editingEntry.getId() == entry.getId()) {
                onCancelEdit();
            }
            view.showMessage(context.getString(R.string.environment_log_deleted));
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
    }

    private EnvironmentLogItem toItem(EnvironmentEntry entry) {
        String timestamp = dateFormat.format(new Date(entry.getTimestamp()));
        String metrics = buildMetricsSummary(entry);
        String notes = entry.getNotes();
        return new EnvironmentLogItem(copyEntry(entry), timestamp, metrics, notes);
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
        copy.setNotes(entry.getNotes());
        copy.setPhotoUri(entry.getPhotoUri());
        return copy;
    }

    /** Representation of an entry adapted for the RecyclerView. */
    public static class EnvironmentLogItem {
        private final EnvironmentEntry entry;
        private final String timestampText;
        private final String metricsText;
        @Nullable
        private final String notes;

        EnvironmentLogItem(EnvironmentEntry entry, String timestampText, String metricsText,
                           @Nullable String notes) {
            this.entry = entry;
            this.timestampText = timestampText;
            this.metricsText = metricsText;
            this.notes = notes;
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
    }
}
