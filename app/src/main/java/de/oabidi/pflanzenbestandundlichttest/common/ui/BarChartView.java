package de.oabidi.pflanzenbestandundlichttest.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.Measurement;

/**
 * Very small custom view drawing a bar chart for PPFD values of measurements.
 */
public class BarChartView extends View {
    private final List<Float> values = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(0xFF4CAF50); // green bars
    }

    /**
     * Sets the measurements to display. Only PPFD values are visualised.
     */
    public void setMeasurements(@Nullable List<Measurement> measurements) {
        values.clear();
        if (measurements != null) {
            for (Measurement m : measurements) {
                values.add(m.getPpfd());
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.isEmpty()) {
            return;
        }
        float width = getWidth();
        float height = getHeight();
        float max = Collections.max(values);
        if (max <= 0) {
            return;
        }
        float barWidth = width / values.size();
        for (int i = 0; i < values.size(); i++) {
            float v = values.get(i);
            float barHeight = (v / max) * height;
            float left = i * barWidth;
            float right = left + barWidth * 0.8f; // small gap between bars
            float top = height - barHeight;
            canvas.drawRect(left, top, right, height, barPaint);
        }
    }
}
