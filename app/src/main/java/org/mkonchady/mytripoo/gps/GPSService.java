package org.mkonchady.mytripoo.gps;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;

import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationRequest;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.utils.UtilsMap;

import java.util.ArrayList;

/*
  Start a GPS Service to keep track of the number of satellites used for a fix
 */
public class GPSService extends Service implements LocationListener {

    private static final String TAG = "GPSService";
    private LocationManager mLocationManager;
    private LocationProvider mProvider;

    // Android M (6.0.1) and below status and listener
    private GpsStatus mGpsStatus;
    private GpsStatus.Listener mGpsStatusListener;

    // Android N (7.0) and above status and listeners
    private GnssStatus mGnssStatus;
    private GnssStatus.Callback mGnssStatusListener;

    public ArrayList<Satellite> satellites;
    private boolean gpsPermissionGranted = false;
    private SharedPreferences sharedPreferences;
    private Context context;
    private long minTime; // Min Time between location updates, in milliseconds
    private float minDistance; // Min Distance between location updates, in meters
    private final IBinder mBinder = new GPSService.LocalBinder();
    private int localLog;

    public static class LocalBinder extends Binder {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        Logger.d(TAG, "Started GPS Service", localLog);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mProvider = mLocationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (mProvider == null) {
            Logger.e(TAG, "Unable to get GPS_PROVIDER", localLog);
            return;
        }
        gpsPermissionGranted = Constants.preMarshmallow;

        // set the min time and distance for the location manager
        LocationRequest mLocationRequest = UtilsMap.getLocationRequest(context);
        minTime = mLocationRequest.getInterval();
        minDistance = mLocationRequest.getSmallestDisplacement();
    }

