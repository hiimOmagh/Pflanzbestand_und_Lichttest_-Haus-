
package de.oabidi.pflanzenbestandundlichttest.feature.environment;

import androidx.annotation.Nullable;

/**
 * Simple data holder representing user input from the environment log form.
 */
public class EnvironmentLogFormData {
    @Nullable
    private final Float temperature;
    @Nullable
    private final Float humidity;
    @Nullable
    private final Float soilMoisture;
    @Nullable
    private final Float height;
    @Nullable
    private final Float width;
    @Nullable
    private final String notes;
    @Nullable
    private final String photoUri;

    public EnvironmentLogFormData(@Nullable Float temperature, @Nullable Float humidity,
                                  @Nullable Float soilMoisture, @Nullable Float height,
                                  @Nullable Float width, @Nullable String notes,
                                  @Nullable String photoUri) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.soilMoisture = soilMoisture;
        this.height = height;
        this.width = width;
        this.notes = notes;
        this.photoUri = photoUri;
    }

    /** Returns the temperature value or {@code null} if not provided. */
    @Nullable
    public Float getTemperature() {
        return temperature;
    }

    /** Returns the humidity value or {@code null} if not provided. */
    @Nullable
    public Float getHumidity() {
        return humidity;
    }

    /** Returns the soil moisture value or {@code null} if not provided. */
    @Nullable
    public Float getSoilMoisture() {
        return soilMoisture;
    }

    /** Returns the height value or {@code null} if not provided. */
    @Nullable
    public Float getHeight() {
        return height;
    }

    /** Returns the width value or {@code null} if not provided. */
    @Nullable
    public Float getWidth() {
        return width;
    }

    /** Returns the notes or {@code null} if left blank. */
    @Nullable
    public String getNotes() {
        return notes;
    }

    /** Returns the selected photo URI or {@code null} if none is selected. */
    @Nullable
    public String getPhotoUri() {
        return photoUri;
    }

    /**
     * Returns {@code true} when at least one field contains user provided data.
     */
    public boolean hasAnyValue() {
        return temperature != null
            || humidity != null
            || soilMoisture != null
            || height != null
            || width != null
            || (notes != null && !notes.isEmpty())
            || (photoUri != null && !photoUri.isEmpty());
    }
}
