package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import java.util.List;

/** Presenter for managing diary entries. */
public class DiaryPresenter {
    public interface View {
        void showEntries(List<DiaryEntry> entries);
        void showError(String message);
    }

    private final View view;
    private final PlantRepository repository;
    private final long plantId;
    private final Context context;
    private String query = "";

    public DiaryPresenter(View view, PlantRepository repository, long plantId, Context context) {
        this.view = view;
        this.repository = repository;
        this.plantId = plantId;
        this.context = context.getApplicationContext();
    }

    /** Load entries matching the given search query. */
    public void loadEntries(String query) {
        this.query = query != null ? query : "";
        repository.searchDiaryEntries(plantId, this.query, view::showEntries,
            e -> view.showError(context.getString(R.string.error_database)));
    }

    /** Insert a new diary entry and refresh the list. */
    public void insertEntry(DiaryEntry entry) {
        repository.insertDiaryEntry(entry, this::reload,
            e -> view.showError(context.getString(R.string.error_database)));
    }

    /** Update an existing entry and refresh. */
    public void updateEntry(DiaryEntry entry) {
        repository.updateDiaryEntry(entry, this::reload,
            e -> view.showError(context.getString(R.string.error_database)));
    }

    /** Delete an entry and run the provided callback after completion. */
    public void deleteEntry(DiaryEntry entry, Runnable afterDelete) {
        repository.deleteDiaryEntry(entry, () -> {
            reload();
            if (afterDelete != null) {
                afterDelete.run();
            }
        }, e -> view.showError(context.getString(R.string.error_database)));
    }

    private void reload() {
        loadEntries(query);
    }
}