    // called when the service is started
    @Override
    @RequiresApi(Build.VERSION_CODES.N)
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Constants.postMarshmallow && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
            gpsPermissionGranted = true;
        if (gpsPermissionGranted)
            mLocationManager.requestLocationUpdates(mProvider.getName(), minTime, minDistance, this);
        if (Constants.postNougat)
            addGnssStatusListener();
        else
            addGpsStatusListener();
        return START_STICKY;
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.N)
    public void onDestroy() {
        removeStatusListener();
        if (Constants.postMarshmallow && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
            gpsPermissionGranted = true;
        if (gpsPermissionGranted)
            mLocationManager.removeUpdates(this);
        super.onDestroy();
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private void addGnssStatusListener() {
        mGnssStatusListener = new GnssStatus.Callback() {
            @Override
            public void onStarted() {
                Logger.d(TAG, "Gnss started", localLog);
            }

            @Override
            public void onStopped() {
                Logger.d(TAG, "Gnss stopped", localLog);
            }

            @Override
            public void onFirstFix(int ttffMillis) {
                Logger.d(TAG, "Got first fix", localLog);
            }

            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                mGnssStatus = status;
                int satelliteCount = mGnssStatus.getSatelliteCount();
                satellites = new ArrayList<>();
                for (int i = 0; i < satelliteCount; i++)
                   satellites.add(getSatellite(mGnssStatus, i));
                putSatellitePreferences();
            }
        };

        checkGPSPermission();
        mLocationManager.registerGnssStatusCallback(mGnssStatusListener);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    // populate a new satellite from gnss status
    private Satellite getSatellite(GnssStatus mGnssStatus, int i) {
        float azimuth = mGnssStatus.getAzimuthDegrees(i);
        float elevation = mGnssStatus.getElevationDegrees(i);
        float snr = mGnssStatus.getCn0DbHz(i);
        int prn = mGnssStatus.getSvid(i);
        boolean used = mGnssStatus.usedInFix(i);
        int type = mGnssStatus.getConstellationType(i);
        return (new Satellite(azimuth, elevation, snr, prn, used, type));
    }

    /*-----------------------OLD API -------------------------------------*/

    @SuppressWarnings("deprecation")
    public static Boolean isLocationEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {  // This is a new method provided in API 28
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return lm.isLocationEnabled();
        } else { // This was deprecated in API 28
            int mode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return (mode != Settings.Secure.LOCATION_MODE_OFF);
        }
    }

    @Deprecated
    private void addGpsStatusListener() {
        Logger.d(TAG, "Adding Gps status listener");
        mGpsStatusListener = new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                checkGPSPermission();
                mGpsStatus = mLocationManager.getGpsStatus(mGpsStatus);
                switch (event) {
                    case GpsStatus.GPS_EVENT_STARTED: break;
                    case GpsStatus.GPS_EVENT_STOPPED: break;
                    case GpsStatus.GPS_EVENT_FIRST_FIX: break;
                    case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                        //Log.d(TAG, "Checking status", localLog);
                        GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
                        satellites = new ArrayList<>();
                        for(GpsSatellite satellite: gpsStatus.getSatellites())
                            satellites.add(getSatellite(satellite));
                        if (Constants.postNougat)  putSatellitePreferences();
                        break;
                }
            }
        };
        if (Constants.postMarshmallow && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
            gpsPermissionGranted = true;
        if (gpsPermissionGranted)
            mLocationManager.addGpsStatusListener(mGpsStatusListener);
    }

    // populate a satellite from gps satellite
    private Satellite getSatellite(GpsSatellite gpsSatellite) {
        float azimuth = gpsSatellite.getAzimuth();
        float elevation = gpsSatellite.getElevation();
        float snr = gpsSatellite.getSnr();
        int prn = gpsSatellite.getPrn();
        boolean used = gpsSatellite.usedInFix();
        return (new Satellite(azimuth, elevation, snr, prn, used));
    }

    /*-------------------------------------------------------------------*/
    private void checkGPSPermission() {
        if ( Constants.postMarshmallow &&
                ContextCompat.checkSelfPermission( context, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    // save the satellite data in preferences
    private void putSatellitePreferences() {
        int numSatellites = satellites.size();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(Constants.GPS_SATELLITES, numSatellites);
        int used_satellites = 0;
        float total_snr = 0;
        for (int i = 0; i < satellites.size(); i++) {
            editor.putFloat(Constants.GPS_AZIMUTH + "_" + i, satellites.get(i).getAzimuth());
            editor.putFloat(Constants.GPS_ELEVATION + "_" + i, satellites.get(i).getElevation());
            editor.putFloat(Constants.GPS_SNR + "_" + i, satellites.get(i).getSnr());
            editor.putInt(Constants.GPS_PRN + "_" + i, satellites.get(i).getPrn());
            editor.putBoolean(Constants.GPS_USED_SATELLITES + "_" + i, satellites.get(i).isUsed() );
            editor.putInt(Constants.GPS_TYPE + "_" + i, satellites.get(i).getType() );
            boolean used_satellite = (Constants.postNougat)? mGnssStatus.usedInFix(i): satellites.get(i).isUsed();
            if (used_satellite) {
                used_satellites++;
                total_snr += (Constants.postNougat)? mGnssStatus.getCn0DbHz(i): satellites.get(i).getSnr();
            }
        }
        float avg_snr = (used_satellites > 0) ? total_snr / used_satellites: 0.0f;
        editor.putFloat(Constants.GPS_SATINFO, avg_snr);
        editor.putInt(Constants.GPS_USED_SATELLITES, used_satellites);
        //Log.d(TAG, "No. of used satellites: " + used_satellites + " SNR: " + total_snr, localLog);
        editor.apply();
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private void removeStatusListener() {
        if (mLocationManager != null) {
            if (Constants.postNougat &&(mGnssStatusListener != null))
                    mLocationManager.unregisterGnssStatusCallback(mGnssStatusListener);
            else if (mGpsStatusListener != null)
                    mLocationManager.removeGpsStatusListener(mGpsStatusListener);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

}