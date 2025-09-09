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
 *
 * <p>Readings are smoothed using a simple moving-average algorithm that maintains a
 * configurable number of recent lux values. As new readings arrive, older ones are
 * dropped once the limit is reached, and the average of the remaining samples is
 * reported to the listener.</p>
 */
public class LightSensorHelper implements SensorEventListener {
    public interface OnLuxChangedListener {
        /**
         * Called when a new light sensor reading is available.
         *
         * @param rawLux the latest raw lux value reported by the sensor
         * @param avgLux the moving average of recent lux readings including this one
         */
        void onLuxChanged(float rawLux, float avgLux);
    }

    /** Maximum number of samples to include in the moving average. */
    private final int maxSamples;

    private final SensorManager sensorManager;
    private final Sensor lightSensor;
    private final OnLuxChangedListener listener;
    private final Deque<Float> recentLuxSamples = new ArrayDeque<>();
    private float luxSum = 0f;

    /**
     * Creates a new helper using the default sample size of 10 readings.
     */
    public LightSensorHelper(Context context, OnLuxChangedListener listener) {
        this(context, listener, 10);
    }

    /**
     * Creates a new helper and configures how many samples are retained for the moving
     * average.
     *
     * @param sampleSize number of recent readings used to compute the average; must be at least 1
     * @throws IllegalArgumentException if {@code sampleSize < 1}
     */
    public LightSensorHelper(Context context, OnLuxChangedListener listener, int sampleSize) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
        this.listener = listener;
        if (sampleSize < 1) {
            throw new IllegalArgumentException("sampleSize must be at least 1");
        }
        this.maxSamples = sampleSize;
    }

    /** Check whether a light sensor is available. */
    public boolean hasLightSensor() {
        return lightSensor != null;
    }

    /** Start listening to the light sensor. */
    public void start() {
        if (sensorManager != null && lightSensor != null) {
            // Clear buffered samples so old values don't skew new sessions
            recentLuxSamples.clear();
            luxSum = 0f;
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /** Stop listening to the light sensor. */
    public void stop() {
        if (sensorManager != null && lightSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    /**
     * Receives raw sensor events, updates the moving average, and notifies the listener.
     * The latest lux value is appended to a queue and, if necessary, the oldest sample
     * is discarded so that no more than {@code maxSamples} readings are retained. The
     * average of the stored samples is then computed and passed to the listener.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (listener != null && event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            recentLuxSamples.addLast(lux);
            luxSum += lux;
            if (recentLuxSamples.size() > maxSamples) {
                luxSum -= recentLuxSamples.removeFirst();
            }
            float averageLux = luxSum / recentLuxSamples.size();
            listener.onLuxChanged(lux, averageLux);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }
}
