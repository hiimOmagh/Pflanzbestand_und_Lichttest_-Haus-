package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.button.MaterialButton;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.common.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.common.ui.LineChartView;
import de.oabidi.pflanzenbestandundlichttest.data.EnvironmentEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying and editing the environment log for a plant.
 */
public class EnvironmentLogFragment extends Fragment implements EnvironmentLogView {
    public static final String TAG = "EnvironmentLogFragment";
    public static final String RESULT_KEY_CHANGES = "environment_log_changes";
    public static final String EXTRA_EVENT = "event";
    public static final String EXTRA_ENTRY_ID = "entry_id";
    private static final String ARG_PLANT_ID = "plantId";

    private long plantId;
    @Nullable
    private PlantRepository repository;
    @Nullable
    private EnvironmentLogPresenter presenter;
    @Nullable
    private EnvironmentLogAdapter adapter;

    @Nullable
    private TextInputLayout temperatureLayout;
    @Nullable
    private TextInputLayout humidityLayout;
    @Nullable
    private TextInputLayout soilMoistureLayout;
    @Nullable
    private TextInputLayout heightLayout;
    @Nullable
    private TextInputLayout widthLayout;
    @Nullable
    private TextInputLayout notesLayout;
    @Nullable
    private MaterialButton saveButton;
    @Nullable
    private MaterialButton cancelButton;
    @Nullable
    private TextView editingLabel;
    @Nullable
    private TextView emptyView;
    @Nullable
    private LineChartView overviewChartView;
    @Nullable
    private TextView overviewChartEmptyView;
    @Nullable
    private LineChartView trendsChartView;
    @Nullable
    private TextView trendsChartEmptyView;
    @Nullable
    private RecyclerView listView;
    @Nullable
    private View loadingView;

    /** Displays the fragment inside the provided container. */
    public static void show(@NonNull FragmentManager fragmentManager, int containerId, long plantId) {
        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(containerId, newInstance(plantId), TAG)
            .addToBackStack(TAG)
            .commit();
    }

