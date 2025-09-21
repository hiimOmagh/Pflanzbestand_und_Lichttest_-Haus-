package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import de.oabidi.pflanzenbestandundlichttest.common.ui.InsetsUtils;

/**
 * Fragment displaying all measurements for a plant.
 */
public class MeasurementListFragment extends Fragment {
    private static final String ARG_PLANT_ID = "plantId";

    private static final int FILTER_ALL = 0;
    private static final int FILTER_LAST_7_DAYS = 1;
    private static final int FILTER_LAST_30_DAYS = 2;

    private long plantId = -1;
    private PlantRepository repository;
    private MeasurementAdapter adapter;
    private Spinner filterSpinner;

    /**
     * Creates a new instance showing measurements for the given plant.
     */
    public static MeasurementListFragment newInstance(long plantId, PlantRepository repository) {
        MeasurementListFragment fragment = new MeasurementListFragment();
        fragment.repository = repository;
        Bundle args = new Bundle();
        args.putLong(ARG_PLANT_ID, plantId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            plantId = args.getLong(ARG_PLANT_ID, -1);
        }
        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_measurement_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.requestApplyInsetsWhenAttached(view);
        RecyclerView listView = view.findViewById(R.id.measurement_list);
        listView.setLayoutManager(new LinearLayoutManager(requireContext()));
        listView.setClipToPadding(false);
        InsetsUtils.applySystemWindowInsetsPadding(listView, false, false, false, true);
        filterSpinner = view.findViewById(R.id.measurement_filter_spinner);
        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.measurement_filter_options,
            android.R.layout.simple_spinner_item);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(filterAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                loadMeasurements();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        adapter = new MeasurementAdapter(measurement -> {
            EditText input = new EditText(requireContext());
            input.setHint(R.string.measurement_add_note);
            input.setText(measurement.getNote());
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.measurement_add_note)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String note = input.getText().toString().trim();
                    measurement.setNote(note.isEmpty() ? null : note);
                    repository.updateMeasurement(measurement, this::loadMeasurements);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        }, measurement -> {
            String message = getString(R.string.confirm_delete_measurement);
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_delete_measurement)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                    repository.deleteMeasurement(measurement, () -> {
                        loadMeasurements();
                        Snackbar.make(requireView(), R.string.measurement_deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_undo, v -> repository.insertMeasurement(measurement, this::loadMeasurements))
                            .show();
                    }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
        listView.setAdapter(adapter);
        loadMeasurements();
    }

    private void loadMeasurements() {
        if (plantId < 0) {
            return;
        }
        if (filterSpinner == null) {
            repository.measurementsForPlantSince(plantId, Long.MIN_VALUE, adapter::submitList,
                e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); });
            return;
        }

        int position = filterSpinner.getSelectedItemPosition();
        long now = System.currentTimeMillis();
        if (position == FILTER_LAST_7_DAYS) {
            long since = now - 7L * 24 * 60 * 60 * 1000;
            repository.measurementsForPlantSince(plantId, since, adapter::submitList,
                e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); });
        } else if (position == FILTER_LAST_30_DAYS) {
            long since = now - 30L * 24 * 60 * 60 * 1000;
            repository.measurementsForPlantSince(plantId, since, adapter::submitList,
                e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); });
        } else {
            repository.measurementsForPlantSince(plantId, Long.MIN_VALUE, adapter::submitList,
                e -> { if (isAdded()) Snackbar.make(requireView(), R.string.error_database, Snackbar.LENGTH_LONG).show(); });
        }
    }
}
