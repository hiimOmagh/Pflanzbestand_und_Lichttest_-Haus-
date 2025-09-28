package de.oabidi.pflanzenbestandundlichttest.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Very small custom view drawing a bar chart for PPFD values of measurements.
 */
public class BarChartView extends View {
    private final List<List<Float>> seriesValues = new ArrayList<>();
    private final List<String> seriesLabels = new ArrayList<>();
    private final List<Paint> seriesPaints = new ArrayList<>();
    private final List<Long> timestamps = new ArrayList<>();
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float maxValue = 0f;
    private static final int[] SERIES_COLORS = {
        R.color.chartSeries1,
        R.color.chartSeries2,
        R.color.chartSeries3,
        R.color.chartSeries4
    };

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
        Context context = getContext();
        int axisColor = ContextCompat.getColor(context, R.color.chartAxis);
        axisPaint.setColor(axisColor);
        axisPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        textPaint.setColor(axisColor);
        textPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
    }

    /**
     * Sets the measurements to display grouped by series label. Only PPFD values are visualised.
     */
    public void setMeasurements(@Nullable Map<String, List<Measurement>> measurements) {
        seriesValues.clear();
        seriesLabels.clear();
        seriesPaints.clear();
        timestamps.clear();
        maxValue = 0f;
        if (measurements != null && !measurements.isEmpty()) {
            Set<Long> timeSet = new HashSet<>();
            for (List<Measurement> list : measurements.values()) {
                for (Measurement m : list) {
                    timeSet.add(m.getTimeEpoch());
                    if (m.getPpfd() > maxValue) {
                        maxValue = m.getPpfd();
                    }
                }
            }
            timestamps.addAll(timeSet);
            Collections.sort(timestamps);

            Context context = getContext();
            int colorIndex = 0;
            for (Map.Entry<String, List<Measurement>> entry : measurements.entrySet()) {
                Map<Long, Float> map = new HashMap<>();
                for (Measurement m : entry.getValue()) {
                    map.put(m.getTimeEpoch(), m.getPpfd());
                }
                List<Float> vals = new ArrayList<>();
                for (Long t : timestamps) {
                    Float v = map.get(t);
                    vals.add(v != null ? v : 0f);
                }
                seriesValues.add(vals);
                seriesLabels.add(entry.getKey());
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                int colorRes = SERIES_COLORS[colorIndex % SERIES_COLORS.length];
                p.setColor(ContextCompat.getColor(context, colorRes));
                seriesPaints.add(p);
                colorIndex++;
            }
            setContentDescription(getContext().getString(
                R.string.format_stats_chart_content_description, maxValue));
        } else {
            setContentDescription(getContext().getString(R.string.stats_chart_default_content_description));
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (seriesValues.isEmpty() || timestamps.isEmpty() || maxValue <= 0) {
            return;
        }
        float width = getWidth();
        float height = getHeight();

        // Reserve space for labels
        float textHeight = textPaint.getTextSize();
        float leftPadding = textHeight * 3f;
        float bottomPadding = textHeight * 4f;
        float chartWidth = width - leftPadding;
        float chartHeight = height - bottomPadding;

        int entryCount = timestamps.size();
        int seriesCount = seriesValues.size();
        float groupWidth = chartWidth / entryCount;
        float barWidth = groupWidth / seriesCount;
        for (int i = 0; i < entryCount; i++) {
            for (int s = 0; s < seriesCount; s++) {
                List<Float> vals = seriesValues.get(s);
                float v = i < vals.size() ? vals.get(i) : 0f;
                float barHeight = (v / maxValue) * chartHeight;
                float left = leftPadding + i * groupWidth + s * barWidth;
                float right = left + barWidth * 0.8f; // small gap between bars
                float top = chartHeight - barHeight;
                canvas.drawRect(left, top, right, chartHeight, seriesPaints.get(s));
            }
        }

        // Draw axes
        canvas.drawLine(leftPadding, 0, leftPadding, chartHeight, axisPaint);
        canvas.drawLine(leftPadding, chartHeight, width, chartHeight, axisPaint);

        float tick = 4 * getResources().getDisplayMetrics().density;

        // Y-axis ticks and labels
        textPaint.setTextAlign(Paint.Align.RIGHT);
        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            float value = (maxValue / steps) * i;
            float y = chartHeight - (value / maxValue) * chartHeight;
            canvas.drawLine(leftPadding - tick, y, leftPadding, y, axisPaint);
            canvas.drawText(String.format(Locale.getDefault(), "%.0f", value),
                leftPadding - tick * 1.5f, y + textHeight / 3f, textPaint);
        }

        // X-axis ticks and labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        SimpleDateFormat dateFormat = new SimpleDateFormat(getResources().getString(R.string.chart_date_pattern),
            Locale.getDefault());
        for (int i = 0; i < entryCount; i++) {
            float x = leftPadding + i * groupWidth + (groupWidth * 0.5f);
            canvas.drawLine(x, chartHeight, x, chartHeight + tick, axisPaint);
            String label;
            label = dateFormat.format(new Date(timestamps.get(i)));
            canvas.drawText(label, x, height - tick, textPaint);
        }

        // Legend
        float legendY = chartHeight + textHeight * 1.5f;
        float legendX = leftPadding;
        textPaint.setTextAlign(Paint.Align.LEFT);
        for (int s = 0; s < seriesCount; s++) {
            Paint p = seriesPaints.get(s);
            canvas.drawRect(legendX, legendY, legendX + textHeight, legendY + textHeight, p);
            float textX = legendX + textHeight + tick;
            float textY = legendY + textHeight * 0.8f;
            canvas.drawText(seriesLabels.get(s), textX, textY, textPaint);
            legendX += textHeight + textPaint.measureText(seriesLabels.get(s)) + tick * 4;
        }
    }
}
