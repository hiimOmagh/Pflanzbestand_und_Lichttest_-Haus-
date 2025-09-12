package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

/**
 * Fragment allowing users to calibrate the light sensor by entering a known PPFD
 * value and calculating the calibration factor k.
 */
public class CalibrationFragment extends Fragment implements LightSensorHelper.OnLuxChangedListener {

    private TextView luxView;
    private EditText ppfdInput;
    private LightSensorHelper lightSensorHelper;
    private float lastLux;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calibration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        luxView = view.findViewById(R.id.calibration_lux_value);
        ppfdInput = view.findViewById(R.id.calibration_ppfd_input);
        Button confirmButton = view.findViewById(R.id.calibration_confirm_button);
        Context context = requireContext().getApplicationContext();
        lightSensorHelper = new LightSensorHelper(context, this);

        confirmButton.setOnClickListener(v -> {
            try {
                float referencePpfd = Float.parseFloat(ppfdInput.getText().toString());
                if (referencePpfd > 0f && lastLux > 0f) {
                    float k = referencePpfd / lastLux;
                    SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putString(SettingsKeys.KEY_CALIBRATION, Float.toString(k)).apply();
                    getParentFragmentManager().popBackStack();
                } else {
                    ppfdInput.setError(getString(R.string.error_positive_number));
                }
            } catch (NumberFormatException e) {
                ppfdInput.setError(getString(R.string.error_positive_number));
            }
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
}
