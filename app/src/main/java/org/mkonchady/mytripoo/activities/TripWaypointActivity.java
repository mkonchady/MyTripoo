package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMap;

import java.util.ArrayList;
import java.util.HashMap;

// place waypoints at select locations on the trip
public class TripWaypointActivity extends Activity implements OnMapReadyCallback {

    Context context;
    private int trip_id;
    DetailDB detailDB;
    ArrayList<DetailProvider.Detail> details = null;
    SummaryDB summaryDB;
    SummaryProvider.Summary summary;
    HashMap<String, Marker> waypoints = new HashMap<>();    // keep track of marker locations (value) by timestamp (key)
    LatLng firstLocation, lastLocation;
    Bitmap wayPointBitmap;
    GoogleMap gMap;

    // GUI fields
    private TextView statusView;
    private int currentSegment = 0;
    private int ZOOM;
    private String plot_speed;
    final String TAG = "TripWaypointActivity";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        context = this;
        trip_id = getIntent().getIntExtra(Constants.PREF_TRIP_ID, -1);
        MapsInitializer.initialize(getApplicationContext());

        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
        summary = summaryDB.getSummary(context, trip_id);
        currentSegment = Constants.ALL_SEGMENTS;
        ZOOM = calcZoom(summary.getDistance());

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        plot_speed = sharedPreferences.getString(Constants.PREF_PLOT_SPEED, "no");
        wayPointBitmap = UtilsMap.getCircleBitmap(Color.rgb(255,0,0));

        // get the detail records for this trip
        details = detailDB.getDetails(context, trip_id);
        setContentView(R.layout.activity_trip_map);
        setActionBarTitle(Constants.ALL_SEGMENTS);
        statusView = this.findViewById(R.id.statusMap);
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_edit_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_map_pause:
                break;
            case R.id.action_map_play:
                break;
            case R.id.action_map_reverse:
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        gMap = map;
        handleTimer();
        centerMap(map);

