package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.button.MaterialButton;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.core.ui.LineChartView;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.LightSummary;
import de.oabidi.pflanzenbestandundlichttest.feature.camera.PlantPhotoCaptureFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.gallery.PlantPhotoViewerFragment;
import de.oabidi.pflanzenbestandundlichttest.feature.environment.EnvironmentPhotoAdapter;
import de.oabidi.pflanzenbestandundlichttest.repository.EnvironmentRepository;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
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
    private static final String STATE_PHOTO_URI = "state_photo_uri";
    private final NumberFormat naturalDliFormat = NumberFormat.getNumberInstance();
    private final DateFormat naturalDliDateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    private long plantId;
    @Nullable
    private PlantRepository repository;
    @Nullable
    private EnvironmentRepository environmentRepository;
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
    private View emptyView;
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
    @Nullable
    private ShapeableImageView photoPreview;
    @Nullable
    private MaterialButton photoPickButton;
    @Nullable
    private MaterialButton photoCaptureButton;
    @Nullable
    private MaterialButton photoRemoveButton;
    @Nullable
    private RecyclerView photoHighlightsView;
    @Nullable
    private TextView photoHighlightsEmptyView;
    @Nullable
    private TextView photoHighlightsTitleView;
    @Nullable
    private EnvironmentPhotoAdapter photoHighlightsAdapter;
    @Nullable
    private View lightSummaryCard;
    @Nullable
    private TextView naturalDliValueView;
    @Nullable
    private TextView naturalDliTimestampView;
    @Nullable
    private TextView artificialDliValueView;
    @Nullable
    private TextView artificialDliTimestampView;
    @Nullable
    private ActivityResultLauncher<String> photoPickerLauncher;
    @Nullable
    private Uri currentPhotoUri;
    private boolean awaitingCaptureResult;
    private boolean captureResultDelivered;

    /**
     * Displays the fragment inside the provided container.
     */
    public static void show(@NonNull FragmentManager fragmentManager, int containerId, long plantId) {
        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(containerId, newInstance(plantId), TAG)
            .addToBackStack(TAG)
            .commit();
    }

    /**
     * Creates a new instance configured for the supplied plant.
     */
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
        environmentRepository = RepositoryProvider.getEnvironmentRepository(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            String photo = savedInstanceState.getString(STATE_PHOTO_URI);
            if (!TextUtils.isEmpty(photo)) {
                currentPhotoUri = Uri.parse(photo);
            }
        }
        naturalDliFormat.setMinimumFractionDigits(1);
        naturalDliFormat.setMaximumFractionDigits(2);
        Bundle args = getArguments();
        if (args != null) {
            plantId = args.getLong(ARG_PLANT_ID, -1);
        }
        PlantRepository repo = repository;
        if (repo == null) {
            repo = RepositoryProvider.getRepository(requireContext());
            repository = repo;
        }
        if (environmentRepository == null) {
            environmentRepository = repo.environmentRepository();
        }
        presenter = new EnvironmentLogPresenter(this, environmentRepository, plantId, requireContext());
        if (currentPhotoUri != null) {
            presenter.restorePendingPhoto(currentPhotoUri.toString());
        }
        photoPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            Context context = requireContext();
            try {
                context.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            EnvironmentLogPresenter p = presenter;
            if (p != null) {
                p.onPhotoSelected(uri.toString());
            }
        });
        getParentFragmentManager().setFragmentResultListener(
            PlantPhotoCaptureFragment.RESULT_KEY,
            this,
            (requestKey, bundle) -> {
                if (!awaitingCaptureResult || bundle == null) {
                    return;
                }
                awaitingCaptureResult = false;
                String uriString = bundle.getString(PlantPhotoCaptureFragment.EXTRA_PHOTO_URI);
                if (!TextUtils.isEmpty(uriString)) {
                    EnvironmentLogPresenter p = presenter;
                    if (p != null) {
                        p.onPhotoSelected(uriString);
                        captureResultDelivered = true;
                    }
                }
            }
        );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentPhotoUri != null) {
            outState.putString(STATE_PHOTO_URI, currentPhotoUri.toString());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (awaitingCaptureResult && !captureResultDelivered) {
            awaitingCaptureResult = false;
        }
        captureResultDelivered = false;
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
        emptyView = view.findViewById(R.id.environment_log_empty_state);
        overviewChartView = view.findViewById(R.id.environment_chart_overview);
        overviewChartEmptyView = view.findViewById(R.id.environment_chart_overview_empty);
        trendsChartView = view.findViewById(R.id.environment_chart_trends);
        trendsChartEmptyView = view.findViewById(R.id.environment_chart_trends_empty);
        listView = view.findViewById(R.id.environment_log_list);
        loadingView = view.findViewById(R.id.environment_log_loading);
        photoPreview = view.findViewById(R.id.environment_photo_preview);
        photoPickButton = view.findViewById(R.id.environment_photo_button);
        photoCaptureButton = view.findViewById(R.id.environment_capture_button);
        photoRemoveButton = view.findViewById(R.id.environment_remove_photo_button);
        photoHighlightsView = view.findViewById(R.id.environment_photo_carousel);
        photoHighlightsEmptyView = view.findViewById(R.id.environment_photo_empty);
        photoHighlightsTitleView = view.findViewById(R.id.environment_photo_section_title);
        lightSummaryCard = view.findViewById(R.id.environment_light_card);
        naturalDliValueView = view.findViewById(R.id.environment_light_natural_value);
        naturalDliTimestampView = view.findViewById(R.id.environment_light_natural_timestamp);
        artificialDliValueView = view.findViewById(R.id.environment_light_artificial_value);
        artificialDliTimestampView = view.findViewById(R.id.environment_light_artificial_timestamp);

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

                @Override
                public void onPhotoClicked(EnvironmentLogPresenter.EnvironmentLogItem item) {
                    openPhotoUri(item.getPhotoUri());
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

        if (photoPickButton != null) {
            photoPickButton.setOnClickListener(v -> {
                if (photoPickerLauncher != null) {
                    photoPickerLauncher.launch("image/*");
                }
            });
        }
        if (photoCaptureButton != null) {
            photoCaptureButton.setOnClickListener(v -> launchCameraCapture());
        }
        if (photoRemoveButton != null) {
            photoRemoveButton.setOnClickListener(v -> {
                EnvironmentLogPresenter p = presenter;
                if (p != null) {
                    p.onPhotoRemoved();
                }
            });
        }
        if (photoPreview != null) {
            photoPreview.setOnClickListener(v -> {
                if (currentPhotoUri != null) {
                    openPhotoUri(currentPhotoUri.toString());
                }
            });
        }
        if (photoHighlightsView != null) {
            photoHighlightsView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
            photoHighlightsAdapter = new EnvironmentPhotoAdapter(uriString -> openPhotoUri(uriString));
            photoHighlightsView.setAdapter(photoHighlightsAdapter);
        }
        updatePhotoPreview();

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
            p.onSubmit(new EnvironmentLogFormData(temperature, humidity, soil, height, width, notes, null));
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
        boolean hasItems = items != null && !items.isEmpty();
        if (listView != null) {
            listView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void showEmptyState(boolean isEmpty) {
        if (emptyView != null) {
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (listView != null) {
            listView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
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
    public void showPhotoHighlights(List<EnvironmentLogPresenter.PhotoHighlight> highlights) {
        if (photoHighlightsAdapter != null) {
            photoHighlightsAdapter.submit(highlights);
        }
        boolean empty = highlights == null || highlights.isEmpty();
        if (photoHighlightsView != null) {
            photoHighlightsView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
        if (photoHighlightsEmptyView != null) {
            photoHighlightsEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (photoHighlightsTitleView != null) {
            photoHighlightsTitleView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void showLightSummary(@NonNull LightSummary summary) {
        if (lightSummaryCard == null || naturalDliValueView == null || naturalDliTimestampView == null
            || artificialDliValueView == null || artificialDliTimestampView == null) {
            return;
        }
        Float natural = summary.getNaturalDli();
        Float artificial = summary.getArtificialDli();
        if (natural == null && artificial == null) {
            lightSummaryCard.setVisibility(View.GONE);
            naturalDliValueView.setText(R.string.environment_log_latest_natural_placeholder);
            naturalDliTimestampView.setText(null);
            naturalDliTimestampView.setVisibility(View.GONE);
            artificialDliValueView.setText(R.string.environment_log_latest_artificial_placeholder);
            artificialDliTimestampView.setText(null);
            artificialDliTimestampView.setVisibility(View.GONE);
            return;
        }
        lightSummaryCard.setVisibility(View.VISIBLE);
        if (natural != null) {
            naturalDliValueView.setText(getString(R.string.environment_log_latest_natural_value,
                naturalDliFormat.format(natural)));
            Long timestamp = summary.getNaturalTimestamp();
            if (timestamp != null) {
                String formatted = naturalDliDateFormat.format(new Date(timestamp));
                naturalDliTimestampView.setVisibility(View.VISIBLE);
                naturalDliTimestampView.setText(getString(R.string.environment_log_latest_natural_updated, formatted));
            } else {
                naturalDliTimestampView.setText(null);
                naturalDliTimestampView.setVisibility(View.GONE);
            }
        } else {
            naturalDliValueView.setText(R.string.environment_log_latest_natural_placeholder);
            naturalDliTimestampView.setText(null);
            naturalDliTimestampView.setVisibility(View.GONE);
        }

        if (artificial != null) {
            artificialDliValueView.setText(getString(R.string.environment_log_latest_artificial_value,
                naturalDliFormat.format(artificial)));
            Long timestamp = summary.getArtificialTimestamp();
            if (timestamp != null) {
                String formatted = naturalDliDateFormat.format(new Date(timestamp));
                artificialDliTimestampView.setVisibility(View.VISIBLE);
                artificialDliTimestampView.setText(getString(R.string.environment_log_latest_artificial_updated, formatted));
            } else {
                artificialDliTimestampView.setText(null);
                artificialDliTimestampView.setVisibility(View.GONE);
            }
        } else {
            artificialDliValueView.setText(R.string.environment_log_latest_artificial_placeholder);
            artificialDliTimestampView.setText(null);
            artificialDliTimestampView.setVisibility(View.GONE);
        }
    }

    @Override
    public void showPhotoPreview(@Nullable String photoUri) {
        if (TextUtils.isEmpty(photoUri)) {
            currentPhotoUri = null;
        } else {
            currentPhotoUri = Uri.parse(photoUri);
        }
        updatePhotoPreview();
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
        currentPhotoUri = null;
        updatePhotoPreview();
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

    private void updatePhotoPreview() {
        if (photoPreview == null) {
            return;
        }
        if (currentPhotoUri == null) {
            photoPreview.setImageResource(android.R.drawable.ic_menu_gallery);
            photoPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            if (photoRemoveButton != null) {
                photoRemoveButton.setVisibility(View.GONE);
            }
        } else {
            photoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photoPreview.setImageURI(currentPhotoUri);
            if (photoRemoveButton != null) {
                photoRemoveButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void launchCameraCapture() {
        captureResultDelivered = false;
        awaitingCaptureResult = true;
        PlantPhotoCaptureFragment.show(getParentFragmentManager(), android.R.id.content);
    }

    private void openPhotoUri(@Nullable String uriString) {
        if (TextUtils.isEmpty(uriString) || !isAdded()) {
            return;
        }
        ArrayList<String> uris = new ArrayList<>();
        uris.add(uriString);
        PlantPhotoViewerFragment.showForUris(
            getParentFragmentManager(), uris,
            getString(R.string.environment_log_photo_viewer_title));
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
