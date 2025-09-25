package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.oabidi.pflanzenbestandundlichttest.repository.DiaryRepository;
import de.oabidi.pflanzenbestandundlichttest.repository.MeasurementRepository;

/** Presenter for loading statistics data. */
public class StatsPresenter {
    public interface View {
        void showPlants(List<Plant> plants);
        void showMeasurements(Map<Long, List<Measurement>> data);
        void showDiaryCounts(String text);
        void showDli(String text);
        void showError(String message);
    }

    private final View view;
    private final PlantRepository repository;
    private final MeasurementRepository measurementRepository;
    private final DiaryRepository diaryRepository;
    private final Context context;
    private static final int DLI_DAYS = 7;

    public StatsPresenter(View view, PlantRepository repository, Context context) {
        this.view = view;
        this.repository = repository;
        this.measurementRepository = repository.measurementRepository();
        this.diaryRepository = repository.diaryRepository();
        this.context = context.getApplicationContext();
    }

    /** Load all plants and pass them to the view. */
    public void loadPlants() {
        repository.getAllPlants(view::showPlants,
            e -> view.showError(context.getString(R.string.error_database)));
    }

    /** Load measurement and diary data for the given plants. */
    public void loadDataForPlants(List<Long> plantIds) {
        if (plantIds.isEmpty()) {
            view.showMeasurements(null);
            view.showDiaryCounts(context.getString(R.string.stats_no_diary_entries));
            view.showDli(context.getString(R.string.dli_placeholder));
            return;
        }

        Map<Long, List<Measurement>> data = new HashMap<>();
        Set<Long> remaining = new HashSet<>(plantIds);
        for (Long id : plantIds) {
            measurementRepository.recentMeasurementsForPlant(id, 30, list -> {
                synchronized (data) {
                    data.put(id, list);
                    remaining.remove(id);
                    if (remaining.isEmpty()) {
                        view.showMeasurements(data);
                    }
                }
            }, e -> view.showError(context.getString(R.string.error_database)));
        }

        if (plantIds.size() == 1) {
            long id = plantIds.get(0);
            diaryRepository.diaryEntriesForPlant(id, entries ->
                    view.showDiaryCounts(formatDiaryCounts(entries)),
                e -> view.showError(context.getString(R.string.error_database)));
            computeDli(id);
        } else {
            view.showDiaryCounts(context.getString(R.string.stats_no_diary_entries));
            view.showDli(context.getString(R.string.dli_placeholder));
        }
    }

    private void computeDli(long plantId) {
        long now = System.currentTimeMillis();
        long end = startOfDay(now) + 86400000L;
        long start = end - DLI_DAYS * 86400000L;
        measurementRepository.sumPpfdAndCountDays(plantId, start, end, result -> {
            if (result.days > 0) {
                float totalDli = result.sum * 0.0036f;
                float avgDli = totalDli / result.days;
                view.showDli(context.getString(R.string.format_dli, avgDli));
            } else {
                view.showDli(context.getString(R.string.dli_placeholder));
            }
        }, e -> view.showError(context.getString(R.string.error_database)));
    }

    private long startOfDay(long time) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String formatDiaryCounts(List<DiaryEntry> entries) {
        int water = 0;
        int fertilize = 0;
        int prune = 0;
        for (DiaryEntry e : entries) {
            String type = e.getType();
            if (DiaryEntry.TYPE_WATER.equals(type)) {
                water++;
            } else if (DiaryEntry.TYPE_FERTILIZE.equals(type)) {
                fertilize++;
            } else if (DiaryEntry.TYPE_PRUNE.equals(type)) {
                prune++;
            }
        }
        if (entries.isEmpty()) {
            return context.getString(R.string.stats_no_diary_entries);
        }
        String waterLabel = context.getString(R.string.diary_type_water);
        String fertilizeLabel = context.getString(R.string.diary_type_fertilize);
        String pruneLabel = context.getString(R.string.diary_type_prune);
        return context.getString(R.string.format_diary_counts,
            waterLabel, water,
            fertilizeLabel, fertilize,
            pruneLabel, prune);
    }
}
