package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
//import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.utils.RepairTrip;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

// break up a trip into one or more sub trips
public class TripSplitActivity extends Activity implements OnMapReadyCallback {

    Context context;
    private int trip_id;
    DetailDB detailDB;
    ArrayList<DetailProvider.Detail> allDetails = null;
    SummaryDB summaryDB;
    ArrayList<Integer> allTripIds = new ArrayList<>();
    boolean freezeMap = false;
    private Timer timer = null;
    private TextView statusView = null;
    GoogleMap gMap;

    // colors for track sections
    private int currentSegment = 0;
    private final int[] trackColors = new int[Constants.MAX_SEGMENTS];
    private final int[] colorIndex = new int[Constants.MAX_SEGMENTS];
    private int NUM_SEGMENTS;

    // hashmap of lat:lon split locations
    private HashMap<String, Integer> splitLocations = new HashMap<>();
    private int INC;
    private int ZOOM;
    final String TAG = "TripSplitActivity";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        context = this;
        trip_id = getIntent().getIntExtra(Constants.PREF_TRIP_ID, -1);
        MapsInitializer.initialize(getApplicationContext());

        buildTrackColors(); buildColorIndex();
        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        NUM_SEGMENTS = summaryDB.getSegmentCount(context, trip_id);
        if ((NUM_SEGMENTS > Constants.MAX_SEGMENTS) || (summaryDB.isImported(context, trip_id)))
            NUM_SEGMENTS = 1;
        currentSegment = NUM_SEGMENTS;
        ZOOM = calcZoom(summary.getDistance());
        allTripIds.add(trip_id);

