package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

/**
 * Verifies that migrating from database version 4 to the current version preserves existing plants.
 */
@RunWith(RobolectricTestRunner.class)
public class PlantDatabaseMigrationTest {
    private static final String DB_NAME = "migration-test";

    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
    }

    @Test
    public void migrate4To7_keepsPlants() {
        Context context = ApplicationProvider.getApplicationContext();

        // Create database in version 4 and insert a sample plant.
        PlantDatabaseV4 v4 = Room.databaseBuilder(context, PlantDatabaseV4.class, DB_NAME)
            .allowMainThreadQueries()
            .build();
        Plant plant = new Plant();
        plant.setName("Test");
        plant.setAcquiredAtEpoch(0L);
        v4.plantDao().insert(plant);
        v4.close();

        // Open database with latest version and run migration.
        PlantDatabase migrated = Room.databaseBuilder(context, PlantDatabase.class, DB_NAME)
            .addMigrations(PlantDatabase.MIGRATION_4_5, PlantDatabase.MIGRATION_5_6, PlantDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build();
        List<Plant> plants = migrated.plantDao().getAll();
        migrated.close();

        assertEquals(1, plants.size());
        assertEquals("Test", plants.get(0).getName());
    }

    /**
     * Version 4 of the database without the {@code plantId} column in reminders.
     */
    @Database(
        entities = {
            Plant.class,
            MeasurementV4.class,
            DiaryEntry.class,
            SpeciesTarget.class,
            ReminderV4.class
        },
        version = 4,
        exportSchema = false
    )
    @TypeConverters({Converters.class})
    abstract static class PlantDatabaseV4 extends RoomDatabase {
        public abstract PlantDao plantDao();
    }

    /** Measurement entity used in version 4 without DLI or note columns. */
    @Entity
    static class MeasurementV4 {
        @PrimaryKey(autoGenerate = true)
        public long id;
        public long plantId;
        public long timeEpoch;
        public float luxAvg;
        public Float ppfd;
    }

    /** Reminder entity prior to version 5 without the {@code plantId} reference. */
    @Entity
    static class ReminderV4 {
        @PrimaryKey(autoGenerate = true)
        public long id;
        public long triggerAt;
        @NonNull
        public String message;
    }
}
