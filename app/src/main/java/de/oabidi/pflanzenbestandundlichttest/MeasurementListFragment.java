package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;

/**
 * Fragment displaying all measurements for a plant.
 */
public class MeasurementListFragment extends Fragment {
    private static final String ARG_PLANT_ID = "plantId";

    private long plantId = -1;
    private PlantRepository repository;
    private MeasurementAdapter adapter;

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
        repository.recentMeasurementsForPlant(plantId, Integer.MAX_VALUE,
            result -> adapter.submitList(result));
    }
}
