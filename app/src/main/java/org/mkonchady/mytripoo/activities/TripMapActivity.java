package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
//import android.widget.Toast;

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
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

// build the maps for the trip
public class TripMapActivity extends Activity implements OnMapReadyCallback {

    Context context;
    int trip_id;
    DetailDB detailDB;
    SummaryDB summaryDB;
    GoogleMap gMap;
    private SparseIntArray colorMap = new SparseIntArray();
    private LatLng firstLocation, lastLocation;
    private int currentSegment = 0;
    private long startTime = 0;
    private boolean freezeMap = false;
    private int NUM_SEGMENTS;
    private int ZOOM;
    private Timer timer = null;
    private TextView statusView = null;
    private int factor;          // maximum value of the color range
    private String plot_speed;

    // maps and index
    private int mapIndex = -1;

    final String TAG = "TripMapActivity";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        context = this;
        trip_id = getIntent().getIntExtra(Constants.PREF_TRIP_ID, -1);
        MapsInitializer.initialize(getApplicationContext());
        //footPrint = BitmapDescriptorFactory.fromResource(R.drawable.footprint);

        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);

        startTime = summary.getStart_time();
        NUM_SEGMENTS = summaryDB.getSegmentCount(context, trip_id);
        // check if segments should be shown or not
        if ( NUM_SEGMENTS > Constants.MAX_SEGMENTS  || NUM_SEGMENTS == 1) { // || summaryDB.isImported(context, trip_id)) {
            NUM_SEGMENTS = 1;
            currentSegment = Constants.ALL_SEGMENTS;
            freezeMap = true;
        } else {
            currentSegment = NUM_SEGMENTS;
        }

        // assign factor to the largest key
        colorMap = UtilsMap.buildColorRamp();
        factor = colorMap.size();
        ZOOM = UtilsMap.calcZoom(summary.getDistance());

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        plot_speed = sharedPreferences.getString(Constants.PREF_PLOT_SPEED, "no");

        setContentView(R.layout.activity_trip_map);
        setActionBar();
        statusView =    this.findViewById(R.id.statusMap);
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_map_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_map_pause:
                freezeMap = true;
                cancelTimer();
                break;
            case R.id.action_map_play:
                if (NUM_SEGMENTS > 1) freezeMap = false;
                if ( (mapIndex == Constants.DRAW_SPEEDS)   || (mapIndex == Constants.DRAW_TRACKS) ||
                     (mapIndex == Constants.DRAW_GRADIENT) || (mapIndex == Constants.DRAW_SATINFO) ||
                     (mapIndex == Constants.DRAW_POWER)    || (mapIndex == Constants.DRAW_WINDS)) {
                    handleTimer(true);
                }
                break;
            case R.id.action_map_reverse:
                if (NUM_SEGMENTS > 1) freezeMap = false;
                if ( (mapIndex == Constants.DRAW_SPEEDS)   || (mapIndex == Constants.DRAW_TRACKS) ||
                     (mapIndex == Constants.DRAW_GRADIENT) || (mapIndex == Constants.DRAW_SATINFO) ||
                     (mapIndex == Constants.DRAW_POWER)    || (mapIndex == Constants.DRAW_WINDS)) {
                    handleTimer(false);
                }
                break;
            case R.id.action_speed_map:
                mapIndex = Constants.DRAW_SPEEDS;
                onMapReady(gMap);
                break;
            case R.id.action_tracks_map:
                mapIndex = Constants.DRAW_TRACKS;
                onMapReady(gMap);
                break;
            case R.id.action_tracks_winds:
                mapIndex = Constants.DRAW_WINDS;
                onMapReady(gMap);
                break;
            case R.id.action_elevation_map:
                mapIndex = Constants.DRAW_ELEVATION;
                onMapReady(gMap);
                break;
            case R.id.action_gradient_map:
                mapIndex = Constants.DRAW_GRADIENT;
                onMapReady(gMap);
                break;
            case R.id.action_satinfo_map:
                mapIndex = Constants.DRAW_SATINFO;
                onMapReady(gMap);
                break;
            case R.id.action_power_map:
                mapIndex = Constants.DRAW_POWER;
                onMapReady(gMap);
                break;
            case R.id.action_bearing_map:
                cancelTimer();
                Intent intent = new Intent(TripMapActivity.this, BearingActivity.class);
                intent.putExtra(Constants.PREF_TRIP_ID, trip_id);
                intent.putExtra("MAPINDEX", mapIndex);
                mapIndex = Constants.DRAW_BEARINGS;
                startActivityForResult(intent, Constants.DRAW_BEARINGS);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mapIndex = (resultCode == Activity.RESULT_OK)? data.getIntExtra("MAPINDEX", 0): 0;
        currentSegment = Constants.ALL_SEGMENTS;
        onMapReady(gMap);
    }

    @Override
    public void onMapReady(GoogleMap map) {

        gMap = map;
        int[] bounds = detailDB.getBoundaries(context, trip_id);
        UtilsMap.centerMap(map, bounds, ZOOM, context);

        // handle the touch location
        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                handleClick(latLng);
            }
        });

        // show an information window
        map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                String snippet = marker.getSnippet();
                String[] parts = snippet.split(Constants.DELIMITER);
                final ViewGroup viewGroup = (ViewGroup) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
                View v = (parts.length > 3)? getLayoutInflater().inflate(R.layout.map_marker4, viewGroup, false):
                                             getLayoutInflater().inflate(R.layout.map_marker3, viewGroup, false);
                TextView titleView =    v.findViewById(R.id.markerView1);
                titleView.setText(marker.getTitle());
                if (snippet.length() > 0) {
                    TextView detailView1 =    v.findViewById(R.id.markerView2);
                    detailView1.setText(parts[0]);
                    TextView detailView2 =    v.findViewById(R.id.markerView3);
                    detailView2.setText(parts[1]);
                    TextView detailView3 =    v.findViewById(R.id.markerView4);
                    detailView3.setText(parts[2]);
                    if (parts.length > 3) {
                        TextView detailView4 =    v.findViewById(R.id.markerView5);
                        detailView4.setText(parts[3]);
                        TextView detailView5 =    v.findViewById(R.id.markerView6);
                        detailView5.setText(parts[4]);
                    }
                }
                return v;
            }
        });

        // draw the appropriate map
        switch (mapIndex) {
            case Constants.DRAW_SPEEDS:
                handleTimer(true);      // first time loop through segments
                break;
            case Constants.DRAW_TRACKS:
                currentSegment = Constants.ALL_SEGMENTS;
                handleTimer(true);
                break;
            case Constants.DRAW_WINDS:
                currentSegment = Constants.ALL_SEGMENTS;
                handleTimer(true);
                break;
            case Constants.DRAW_ELEVATION:
                freezeMap = true;
                cancelTimer();
                currentSegment = Constants.ALL_SEGMENTS;
                Object[] param2 = {gMap, trip_id, Constants.ALL_SEGMENTS};
                new DrawAltitudes().execute(param2);
                break;
            case Constants.DRAW_GRADIENT:
                currentSegment = Constants.ALL_SEGMENTS;
                handleTimer(true);
                break;
            case Constants.DRAW_SATINFO:
                currentSegment = Constants.ALL_SEGMENTS;
                handleTimer(true);
                break;
            case Constants.DRAW_POWER:
                currentSegment = Constants.ALL_SEGMENTS;
                handleTimer(true);
                break;
            default:
                cancelTimer();
                break;
        }

    }
    // if a map click was detected then either freeze the current map
    // or iterate through the segments
    private void handleTimer(final boolean positiveIncr) {
        if (gMap == null) return;
        if ((NUM_SEGMENTS == 1) || (NUM_SEGMENTS > Constants.MAX_SEGMENTS) ) {
            Object[] param1 = {gMap, trip_id, Constants.ALL_SEGMENTS};
            drawMap(param1);
        } else {
            cancelTimer();
            timer = new Timer();
            final long DELAY = 0;
            final long PERIOD = 10000;
            timer.scheduleAtFixedRate(new TimerTask() {

                @Override
                public void run() {
                    incCurrentSegment(positiveIncr);
                    Object[] param1 = {gMap, trip_id, currentSegment};
                    drawMap(param1);
                }
            }, DELAY, PERIOD);
        }
    }

    // draw the map based on the mapindex
    private void drawMap(Object[] param) {
        switch (mapIndex) {
            case Constants.DRAW_SPEEDS: new DrawSpeeds().execute(param); break; // draw all segments for speeds
            case Constants.DRAW_TRACKS: new DrawTracks().execute(param); break; // draw all segments for tracks
            case Constants.DRAW_WINDS: new DrawWinds().execute(param); break; // draw all segments for winds
            case Constants.DRAW_GRADIENT: new DrawGradient().execute(param); break; // draw all segments for gradients
            case Constants.DRAW_SATINFO: new DrawSatinfo().execute(param); break;   // draw all segments for satellite info
            case Constants.DRAW_POWER: new DrawPower().execute(param); break;   // draw all segments for power
            default: break;
        }
    }

    private void handleClick(LatLng latLng) {

        // find the closest location on the track to the clicked lat and lon
        int lat = (int) Math.round(latLng.latitude * Constants.MILLION);
        int lon = (int) Math.round(latLng.longitude * Constants.MILLION);

        // segment number must be -1 or between 1 and NUM_SEGMENTS
        if (!validCurrentSegment()) return;

        // get the closest location on the track to clicked location
        int segmentNumber = currentSegment;
        int[] closestLocation = detailDB.getClosestDetailByLocation(context, trip_id, lat, lon, segmentNumber, 1);

        float distance = (float) UtilsMap.getFastKmDistance(lat, lon, closestLocation[0], closestLocation[1]);
        float zoom = gMap.getCameraPosition().zoom;

        // set the minimum distance based on the zoom between the closest point and the clicked point
        float MIN_DISTANCE;
        if (zoom < 10) MIN_DISTANCE = 4.00f;
        else if ((10 <= zoom) && (zoom < 13)) MIN_DISTANCE = 1.00f;
        else MIN_DISTANCE = 0.10f;

        boolean clickedOnTrack = (distance < MIN_DISTANCE); // click is on the track

        // if the map is frozen, i.e. timer has been cancelled
        if (freezeMap) {
            if (clickedOnTrack) {
               setMarkerInformation(closestLocation);
            } else {
                setSegmentInformation(latLng);
            }
        }
    }

    private void setMarkerInformation(int[] closestLocation) {
        String snippet = genSnippet(closestLocation);
        LatLng location = new LatLng(closestLocation[0] / Constants.MILLION, closestLocation[1] / Constants.MILLION);
        if (mapIndex != Constants.DRAW_TRACKS && mapIndex != Constants.DRAW_WINDS)
            gMap.addMarker(new MarkerOptions().position(location).title("Details").snippet(snippet)).showInfoWindow();
    }

    private String genSnippet(int[] closestLocation) {
        String snippet = "";
        DetailProvider.Detail detail = detailDB.getDetail(context, trip_id, closestLocation[0], closestLocation[1], currentSegment);
        if (detail == null)
            return snippet;
        LatLng location = new LatLng(closestLocation[0] / Constants.MILLION, closestLocation[1] / Constants.MILLION);
        String speed_string = "Speed: " + UtilsMisc.formatFloat(detail.getSpeed(), 1) + " kmph.";
        //String bearing_string = "Bearing: " + detail.getBearing() + "°";
        String duration_string = "Duration: " + UtilsDate.getTimeDurationHHMMSS(detail.getTimestamp() - startTime, false) + " secs.";
        String altitude_string =  "Altitude: " + UtilsMisc.formatFloat(detail.getAltitude(),1) + " m.";
        String latitude_string = "Latitude: " + UtilsMisc.formatDouble(location.latitude, 6) + "°";
        String longitude_string = "Longitude: " + UtilsMisc.formatDouble(location.longitude, 6) + "°";
        String gradient_string = "Gradient: " + UtilsMisc.formatFloat(detail.getGradient(), 2) + "%";
        String satellites_string = "Satellites: " + detail.getSatellites();
        String snr_string = "Avg. SNR: " + UtilsMisc.formatFloat(detail.getSatinfo(), 1) + "db.";
        String power_string = "Power: " + UtilsMisc.formatDouble(detail.getPower(),0) + " watts";
        String wind_string = "Wind: " + UtilsMisc.formatDouble(detail.getWindSpeed() * 3.6, 0) + " kmph." +
                " from " + UtilsMap.getDirection(detail.getWindDeg());

        // build the snippet depending on the type of map
        switch (mapIndex) {
            case Constants.DRAW_ELEVATION:
                snippet = altitude_string + Constants.DELIMITER + latitude_string + Constants.DELIMITER + longitude_string;
                break;
            case Constants.DRAW_SPEEDS:
                snippet = speed_string + Constants.DELIMITER + duration_string + Constants.DELIMITER + altitude_string;
                break;
            //case DRAW_TRACKS:
            //    snippet = latitude_string + Constants.DELIMITER + longitude_string + Constants.DELIMITER + bearing_string;
            //    break;
            case Constants.DRAW_GRADIENT:
                snippet = gradient_string + Constants.DELIMITER + speed_string + Constants.DELIMITER + altitude_string;
                break;
            case Constants.DRAW_SATINFO:
                snippet = duration_string + Constants.DELIMITER + satellites_string + Constants.DELIMITER + snr_string;
                break;
            case Constants.DRAW_POWER:
                snippet = wind_string + Constants.DELIMITER + speed_string + Constants.DELIMITER + power_string;
                break;
            default:
                snippet = "";
        }
        return snippet;
    }
    private void setSegmentInformation(LatLng latLng) {
        String snippet; String title;
        if (currentSegment == Constants.ALL_SEGMENTS) {
            SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
            long start = summary.getStart_time();
            long end = summary.getEnd_time();
            String duration = UtilsDate.getTimeDurationHHMMSS(end - start, false);
            String fDistance = UtilsMisc.formatFloat(summary.getDistance() / 1000.0f, 3);
            String fSpeed = UtilsMisc.formatFloat((float) UtilsMap.calcKmphSpeed(summary.getDistance(), end - start), 3);
            snippet = "Duration: " + duration + Constants.DELIMITER + "Distance: " + fDistance + " kms." +
                    Constants.DELIMITER + "Speed: " + fSpeed + " kmph.";
            title = "Summary ";
        } else {
            String duration = UtilsDate.getTimeDurationHHMMSS(summaryDB.getSegmentTime(context, trip_id, currentSegment), false);
            String fDistance = UtilsMisc.formatFloat(summaryDB.getSegmentDistance(context, trip_id, currentSegment), 3);
            String fSpeed = UtilsMisc.formatFloat(summaryDB.getSegmentSpeed(context, trip_id, currentSegment), 3);

            snippet = "Duration: " + duration + Constants.DELIMITER + "Distance: " + fDistance + " kms." +
                    Constants.DELIMITER + "Speed: " + fSpeed + " kmph.";
            title = "Summary " + currentSegment;
        }
        gMap.addMarker(new MarkerOptions().position(latLng).title(title).snippet(snippet)).showInfoWindow();
    }

    private class DrawSpeeds extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = null;
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
            float min_speed = summary.getMin_speed();
            float max_speed = summary.getMax_speed();
            polylineList = new ArrayList<>();
            if (segment == Constants.ALL_SEGMENTS) {
                int max_segments = summaryDB.getSegmentCount(context, trip_id);
                for (int i = 1; i <= max_segments; i++)
                    addToList(polylineList, i, min_speed, max_speed);
            } else {
                addToList(polylineList, segment, min_speed, max_speed);
            }
            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);
            addFirstLastMarker();
            int segmentNumber = Integer.parseInt(result);
            String status = (segmentNumber != Constants.ALL_SEGMENTS)? "Segment: " + segmentNumber: " All segments";
            statusView.setText(status);
            setActionBarTitle(segmentNumber);
        }
    }

    private class DrawTracks extends AsyncTask<Object, Integer, String> {
        GoogleMap gMap;
        int trip_id;
        int segment;
        ArrayList<MarkerOptions> footprints = new ArrayList<>();

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];

            // get the min and max speeds
            SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
            float min_speed = summary.getMin_speed();
            float max_speed = summary.getMax_speed();

            // get the detail records for this trip
            ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id, segment);
            firstLocation = new LatLng(details.get(0).getLat() / Constants.MILLION,
                                       details.get(0).getLon() / Constants.MILLION);
            lastLocation = new LatLng(details.get(details.size() - 1).getLat() / Constants.MILLION,
                                      details.get(details.size() - 1).getLon() / Constants.MILLION);
            int numDetails = details.size();
            int INC = getInc(numDetails);
            int prevLat = (int) Math.round(firstLocation.latitude  * Constants.MILLION);
            int prevLon = (int) Math.round(firstLocation.longitude * Constants.MILLION);
            float distance = 0.0f;
            // build an array of footprint markers at specific locations
            for (int j = 0; j < details.size(); j += INC) {
                DetailProvider.Detail detail = details.get(j);
                int bearing = detail.getBearing();
                double lat = detail.getLat() / Constants.MILLION;
                double lon = detail.getLon() / Constants.MILLION;
                distance += UtilsMap.getFastKmDistance(detail.getLat(), detail.getLon(), prevLat, prevLon);
                float speed = detail.getSpeed();
                prevLat = detail.getLat(); prevLon = detail.getLon();
                // build the snippet string
                String distance_string = "Distance: " + UtilsMisc.formatFloat(distance, 3) + " kms.";
                String latitude_string = "Latitude: " + UtilsMisc.formatDouble(lat, 6);
                String longitude_string = "Longitude: " + UtilsMisc.formatDouble(lon, 6);
                String speed_string = "Speed: " + UtilsMisc.formatFloat(speed, 3) + " kmph.";
                String duration_string = "Duration: " + UtilsDate.getTimeDurationHHMMSS(detail.getTimestamp() - startTime, false) + " secs.";
                String snippet = latitude_string + Constants.DELIMITER + longitude_string + Constants.DELIMITER
                               + distance_string + Constants.DELIMITER + duration_string + Constants.DELIMITER + speed_string;
                LatLng location = new LatLng(lat, lon);

                // calculate the color of the footprint and create the bitmap
                double percent = UtilsMisc.calcPercentage(min_speed, max_speed, speed);
                int colorValue = numberToColor(percent);
                BitmapDescriptor footprint;
                if (j == 0)                 // first footprint
                    footprint = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
                else if ((j+INC) >= details.size())         // last footprint
                    footprint = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
                else                                        // other footprints
                    footprint = BitmapDescriptorFactory.fromBitmap(UtilsMap.getFootprintBitmap(colorValue));

                MarkerOptions footprintMarker = new MarkerOptions().position(location).icon(footprint)
                        .anchor(0.5f, 0.5f).rotation(bearing).snippet(snippet).title("Detail");
                footprints.add(footprintMarker);
            }

            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (MarkerOptions footprint: footprints)
                gMap.addMarker(footprint);

            int segmentNumber = Integer.parseInt(result);
            String status = (segmentNumber != Constants.ALL_SEGMENTS)? "Segment: " + segmentNumber: " All segments";
            statusView.setText(status);
            setActionBarTitle(segmentNumber);
        }
    }

    private class DrawWinds extends AsyncTask<Object, Integer, String> {
        GoogleMap gMap;
        int trip_id;
        int segment;
        ArrayList<MarkerOptions> footprints = new ArrayList<>();

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];

            // get the min and max speeds
            SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
            float min_speed = 0.0f;
            float max_speed = 15.0f;

            // get the detail records for this trip
            ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id, segment);
            firstLocation = new LatLng(details.get(0).getLat() / Constants.MILLION,
                    details.get(0).getLon() / Constants.MILLION);
            lastLocation = new LatLng(details.get(details.size() - 1).getLat() / Constants.MILLION,
                    details.get(details.size() - 1).getLon() / Constants.MILLION);
            int numDetails = details.size();
            int INC = getInc(numDetails);
            int prevLat = (int) Math.round(firstLocation.latitude  * Constants.MILLION);
            int prevLon = (int) Math.round(firstLocation.longitude * Constants.MILLION);
           // float distance = 0.0f;
            // build an array of footprint markers at specific locations
            for (int j = 0; j < details.size(); j += INC) {
                DetailProvider.Detail detail = details.get(j);
                int bearing = detail.getBearing();
                double lat = detail.getLat() / Constants.MILLION;
                double lon = detail.getLon() / Constants.MILLION;
                float wind_speed = (float) detail.getWindSpeed();
                wind_speed = (float) UtilsMap.mpsTokmph(wind_speed);
                int wind_deg =  (int) detail.getWindDeg();

                // build the snippet string
                String wind_speed_string = "Wind Speed: " + UtilsMisc.formatFloat(wind_speed, 1) + " kmph.";
                String wind_degree_string = "Wind Degree: " + wind_deg + " °";
                String latitude_string = "Latitude: " + UtilsMisc.formatDouble(lat, 6);
                String longitude_string = "Longitude: " + UtilsMisc.formatDouble(lon, 6);
                String duration_string = "Duration: " + UtilsDate.getTimeDurationHHMMSS(detail.getTimestamp() - startTime, false) + " secs.";
                String snippet = latitude_string + Constants.DELIMITER + longitude_string + Constants.DELIMITER
                        + wind_speed_string + Constants.DELIMITER + duration_string + Constants.DELIMITER + wind_degree_string;
                LatLng location = new LatLng(lat, lon);

                // calculate the color of the footprint and create the bitmap
                double percent = UtilsMisc.calcPercentage(min_speed, max_speed, wind_speed);
                int colorValue = numberToColor(percent);
                BitmapDescriptor footprint;
                if (j == 0)                 // first footprint
                    footprint = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
                else if ((j+INC) >= details.size())         // last footprint
                    footprint = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
                else                                        // other footprints
                    footprint = BitmapDescriptorFactory.fromBitmap(UtilsMap.getArrowBitmap(colorValue));

                MarkerOptions footprintMarker = new MarkerOptions().position(location).icon(footprint)
                        .anchor(0.5f, 0.5f).rotation((wind_deg + 180) % 360).snippet(snippet).title("Detail");
                footprints.add(footprintMarker);
            }

            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (MarkerOptions footprint: footprints)
                gMap.addMarker(footprint);

            int segmentNumber = Integer.parseInt(result);
            String status = (segmentNumber != Constants.ALL_SEGMENTS)? "Segment: " + segmentNumber: " All segments";
            statusView.setText(status);
            setActionBarTitle(segmentNumber);
        }
    }


    private class DrawAltitudes extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = new ArrayList<>();
        GoogleMap gMap;
        int trip_id;
        int segment;

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];

            // get the min and max altitudes
            SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
            float min_elevation = summary.getMin_elevation();
            float max_elevation = summary.getMax_elevation();

            int max_segments = summaryDB.getSegmentCount(context, trip_id);
            for (int i = 1; i <= max_segments; i++)
                addToList(polylineList, i, min_elevation, max_elevation);

            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            currentSegment = Constants.ALL_SEGMENTS;
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);
            addFirstLastMarker();
            String status =  "All segments";
            statusView.setText(status);
            setActionBarTitle(0);

        }
    }

    private class DrawGradient extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = new ArrayList<>();
        GoogleMap gMap;
        int trip_id;
        int segment;

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];
            polylineList = new ArrayList<>();
            if (segment == Constants.ALL_SEGMENTS) {
                int max_segments = summaryDB.getSegmentCount(context, trip_id);
                for (int i = 1; i <= max_segments; i++)
                    addToList(polylineList, i, -10.0f, +10.0f);
            } else {
                addToList(polylineList, segment, -10.0f, +10.0f);
            }
            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);
            addFirstLastMarker();
            int segmentNumber = Integer.parseInt(result);
            String status = (segmentNumber != Constants.ALL_SEGMENTS)? "Segment: " + segmentNumber: " All segments";
            statusView.setText(status);
            setActionBarTitle(segmentNumber);
        }
    }

    private class DrawSatinfo extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = new ArrayList<>();
        GoogleMap gMap;
        int trip_id;
        int segment;

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];
            polylineList = new ArrayList<>();
            if (segment == Constants.ALL_SEGMENTS) {
                int max_segments = summaryDB.getSegmentCount(context, trip_id);
                for (int i = 1; i <= max_segments; i++)
                    addToList(polylineList, i, 0.0f, +100.0f);
            } else {
                addToList(polylineList, segment, 0.0f, +100.0f);
            }
            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);
            addFirstLastMarker();
            int segmentNumber = Integer.parseInt(result);
            String status = (segmentNumber != Constants.ALL_SEGMENTS) ? "Segment: " + segmentNumber: " All segments";
            statusView.setText(status);
            setActionBarTitle(segmentNumber);

        }
    }

    private class DrawPower extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = new ArrayList<>();
        GoogleMap gMap;
        int trip_id;
        int segment;

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];
            polylineList = new ArrayList<>();
            if (segment == Constants.ALL_SEGMENTS) {
                int max_segments = summaryDB.getSegmentCount(context, trip_id);
                for (int i = 1; i <= max_segments; i++)
                    addToList(polylineList, i, 0.0f, +700.0f);
            } else {
                addToList(polylineList, segment, 0.0f, +700.0f);
            }
            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);
            addFirstLastMarker();
            int segmentNumber = Integer.parseInt(result);
            String status = (segmentNumber != Constants.ALL_SEGMENTS)? "Segment: " + segmentNumber: " All segments";
            statusView.setText(status);
            setActionBarTitle(segmentNumber);
        }
    }


    private void addFirstLastMarker() {
        // add the last marker
        int[] lastloc = { (int) Math.round(lastLocation.latitude * Constants.MILLION),
                          (int) Math.round(lastLocation.longitude * Constants.MILLION)};
        gMap.addMarker(new MarkerOptions().position(lastLocation).title("Details").snippet(genSnippet(lastloc)));

        // add the first marker
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(
                BitmapDescriptorFactory.HUE_GREEN);
        int[] firstloc = { (int) Math.round(firstLocation.latitude * Constants.MILLION),
                (int) Math.round(firstLocation.longitude * Constants.MILLION)};
        gMap.addMarker(new MarkerOptions().icon(bitmapDescriptor).position(firstLocation).title("Details")
                .snippet(genSnippet(firstloc)));
    }

    private void addToList(ArrayList<PolylineOptions> polylineList, int segment,
                           float min_value, float max_value) {
        // get the detail records for this trip
        ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id, segment);
        // set the start and end location for the map
        firstLocation = new LatLng(details.get(0).getLat() / Constants.MILLION,
                                   details.get(0).getLon() / Constants.MILLION);
        lastLocation = new LatLng(details.get(details.size() - 1).getLat() / Constants.MILLION,
                details.get(details.size() - 1).getLon() / Constants.MILLION);

        // calculate the gradients for the map
        float[] gradients = new float[details.size()];
        float max_gradient = -Constants.LARGE_FLOAT;
        float min_gradient = Constants.LARGE_FLOAT;
        if (mapIndex == Constants.DRAW_GRADIENT) {
            gradients[0] = 0.0f;
            for (int i = 1; i < details.size(); i++) {
                gradients[i] = details.get(i).getGradient();
                if (gradients[i] > max_gradient) max_gradient = gradients[i];
                if (gradients[i] < min_gradient) min_gradient = gradients[i];
            }
        }

        // calculate the satellites for the map
        float[] satellites = new float[details.size()];
        float max_satellites = -Constants.LARGE_FLOAT;
        float min_satellites = Constants.LARGE_FLOAT;
        if (mapIndex == Constants.DRAW_SATINFO) {
            for (int i = 0; i < details.size(); i++) {
                satellites[i] = details.get(i).getSatellites();
                if (satellites[i] > max_satellites) max_satellites = satellites[i];
                if (satellites[i] < min_satellites) min_satellites = satellites[i];
            }
        }

        // calculate the min / max power for the map
        float max_power = -Constants.LARGE_FLOAT;
        float min_power = Constants.LARGE_FLOAT;
        if (mapIndex == Constants.DRAW_POWER) {
            for (int i = 0; i < details.size(); i++) {
                float power = (float) details.get(i).getPower();
                if (power > max_power) max_power = power;
                if (power < min_power) min_power = power;
            }
        }

        int numDetails = details.size();
        int INC = getInc(numDetails);

        for (int i = INC; i < details.size(); i += INC) {
            double lat0 = details.get(i - INC).getLat() / Constants.MILLION;
            double lon0 = details.get(i - INC).getLon() / Constants.MILLION;
            double lat1 = details.get(i).getLat() / Constants.MILLION;
            double lon1 = details.get(i).getLon() / Constants.MILLION;

            // convert the current speed to a percentage and get the appropriate color
            double percent;
            int colorValue;
            switch (mapIndex) {
                case Constants.DRAW_SPEEDS:
                    percent = UtilsMisc.calcPercentage(min_value, max_value, details.get(i).getSpeed());
                    colorValue = numberToColor(percent);
                    break;
                case Constants.DRAW_ELEVATION:
                    percent = UtilsMisc.calcPercentage(min_value, max_value, details.get(i).getAltitude());
                    colorValue = numberToColor(percent);
                    break;
                case Constants.DRAW_GRADIENT:
                    percent = UtilsMisc.calcPercentage(min_gradient, max_gradient, gradients[i]);
                    colorValue = numberToColor(percent);
                    break;
                case Constants.DRAW_SATINFO:
                    percent = UtilsMisc.calcPercentage(min_satellites, max_satellites, satellites[i]);
                    colorValue = numberToColor(percent);
                    break;
                case Constants.DRAW_POWER:
                    percent = UtilsMisc.calcPercentage(min_power, max_power, (float) details.get(i).getPower());
                    colorValue = numberToColor(percent);
                    break;
                default:
                    percent = 0.0;
                    colorValue = numberToColor(percent);
                    break;
            }
            PolylineOptions polylineOptions = new PolylineOptions();
            polylineOptions.add(new LatLng(lat0, lon0)).color(colorValue);
            polylineOptions.add(new LatLng(lat1, lon1)).color(colorValue);
            polylineList.add(polylineOptions);
        }

    }

    private int getInc(int numDetails) {
        if (plot_speed.equals("yes")) return 1;
        return (numDetails < 400)  ? 1 :        // adjust the increment to speed up rendering
               (numDetails < 800)  ? 2 :        // of long trips
               (numDetails < 1600) ? 3 : 4;
    }

    private int numberToColor(double value) {
        return numberToColor(value, true);
    }

    // value should be a percentage -- restrict value to 0 and 100
    private int numberToColor(double value, boolean ascending) {
        if (value < 0) value = 0;
        if (value > 100.0) value = 100.0;
        value = value / 100.0;
        if (!ascending) value = 1.0 - value;
        Double d = value * factor;      // convert to a value that will map to a color
        int index = d.intValue();
        if (index == factor) index--;   // keep the index within array bounds
        return colorMap.get(index);
    }

    private void incCurrentSegment(boolean positiveIncr) {
        currentSegment = (positiveIncr)? currentSegment+1: currentSegment-1;
        if (positiveIncr) {
            if (currentSegment == 0)
                currentSegment = 1;
            else if (currentSegment > NUM_SEGMENTS)
                currentSegment = Constants.ALL_SEGMENTS;
        } else {
            if (currentSegment == 0)
                currentSegment = Constants.ALL_SEGMENTS;
            else if (currentSegment < 0)
                currentSegment = NUM_SEGMENTS;
        }
    }

    private boolean validCurrentSegment() {
        return (currentSegment == Constants.ALL_SEGMENTS) || ((1 <= currentSegment) && (currentSegment <= NUM_SEGMENTS));
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mapIndex == -1)
            mapIndex = Constants.DRAW_SPEEDS;
        handleTimer(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelTimer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelTimer();
    }

    private void cancelTimer() {
        if (timer != null)
            timer.cancel();
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

    // display the title of the action bar
    private void setActionBarTitle(int segmentNum) {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            String title = "";
            String segment = (segmentNum == Constants.ALL_SEGMENTS)? "": " " + segmentNum + "";
            switch (mapIndex) {
                case Constants.DRAW_ELEVATION:  title += "Altitude"; break;
                case Constants.DRAW_SPEEDS:     title += "Speed" + segment; break;
                case Constants.DRAW_TRACKS:     title += "Tracks" + segment; break;
                case Constants.DRAW_WINDS:     title += "Winds" + segment; break;
                case Constants.DRAW_GRADIENT:   title += "Grade" + segment; break;
                case Constants.DRAW_SATINFO:    title += "Satinfo" + segment; break;
                case Constants.DRAW_POWER:    title += "Power" + segment; break;
                default: break;
            }
            actionBar.setTitle(title);
        }
    }
}