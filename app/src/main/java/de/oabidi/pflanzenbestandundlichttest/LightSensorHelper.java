package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Helper class that manages the ambient light sensor and reports lux readings.
 */
public class LightSensorHelper implements SensorEventListener {
    public interface OnLuxChangedListener {
        void onLuxChanged(float lux);
    }

    private static final int MAX_SAMPLES = 10;

    private final SensorManager sensorManager;
    private final Sensor lightSensor;
    private final OnLuxChangedListener listener;
    private final Deque<Float> recentLuxSamples = new ArrayDeque<>();
    private float luxSum = 0f;

    public LightSensorHelper(Context context, OnLuxChangedListener listener) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
        this.listener = listener;
    }

    /** Check whether a light sensor is available. */
    public boolean hasLightSensor() {
        return lightSensor != null;
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
            float lux = event.values[0];
            recentLuxSamples.addLast(lux);
            luxSum += lux;
            if (recentLuxSamples.size() > MAX_SAMPLES) {
                luxSum -= recentLuxSamples.removeFirst();
            }
            float averageLux = luxSum / recentLuxSamples.size();
            listener.onLuxChanged(averageLux);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }
}
