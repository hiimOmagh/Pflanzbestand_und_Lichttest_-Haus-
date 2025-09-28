package de.oabidi.pflanzenbestandundlichttest.feature.reminders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantProfile;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTarget;
import de.oabidi.pflanzenbestandundlichttest.core.data.EnvironmentEntry;

/**
 * Unit tests for {@link SmartReminderEngine}.
 */
public class SmartReminderEngineTest {
    private SmartReminderEngine engine;

    @Before
    public void setUp() {
        engine = new SmartReminderEngine();
    }

    @Test
    public void suggest_usesSpeciesFrequency() {
        PlantProfile profile = createProfile("Water every 3-4 weeks", null, SpeciesTarget.Category.HOUSEPLANT);

        SmartReminderEngine.Suggestion suggestion = engine.suggest(profile, Collections.emptyList());

        assertEquals(25, suggestion.getBaselineDays());
        assertEquals(25, suggestion.getSuggestedDays());
        assertSame(SmartReminderEngine.BaselineSource.SPECIES_FREQUENCY, suggestion.getBaselineSource());
        assertSame(SmartReminderEngine.EnvironmentSignal.NO_DATA, suggestion.getEnvironmentSignal());
    }

    @Test
    public void suggest_extendsWhenSoilIsWet() {
        PlantProfile profile = createProfile("Water every 3-4 weeks", null, SpeciesTarget.Category.HOUSEPLANT);
        List<EnvironmentEntry> entries = Arrays.asList(
            entryWithSoil(82f, 3L),
            entryWithSoil(78f, 2L),
            entryWithSoil(80f, 1L)
        );

        SmartReminderEngine.Suggestion suggestion = engine.suggest(profile, entries);

        assertEquals(25, suggestion.getBaselineDays());
        assertEquals(34, suggestion.getSuggestedDays());
        assertSame(SmartReminderEngine.EnvironmentSignal.WET, suggestion.getEnvironmentSignal());
    }

    @Test
    public void suggest_shortensWhenSoilIsDry() {
        PlantProfile profile = createProfile(null, "Low", SpeciesTarget.Category.HERB);
        List<EnvironmentEntry> entries = Arrays.asList(
            entryWithSoil(18f, 5L),
            entryWithSoil(22f, 4L)
        );

        SmartReminderEngine.Suggestion suggestion = engine.suggest(profile, entries);

        assertEquals(4, suggestion.getBaselineDays());
        assertEquals(2, suggestion.getSuggestedDays());
        assertSame(SmartReminderEngine.EnvironmentSignal.DRY, suggestion.getEnvironmentSignal());
    }

    private PlantProfile createProfile(String frequency, String tolerance, SpeciesTarget.Category category) {
        SpeciesTarget.WateringInfo wateringInfo = new SpeciesTarget.WateringInfo(frequency, null, tolerance);
        SpeciesTarget target = new SpeciesTarget("test",
            null,
            null,
            category,
            new SpeciesTarget.StageTarget(),
            new SpeciesTarget.StageTarget(),
            new SpeciesTarget.StageTarget(),
            wateringInfo,
            new SpeciesTarget.FloatRange(),
            new SpeciesTarget.FloatRange(),
            null,
            null,
            null,
            null);
        return PlantProfile.fromTarget(target);
    }

    private EnvironmentEntry entryWithSoil(float soil, long id) {
        EnvironmentEntry entry = new EnvironmentEntry();
        entry.setId(id);
        entry.setTimestamp(id);
        entry.setSoilMoisture(soil);
        return entry;
    }
}