        // get the detail records for this trip
        allDetails = detailDB.getDetails(context, trip_id);
        int numDetails = allDetails.size();
        INC = (numDetails < 400) ?  1 :        // adjust the increment to speed up rendering
              (numDetails < 800) ?  2 :        // of long trips
              (numDetails < 1600) ? 3 : 4;
        //Log.d(TAG, "INC: " + INC);
        setContentView(R.layout.activity_trip_map);
        setActionBar();
        statusView = this.findViewById(R.id.statusMap);
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_split_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }
*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_split_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_map_pause:
                freezeMap = true;
                cancelTimer();
                break;
            case R.id.action_map_play:
                freezeMap = false;
                handleTimer(true);
                break;
            case R.id.action_map_reverse:
                freezeMap = false;
                handleTimer(false);
                break;
            case R.id.action_map_split:
                freezeMap = true;
                cancelTimer();
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setMessage("Do you want to create " + NUM_SEGMENTS + " sub-trips by segment?");
                alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        Object[] param = {true};
                        new CreateTrips().execute(param);
                        String status = "Creating " + NUM_SEGMENTS + " sub-trips.";
                        statusView.setText(status);
                    }
                });

                alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // if a map click was detected then either freeze the current map
    // or iterate through the segments
    private void handleTimer(final boolean positiveIncr) {
        if (gMap == null) return;
        if ((NUM_SEGMENTS == 1) || (NUM_SEGMENTS > Constants.MAX_SEGMENTS)) {
            Object[] param1 = {gMap, trip_id, Constants.ALL_SEGMENTS};
            new DrawTracks().execute(param1);
        } else {
            cancelTimer();
            timer = new Timer();
            final long DELAY = 0;
            final long PERIOD = 5000;
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    incCurrentSegment(positiveIncr);
                    Object[] param1 = {gMap, trip_id, currentSegment};
                    new DrawTracks().execute(param1);
                }
            }, DELAY, PERIOD);
        }
    }

    private void cancelTimer() {
        if (timer != null)
            timer.cancel();
    }


    @Override
    public void onMapReady(GoogleMap map) {
        gMap = map;
        centerMap(map);
        //Object[] param = {gMap, trip_id, currentSegment};
        //new DrawTracks().execute(param);
        handleTimer(true);

        // handle the touch location
        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                handleClick(latLng);
            }
        });
    }

    private void handleClick(LatLng latLng) {

        // find the closest location on the track to the clicked lat and lon
        int lat = (int) Math.round(latLng.latitude * Constants.MILLION);
        int lon = (int) Math.round(latLng.longitude * Constants.MILLION);

        // get the closest INC locations on the track to clicked location
        //DetailProvider.Detail closestDetail = detailDB.getClosestDetail(context, trip_id, lat, lon, currentSegment);
        ArrayList<DetailProvider.Detail> closestDetails = detailDB.getClosestDetails(context, trip_id, lat, lon, currentSegment, INC, false);

        // find the closest location out of the INC locations
        //float closest_distance = (float) Constants.getFastKmDistance(lat, lon, closestDetail.getLat(), closestDetail.getLon());
        float closest_distance = 10000000;
        String closest_key = "";
        for (DetailProvider.Detail detail: closestDetails) {
            float distance = (float) UtilsMap.getFastKmDistance(lat, lon, detail.getLat(), detail.getLon());
            if (distance < closest_distance) {
                closest_distance = distance;
                closest_key = detail.getLat() + ":" + detail.getLon() + ":" + detail.getTimestamp();
            }
        }

        // set the minimum distance based on the zoom between the closest point and the clicked point
        float zoom = gMap.getCameraPosition().zoom;
        float MIN_DISTANCE;
        if (zoom < 10) MIN_DISTANCE = 4.00f;
        else if ((10 <= zoom) && (zoom < 13)) MIN_DISTANCE = 1.00f;
        else MIN_DISTANCE = 0.10f;

        // if clicked on track
        if (closest_distance < MIN_DISTANCE) {
            //String closest_key = closestDetail.getLat() + ":" + closestDetail.getLon() + ":" + closestDetail.getTimestamp();
            if (splitLocations.containsKey(closest_key))             // remove a split location if clicked on
                splitLocations.remove(closest_key);                  // the same location
            else if (splitLocations.size() < Constants.MAX_SEGMENTS)   // otherwise, add a split location
                    splitLocations.put(closest_key, 1);
        } else {
            // show dialog to create new sub trips
            final int num_trips = splitLocations.size() + 1;
            if (num_trips > 1) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setMessage("Do you want to create " + num_trips + " sub-trips?");
                alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        Object[] param = {false};
                        new CreateTrips().execute(param);
                        String status = "Creating " + num_trips + " sub-trips.";
                        statusView.setText(status);
                        //Toast.makeText(TripSplitActivity.this, "Creating " + num_trips + " sub-trips.", Toast.LENGTH_SHORT).show();

                    }
                });

                alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
            }
        }
        
        // re-draw the track in respective colors
        Object[] param = {gMap, trip_id, currentSegment};
        new DrawTracks().execute(param);
    }

    private class DrawTracks extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = null;
        GoogleMap gMap;
        int trip_id;
        int segment;

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];
            polylineList = new ArrayList<>();
            addToList(polylineList, segment);
            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);

            int segmentNumber = Integer.parseInt(result);
            String status = (segmentNumber != Constants.ALL_SEGMENTS) ? "Segment: " + segmentNumber: "All segments ";
            statusView.setText(status);
            setActionBarTitle(segmentNumber);
        }
    }

    private void addToList(ArrayList<PolylineOptions> polylineList, int segment) {

        int colIndex = 0;
        ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id, segment);
        for (int i = 0; i+INC < details.size(); i += INC) {
            double lat0 = details.get(i).getLat() / Constants.MILLION;
            double lon0 = details.get(i).getLon() / Constants.MILLION;
            double lat1 = details.get(i+INC).getLat() / Constants.MILLION;
            double lon1 = details.get(i+INC).getLon() / Constants.MILLION;
            PolylineOptions polylineOptions = new PolylineOptions();
            polylineOptions.add(new LatLng(lat0, lon0)).color(trackColors[colIndex]);
            polylineOptions.add(new LatLng(lat1, lon1)).color(trackColors[colIndex]);
            polylineList.add(polylineOptions);
            boolean containsKey = false;
            for (int j = i; j < i+INC; j++) {
                String key = details.get(j).getLat() + ":" + details.get(j).getLon() + ":" + details.get(j).getTimestamp();
                if (splitLocations.containsKey(key)) containsKey = true;
            }
            if (containsKey) {
                colIndex = (colIndex < (Constants.MAX_SEGMENTS - 1)) ? colorIndex[++colIndex] : colorIndex[colIndex];
            }
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

    private class CreateTrips extends AsyncTask<Object, Integer, String> {
        SummaryDB summaryTable;
        DetailDB detailTable;
        ArrayList<Integer> trips = new ArrayList<>();

        @Override
        protected String doInBackground(Object... params) {

            boolean splitOnSegments = (boolean) params[0];
            detailTable = new DetailDB(DetailProvider.db);
            summaryTable = new SummaryDB(SummaryProvider.db);

            // if split on segment boundaries, then create the split locations
            // hash set with segment boundaries
            if (splitOnSegments) {
                splitLocations = new HashMap<>();
                for (int segment = 2; segment <= NUM_SEGMENTS; segment++) {
                    DetailProvider.Detail detail = detailTable.getFirstDetail(context, trip_id, segment);
                    String key = detail.getLat() + ":" + detail.getLon() + ":" + detail.getTimestamp();
                    splitLocations.put(key, 1);
                }
            }

            // loop through the detail records of the existing trip
            HashSet<DetailProvider.Detail> subDetails = new HashSet<>();
            for (DetailProvider.Detail detail: allDetails) {
                // build a key from the lat / lon / and timestamp
                String key = detail.getLat() + ":" + detail.getLon() + ":" + detail.getTimestamp();
                // if at a split point, then create a new trip
                if (splitLocations.containsKey(key) && (subDetails.size() > 0) ) {
                    subDetails.add(detail);
                    trips.add(createNewTrip(subDetails));
                    subDetails = new HashSet<>();
                }
                subDetails.add(detail);
            }
            // add the last sub-trip
            if (subDetails.size() > 0)
                trips.add(createNewTrip(subDetails));

            // repair the sub-trips
            int tripNum = 1;
            for (Integer tripId: trips) {
                // Parms - null ProgressListener, trip id, not imported, no calc. segments
                new RepairTrip(context, null, tripId, false, false).run();
                publishProgress(tripNum++);
            }
            return "";
        }

        private int createNewTrip(HashSet<DetailProvider.Detail> details) {
            // create a new trip
            SummaryProvider.Summary summary = SummaryProvider.createSummary();
            int trip_id = summaryTable.addSummary(context, summary);
            for (DetailProvider.Detail detail: details) {
                detail.setTrip_id(trip_id);
                detail.setSegment(1);
                detailTable.addDetail(context, detail);
            }
            return trip_id;
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String suffix = (progress[0] <= 1)? "": "s";
            String status = "Created " + progress[0] + " trip" + suffix;
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            Intent data = getIntent();
            trips.add(trip_id);
            data.putIntegerArrayListExtra("tripIds", trips);
            setResult(RESULT_OK, data);
            finish();
        }
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

    // create a list of colors for tracks in trackcolors
    private void buildTrackColors() {
        SparseIntArray colors = UtilsMap.buildColorRamp();
        for (int i = 0; i < Constants.MAX_SEGMENTS; i++) {
            double fraction = 1.0 * i / Constants.MAX_SEGMENTS;
            Double d = fraction * colors.size();
            int index = d.intValue();
            if (index == colors.size()) index--;   // keep the index within array bounds
            trackColors[i] = colors.get(index);
        }
    }

    // create an index for the trackcolors such that colors are far apart as possible
    private void buildColorIndex() {
        int startColor = 0;
        for (int i = 0; i < Constants.MAX_SEGMENTS; i+=2)
            colorIndex[i] = startColor++;
        int endColor = 15;
        for (int i = 1; i < Constants.MAX_SEGMENTS; i+= 2)
            colorIndex[i] = endColor--;
    }

    // set the zoom lower for longer trip distances
    private int calcZoom(float distance) {
        int zoom;
        if (distance < 10 * 1000.0f) zoom = 15;
        else if (distance < 25 * 1000.0f) zoom = 13;
        else if (distance < 50 * 1000.0f) zoom = 11;
        else if (distance < 100 * 1000.0f) zoom = 9;
        else zoom = 7;
        return zoom;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
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

    // display the action bar
    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setLogo(R.drawable.icon);
        }
    }

    // display the title of the action bar
    private void setActionBarTitle(int segmentNum) {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            String segment = (segmentNum == Constants.ALL_SEGMENTS)? "": "" + segmentNum;
            String title = "Split " + segment;
            actionBar.setTitle(title);
        }
    }
}