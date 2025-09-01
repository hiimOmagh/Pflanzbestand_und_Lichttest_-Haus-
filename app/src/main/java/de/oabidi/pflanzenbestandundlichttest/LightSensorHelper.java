package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Helper class that manages the ambient light sensor and reports lux readings.
 */
public class LightSensorHelper implements SensorEventListener {
    public interface OnLuxChangedListener {
        void onLuxChanged(float lux);
    }

    private final SensorManager sensorManager;
    private final Sensor lightSensor;
    private final OnLuxChangedListener listener;

    public LightSensorHelper(Context context, OnLuxChangedListener listener) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
        this.listener = listener;
    }

    /** Start listening to the light sensor. */
    public void start() {
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /** Stop listening to the light sensor. */
    public void stop() {
        if (sensorManager != null && lightSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (listener != null && event.sensor.getType() == Sensor.TYPE_LIGHT) {
            listener.onLuxChanged(event.values[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }
}
