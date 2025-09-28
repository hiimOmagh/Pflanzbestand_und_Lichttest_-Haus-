package de.oabidi.pflanzenbestandundlichttest.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
    import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.oabidi.pflanzenbestandundlichttest.Measurement;
import de.oabidi.pflanzenbestandundlichttest.MeasurementDao;
import de.oabidi.pflanzenbestandundlichttest.PlantDao;
import de.oabidi.pflanzenbestandundlichttest.core.system.reminder.ReminderDao;
import de.oabidi.pflanzenbestandundlichttest.SpeciesTargetDao;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;

@RunWith(RobolectricTestRunner.class)
public class MeasurementRepositoryTest extends RepositoryTestBase {
    @Mock
    private MeasurementDao measurementDao;
    @Mock
    private PlantDao plantDao;
    @Mock
    private SpeciesTargetDao speciesTargetDao;
    @Mock
    private ReminderDao reminderDao;

    private MeasurementRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().putBoolean(SettingsKeys.KEY_DLI_ALERTS_ENABLED, false).apply();
        repository = new MeasurementRepository(context, handler, ioExecutor, measurementDao, plantDao, speciesTargetDao, reminderDao);
    }

    @Test
    public void insertMeasurement_invokesDaoAndCallback() throws Exception {
        Measurement measurement = new Measurement(1L, System.currentTimeMillis(), 100f, null, null, null);
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        repository.insertMeasurement(measurement, () -> callbackInvoked.set(true), null);

        drainMainLooper();

        assertTrue(callbackInvoked.get());
        verify(measurementDao).insert(measurement);
    }

    @Test
    public void sumPpfdAndCountDays_returnsZeroWhenDaoNull() throws Exception {
        when(measurementDao.sumPpfdAndCountDays(anyLong(), anyLong(), anyLong())).thenReturn(null);

        AtomicReference<MeasurementDao.SumAndDays> result = new AtomicReference<>();

        repository.sumPpfdAndCountDays(2L, 0L, 10L, value -> {
            result.set(value);
        });

        drainMainLooper();

        MeasurementDao.SumAndDays data = result.get();
        assertNotNull(data);
        assertEquals(0f, data.sum, 0.0001f);
        assertEquals(0, data.days);
    }
}
