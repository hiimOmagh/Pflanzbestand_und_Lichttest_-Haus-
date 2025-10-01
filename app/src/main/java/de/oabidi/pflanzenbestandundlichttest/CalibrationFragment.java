package de.oabidi.pflanzenbestandundlichttest;

import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import java.util.Locale;

import de.oabidi.pflanzenbestandundlichttest.analytics.CalibrationAnalytics;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfileCalibration;
import de.oabidi.pflanzenbestandundlichttest.feature.light.measurement.LightSensorHelper;

/**
 * Fragment guiding users through the LED calibration workflow using a step-by-step wizard.
 */
public class CalibrationFragment extends Fragment implements LightSensorHelper.OnLuxChangedListener {

    private static final float DEFAULT_CALIBRATION = 0.0185f;
    private ViewFlipper stepFlipper;
    private TextView stepIndicatorView;
    private TextView stepTitleView;
    private TextView profileSummaryView;
    private TextView profileWarningView;
    private TextView liveLuxView;
    private TextView capturedLuxView;
    private TextView ambientFactorView;
    private EditText ppfdInput;
    private EditText cameraFactorInput;
    private TextView summaryProfileView;
    private TextView summaryAmbientView;
    private TextView summaryCameraView;
    private MaterialButton backButton;
    private MaterialButton primaryButton;
    private MaterialButton captureButton;

    private LightSensorHelper lightSensorHelper;
    private PlantRepository repository;
    private long plantId = -1L;
    @Nullable
    private LedProfileCalibration currentCalibration;
    private Step currentStep = Step.INSTRUCTIONS;
    private boolean stepInitialized;
    private boolean saving;
    private float lastLux;
    @Nullable
    private Float capturedLux;
    @Nullable
    private Float referencePpfd;
    @Nullable
    private Float ambientFactor;
    @Nullable
    private Float cameraFactor;
    private boolean cameraFactorDirty;
    private boolean suppressCameraWatcher;
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

        stepIndicatorView = view.findViewById(R.id.calibration_step_indicator);
        stepTitleView = view.findViewById(R.id.calibration_step_title);
        profileSummaryView = view.findViewById(R.id.calibration_profile_summary);
        profileWarningView = view.findViewById(R.id.calibration_profile_warning);
        stepFlipper = view.findViewById(R.id.calibration_step_flipper);
        liveLuxView = view.findViewById(R.id.calibration_live_lux_value);
        capturedLuxView = view.findViewById(R.id.calibration_captured_value);
        ambientFactorView = view.findViewById(R.id.calibration_ambient_value);
        ppfdInput = view.findViewById(R.id.calibration_ppfd_input);
        cameraFactorInput = view.findViewById(R.id.calibration_camera_input);
        summaryProfileView = view.findViewById(R.id.calibration_summary_profile);
        summaryAmbientView = view.findViewById(R.id.calibration_summary_ambient);
        summaryCameraView = view.findViewById(R.id.calibration_summary_camera);
        backButton = view.findViewById(R.id.calibration_back_button);
        primaryButton = view.findViewById(R.id.calibration_primary_button);
        captureButton = view.findViewById(R.id.calibration_capture_button);

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

        setupInteractions();
        applyCameraFactorValue(DEFAULT_CALIBRATION);
        updateCapturedLuxView();
        updateAmbientFactorView();
        updateSummaryViews();
        updateProfileViews();
        showStep(Step.INSTRUCTIONS);