    /** Creates a new instance configured for the supplied plant. */
    public static EnvironmentLogFragment newInstance(long plantId) {
        EnvironmentLogFragment fragment = new EnvironmentLogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLANT_ID, plantId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        repository = RepositoryProvider.getRepository(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            plantId = args.getLong(ARG_PLANT_ID, -1);
        }
        PlantRepository repo = repository;
        if (repo == null) {
            repo = RepositoryProvider.getRepository(requireContext());
            repository = repo;
        }
        presenter = new EnvironmentLogPresenter(this, repo, plantId, requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_environment_log, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.requestApplyInsetsWhenAttached(view);

        temperatureLayout = view.findViewById(R.id.environment_temperature_layout);
        humidityLayout = view.findViewById(R.id.environment_humidity_layout);
        soilMoistureLayout = view.findViewById(R.id.environment_soil_layout);
        heightLayout = view.findViewById(R.id.environment_height_layout);
        widthLayout = view.findViewById(R.id.environment_width_layout);
        notesLayout = view.findViewById(R.id.environment_notes_layout);
        saveButton = view.findViewById(R.id.environment_save_button);
        cancelButton = view.findViewById(R.id.environment_cancel_button);
        editingLabel = view.findViewById(R.id.environment_editing_label);
        emptyView = view.findViewById(R.id.environment_log_empty);
        overviewChartView = view.findViewById(R.id.environment_chart_overview);
        overviewChartEmptyView = view.findViewById(R.id.environment_chart_overview_empty);
        trendsChartView = view.findViewById(R.id.environment_chart_trends);
        trendsChartEmptyView = view.findViewById(R.id.environment_chart_trends_empty);
        listView = view.findViewById(R.id.environment_log_list);
        loadingView = view.findViewById(R.id.environment_log_loading);

        if (listView != null) {
            listView.setLayoutManager(new LinearLayoutManager(requireContext()));
            InsetsUtils.applySystemWindowInsetsPadding(listView, false, false, false, true);
            adapter = new EnvironmentLogAdapter(new EnvironmentLogAdapter.Callbacks() {
                @Override
                public void onEdit(EnvironmentLogPresenter.EnvironmentLogItem item) {
                    EnvironmentLogPresenter p = presenter;
                    if (p != null) {
                        p.onEntrySelected(item);
                    }
                }

                @Override
                public void onDelete(EnvironmentLogPresenter.EnvironmentLogItem item) {
                    confirmDelete(item);
                }
            });
            listView.setAdapter(adapter);
        }

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> submitForm());
        }
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                EnvironmentLogPresenter p = presenter;
                if (p != null) {
                    p.onCancelEdit();
                }
            });
        }

        EnvironmentLogPresenter p = presenter;
        if (p != null) {
            p.loadEntries();
        }
    }

    private void submitForm() {
        clearErrors();
        boolean parseFailed = false;
        Float temperature = parseFloatOrNull(temperatureLayout);
        if (temperatureLayout != null && temperatureLayout.getError() != null) {
            parseFailed = true;
        }
        Float humidity = parseFloatOrNull(humidityLayout);
        if (humidityLayout != null && humidityLayout.getError() != null) {
            parseFailed = true;
        }
        Float soil = parseFloatOrNull(soilMoistureLayout);
        if (soilMoistureLayout != null && soilMoistureLayout.getError() != null) {
            parseFailed = true;
        }
        Float height = parseFloatOrNull(heightLayout);
        if (heightLayout != null && heightLayout.getError() != null) {
            parseFailed = true;
        }
        Float width = parseFloatOrNull(widthLayout);
        if (widthLayout != null && widthLayout.getError() != null) {
            parseFailed = true;
        }
        String notes = getText(notesLayout);
        if (parseFailed) {
            return;
        }
        EnvironmentLogPresenter p = presenter;
        if (p != null) {
            p.onSubmit(new EnvironmentLogFormData(temperature, humidity, soil, height, width, notes));
        }
    }

    private void confirmDelete(EnvironmentLogPresenter.EnvironmentLogItem item) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.environment_log_delete_title)
            .setMessage(R.string.environment_log_delete_confirm)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                EnvironmentLogPresenter p = presenter;
                if (p != null) {
                    p.onDeleteEntry(item);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void clearErrors() {
        if (temperatureLayout != null) temperatureLayout.setError(null);
        if (humidityLayout != null) humidityLayout.setError(null);
        if (soilMoistureLayout != null) soilMoistureLayout.setError(null);
        if (heightLayout != null) heightLayout.setError(null);
        if (widthLayout != null) widthLayout.setError(null);
    }

    @Nullable
    private Float parseFloatOrNull(@Nullable TextInputLayout layout) {
        if (layout == null) {
            return null;
        }
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText == null) {
            return null;
        }
        CharSequence text = editText.getText();
        String value = text != null ? text.toString().trim() : "";
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.environment_log_error_invalid_number));
            return null;
        }
    }

    @Nullable
    private String getText(@Nullable TextInputLayout layout) {
        if (layout == null) {
            return null;
        }
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText == null) {
            return null;
        }
        CharSequence text = editText.getText();
        if (text == null) {
            return null;
        }
        String value = text.toString().trim();
        return value.isEmpty() ? null : value;
    }

    @Override
    public void showEntries(List<EnvironmentLogPresenter.EnvironmentLogItem> items) {
        if (adapter != null) {
            adapter.submitList(items);
        }
        if (listView != null) {
            listView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void showEmptyState(boolean isEmpty) {
        if (emptyView != null) {
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void showGrowthChart(@Nullable EnvironmentLogPresenter.ChartData data) {
        renderChart(overviewChartView, overviewChartEmptyView, data);
    }

    @Override
    public void showClimateChart(@Nullable EnvironmentLogPresenter.ChartData data) {
        renderChart(trendsChartView, trendsChartEmptyView, data);
    }

    @Override
    public void showMessage(String message) {
        if (!isAdded()) {
            return;
        }
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void showError(String message) {
        if (!isAdded()) {
            return;
        }
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void showLoading(boolean loading) {
        if (loadingView != null) {
            loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (saveButton != null) {
            saveButton.setEnabled(!loading);
        }
        if (cancelButton != null) {
            cancelButton.setEnabled(!loading);
        }
    }

    @Override
    public void showEmptyFormError() {
        if (!isAdded()) {
            return;
        }
        Snackbar.make(requireView(), R.string.environment_log_error_empty_form, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void clearForm() {
        clearErrors();
        clearText(temperatureLayout);
        clearText(humidityLayout);
        clearText(soilMoistureLayout);
        clearText(heightLayout);
        clearText(widthLayout);
        clearText(notesLayout);
    }

    private void clearText(@Nullable TextInputLayout layout) {
        if (layout == null) {
            return;
        }
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText != null) {
            editText.setText(null);
        }
    }

    @Override
    public void populateForm(EnvironmentEntry entry) {
        setText(temperatureLayout, entry.getTemperature());
        setText(humidityLayout, entry.getHumidity());
        setText(soilMoistureLayout, entry.getSoilMoisture());
        setText(heightLayout, entry.getHeight());
        setText(widthLayout, entry.getWidth());
        setText(notesLayout, entry.getNotes());
    }

    private void setText(@Nullable TextInputLayout layout, @Nullable Float value) {
        if (layout == null) {
            return;
        }
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText != null) {
            if (value == null) {
                editText.setText(null);
            } else {
                editText.setText(String.valueOf(value));
            }
        }
    }

    private void setText(@Nullable TextInputLayout layout, @Nullable String value) {
        if (layout == null) {
            return;
        }
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText != null) {
            editText.setText(value);
        }
    }

    private void renderChart(@Nullable LineChartView chartView, @Nullable TextView emptyChartView,
                             @Nullable EnvironmentLogPresenter.ChartData data) {
        if (chartView == null || emptyChartView == null) {
            return;
        }
        if (data == null || data.getSeries().isEmpty()) {
            chartView.setSeries(null);
            chartView.setVisibility(View.GONE);
            emptyChartView.setVisibility(View.VISIBLE);
            return;
        }
        List<LineChartView.LineSeries> series = new ArrayList<>();
        for (EnvironmentLogPresenter.ChartSeries chartSeries : data.getSeries()) {
            List<LineChartView.Point> points = new ArrayList<>();
            for (EnvironmentLogPresenter.ChartPoint point : chartSeries.getPoints()) {
                points.add(new LineChartView.Point(point.getTimestamp(), point.getValue()));
            }
            series.add(new LineChartView.LineSeries(chartSeries.getLabel(), points));
        }
        chartView.setSeries(series);
        chartView.setVisibility(View.VISIBLE);
        emptyChartView.setVisibility(View.GONE);
    }

    @Override
    public void showEditingState(boolean editing) {
        if (editingLabel != null) {
            editingLabel.setVisibility(editing ? View.VISIBLE : View.GONE);
        }
        if (cancelButton != null) {
            cancelButton.setVisibility(editing ? View.VISIBLE : View.GONE);
        }
        if (saveButton != null) {
            saveButton.setText(editing ? R.string.environment_log_update_entry
                : R.string.environment_log_add_entry);
        }
    }

    @Override
    public void notifyLogEvent(@NonNull String event, long entryId) {
        Bundle result = new Bundle();
        result.putString(EXTRA_EVENT, event);
        result.putLong(EXTRA_ENTRY_ID, entryId);
        getParentFragmentManager().setFragmentResult(RESULT_KEY_CHANGES, result);
    }
}
