package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying all measurements for a plant.
 */
public class MeasurementListFragment extends Fragment {
    private static final String ARG_PLANT_ID = "plantId";

    private long plantId = -1;
    private PlantRepository repository;
    private MeasurementAdapter adapter;
    private Spinner filterSpinner;

    /**
     * Creates a new instance showing measurements for the given plant.
     */
    public static MeasurementListFragment newInstance(long plantId) {
        MeasurementListFragment fragment = new MeasurementListFragment();
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
        repository = new PlantRepository(requireContext().getApplicationContext());
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
        RecyclerView listView = view.findViewById(R.id.measurement_list);
        listView.setLayoutManager(new LinearLayoutManager(requireContext()));
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
        adapter = new MeasurementAdapter(measurement -> new AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_delete_measurement)
            .setMessage(R.string.confirm_delete_measurement)
            .setPositiveButton(android.R.string.ok, (d, w) ->
                repository.deleteMeasurement(measurement, () -> {
                    loadMeasurements();
                    Snackbar.make(requireView(), R.string.measurement_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo, v -> repository.insertMeasurement(measurement, this::loadMeasurements))
                        .show();
                }))
            .setNegativeButton(android.R.string.cancel, null)
            .show());
        listView.setAdapter(adapter);
        loadMeasurements();
    }

    private void loadMeasurements() {
        if (plantId < 0) {
            return;
        }
        repository.recentMeasurementsForPlant(plantId, Integer.MAX_VALUE, result -> {
            if (filterSpinner != null) {
                String selected = (String) filterSpinner.getSelectedItem();
                long threshold = Long.MIN_VALUE;
                long now = System.currentTimeMillis();
                if (getString(R.string.filter_last_7_days).equals(selected)) {
                    threshold = now - 7L * 24 * 60 * 60 * 1000;
                } else if (getString(R.string.filter_last_30_days).equals(selected)) {
                    threshold = now - 30L * 24 * 60 * 60 * 1000;
                }
                if (threshold != Long.MIN_VALUE) {
                    List<Measurement> filtered = new ArrayList<>();
                    for (Measurement m : result) {
                        if (m.getTimeEpoch() >= threshold) {
                            filtered.add(m);
                        }
                    }
                    adapter.submitList(filtered);
                    return;
                }
            }
            adapter.submitList(result);
        });
    }
}
