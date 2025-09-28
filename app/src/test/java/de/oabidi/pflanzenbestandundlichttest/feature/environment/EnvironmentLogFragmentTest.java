package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.TestExecutorApp;
import de.oabidi.pflanzenbestandundlichttest.core.ui.LineChartView;

/**
 * Robolectric tests covering the chart rendering behaviour of EnvironmentLogFragment.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestExecutorApp.class)
public class EnvironmentLogFragmentTest {

    @Test
    public void showGrowthChart_togglesChartVisibility() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        EnvironmentLogFragment fragment = new EnvironmentLogFragment();
        LineChartView chart = new LineChartView(context);
        TextView empty = new TextView(context);
        setField(fragment, "overviewChartView", chart);
        setField(fragment, "overviewChartEmptyView", empty);

        EnvironmentLogPresenter.ChartPoint p1 = new EnvironmentLogPresenter.ChartPoint(1L, 10f);
        EnvironmentLogPresenter.ChartPoint p2 = new EnvironmentLogPresenter.ChartPoint(2L, 12f);
        EnvironmentLogPresenter.ChartSeries series = new EnvironmentLogPresenter.ChartSeries("Height", List.of(p1, p2));
        EnvironmentLogPresenter.ChartData data = new EnvironmentLogPresenter.ChartData(List.of(series));

        fragment.showGrowthChart(data);

        assertEquals(View.VISIBLE, chart.getVisibility());
        assertEquals(View.GONE, empty.getVisibility());
        assertEquals(1, chart.getSeriesCount());
        assertEquals(List.of(1L, 2L), chart.getTimestamps());

        fragment.showGrowthChart(null);

        assertEquals(View.GONE, chart.getVisibility());
        assertEquals(View.VISIBLE, empty.getVisibility());
    }

    @Test
    public void showClimateChart_togglesChartVisibility() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        EnvironmentLogFragment fragment = new EnvironmentLogFragment();
        LineChartView chart = new LineChartView(context);
        TextView empty = new TextView(context);
        setField(fragment, "trendsChartView", chart);
        setField(fragment, "trendsChartEmptyView", empty);

        EnvironmentLogPresenter.ChartPoint p1 = new EnvironmentLogPresenter.ChartPoint(5L, 40f);
        EnvironmentLogPresenter.ChartPoint p2 = new EnvironmentLogPresenter.ChartPoint(6L, 45f);
        EnvironmentLogPresenter.ChartSeries series = new EnvironmentLogPresenter.ChartSeries("Humidity", List.of(p1, p2));
        EnvironmentLogPresenter.ChartData data = new EnvironmentLogPresenter.ChartData(List.of(series));

        fragment.showClimateChart(data);

        assertEquals(View.VISIBLE, chart.getVisibility());
        assertEquals(View.GONE, empty.getVisibility());
        assertEquals(List.of(5L, 6L), chart.getTimestamps());

        fragment.showClimateChart(null);

        assertEquals(View.GONE, chart.getVisibility());
        assertEquals(View.VISIBLE, empty.getVisibility());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = EnvironmentLogFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
