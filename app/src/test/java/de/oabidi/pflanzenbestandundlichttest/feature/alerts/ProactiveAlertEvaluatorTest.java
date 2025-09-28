package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import android.content.Context;
import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.oabidi.pflanzenbestandundlichttest.DiaryEntry;
import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;

@RunWith(RobolectricTestRunner.class)
public class ProactiveAlertEvaluatorTest {
    private ProactiveAlertEvaluator evaluator;
    private Resources resources;

    @Before
    public void setUp() {
        evaluator = new ProactiveAlertEvaluator();
        Context context = ApplicationProvider.getApplicationContext();
        resources = context.getResources();
    }

    @Test
    public void evaluate_detectsCriticalHumidityDrop() {
        Plant plant = buildPlant("Fern");
        SpeciesTarget target = new SpeciesTarget();
        target.setSpeciesKey("fern");
        target.setHumidityRange(new SpeciesTarget.FloatRange(50f, 65f));
        PlantProfile profile = PlantProfile.fromTarget(target);

        EnvironmentEntry entry = new EnvironmentEntry();
        entry.setPlantId(plant.getId());
        entry.setTimestamp(System.currentTimeMillis());
        entry.setHumidity(30f);
        List<ProactiveAlert> alerts = evaluator.evaluate(plant, profile,
            Collections.singletonList(entry), null, resources);

        assertThat(alerts, hasSize(1));
        ProactiveAlert alert = alerts.get(0);
        assertThat(alert.getTrigger(), equalTo(ProactiveAlertTrigger.HUMIDITY_LOW));
        assertThat(alert.getSeverity(), equalTo(ProactiveAlert.Severity.CRITICAL));
        assertThat(alert.getMessage(), notNullValue());
    }

    @Test
    public void evaluate_flagsDiaryInactivityWhenNoRecentEntries() {
        Plant plant = buildPlant("Orchid");
        long eightDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8);
        DiaryEntry diaryEntry = new DiaryEntry();
        diaryEntry.setPlantId(plant.getId());
        diaryEntry.setTimeEpoch(eightDaysAgo);

        List<ProactiveAlert> alerts = evaluator.evaluate(plant, null,
            Collections.emptyList(), diaryEntry, resources);

        assertThat(alerts, hasItem(org.hamcrest.Matchers.hasProperty("trigger",
            equalTo(ProactiveAlertTrigger.DIARY_INACTIVITY))));
    }

    private Plant buildPlant(String name) {
        Plant plant = new Plant();
        plant.setId(42);
        plant.setName(name);
        plant.setAcquiredAtEpoch(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10));
        return plant;
    }
}
