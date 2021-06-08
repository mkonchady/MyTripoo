package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.WindowManager;

import android.location.Location;
import android.location.LocationListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.gps.GPSService;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.views.SatellitesView;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

// show the used satellites and locations
public class SatellitesActivity extends Activity  implements LocationListener {

    //private final String TAG = "SatellitesActivity";
    public SatellitesView satellitesView = null;
    public int localLog = 0;
    private boolean satInfoRequested;
    private static final long FASTEST_INTERVAL = 1000;         // 1 second
    private static final long INTERVAL = 1000 * 10;            // 10 seconds

    private LocationRequest mLocationRequest;
    public final String TAG = "LocationStartup";
    FusedLocationProviderClient mFusedLocationClient = null;
    LocationCallback locationCallback;
    public Context context;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // hide the action bar
        ActionBar actionBar = getActionBar();
        if(actionBar != null) actionBar.hide();
        // fetch the preferences
        context = getApplicationContext();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

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
                //Location location = locationResult.getLastLocation();
                //if (location != null) {
                //    //mCurrentLocation = location;
                //}
            }
        };

        satellitesView = this.findViewById(R.id.satellitesView);
        setContentView(R.layout.activity_satellite);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        //satellitesView.timer.cancel();
    }

    @Override
    protected void onStop() {
        super.onStop();
        //satellitesView.timer.cancel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        satInfoRequested = false;
        startConnection();
        if (!UtilsMisc.isServiceRunning(context, GPSService.class))
            context.startService(new Intent(SatellitesActivity.this, GPSService.class));

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!satInfoRequested && UtilsMisc.isServiceRunning(context, GPSService.class))
            context.stopService(new Intent(SatellitesActivity.this, GPSService.class));
        stopConnection();

    }

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            context.startActivity(intent);
        }
    }

    public void startConnection() {
        //Log.d(TAG, "start fired ...", localLog);
        if(mFusedLocationClient == null) {
            this.runOnUiThread(new Runnable() {
                public void run() {
                    checkPermission();
                    mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, null /* Looper */);
                }
            });
        }
    }

    public void stopConnection() {
        //Log.d(TAG, "stop fired ...", localLog);
        mFusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onLocationChanged(@NonNull  Location location) {
        Logger.d(TAG, "Firing onLocationChanged...", localLog);
        //mCurrentLocation = location;
    }

    //public boolean isSatInfoRequested() {
    //    return satInfoRequested;
    //}

    public void setSatInfoRequested(boolean satInfoRequested) {
        this.satInfoRequested = satInfoRequested;
    }

}