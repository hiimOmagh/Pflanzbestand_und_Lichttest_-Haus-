package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Locale;

import de.oabidi.pflanzenbestandundlichttest.common.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfileCalibration;

/**
 * Fragment allowing users to calibrate the light sensor by entering a known PPFD
 * value and calculating the calibration factor k.
 */
public class CalibrationFragment extends Fragment implements LightSensorHelper.OnLuxChangedListener {

    private TextView luxView;
    private EditText ppfdInput;
    private EditText ambientFactorInput;
    private EditText cameraFactorInput;
    private LightSensorHelper lightSensorHelper;
    private float lastLux;
    private long plantId = -1L;
    private PlantRepository repository;
    private static final float DEFAULT_CALIBRATION = 0.0185f;
    @Nullable
    private LedProfileCalibration currentCalibration;
    private boolean profileWarningShown;

    public static CalibrationFragment newInstance(long plantId) {
        CalibrationFragment fragment = new CalibrationFragment();
        Bundle args = new Bundle();
        args.putLong("plantId", plantId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calibration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsUtils.applySystemWindowInsetsPadding(view, false, true, false, true);
        InsetsUtils.requestApplyInsetsWhenAttached(view);
        luxView = view.findViewById(R.id.calibration_lux_value);
        ppfdInput = view.findViewById(R.id.calibration_ppfd_input);
        ambientFactorInput = view.findViewById(R.id.calibration_ambient_input);
        cameraFactorInput = view.findViewById(R.id.calibration_camera_input);
        Button confirmButton = view.findViewById(R.id.calibration_confirm_button);
        Context context = requireContext().getApplicationContext();
        lightSensorHelper = new LightSensorHelper(context, this);

        repository = RepositoryProvider.getRepository(context);
        Bundle args = getArguments();
        if (args != null) {
            plantId = args.getLong("plantId", -1L);
        }
        if (plantId == -1L) {
            plantId = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(SettingsKeys.KEY_SELECTED_PLANT, -1L);
        }
        populateExistingCalibration();

        confirmButton.setOnClickListener(v -> {
            if (plantId == -1L) {
                Toast.makeText(requireContext(), R.string.error_select_plant, Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentCalibration == null || !currentCalibration.hasAssignedProfile()) {
                maybeShowMissingProfileWarning();
            }
            String ppfdText = ppfdInput.getText().toString().trim();
            float ambient;
            if (!ppfdText.isEmpty()) {
                try {
                    float referencePpfd = Float.parseFloat(ppfdText);
                    if (referencePpfd <= 0f) {
                        ppfdInput.setError(getString(R.string.error_positive_number));
                        return;
                    }
                    if (lastLux <= 0f) {
                        Toast.makeText(requireContext(), R.string.calibration_instructions, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ambient = referencePpfd / lastLux;
                    ambientFactorInput.setText(formatFactor(ambient));
                } catch (NumberFormatException e) {
                    ppfdInput.setError(getString(R.string.error_positive_number));
                    return;
                }
            } else {
                String ambientText = ambientFactorInput.getText().toString().trim();
                if (ambientText.isEmpty()) {
                    ambientFactorInput.setError(getString(R.string.error_positive_number));
                    return;
                }
                try {
                    ambient = Float.parseFloat(ambientText);
                } catch (NumberFormatException e) {
                    ambientFactorInput.setError(getString(R.string.error_positive_number));
                    return;
                }
                if (ambient <= 0f) {
                    ambientFactorInput.setError(getString(R.string.error_positive_number));
                    return;
                }
            }

            String cameraText = cameraFactorInput.getText().toString().trim();
            float camera;
            if (cameraText.isEmpty()) {
                camera = ambient;
                cameraFactorInput.setText(formatFactor(camera));
            } else {
                try {
                    camera = Float.parseFloat(cameraText);
                } catch (NumberFormatException e) {
                    cameraFactorInput.setError(getString(R.string.error_positive_number));
                    return;
                }
                if (camera <= 0f) {
                    cameraFactorInput.setError(getString(R.string.error_positive_number));
                    return;
                }
            }

            confirmButton.setEnabled(false);
            repository.saveLedCalibrationForPlant(plantId, ambient, camera, () -> {
                if (!isAdded()) {
                    return;
                }
                confirmButton.setEnabled(true);
                getParentFragmentManager().popBackStack();
            }, e -> {
                if (!isAdded()) {
                    return;
                }
                confirmButton.setEnabled(true);
                Toast.makeText(requireContext(), R.string.error_database, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lightSensorHelper.hasLightSensor()) {
            lightSensorHelper.start();
        } else {
            luxView.setText(R.string.no_light_sensor);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        lightSensorHelper.stop();
    }

    @Override
    public void onLuxChanged(float rawLux, float avgLux) {
        lastLux = avgLux;
        luxView.setText(getString(R.string.format_lux, avgLux));
    }

    private void populateExistingCalibration() {
        if (ambientFactorInput == null || cameraFactorInput == null || repository == null) {
            return;
        }
        ambientFactorInput.setText(formatFactor(DEFAULT_CALIBRATION));
        cameraFactorInput.setText(formatFactor(DEFAULT_CALIBRATION));
        if (plantId == -1L) {
            return;
        }
        repository.getLedCalibrationForPlant(plantId, calibration -> {
            if (!isAdded()) {
                return;
            }
            currentCalibration = calibration;
            if (calibration != null && calibration.hasCalibrationValues()) {
                Float ambient = calibration.getAmbientFactor();
                Float camera = calibration.getCameraFactor();
                if (ambient != null) {
                    ambientFactorInput.setText(formatFactor(ambient));
                }
                if (camera != null) {
                    cameraFactorInput.setText(formatFactor(camera));
                }
            }
            if (calibration == null || !calibration.hasAssignedProfile()) {
                maybeShowMissingProfileWarning();
            }
        }, e -> {
            if (isAdded()) {
                Toast.makeText(requireContext(), R.string.error_database, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void maybeShowMissingProfileWarning() {
        if (profileWarningShown || !isAdded()) {
            return;
        }
        profileWarningShown = true;
        Toast.makeText(requireContext(), R.string.calibration_missing_profile_warning,
            Toast.LENGTH_LONG).show();
    }

    private String formatFactor(float value) {
        return String.format(Locale.US, "%.4f", value);
    }
}
