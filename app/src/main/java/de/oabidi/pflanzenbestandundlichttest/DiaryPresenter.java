package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import java.util.List;

/** Presenter for managing diary entries. */
public class DiaryPresenter {
    public interface View {
        void showEntries(List<DiaryEntry> entries);
    }

    private final View view;
    private final PlantRepository repository;
    private final long plantId;
    private String query = "";

    public DiaryPresenter(View view, Context context, long plantId) {
        this.view = view;
        this.repository = ((PlantApp) context.getApplicationContext()).getRepository();
        this.plantId = plantId;
    }

    /** Load entries matching the given search query. */
    public void loadEntries(String query) {
        this.query = query != null ? query : "";
        repository.searchDiaryEntries(plantId, this.query, view::showEntries);
    }

    /** Insert a new diary entry and refresh the list. */
    public void insertEntry(DiaryEntry entry) {
        repository.insertDiaryEntry(entry, this::reload);
    }

    /** Update an existing entry and refresh. */
    public void updateEntry(DiaryEntry entry) {
        repository.updateDiaryEntry(entry, this::reload);
    }

    /** Delete an entry and run the provided callback after completion. */
    public void deleteEntry(DiaryEntry entry, Runnable afterDelete) {
        repository.deleteDiaryEntry(entry, () -> {
            reload();
            if (afterDelete != null) {
                afterDelete.run();
            }
        });
    }

    private void reload() {
        loadEntries(query);
    }
}
