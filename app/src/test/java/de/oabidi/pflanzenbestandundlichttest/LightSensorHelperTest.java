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
    public void movingAverageHonorsSampleSize() {
        Context context = ApplicationProvider.getApplicationContext();
        List<Float> averages = new ArrayList<>();
        LightSensorHelper helper = new LightSensorHelper(context, (raw, avg) -> averages.add(avg), 3);

        helper.onSensorChanged(createLightEvent(10f));
        helper.onSensorChanged(createLightEvent(20f));
        helper.onSensorChanged(createLightEvent(30f));
        helper.onSensorChanged(createLightEvent(40f));

        assertEquals(4, averages.size());
        assertEquals(10f, averages.get(0), 0.0001f);
        assertEquals(15f, averages.get(1), 0.0001f);
        assertEquals(20f, averages.get(2), 0.0001f);
        assertEquals(30f, averages.get(3), 0.0001f);
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
