package org.mkonchady.mytripoo.gps;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.preference.PreferenceManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;

// get the first location as fast as possible
public class LocationStartup implements LocationListener {

    private boolean freshLocation = false;
    private static final long FASTEST_INTERVAL = 1000;         // 1 second
    private static final long INTERVAL = 1000 * 10;            // 10 seconds
    private static final long FRESH_LOC_PERIOD = Constants.MILLISECONDS_PER_MINUTE * 2;     // 2 minutes
    int localLog;
    private final LocationRequest mLocationRequest;
    Context context;
    private final Activity mainActivity;
    private Location mCurrentLocation = null;
    public final String TAG = "LocationStartup";
    FusedLocationProviderClient mFusedLocationClient = null;
    LocationCallback locationCallback;

    public LocationStartup(Context mcontext, Activity mainActivity) {
        this.context = mcontext;
        this.mainActivity = mainActivity;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        Logger.d(TAG, "Create LocationStartup ...", localLog);

        // set the parameters for the first location request
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setSmallestDisplacement(0)
                .setInterval(INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();
                if (location != null) {//...you can get updated location
                    long locationTime = System.currentTimeMillis() - ((location == null) ? 0L : location.getTime());
                    freshLocation = (locationTime < FRESH_LOC_PERIOD);
                    mCurrentLocation = location;
                }
            }
        };
    }

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            context.startActivity(intent);
        }
    }
    public void startConnection() {
        Logger.d(TAG, "start fired ...", localLog);
        if(mFusedLocationClient == null) {
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    checkPermission();
                    mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, null /* Looper */);
                }
            });


        }
        //locationManager.requestLocationUpdates(provider, 400, 1, (LocationListener) this);
    }

    public void stopConnection() {
        Logger.d(TAG, "stop fired ...", localLog);
//        locationManager.removeUpdates((LocationListener) this);
        //REMOVE LOCATION UPDATES
        mFusedLocationClient.removeLocationUpdates(locationCallback);

        Logger.d(TAG, "isConnected : " , localLog);
    }

    @Override
    public void onLocationChanged(@NonNull  Location location) {
        Logger.d(TAG, "Firing onLocationChanged...", localLog);
        long locationTime = System.currentTimeMillis() - location.getTime();
        freshLocation = (locationTime < FRESH_LOC_PERIOD);
        mCurrentLocation = location;
    }


    public boolean noInitialCoordinates() {
        return (mCurrentLocation == null);
    }

    public void setmCurrentLocation(Location location) {
        mCurrentLocation = location;
    }

    public Location getLocation() {
        return  mCurrentLocation;
    }

    public boolean isFreshLocation() {
        return freshLocation;
    }
}