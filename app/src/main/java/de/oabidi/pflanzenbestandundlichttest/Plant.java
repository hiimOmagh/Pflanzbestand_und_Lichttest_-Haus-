package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity
public class Plant {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;
    private String description;
    private String species;
    private String locationHint;
    private long acquiredAtEpoch;
    private Uri photoUri;

    public Plant() {
    }

    @Ignore
    public Plant(String name, String description, String species, String locationHint, long acquiredAtEpoch, Uri photoUri) {
        this.name = name;
        this.description = description;
        this.species = species;
        this.locationHint = locationHint;
        this.acquiredAtEpoch = acquiredAtEpoch;
        this.photoUri = photoUri;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getLocationHint() {
        return locationHint;
    }

    public void setLocationHint(String locationHint) {
        this.locationHint = locationHint;
    }

    public long getAcquiredAtEpoch() {
        return acquiredAtEpoch;
    }

    public void setAcquiredAtEpoch(long acquiredAtEpoch) {
        this.acquiredAtEpoch = acquiredAtEpoch;
    }

    public Uri getPhotoUri() {
        return photoUri;
    }

    public void setPhotoUri(Uri photoUri) {
        this.photoUri = photoUri;
    }
}
