package de.oabidi.pflanzenbestandundlichttest.feature.lighting;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;

/**
 * Dialog fragment for creating or editing {@link LedProfile} entries.
 */
public class LedProfileEditorFragment extends DialogFragment implements LedProfileScheduleAdapter.OnEntriesChangedListener {

    private static final String ARG_ID = "arg_id";
    private static final String ARG_NAME = "arg_name";
    private static final String ARG_TYPE = "arg_type";
    private static final String ARG_DISTANCE = "arg_distance";
    private static final String ARG_AMBIENT = "arg_ambient";
    private static final String ARG_CAMERA = "arg_camera";
    private static final String ARG_SCHEDULE_STARTS = "arg_schedule_starts";
    private static final String ARG_SCHEDULE_ENDS = "arg_schedule_ends";
    private static final String ARG_SCHEDULE_INTENSITIES = "arg_schedule_intensities";

    private PlantRepository repository;
    private TextInputEditText nameInput;
    private TextInputEditText typeInput;
    private TextInputEditText distanceInput;
    private TextInputEditText ambientInput;
    private TextInputEditText cameraInput;
    private LedProfileScheduleAdapter scheduleAdapter;
    private Button positiveButton;

    private boolean entriesValid = true;

    public static LedProfileEditorFragment newInstance(@Nullable LedProfile profile) {
        LedProfileEditorFragment fragment = new LedProfileEditorFragment();
        if (profile != null) {
            Bundle args = new Bundle();
            args.putLong(ARG_ID, profile.getId());
            args.putString(ARG_NAME, profile.getName());
            args.putString(ARG_TYPE, profile.getType());
            if (profile.getMountingDistanceCm() != null) {
                args.putFloat(ARG_DISTANCE, profile.getMountingDistanceCm());
            }
            Float ambient = profile.getCalibrationFactors().get(LedProfile.CALIBRATION_KEY_AMBIENT);
            if (ambient != null) {
                args.putFloat(ARG_AMBIENT, ambient);
            }
            Float camera = profile.getCalibrationFactors().get(LedProfile.CALIBRATION_KEY_CAMERA);
            if (camera != null) {
                args.putFloat(ARG_CAMERA, camera);
            }
            List<LedProfile.ScheduleEntry> schedule = profile.getSchedule();
            if (schedule != null && !schedule.isEmpty()) {
                ArrayList<String> starts = new ArrayList<>(schedule.size());
                ArrayList<String> ends = new ArrayList<>(schedule.size());
                ArrayList<Integer> intensities = new ArrayList<>(schedule.size());
                for (LedProfile.ScheduleEntry entry : schedule) {
                    starts.add(entry.getStartTime());
                    ends.add(entry.getEndTime());
                    intensities.add(entry.getIntensityPercent());
                }
                args.putStringArrayList(ARG_SCHEDULE_STARTS, starts);
                args.putStringArrayList(ARG_SCHEDULE_ENDS, ends);
                args.putIntegerArrayList(ARG_SCHEDULE_INTENSITIES, intensities);
            }
            fragment.setArguments(args);
        }
        return fragment;
    }

    private static String safeTrim(@Nullable CharSequence cs) {
        if (cs == null) {
            return null;
        }
        String trimmed = cs.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static Float parseFloat(@Nullable CharSequence cs) {
        if (cs == null) {
            return null;
        }
        String trimmed = cs.toString().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setRepository(PlantRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (repository == null) {
            repository = PlantRepositoryProvider.fromContext(requireContext());
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View view = inflater.inflate(R.layout.dialog_led_profile_editor, null);

        nameInput = view.findViewById(R.id.input_profile_name);
        typeInput = view.findViewById(R.id.input_profile_type);
        distanceInput = view.findViewById(R.id.input_profile_distance);
        ambientInput = view.findViewById(R.id.input_calibration_ambient);
        cameraInput = view.findViewById(R.id.input_calibration_camera);
        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePositiveButtonState();
            }
        });
        androidx.recyclerview.widget.RecyclerView scheduleList = view.findViewById(R.id.list_schedule_entries);
        scheduleList.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleAdapter = new LedProfileScheduleAdapter(this);
        scheduleList.setAdapter(scheduleAdapter);
        Button addEntryButton = view.findViewById(R.id.btn_add_schedule_entry);
        addEntryButton.setOnClickListener(v -> scheduleAdapter.addEntry());

        populateFromArgs();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setTitle(hasExistingProfile() ? R.string.action_edit_led_profile : R.string.action_add_led_profile)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss());

        if (hasExistingProfile()) {
            builder.setNeutralButton(R.string.action_delete_led_profile, (dialog, which) -> confirmDelete());
        }

        Dialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            positiveButton = ((android.app.AlertDialog) dialog).getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> saveProfile());
            updatePositiveButtonState();
        });
        return dialog;
    }

    private void populateFromArgs() {
        Bundle args = getArguments();
        if (args == null) {
            scheduleAdapter.setEntries(null);
            return;
        }
        nameInput.setText(args.getString(ARG_NAME));
        typeInput.setText(args.getString(ARG_TYPE));
        if (args.containsKey(ARG_DISTANCE)) {
            distanceInput.setText(Float.toString(args.getFloat(ARG_DISTANCE)));
        }
        if (args.containsKey(ARG_AMBIENT)) {
            ambientInput.setText(Float.toString(args.getFloat(ARG_AMBIENT)));
        }
        if (args.containsKey(ARG_CAMERA)) {
            cameraInput.setText(Float.toString(args.getFloat(ARG_CAMERA)));
        }
        ArrayList<String> starts = args.getStringArrayList(ARG_SCHEDULE_STARTS);
        ArrayList<String> ends = args.getStringArrayList(ARG_SCHEDULE_ENDS);
        ArrayList<Integer> intensities = args.getIntegerArrayList(ARG_SCHEDULE_INTENSITIES);
        if (starts != null && ends != null && intensities != null
            && starts.size() == ends.size() && starts.size() == intensities.size()) {
            List<LedProfile.ScheduleEntry> schedule = new ArrayList<>(starts.size());
            for (int i = 0; i < starts.size(); i++) {
                LedProfile.ScheduleEntry entry = new LedProfile.ScheduleEntry();
                entry.setStartTime(starts.get(i));
                entry.setEndTime(ends.get(i));
                entry.setIntensityPercent(intensities.get(i));
                schedule.add(entry);
            }
            scheduleAdapter.setEntries(schedule);
        } else {
            scheduleAdapter.setEntries(null);
        }
    }

    private boolean hasExistingProfile() {
        Bundle args = getArguments();
        return args != null && args.containsKey(ARG_ID);
    }

    private void updatePositiveButtonState() {
        if (positiveButton == null) {
            return;
        }
        boolean hasName = !TextUtils.isEmpty(nameInput.getText());
        positiveButton.setEnabled(hasName && entriesValid);
    }

    private void saveProfile() {
        String name = safeTrim(nameInput.getText());
        if (TextUtils.isEmpty(name)) {
            nameInput.setError(getString(R.string.error_required));
            updatePositiveButtonState();
            return;
        }
        nameInput.setError(null);
        String type = safeTrim(typeInput.getText());
        Float distance = parseFloat(distanceInput.getText());
        Float ambient = parseFloat(ambientInput.getText());
        Float camera = parseFloat(cameraInput.getText());

        LedProfile profile = new LedProfile();
        profile.setName(name);
        profile.setType(type);
        profile.setMountingDistanceCm(distance);
        Map<String, Float> factors = new HashMap<>();
        if (ambient != null) {
            factors.put(LedProfile.CALIBRATION_KEY_AMBIENT, ambient);
        }
        if (camera != null) {
            factors.put(LedProfile.CALIBRATION_KEY_CAMERA, camera);
        }
        profile.setCalibrationFactors(factors);
        profile.setSchedule(scheduleAdapter.getEntries());

        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_ID)) {
            profile.setId(args.getLong(ARG_ID));
            repository.updateLedProfile(profile, this::onOperationComplete,
                e -> showErrorAndDismiss());
        } else {
            repository.createLedProfile(profile, created -> onOperationComplete(),
                e -> showErrorAndDismiss());
        }
    }

    private void confirmDelete() {
        Bundle args = getArguments();
        if (args == null || !args.containsKey(ARG_ID)) {
            dismiss();
            return;
        }
        long id = args.getLong(ARG_ID);
        String name = args.getString(ARG_NAME, "");
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete_led_profile)
            .setMessage(getString(R.string.confirm_delete_led_profile, name))
            .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteProfile(id, name))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deleteProfile(long id, String name) {
        LedProfile profile = new LedProfile();
        profile.setId(id);
        profile.setName(name);
        repository.deleteLedProfile(profile, this::onOperationComplete,
            e -> showErrorAndDismiss());
    }

    private void onOperationComplete() {
        if (!isAdded()) {
            return;
        }
        Bundle result = new Bundle();
        getParentFragmentManager().setFragmentResult(LedProfileListFragment.RESULT_KEY_PROFILES_UPDATED, result);
        dismiss();
    }

    private void showErrorAndDismiss() {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), R.string.error_database, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    @Override
    public void onEntriesChanged() {
        entriesValid = true;
        updatePositiveButtonState();
    }

    private static final class PlantRepositoryProvider {
        private PlantRepositoryProvider() {
        }

        static PlantRepository fromContext(@NonNull android.content.Context context) {
            if (context instanceof de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider) {
                return ((de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider) context).getRepository();
            }
            android.content.Context appContext = context.getApplicationContext();
            if (appContext instanceof de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider) {
                return ((de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider) appContext).getRepository();
            }
            throw new IllegalStateException("Application does not implement RepositoryProvider");
        }
    }
}
