package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.LightMath;
import de.oabidi.pflanzenbestandundlichttest.data.PlantCalibration;
import de.oabidi.pflanzenbestandundlichttest.data.PlantPhoto;

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
    public void migrate4To9_keepsPlants() {
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
            .addMigrations(
                PlantDatabase.MIGRATION_4_5,
                PlantDatabase.MIGRATION_5_6,
                PlantDatabase.MIGRATION_6_7,
                PlantDatabase.MIGRATION_7_8,
                PlantDatabase.MIGRATION_8_9,
                PlantDatabase.MIGRATION_9_10,
                PlantDatabase.MIGRATION_10_11,
                PlantDatabase.MIGRATION_11_12)
            .allowMainThreadQueries()
            .build();
        List<Plant> plants = migrated.plantDao().getAll();
        migrated.close();

        assertEquals(1, plants.size());
        assertEquals("Test", plants.get(0).getName());
    }

    @Test
    public void migrate12To13_transformsSpeciesTargets() {
        Context context = ApplicationProvider.getApplicationContext();

        PlantDatabaseV12 v12 = Room.databaseBuilder(context, PlantDatabaseV12.class, DB_NAME)
            .allowMainThreadQueries()
            .build();
        SpeciesTargetV12 legacy = new SpeciesTargetV12();
        legacy.speciesKey = "legacy";
        legacy.ppfdMin = 120f;
        legacy.ppfdMax = 240f;
        v12.speciesTargetDao().insert(legacy);
        v12.close();

        PlantDatabase migrated = Room.databaseBuilder(context, PlantDatabase.class, DB_NAME)
            .addMigrations(
                PlantDatabase.MIGRATION_2_3,
                PlantDatabase.MIGRATION_3_4,
                PlantDatabase.MIGRATION_4_5,
                PlantDatabase.MIGRATION_5_6,
                PlantDatabase.MIGRATION_6_7,
                PlantDatabase.MIGRATION_7_8,
                PlantDatabase.MIGRATION_8_9,
                PlantDatabase.MIGRATION_9_10,
                PlantDatabase.MIGRATION_10_11,
                PlantDatabase.MIGRATION_11_12,
                PlantDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build();

        SpeciesTarget migratedTarget = migrated.speciesTargetDao().findBySpeciesKey("legacy");
        migrated.close();

        assertNotNull(migratedTarget);
        assertEquals(120f, migratedTarget.getSeedlingStage().getPpfdMin(), 0.001f);
        assertEquals(240f, migratedTarget.getSeedlingStage().getPpfdMax(), 0.001f);
        assertEquals(120f, migratedTarget.getVegetativeStage().getPpfdMin(), 0.001f);
        assertEquals(240f, migratedTarget.getVegetativeStage().getPpfdMax(), 0.001f);
        assertEquals(120f, migratedTarget.getFlowerStage().getPpfdMin(), 0.001f);
        assertEquals(240f, migratedTarget.getFlowerStage().getPpfdMax(), 0.001f);
        assertEquals(LightMath.dliFromPpfd(120f, 12f), migratedTarget.getSeedlingStage().getDliMin(), 0.001f);
        assertEquals(LightMath.dliFromPpfd(240f, 12f), migratedTarget.getSeedlingStage().getDliMax(), 0.001f);
    }

    /**
     * Minimal DAO used for seeding the version 4 database in tests.
     */
    @Dao
    interface PlantDaoV4 {
        @Insert
        long insert(Plant plant);

        @Query("SELECT * FROM Plant ORDER BY name ASC")
        List<Plant> getAll();
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
        public abstract PlantDaoV4 plantDao();
    }

    /**
     * Measurement entity used in version 4 without DLI or note columns.
     */
    @Entity
    static class MeasurementV4 {
        @PrimaryKey(autoGenerate = true)
        public long id;
        public long plantId;
        public long timeEpoch;
        public float luxAvg;
        public Float ppfd;
    }

    /**
     * Reminder entity prior to version 5 without the {@code plantId} reference.
     */
    @Entity
    static class ReminderV4 {
        @PrimaryKey(autoGenerate = true)
        public long id;
        public long triggerAt;
        public String message;
    }

    @Dao
    interface SpeciesTargetDaoV12 {
        @Insert
        void insert(SpeciesTargetV12 target);
    }

    @Entity
    static class SpeciesTargetV12 {
        @PrimaryKey
        @NonNull
        public String speciesKey;
        public float ppfdMin;
        public float ppfdMax;
    }

    @Database(
        entities = {
            Plant.class,
            Measurement.class,
            DiaryEntry.class,
            SpeciesTargetV12.class,
            Reminder.class,
            PlantFts.class,
            DiaryEntryFts.class,
            PlantPhoto.class,
            PlantCalibration.class
        },
        version = 12,
        exportSchema = false
    )
    @TypeConverters({Converters.class})
    abstract static class PlantDatabaseV12 extends RoomDatabase {
        public abstract SpeciesTargetDaoV12 speciesTargetDao();
    }
}