        populateExistingCalibration();
    }

    private void setupInteractions() {
        ppfdInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onReferenceInputChanged();
            }
        });

        cameraFactorInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressCameraWatcher) {
                    return;
                }
                cameraFactorDirty = true;
                onCameraFactorChanged();
            }
        });

        captureButton.setOnClickListener(v -> captureLuxSample());
        backButton.setOnClickListener(v -> navigateBack());
        primaryButton.setOnClickListener(v -> handlePrimaryAction());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lightSensorHelper.hasLightSensor()) {
            lightSensorHelper.start();
        } else if (liveLuxView != null) {
            liveLuxView.setText(R.string.no_light_sensor);
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
        if (liveLuxView != null) {
            liveLuxView.setText(getString(R.string.calibration_capture_current_lux, avgLux));
        }
    }

    private void handlePrimaryAction() {
        if (saving) {
            return;
        }
        switch (currentStep) {
            case INSTRUCTIONS:
                CalibrationAnalytics.logStepCompleted(Step.INSTRUCTIONS.name());
                showStep(Step.CAPTURE);
                break;
            case CAPTURE:
                if (!hasCapturedLux()) {
                    showToast(R.string.calibration_capture_required);
                    return;
                }
                CalibrationAnalytics.logStepCompleted(Step.CAPTURE.name());
                showStep(Step.ENTRY);
                break;
            case ENTRY:
                if (!validateReferenceInputs()) {
                    return;
                }
                CalibrationAnalytics.logStepCompleted(Step.ENTRY.name());
                showStep(Step.CONFIRM);
                break;
            case CONFIRM:
                if (!validateReferenceInputs()) {
                    return;
                }
                CalibrationAnalytics.logStepCompleted(Step.CONFIRM.name());
                saveCalibration();
                break;
        }
    }

    private void navigateBack() {
        if (saving) {
            return;
        }
        switch (currentStep) {
            case CAPTURE:
                showStep(Step.INSTRUCTIONS);
                break;
            case ENTRY:
                showStep(Step.CAPTURE);
                break;
            case CONFIRM:
                showStep(Step.ENTRY);
                break;
            default:
                break;
        }
    }

    private void showStep(Step step) {
        if (stepFlipper == null) {
            return;
        }
        boolean isNewStep = !stepInitialized || currentStep != step;
        currentStep = step;
        stepInitialized = true;
        stepFlipper.setDisplayedChild(step.ordinal());
        updateStepHeader(step);
        if (step == Step.CONFIRM) {
            updateSummaryViews();
        }
        updatePrimaryButtonState();
        if (isNewStep) {
            CalibrationAnalytics.logStepShown(step.name());
            showStepToast(step);
        }
    }

    private void updateStepHeader(Step step) {
        if (stepIndicatorView != null) {
            stepIndicatorView.setText(getString(R.string.calibration_step_indicator,
                step.ordinal() + 1, Step.values().length));
        }
        if (stepTitleView != null) {
            stepTitleView.setText(getString(getStepTitleRes(step)));
        }
        if (primaryButton != null) {
            primaryButton.setText(step == Step.CONFIRM
                ? R.string.calibration_finish_button
                : R.string.calibration_next_button);
        }
        if (backButton != null) {
            backButton.setVisibility(step == Step.INSTRUCTIONS ? View.INVISIBLE : View.VISIBLE);
            backButton.setEnabled(step != Step.INSTRUCTIONS && !saving);
        }
    }

    private void updatePrimaryButtonState() {
        if (primaryButton == null) {
            return;
        }
        if (saving) {
            primaryButton.setEnabled(false);
            return;
        }
        primaryButton.setEnabled(isPrimaryButtonEnabled(currentStep));
    }

    private boolean isPrimaryButtonEnabled(Step step) {
        switch (step) {
            case INSTRUCTIONS:
                return true;
            case CAPTURE:
                return hasCapturedLux();
            case ENTRY:
                return hasCapturedLux() && referencePpfd != null && referencePpfd > 0f
                    && cameraFactor != null && cameraFactor > 0f;
            case CONFIRM:
                return hasCapturedLux() && referencePpfd != null && referencePpfd > 0f
                    && ambientFactor != null && ambientFactor > 0f
                    && cameraFactor != null && cameraFactor > 0f;
            default:
                return false;
        }
    }

    private void captureLuxSample() {
        if (lastLux <= 0f) {
            showToast(R.string.calibration_capture_required);
            return;
        }
        capturedLux = lastLux;
        updateCapturedLuxView();
        if (referencePpfd != null && referencePpfd > 0f) {
            updateAmbientFactorFromInputs();
        }
        CalibrationAnalytics.logLuxCaptured(lastLux);
        showToast(getString(R.string.calibration_capture_toast, lastLux));
        updatePrimaryButtonState();
    }

    private void updateCapturedLuxView() {
        if (capturedLuxView == null) {
            return;
        }
        if (hasCapturedLux()) {
            capturedLuxView.setText(getString(R.string.calibration_capture_saved_lux, capturedLux));
        } else {
            capturedLuxView.setText(R.string.calibration_capture_saved_placeholder);
        }
    }

    private boolean hasCapturedLux() {
        return capturedLux != null && capturedLux > 0f;
    }

    private void onReferenceInputChanged() {
        referencePpfd = parsePositiveFloat(ppfdInput);
        if (referencePpfd != null) {
            ppfdInput.setError(null);
        }
        updateAmbientFactorFromInputs();
        updatePrimaryButtonState();
    }

    private void onCameraFactorChanged() {
        cameraFactor = parsePositiveFloat(cameraFactorInput);
        if (cameraFactor != null) {
            cameraFactorInput.setError(null);
        }
        updatePrimaryButtonState();
        if (currentStep == Step.CONFIRM) {
            updateSummaryViews();
        }
    }

    private void updateAmbientFactorFromInputs() {
        if (!hasCapturedLux() || referencePpfd == null || referencePpfd <= 0f) {
            ambientFactor = null;
            updateAmbientFactorView();
            if (currentStep == Step.CONFIRM) {
                updateSummaryViews();
            }
            return;
        }
        ambientFactor = referencePpfd / capturedLux;
        updateAmbientFactorView();
        if (!cameraFactorDirty) {
            applyCameraFactorValue(ambientFactor);
        }
        if (currentStep == Step.CONFIRM) {
            updateSummaryViews();
        }
    }

    private void updateAmbientFactorView() {
        if (ambientFactorView == null) {
            return;
        }
        if (ambientFactor != null && ambientFactor > 0f) {
            ambientFactorView.setText(getString(R.string.calibration_ambient_result, ambientFactor));
        } else {
            ambientFactorView.setText(R.string.calibration_ambient_placeholder);
        }
    }

    private boolean validateReferenceInputs() {
        boolean valid = true;
        Float ppfd = parsePositiveFloat(ppfdInput);
        if (ppfd == null) {
            ppfdInput.setError(getString(R.string.calibration_reference_error));
            valid = false;
        } else {
            referencePpfd = ppfd;
            ppfdInput.setError(null);
        }

        Float camera = parsePositiveFloat(cameraFactorInput);
        if (camera == null) {
            cameraFactorInput.setError(getString(R.string.calibration_camera_error));
            valid = false;
        } else {
            cameraFactor = camera;
            cameraFactorInput.setError(null);
        }

        if (!hasCapturedLux()) {
            showToast(R.string.calibration_capture_required);
            valid = false;
        }

        if (valid && referencePpfd != null && capturedLux != null && capturedLux > 0f) {
            ambientFactor = referencePpfd / capturedLux;
            updateAmbientFactorView();
        }
        updatePrimaryButtonState();
        if (!valid) {
            return false;
        }
        if (currentStep == Step.CONFIRM) {
            updateSummaryViews();
        }
        return true;
    }

    private void applyCameraFactorValue(float value) {
        suppressCameraWatcher = true;
        cameraFactorInput.setText(formatFactor(value));
        suppressCameraWatcher = false;
        cameraFactor = value;
        cameraFactorInput.setError(null);
        cameraFactorDirty = false;
    }

    private void saveCalibration() {
        if (ambientFactor == null || cameraFactor == null) {
            return;
        }
        if (plantId == -1L) {
            showToast(R.string.error_select_plant);
            return;
        }
        saving = true;
        updatePrimaryButtonState();
        if (backButton != null) {
            backButton.setEnabled(false);
        }
        repository.saveLedCalibrationForPlant(plantId, ambientFactor, cameraFactor, () -> {
            if (!isAdded()) {
                return;
            }
            saving = false;
            CalibrationAnalytics.logCalibrationSaved(plantId, ambientFactor, cameraFactor);
            showToast(R.string.calibration_complete_toast);
            getParentFragmentManager().popBackStack();
        }, e -> {
            if (!isAdded()) {
                return;
            }
            saving = false;
            updatePrimaryButtonState();
            if (backButton != null) {
                backButton.setEnabled(true);
            }
            showToast(R.string.error_database);
        });
    }

    private void populateExistingCalibration() {
        if (cameraFactorInput == null || repository == null) {
            return;
        }
        if (plantId == -1L) {
            updateProfileViews();
            return;
        }
        repository.getLedCalibrationForPlant(plantId, calibration -> {
            if (!isAdded()) {
                return;
            }
            currentCalibration = calibration;
            updateProfileViews();
            if (calibration != null && calibration.hasCalibrationValues()) {
                Float ambient = calibration.getAmbientFactor();
                Float camera = calibration.getCameraFactor();
                if (ambient != null && ambient > 0f) {
                    ambientFactor = ambient;
                    updateAmbientFactorView();
                }
                if (camera != null && camera > 0f) {
                    applyCameraFactorValue(camera);
                }
            }
            if (calibration == null || !calibration.hasAssignedProfile()) {
                maybeShowMissingProfileWarning();
            }
            if (currentStep == Step.CONFIRM) {
                updateSummaryViews();
            }
        }, e -> {
            if (isAdded()) {
                showToast(R.string.error_database);
            }
        });
    }

    private void updateProfileViews() {
        if (profileSummaryView == null || profileWarningView == null) {
            return;
        }
        if (currentCalibration == null) {
            profileSummaryView.setText(R.string.calibration_profile_loading);
            profileWarningView.setVisibility(View.GONE);
            return;
        }
        if (currentCalibration.hasAssignedProfile()) {
            String name = currentCalibration.getProfileName();
            if (name == null || name.trim().isEmpty()) {
                name = getString(R.string.calibration_profile_unknown);
            }
            profileSummaryView.setText(getString(R.string.calibration_profile_summary, name));
            profileWarningView.setVisibility(View.GONE);
        } else {
            profileSummaryView.setText(R.string.calibration_missing_profile_banner);
            profileWarningView.setVisibility(View.VISIBLE);
            profileWarningView.setText(R.string.calibration_missing_profile_summary);
        }
        if (currentStep == Step.CONFIRM) {
            updateSummaryViews();
        }
    }

    private void updateSummaryViews() {
        if (summaryProfileView != null) {
            if (currentCalibration == null) {
                summaryProfileView.setText(R.string.calibration_profile_loading);
            } else if (currentCalibration.hasAssignedProfile()) {
                String name = currentCalibration.getProfileName();
                if (name == null || name.trim().isEmpty()) {
                    name = getString(R.string.calibration_profile_unknown);
                }
                summaryProfileView.setText(getString(R.string.calibration_summary_profile_assigned, name));
            } else {
                summaryProfileView.setText(R.string.calibration_summary_profile_missing);
            }
        }
        if (summaryAmbientView != null) {
            if (ambientFactor != null && ambientFactor > 0f) {
                summaryAmbientView.setText(getString(R.string.calibration_summary_ambient, ambientFactor));
            } else {
                summaryAmbientView.setText(R.string.calibration_summary_ambient_placeholder);
            }
        }
        if (summaryCameraView != null) {
            if (cameraFactor != null && cameraFactor > 0f) {
                summaryCameraView.setText(getString(R.string.calibration_summary_camera, cameraFactor));
            } else {
                summaryCameraView.setText(R.string.calibration_summary_camera_placeholder);
            }
        }
    }

    private void maybeShowMissingProfileWarning() {
        if (profileWarningShown || !isAdded()) {
            return;
        }
        profileWarningShown = true;
        showToast(R.string.calibration_missing_profile_warning);
    }

    private void showStepToast(Step step) {
        showToast(getString(getStepToastRes(step)));
    }

    private void showToast(@StringRes int resId) {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
    }

    private void showToast(String message) {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @StringRes
    private int getStepTitleRes(Step step) {
        switch (step) {
            case CAPTURE:
                return R.string.calibration_step_title_capture;
            case ENTRY:
                return R.string.calibration_step_title_entry;
            case CONFIRM:
                return R.string.calibration_step_title_confirm;
            case INSTRUCTIONS:
            default:
                return R.string.calibration_step_title_instructions;
        }
    }

    @StringRes
    private int getStepToastRes(Step step) {
        switch (step) {
            case CAPTURE:
                return R.string.calibration_step_capture_toast;
            case ENTRY:
                return R.string.calibration_step_entry_toast;
            case CONFIRM:
                return R.string.calibration_step_confirm_toast;
            case INSTRUCTIONS:
            default:
                return R.string.calibration_step_instructions_toast;
        }
    }

    @Nullable
    private Float parsePositiveFloat(@Nullable EditText editText) {
        if (editText == null) {
            return null;
        }
        String text = editText.getText().toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            float value = Float.parseFloat(text);
            if (value > 0f) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private String formatFactor(float value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private enum Step {
        INSTRUCTIONS,
        CAPTURE,
        ENTRY,
        CONFIRM
    }
}
