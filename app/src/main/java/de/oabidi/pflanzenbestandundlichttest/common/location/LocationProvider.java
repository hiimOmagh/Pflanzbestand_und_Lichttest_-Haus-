package de.oabidi.pflanzenbestandundlichttest.common.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

//import com.google.android.gms.location.CancellationTokenSource;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.CancellationTokenSource;

import android.util.Log;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Lightweight wrapper around {@link FusedLocationProviderClient} used for
 * retrieving coarse/fine location coordinates.
 */
public class LocationProvider {
    private static final String TAG = "LocationProvider";
    private final Context context;
    private final FusedLocationProviderClient fusedClient;

    public LocationProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.fusedClient = LocationServices.getFusedLocationProviderClient(this.context);
    }

    /**
     * Returns whether either coarse or fine location permission has been granted.
     */
    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Attempts to fetch the current location using a balanced power request and
     * falls back to {@link #getLastLocation(Consumer, Consumer)} when unavailable.
     */
    @SuppressLint("MissingPermission")
    public void getCurrentLocation(@NonNull Consumer<Location> successCallback,
                                   @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(successCallback, "successCallback");
        if (!hasLocationPermission()) {
            if (errorCallback != null) {
                errorCallback.accept(new SecurityException("Missing location permission"));
            }
            return;
        }
        CancellationTokenSource tokenSource = new CancellationTokenSource();
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        successCallback.accept(location);
                    } else {
                        getLastLocation(successCallback, errorCallback);
                    }
                })
                .addOnFailureListener(wrapFailure(errorCallback));
        } catch (SecurityException e) {
            if (errorCallback != null) {
                errorCallback.accept(e);
            } else {
                Log.e(TAG, "Missing location permission while requesting current location", e);
            }
        }
    }

    /**
     * Retrieves the last known location if available.
     */
    @SuppressLint("MissingPermission")
    public void getLastLocation(@NonNull Consumer<Location> successCallback,
                                @Nullable Consumer<Exception> errorCallback) {
        Objects.requireNonNull(successCallback, "successCallback");
        if (!hasLocationPermission()) {
            if (errorCallback != null) {
                errorCallback.accept(new SecurityException("Missing location permission"));
            }
            return;
        }
        try {
            fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        successCallback.accept(location);
                    } else if (errorCallback != null) {
                        errorCallback.accept(new IllegalStateException("Location unavailable"));
                    }
                })
                .addOnFailureListener(wrapFailure(errorCallback));
        } catch (SecurityException e) {
            if (errorCallback != null) {
                errorCallback.accept(e);
            } else {
                Log.e(TAG, "Missing location permission while retrieving last location", e);
            }
        }
    }

    private OnFailureListener wrapFailure(@Nullable Consumer<Exception> errorCallback) {
        return e -> {
            if (errorCallback != null) {
                errorCallback.accept(e);
            }
        };
    }
}
