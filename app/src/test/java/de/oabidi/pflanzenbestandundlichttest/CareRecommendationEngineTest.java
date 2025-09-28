package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.CareRecommendation;
import de.oabidi.pflanzenbestandundlichttest.CareRecommendationEngine.Severity;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;
import de.oabidi.pflanzenbestandundlichttest.core.data.plant.SpeciesTarget;

/**
 * Unit tests covering the {@link CareRecommendationEngine} scenarios for care tips.
 */
@RunWith(RobolectricTestRunner.class)
public class CareRecommendationEngineTest {

    private CareRecommendationEngine engine;
    private Resources resources;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        engine = new CareRecommendationEngine();
        resources = context.getResources();
    }

    @Test
    public void evaluateReturnsEmptyWhenMetricsWithinRange() {
        PlantProfile profile = createProfile(18f, 26f);
        List<EnvironmentEntry> entries = new ArrayList<>();
        entries.add(new EnvironmentEntry(1L, 2_000L, 22f, 50f, 50f, 15f, 12f, null, null, null, "healthy", null));
        entries.add(new EnvironmentEntry(1L, 1_000L, 21f, 55f, 45f, 14f, 11f, null, null, null, "previous", null));

        List<CareRecommendation> recommendations = engine.evaluate(profile, entries, resources);

        assertTrue(recommendations.isEmpty());
    }

    @Test
    public void evaluateReturnsWarningsForOutOfRangeMetrics() {
        PlantProfile profile = createProfile(20f, 24f);
        EnvironmentEntry entry = new EnvironmentEntry(1L, 5_000L, 16f, 70f, 10f,
            null, null, null, null, null, "dry", null);

        List<CareRecommendation> recommendations = engine.evaluate(profile,
            Collections.singletonList(entry), resources);

        CareRecommendation temperature = findRecommendation(recommendations, "temperature_low");
        assertNotNull(temperature);
        assertEquals(Severity.CRITICAL, temperature.getSeverity());
        assertEquals(R.drawable.ic_care_temperature, temperature.getIconResId());
        assertEquals(Integer.valueOf(R.string.care_temperature_low_range),
            temperature.getMessageResId());
        assertEquals(resources.getString(R.string.care_temperature_low_range, 16f, 20f, 24f),
            resources.getString(temperature.getMessageResId(), temperature.getFormatArgs()));

        CareRecommendation humidity = findRecommendation(recommendations, "humidity_high");
        assertNotNull(humidity);
        assertEquals(Severity.CRITICAL, humidity.getSeverity());
        assertEquals(R.drawable.ic_care_water_drop, humidity.getIconResId());
        assertEquals(Integer.valueOf(R.string.care_humidity_high_range), humidity.getMessageResId());
        assertEquals(resources.getString(R.string.care_humidity_high_range, 70f, 40f, 60f),
            resources.getString(humidity.getMessageResId(), humidity.getFormatArgs()));

        CareRecommendation soil = findRecommendation(recommendations, "soil_dry");
        assertNotNull(soil);
        assertEquals(Severity.WARNING, soil.getSeverity());
        assertEquals(R.drawable.ic_care_water_drop, soil.getIconResId());
        assertEquals(Integer.valueOf(R.string.care_soil_dry), soil.getMessageResId());
        assertEquals(resources.getString(R.string.care_soil_dry, 10f),
            resources.getString(soil.getMessageResId(), soil.getFormatArgs()));
    }

    @Test
    public void evaluateHandlesMissingDataGracefully() {
        PlantProfile profile = createProfile(18f, 24f);
        EnvironmentEntry empty = new EnvironmentEntry(1L, 3_000L, null, null, null,
            null, null, null, null, null, null, null);

        List<CareRecommendation> withEmptyEntry = engine.evaluate(profile,
            Collections.singletonList(empty), resources);
        assertTrue(withEmptyEntry.isEmpty());

        List<CareRecommendation> withoutEntries = engine.evaluate(profile, null, resources);
        assertTrue(withoutEntries.isEmpty());

        List<CareRecommendation> withoutProfile = engine.evaluate(null,
            Collections.singletonList(empty), resources);
        assertTrue(withoutProfile.isEmpty());
    }

    private CareRecommendation findRecommendation(List<CareRecommendation> recommendations,
                                                  String id) {
        for (CareRecommendation recommendation : recommendations) {
            if (recommendation.getId().equals(id)) {
                return recommendation;
            }
        }
        return null;
    }

    private PlantProfile createProfile(Float temperatureMin, Float temperatureMax) {
        SpeciesTarget.StageTarget emptyStage = new SpeciesTarget.StageTarget();
        SpeciesTarget target = new SpeciesTarget("profile",
            null,
            null,
            SpeciesTarget.Category.HOUSEPLANT,
            emptyStage,
            emptyStage,
            emptyStage,
            new SpeciesTarget.WateringInfo(null, null, null),
            new SpeciesTarget.FloatRange(temperatureMin, temperatureMax),
            new SpeciesTarget.FloatRange(40f, 60f),
            null,
            null,
            null,
            null);
        return PlantProfile.fromTarget(target);
    }
}
