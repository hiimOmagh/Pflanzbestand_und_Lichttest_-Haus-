package de.oabidi.pflanzenbestandundlichttest.common.sensor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

/**
 * Analyzer that calculates camera luma and smooths it using an exponential moving average.
 */
public final class CameraLumaMonitor implements ImageAnalysis.Analyzer {
    private static final float DEFAULT_SMOOTHING_ALPHA = 0.15f;
    @Nullable
    private final Listener listener;
    private final float smoothingAlpha;
    private final Object lock = new Object();
    private float smoothedLuma = Float.NaN;
    public CameraLumaMonitor(@NonNull Listener listener) {
        this(listener, DEFAULT_SMOOTHING_ALPHA);
    }

    public CameraLumaMonitor(@Nullable Listener listener, float smoothingAlpha) {
        if (smoothingAlpha < 0f || smoothingAlpha > 1f) {
            throw new IllegalArgumentException("smoothingAlpha must be between 0 and 1");
        }
        this.listener = listener;
        this.smoothingAlpha = smoothingAlpha;
    }

    /**
     * Clears the internal smoothed state so the next reading starts fresh.
     */
    public void reset() {
        synchronized (lock) {
            smoothedLuma = Float.NaN;
        }
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            if (planes.length == 0) {
                return;
            }
            ByteBuffer buffer = planes[0].getBuffer();
            ByteBuffer data = buffer.duplicate();
            data.rewind();
            int remaining = data.remaining();
            if (remaining <= 0) {
                return;
            }
            float sum = 0f;
            while (data.hasRemaining()) {
                sum += (data.get() & 0xFF);
            }
            float rawLuma = sum / remaining;
            float ema;
            synchronized (lock) {
                if (Float.isNaN(smoothedLuma)) {
                    smoothedLuma = rawLuma;
                } else {
                    smoothedLuma += smoothingAlpha * (rawLuma - smoothedLuma);
                }
                ema = smoothedLuma;
            }
            if (listener != null) {
                listener.onLumaUpdated(rawLuma, ema);
            }
        } finally {
            image.close();
        }
    }

    /**
     * Callback receiving raw and smoothed luma values.
     */
    public interface Listener {
        void onLumaUpdated(float rawLuma, float smoothedLuma);
    }
}