        // handle the touch operation
        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                handleClick(latLng);
            }
        });

        // show an information window
        gMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(final Marker marker) {
                int lat = (int) Math.round(marker.getPosition().latitude * Constants.MILLION);
                int lon = (int) Math.round(marker.getPosition().longitude * Constants.MILLION);

                // get the closest location on the track to clicked location
                int segmentNumber = currentSegment;
                int[] closestLocation = detailDB.getClosestDetailByLocation(context, trip_id, lat, lon, segmentNumber, 1);

                // check for a duplicate click
                String latlon = closestLocation[0] + UtilsMap.DELIMITER + closestLocation[1];
                if (waypoints.containsKey(latlon)) {
                    Marker marker1 = waypoints.get(latlon);
                    marker1.remove();
                    waypoints.remove(latlon);
                }
                return null;
            }
        });
    }

    private void handleClick(LatLng latLng) {

        // find the closest location on the track to the clicked lat and lon
        int lat = (int) Math.round(latLng.latitude * Constants.MILLION);
        int lon = (int) Math.round(latLng.longitude * Constants.MILLION);

        // get the closest location on the track to clicked location
        int segmentNumber = currentSegment;
        int[] closestLocation = detailDB.getClosestDetailByLocation(context, trip_id, lat, lon, segmentNumber, 1);

        // check for a duplicate click
        String latlon = closestLocation[0] + UtilsMap.DELIMITER + closestLocation[1];
        if (waypoints.containsKey(latlon)) {
            Marker marker = waypoints.get(latlon);
            marker.remove();
            return;
        }

        float distance = (float) UtilsMap.getFastKmDistance(lat, lon, closestLocation[0], closestLocation[1]);
        float zoom = gMap.getCameraPosition().zoom;

        // set the minimum distance based on the zoom between the closest point and the clicked point
        float MIN_DISTANCE;
        if (zoom < 10) MIN_DISTANCE = 4.00f;
        else if ((10 <= zoom) && (zoom < 13)) MIN_DISTANCE = 1.00f;
        else MIN_DISTANCE = 0.10f;

        boolean clickedOnTrack = (distance < MIN_DISTANCE); // click is on the track
        if (clickedOnTrack) {
            setMarkerInformation(closestLocation);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            String plural = waypoints.size() > 1? "s": "";
            String title = waypoints.size() == 0? "Delete all waypoints": "Create " + waypoints.size() + " Waypoint" + plural;
            builder.setTitle(title);
            builder.setIcon(R.drawable.icon_w);
            builder.setNegativeButton("No",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            builder.setPositiveButton("Yes",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            String status = "Creating waypoints";
                            statusView.setText(status);
                            summaryDB.setSummaryExtras(context, UtilsJSON.addWayPoints(waypoints.keySet(),
                                                                                          UtilsJSON.delWayPoints(summary.getExtras())), trip_id);
                            dialog.cancel();
                            finish();
                        }
                    });
            builder.show();
        }
    }

    private void setMarkerInformation(int[] closestLocation) {
        LatLng latLng = new LatLng(closestLocation[0] / Constants.MILLION, closestLocation[1] / Constants.MILLION);
        String snippet = closestLocation[0] + UtilsMap.DELIMITER + closestLocation[1];
        Marker marker = gMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(wayPointBitmap)).position(latLng));
                //.title("").snippet(""));
        waypoints.put(snippet, marker);
    }

    // if a map click was detected then either freeze the current map
    // or iterate through the segments
    private void handleTimer() {
        if (gMap == null) return;
            final Object[] param1 = {gMap, trip_id, Constants.ALL_SEGMENTS};
            new DrawTracks().execute(param1);

    }

    private class DrawTracks extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = null;
        ArrayList<String> waypoints = new ArrayList<>();

        GoogleMap gMap;
        int trip_id;
        int segment;

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];

            // get the min and max speeds
            SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
            polylineList = new ArrayList<>();
            if (segment == Constants.ALL_SEGMENTS) {
                int max_segments = summaryDB.getSegmentCount(context, trip_id);
                for (int i = 1; i <= max_segments; i++)
                    addToList(polylineList, i);
            } else {
                addToList(polylineList, segment);
            }
            // get the list of waypoints
            waypoints = UtilsJSON.getWayPoints(summary.getExtras());
            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);
            addMarkers(waypoints);
            int segmentNumber = Integer.parseInt(result);
            String status = (segmentNumber != Constants.ALL_SEGMENTS)? "Segment: " + segmentNumber: " All segments";
            statusView.setText(status);
            setActionBarTitle(segmentNumber);
        }
    }

    private void addToList(ArrayList<PolylineOptions> polylineList, int segment) {
        // get the detail records for this trip
        ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id, segment);
        // set the start and end location for the map
        firstLocation = new LatLng(details.get(0).getLat() / Constants.MILLION,
                details.get(0).getLon() / Constants.MILLION);
        lastLocation = new LatLng(details.get(details.size() - 1).getLat() / Constants.MILLION,
                details.get(details.size() - 1).getLon() / Constants.MILLION);

        int numDetails = details.size();
        int INC = getInc(numDetails);
        PolylineOptions polylineOptions = new PolylineOptions();
        for (int i = INC; i < details.size(); i += INC) {
            double lat0 = details.get(i - INC).getLat() / Constants.MILLION;
            double lon0 = details.get(i - INC).getLon() / Constants.MILLION;
            double lat1 = details.get(i).getLat() / Constants.MILLION;
            double lon1 = details.get(i).getLon() / Constants.MILLION;
            polylineOptions.add(new LatLng(lat0, lon0)).color(Color.rgb(0,0,255));
            polylineOptions.add(new LatLng(lat1, lon1)).color(Color.rgb(0,0,255));
        }
        polylineList.add(polylineOptions);
    }

    private int getInc(int numDetails) {
        if (plot_speed.equals("yes")) return 1;
        return (numDetails < 400)  ? 1 :        // adjust the increment to speed up rendering
                (numDetails < 800)  ? 2 :        // of long trips
                        (numDetails < 1600) ? 3 : 4;
    }

    private void addMarkers(ArrayList<String> current_waypoints) {
        // add the first and last marker
        gMap.addMarker(new MarkerOptions().position(lastLocation).title("Details").snippet("Last Point"));
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(
                BitmapDescriptorFactory.HUE_GREEN);
        gMap.addMarker(new MarkerOptions().icon(bitmapDescriptor).position(firstLocation).title("Details")
                .snippet("First Point"));
        for (String waypoint: current_waypoints) {
            String[] parts = waypoint.split(UtilsMap.DELIMITER);
            double lat0 = Integer.parseInt(parts[0]) / Constants.MILLION;
            double lon0 = Integer.parseInt(parts[1]) / Constants.MILLION;
            LatLng latLng = new LatLng(lat0, lon0);
            Marker marker = gMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(wayPointBitmap)).position(latLng));
            waypoints.put(waypoint, marker);
        }
    }

    public void centerMap(GoogleMap map) {
        int[] bounds = detailDB.getBoundaries(context, trip_id);
        double north = bounds[0] / Constants.MILLION;
        double south = bounds[1] / Constants.MILLION;
        double east = bounds[2] / Constants.MILLION;
        double west = bounds[3] / Constants.MILLION;
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

    // set the zoom lower for longer trip distances
    private int calcZoom(float distance) {
        int zoom;
        if      (distance < 10 * 1000.0f) zoom = 15;
        else if (distance < 25 * 1000.0f) zoom = 13;
        else if (distance < 50 * 1000.0f) zoom = 11;
        else if (distance < 100 * 1000.0f) zoom = 9;
        else zoom = 7;
        return zoom;
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void setActionBarTitle(int currentSegment) {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            String title = "";
            String segment = (currentSegment == Constants.ALL_SEGMENTS)? "": " " + currentSegment + "";
            title += "Waypoints " + segment;
            actionBar.setTitle(title);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setLogo(R.drawable.icon);
        }
    }
}