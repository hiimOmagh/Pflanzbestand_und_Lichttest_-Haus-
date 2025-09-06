package de.oabidi.pflanzenbestandundlichttest.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Very small custom view drawing a bar chart for PPFD values of measurements.
 */
public class BarChartView extends View {
    private final List<Float> values = new ArrayList<>();
    private final List<Long> timestamps = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float maxValue = 0f;

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
        axisPaint.setColor(0xFF000000); // black axes
        axisPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        textPaint.setColor(0xFF000000);
        textPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
    }

    /**
     * Sets the measurements to display. Only PPFD values are visualised.
     * A parallel list of timestamps is used for labelling the x-axis.
     */
    public void setMeasurements(@Nullable List<Measurement> measurements,
                                @Nullable List<Long> times) {
        values.clear();
        timestamps.clear();
        if (measurements != null) {
            for (Measurement m : measurements) {
                values.add(m.getPpfd());
            }
        }
        if (times != null) {
            timestamps.addAll(times);
        }
        if (values.isEmpty()) {
            maxValue = 0f;
            setContentDescription(getContext().getString(R.string.stats_chart_default_content_description));
        } else {
            maxValue = Collections.max(values);
            setContentDescription(getContext().getString(
                R.string.format_stats_chart_content_description, maxValue));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.isEmpty() || maxValue <= 0) {
            return;
        }
        float width = getWidth();
        float height = getHeight();

        // Reserve space for labels
        float textHeight = textPaint.getTextSize();
        float leftPadding = textHeight * 3f;
        float bottomPadding = textHeight * 2f;
        float chartWidth = width - leftPadding;
        float chartHeight = height - bottomPadding;
        float originX = leftPadding;
        float originY = chartHeight;

        float barWidth = chartWidth / values.size();
        for (int i = 0; i < values.size(); i++) {
            float v = values.get(i);
            float barHeight = (v / maxValue) * chartHeight;
            float left = originX + i * barWidth;
            float right = left + barWidth * 0.8f; // small gap between bars
            float top = originY - barHeight;
            canvas.drawRect(left, top, right, originY, barPaint);
        }

        // Draw axes
        canvas.drawLine(originX, 0, originX, originY, axisPaint);
        canvas.drawLine(originX, originY, width, originY, axisPaint);

        float tick = 4 * getResources().getDisplayMetrics().density;

        // Y-axis ticks and labels
        textPaint.setTextAlign(Paint.Align.RIGHT);
        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            float value = (maxValue / steps) * i;
            float y = originY - (value / maxValue) * chartHeight;
            canvas.drawLine(originX - tick, y, originX, y, axisPaint);
            canvas.drawText(String.format(Locale.getDefault(), "%.0f", value),
                originX - tick * 1.5f, y + textHeight / 3f, textPaint);
        }

        // X-axis ticks and labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());
        for (int i = 0; i < values.size(); i++) {
            float x = originX + i * barWidth + (barWidth * 0.4f);
            canvas.drawLine(x, originY, x, originY + tick, axisPaint);
            String label;
            if (i < timestamps.size()) {
                label = dateFormat.format(new Date(timestamps.get(i)));
            } else {
                label = String.valueOf(i + 1);
            }
            canvas.drawText(label, x, height - tick, textPaint);
        }
    }
}
