package de.oabidi.pflanzenbestandundlichttest.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a configurable LED lighting profile that can be associated with one or more plants.
 */
@Entity
public class LedProfile {
    /** Calibration key representing the ambient light sensor factor. */
    public static final String CALIBRATION_KEY_AMBIENT = "ambient";

    /** Calibration key representing the camera-derived light factor. */
    public static final String CALIBRATION_KEY_CAMERA = "camera";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String name;

    @Nullable
    private String type;

    @Nullable
    private Float mountingDistanceCm;

    @NonNull
    private Map<String, Float> calibrationFactors;

    @NonNull
    private List<ScheduleEntry> schedule;

    public LedProfile() {
        this.name = "";
        this.calibrationFactors = new HashMap<>();
        this.schedule = new ArrayList<>();
    }

    @Ignore
    public LedProfile(@NonNull String name,
                      @Nullable String type,
                      @Nullable Float mountingDistanceCm,
                      @Nullable Map<String, Float> calibrationFactors,
                      @Nullable List<ScheduleEntry> schedule) {
        this.name = name;
        this.type = type;
        this.mountingDistanceCm = mountingDistanceCm;
        this.calibrationFactors = calibrationFactors != null
            ? new HashMap<>(calibrationFactors)
            : new HashMap<>();
        this.schedule = schedule != null ? new ArrayList<>(schedule) : new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @Nullable
    public String getType() {
        return type;
    }

    public void setType(@Nullable String type) {
        this.type = type;
    }

    @Nullable
    public Float getMountingDistanceCm() {
        return mountingDistanceCm;
    }

    public void setMountingDistanceCm(@Nullable Float mountingDistanceCm) {
        this.mountingDistanceCm = mountingDistanceCm;
    }

    @NonNull
    public Map<String, Float> getCalibrationFactors() {
        return calibrationFactors;
    }

    public void setCalibrationFactors(@Nullable Map<String, Float> calibrationFactors) {
        this.calibrationFactors = calibrationFactors != null
            ? new HashMap<>(calibrationFactors)
            : new HashMap<>();
    }

    @NonNull
    public List<ScheduleEntry> getSchedule() {
        return schedule;
    }

    public void setSchedule(@Nullable List<ScheduleEntry> schedule) {
        this.schedule = schedule != null ? new ArrayList<>(schedule) : new ArrayList<>();
    }

    /**
     * Defines a single schedule entry for controlling LED output over time.
     */
    public static class ScheduleEntry {
        @Nullable
        private String startTime;

        @Nullable
        private String endTime;

        private int intensityPercent;

        public ScheduleEntry() {
        }

        @Ignore
        public ScheduleEntry(@Nullable String startTime, @Nullable String endTime, int intensityPercent) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.intensityPercent = intensityPercent;
        }

        @Nullable
        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(@Nullable String startTime) {
            this.startTime = startTime;
        }

        @Nullable
        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(@Nullable String endTime) {
            this.endTime = endTime;
        }

        public int getIntensityPercent() {
            return intensityPercent;
        }

        public void setIntensityPercent(int intensityPercent) {
            this.intensityPercent = intensityPercent;
        }
    }
}
