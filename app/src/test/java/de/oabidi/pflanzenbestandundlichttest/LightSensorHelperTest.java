package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import de.oabidi.pflanzenbestandundlichttest.feature.light.measurement.LightSensorHelper;

/**
 * Tests for {@link LightSensorHelper} verifying moving averages and buffer resets.
 */
@RunWith(RobolectricTestRunner.class)
public class LightSensorHelperTest {

    /**
     * Creates a light {@link SensorEvent} with the given lux value.
     */
    private static SensorEvent createLightEvent(float lux) {
        Sensor sensor = ReflectionHelpers.callConstructor(Sensor.class);
        ReflectionHelpers.setField(sensor, "mType", Sensor.TYPE_LIGHT);
        SensorEvent event = ReflectionHelpers.callConstructor(
            SensorEvent.class,
            ReflectionHelpers.ClassParameter.from(int.class, 1));
        event.sensor = sensor;
        event.values[0] = lux;
        return event;
    }

    @Test
    public void movingAverageHonorsDifferentSampleSizes() {
        Context context = ApplicationProvider.getApplicationContext();

        float[] luxValues = {10f, 20f, 30f, 40f};
        int[] sampleSizes = {1, 3, 5};
        float[][] expected = {
            {10f, 20f, 30f, 40f},      // sampleSize = 1
            {10f, 15f, 20f, 30f},      // sampleSize = 3
            {10f, 15f, 20f, 25f}       // sampleSize = 5
        };

        for (int i = 0; i < sampleSizes.length; i++) {
            List<Float> averages = new ArrayList<>();
            LightSensorHelper helper =
                new LightSensorHelper(context, (raw, avg) -> averages.add(avg), sampleSizes[i]);

            for (float lux : luxValues) {
                helper.onSensorChanged(createLightEvent(lux));
            }

            assertEquals(expected[i].length, averages.size());
            for (int j = 0; j < expected[i].length; j++) {
                assertEquals(
                    "sampleSize=" + sampleSizes[i] + " index=" + j,
                    expected[i][j],
                    averages.get(j),
                    0.0001f);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void startClearsBufferAndResetsAverage() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        List<Float> averages = new ArrayList<>();
        LightSensorHelper helper = new LightSensorHelper(context, (raw, avg) -> averages.add(avg), 3);

        helper.onSensorChanged(createLightEvent(50f));
        helper.onSensorChanged(createLightEvent(60f));

        Field samplesField = LightSensorHelper.class.getDeclaredField("recentLuxSamples");
        samplesField.setAccessible(true);
        Deque<Float> samples = (Deque<Float>) samplesField.get(helper);
        assertEquals(2, Objects.requireNonNull(samples).size());

        Field sumField = LightSensorHelper.class.getDeclaredField("luxSum");
        sumField.setAccessible(true);
        assertTrue(sumField.getFloat(helper) > 0f);

        Sensor lightSensor = ReflectionHelpers.callConstructor(Sensor.class);
        ReflectionHelpers.setField(lightSensor, "mType", Sensor.TYPE_LIGHT);
        ReflectionHelpers.setField(helper, "lightSensor", lightSensor);

        helper.start();

        assertEquals(0, samples.size());
        assertEquals(0f, sumField.getFloat(helper), 0.0001f);

        // A new reading after start() should behave as the first sample.
        helper.onSensorChanged(createLightEvent(70f));

        assertEquals(1, samples.size());
        assertEquals(70f, sumField.getFloat(helper), 0.0001f);
        assertEquals(1, averages.size());
        assertEquals(70f, averages.get(0), 0.0001f);
    }
}
