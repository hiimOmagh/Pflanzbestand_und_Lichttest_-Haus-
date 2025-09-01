package de.oabidi.pflanzenbestandundlichttest;

import android.net.Uri;

public class Plant {
    private final String name;
    private final String description;
    private final String species;
    private final String locationHint;
    private final long acquiredAtEpoch;
    private final Uri photoUri;

    public Plant(String name, String description, String species, String locationHint, long acquiredAtEpoch, Uri photoUri) {
        this.name = name;
        this.description = description;
        this.species = species;
        this.locationHint = locationHint;
        this.acquiredAtEpoch = acquiredAtEpoch;
        this.photoUri = photoUri;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }


    public String getSpecies() {
        return species;
    }

    public String getLocationHint() {
        return locationHint;
    }

    public long getAcquiredAtEpoch() {
        return acquiredAtEpoch;
    }

    public Uri getPhotoUri() {
        return photoUri;
    }
}
