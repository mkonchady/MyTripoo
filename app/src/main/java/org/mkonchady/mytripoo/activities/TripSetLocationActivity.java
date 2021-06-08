package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;

// show a map to set the correct location
public class TripSetLocationActivity extends Activity implements OnMapReadyCallback {
    Context context;
    GoogleMap gMap;
    SharedPreferences sharedPreferences;
    TextView statusView;
    final String TAG = "TripSetLocationActivity";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        context = this;
        MapsInitializer.initialize(getApplicationContext());
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        //localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        setContentView(R.layout.activity_set_location);
        setActionBar();
        statusView = this.findViewById(R.id.setLocationMap);
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        //Log.d(TAG, "Started current map", localLog);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        gMap = map;
        float lat = sharedPreferences.getFloat(Constants.PREF_LATITUDE, 0.0f);
        float lon = sharedPreferences.getFloat(Constants.PREF_LONGITUDE, 0.0f);
        centerMap(map, lat, lon);
        handleClick(new LatLng(lat, lon));
        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                handleClick(latLng);
            }
        });
    }

    // move the marker to the click location and set the lat/lon in preferences
    private void handleClick(LatLng latLng) {
        gMap.clear();
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(
                BitmapDescriptorFactory.HUE_GREEN);
        gMap.addMarker(new MarkerOptions().icon(bitmapDescriptor).position(latLng).title("Location"));
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(Constants.PREF_LATITUDE, (float) latLng.latitude);
        editor.putFloat(Constants.PREF_LONGITUDE, (float) latLng.longitude);
        editor.apply();
    }

    public void centerMap(GoogleMap map, float lat, float lon) {
        final int ZOOM = 18;
        double north = lat + 0.125;
        double south = lat - 0.125;
        double east = lon + 0.125;
        double west = lon - 0.125;
        LatLng southWest = new LatLng(south, west);
        LatLng northEast = new LatLng(north, east);
        LatLngBounds latLngBounds = new LatLngBounds(southWest, northEast);
        CameraUpdate camera_center = CameraUpdateFactory.newLatLngZoom(latLngBounds.getCenter(), ZOOM);
        CameraUpdate camera_zoom = CameraUpdateFactory.zoomTo(ZOOM);
        map.moveCamera(camera_center);
        map.animateCamera(camera_zoom);
        final int MY_PERMISSIONS_FINE_LOCATION = 10;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_FINE_LOCATION);
        }
        map.setMyLocationEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    // display the action bar
    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
        }
    }
}