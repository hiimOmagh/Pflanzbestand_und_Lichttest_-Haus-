package de.oabidi.pflanzenbestandundlichttest.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Lightweight line chart view used for visualising environment metrics over time.
 */
public class LineChartView extends View {
    private static final int[] SERIES_COLORS = {
        R.color.chartSeries1,
        R.color.chartSeries2,
        R.color.chartSeries3,
        R.color.chartSeries4
    };

    private final List<Map<Long, Float>> seriesValues = new ArrayList<>();
    private final List<String> seriesLabels = new ArrayList<>();
    private final List<Paint> seriesPaints = new ArrayList<>();
    private final List<Paint> pointPaints = new ArrayList<>();
    private final List<Long> timestamps = new ArrayList<>();
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float minValue = 0f;
    private float maxValue = 0f;
    private float density = 1f;
    private float scaledDensity = 1f;

    public LineChartView(Context context) {
        super(context);
        init();
    }

    public LineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        int axisColor = ContextCompat.getColor(getContext(), R.color.chartAxis);
        axisPaint.setColor(axisColor);
        axisPaint.setStrokeWidth(density);
        axisPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(axisColor);
        textPaint.setTextSize(12f * scaledDensity);
    }

    /**
     * Updates the chart with the provided series. Each series is mapped by timestamp.
     */
    public void setSeries(@Nullable List<LineSeries> series) {
        seriesValues.clear();
        seriesLabels.clear();
        seriesPaints.clear();
        pointPaints.clear();
        timestamps.clear();
        minValue = Float.MAX_VALUE;
        maxValue = -Float.MAX_VALUE;

        if (series != null && !series.isEmpty()) {
            Set<Long> timestampSet = new HashSet<>();
            Context context = getContext();
            int colorIndex = 0;
            for (LineSeries s : series) {
                if (s == null) {
                    continue;
                }
                Map<Long, Float> values = new HashMap<>();
                for (Point point : s.getPoints()) {
                    if (point == null) {
                        continue;
                    }
                    values.put(point.getTimestamp(), point.getValue());
                    timestampSet.add(point.getTimestamp());
                    maxValue = Math.max(maxValue, point.getValue());
                    minValue = Math.min(minValue, point.getValue());
                }
                if (!values.isEmpty()) {
                    seriesValues.add(values);
                    seriesLabels.add(s.getLabel());
                    int colorRes = SERIES_COLORS[colorIndex % SERIES_COLORS.length];
                    int color = ContextCompat.getColor(context, colorRes);
                    Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    linePaint.setColor(color);
                    linePaint.setStyle(Paint.Style.STROKE);
                    linePaint.setStrokeCap(Paint.Cap.ROUND);
                    linePaint.setStrokeWidth(2f * density);
                    seriesPaints.add(linePaint);

                    Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    pointPaint.setColor(color);
                    pointPaint.setStyle(Paint.Style.FILL);
                    pointPaints.add(pointPaint);
                    colorIndex++;
                }
            }
            if (!timestampSet.isEmpty()) {
                timestamps.addAll(timestampSet);
                Collections.sort(timestamps);
            }
        }

        if (seriesValues.isEmpty() || timestamps.size() < 2) {
            minValue = 0f;
            maxValue = 0f;
            setContentDescription(getContext().getString(
                R.string.environment_chart_default_content_description));
        } else {
            if (minValue == Float.MAX_VALUE) {
                minValue = 0f;
            }
            if (maxValue == -Float.MAX_VALUE) {
                maxValue = 0f;
            }
            if (Math.abs(maxValue - minValue) < 1e-6f) {
                float adjustment = Math.max(1f, Math.abs(maxValue) * 0.05f);
                maxValue += adjustment;
                minValue -= adjustment;
            }
            setContentDescription(getContext().getString(
                R.string.environment_chart_content_description, timestamps.size()));
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (seriesValues.isEmpty() || timestamps.size() < 2) {
            return;
        }
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        float textHeight = textPaint.getTextSize();
        float leftPadding = textHeight * 3f;
        float rightPadding = textHeight * 1.5f;
        float topPadding = textHeight * 2f;
        float bottomPadding = textHeight * 4f;
        float chartWidth = width - leftPadding - rightPadding;
        float chartHeight = height - topPadding - bottomPadding;
        if (chartWidth <= 0f || chartHeight <= 0f) {
            return;
        }

        float originY = height - bottomPadding;
        float stepX = chartWidth / (timestamps.size() - 1);
        float range = maxValue - minValue;
        if (range <= 0f) {
            range = 1f;
        }

        for (int s = 0; s < seriesValues.size(); s++) {
            Map<Long, Float> values = seriesValues.get(s);
            Paint linePaint = seriesPaints.get(s);
            Paint pointPaint = pointPaints.get(s);
            Float lastX = null;
            Float lastY = null;
            for (int i = 0; i < timestamps.size(); i++) {
                long timestamp = timestamps.get(i);
                Float value = values.get(timestamp);
                if (value == null) {
                    lastX = null;
                    lastY = null;
                    continue;
                }
                float x = leftPadding + (stepX * i);
                float y = originY - ((value - minValue) / range) * chartHeight;
                if (lastX != null) {
                    canvas.drawLine(lastX, lastY, x, y, linePaint);
                }
                canvas.drawCircle(x, y, 3f * density, pointPaint);
                lastX = x;
                lastY = y;
            }
        }

        // Draw axes
        canvas.drawLine(leftPadding, originY - chartHeight, leftPadding, originY, axisPaint);
        canvas.drawLine(leftPadding, originY, leftPadding + chartWidth, originY, axisPaint);
        float tick = 4f * density;

        // Y-axis labels
        textPaint.setTextAlign(Paint.Align.RIGHT);
        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            float value = minValue + ((range / steps) * i);
            float y = originY - ((value - minValue) / range) * chartHeight;
            canvas.drawLine(leftPadding - tick, y, leftPadding, y, axisPaint);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", value),
                leftPadding - tick * 1.5f, y + textHeight / 3f, textPaint);
        }

        // X-axis labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
            getResources().getString(R.string.chart_date_pattern), Locale.getDefault());
        for (int i = 0; i < timestamps.size(); i++) {
            float x = leftPadding + (stepX * i);
            canvas.drawLine(x, originY, x, originY + tick, axisPaint);
            String label = dateFormat.format(new Date(timestamps.get(i)));
            canvas.drawText(label, x, height - tick, textPaint);
        }

        // Legend
        textPaint.setTextAlign(Paint.Align.LEFT);
        float legendY = originY + textHeight * 1.5f;
        float legendX = leftPadding;
        for (int s = 0; s < seriesLabels.size(); s++) {
            Paint markerPaint = pointPaints.get(s);
            canvas.drawRect(legendX, legendY, legendX + textHeight, legendY + textHeight, markerPaint);
            float textX = legendX + textHeight + tick;
            float textY = legendY + textHeight * 0.8f;
            canvas.drawText(seriesLabels.get(s), textX, textY, textPaint);
            legendX += textHeight + textPaint.measureText(seriesLabels.get(s)) + tick * 4f;
        }
    }

    /** Returns the number of rendered series. */
    @VisibleForTesting
    public int getSeriesCount() {
        return seriesValues.size();
    }

    /** Returns the timestamps currently used by the chart. */
    @NonNull
    @VisibleForTesting
    public List<Long> getTimestamps() {
        return new ArrayList<>(timestamps);
    }

    /** Represents a single series composed of timestamp-value pairs. */
    public static class LineSeries {
        private final String label;
        private final List<Point> points;

        public LineSeries(@NonNull String label, @NonNull List<Point> points) {
            this.label = label;
            this.points = new ArrayList<>(points);
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        @NonNull
        public List<Point> getPoints() {
            return Collections.unmodifiableList(points);
        }
    }

    /** Represents a single data point for a series. */
    public static class Point {
        private final long timestamp;
        private final float value;

        public Point(long timestamp, float value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public float getValue() {
            return value;
        }
    }
}
