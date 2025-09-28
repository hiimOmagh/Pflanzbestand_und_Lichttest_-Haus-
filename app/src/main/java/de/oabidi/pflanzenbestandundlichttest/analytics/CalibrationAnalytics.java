package de.oabidi.pflanzenbestandundlichttest.analytics;

import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Lightweight helper for logging calibration wizard analytics events.
 */
public final class CalibrationAnalytics {

    private static final String TAG = "CalibrationAnalytics";

    private CalibrationAnalytics() {
    }

    /** Logs when a specific wizard step becomes visible. */
    public static void logStepShown(@NonNull String stepName) {
        Log.d(TAG, "step_shown:" + stepName);
    }

    /** Logs when the user completes a wizard step. */
    public static void logStepCompleted(@NonNull String stepName) {
        Log.d(TAG, "step_completed:" + stepName);
    }

    /** Logs the lux value captured by the user. */
    public static void logLuxCaptured(float lux) {
        Log.d(TAG, "lux_captured:" + lux);
    }

    /** Logs that calibration values were persisted for a plant. */
    public static void logCalibrationSaved(long plantId, float ambientFactor, float cameraFactor) {
        Log.d(TAG, "calibration_saved plant=" + plantId
            + " ambient=" + ambientFactor + " camera=" + cameraFactor);
    }
}
