package org.mkonchady.mytripoo.activities;

import android.Manifest;
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
import com.google.android.gms.maps.model.PolylineOptions;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsMap;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

// build the maps for the trip
public class TripCurrentActivity extends Activity implements OnMapReadyCallback {

    Context context;
    final int RED_TRACK = 50;
    final int BLUE_TRACK = 0;
    final long REDRAW_DELAY = 0; // delay before drawing current track
    final long SHORT_PERIOD = 5000L; // 5 seconds
    final long LONG_PERIOD = 10000L; // 10 seconds
    long REDRAW_PERIOD = SHORT_PERIOD;  // delay between updates of the track in milliseconds
    final int SMALL_MAP_SIZE = 500; // a small map with less than 500 details
    DetailDB detailDB;
    SummaryDB summaryDB;
    GoogleMap gMap;
    ArrayList<DetailProvider.Detail> follow_details = new ArrayList<>();
    private SparseIntArray colorMap = new SparseIntArray();
    private LatLng firstLocation, lastLocation;
    private int ZOOM;
    private TextView statusView = null;
    private int factor;          // maximum value of the color range
    int trip_id;                 // current trip id
    private int localLog;
    private int follow_trip_id;  // check if we are following a trip
    private Timer timer = null;
    final String TAG = "TripCurrentActivity";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        context = this;
        MapsInitializer.initialize(getApplicationContext());
        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);

        // if there is no running trip, then exit
        trip_id = summaryDB.getRunningSummaryID(context);
        if (trip_id == -1)
            finish();
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);

        // assign factor to the largest key
        colorMap = UtilsMap.buildColorRamp();
        factor = colorMap.size();
        ZOOM = UtilsMap.calcZoom(summary.getDistance());

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        follow_trip_id = sharedPreferences.getInt(Constants.PREF_FOLLOW_TRIP, -1);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

        // get the detail records for follow trip
        if (follow_trip_id != -1)
            follow_details = detailDB.getDetails(context, follow_trip_id, Constants.ALL_SEGMENTS);

        setContentView(R.layout.activity_trip_map);
        setActionBar();
        statusView = this.findViewById(R.id.statusMap);
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        Logger.d(TAG, "Started current map", localLog);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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

        // draw the track of the map for the followed trip in blue
        //if (follow_trip_id != -1) {
        //    Object[] param2 = {gMap, follow_trip_id, Constants.ALL_SEGMENTS, BLUE_TRACK};
        //    new DrawTrack().execute(param2);
        //}

        // start the timer to draw tracks if not null
        //if (gMap == null) return;
        if (timer == null) {
            timer = new Timer();

            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    final Object[] param1 = {gMap, trip_id, follow_trip_id};
                    new DrawTrack().execute(param1);
                }
            }, REDRAW_DELAY, REDRAW_PERIOD);
        }

        // draw the map with the current track in red and followed track in blue
        //Object[] param1 = {gMap, trip_id, follow_trip_id};
        //new DrawTrack().execute(param1);

    }

    private void handleClick(LatLng latLng) {
        int covered_distance = summaryDB.getDistance(context, trip_id);
        int trip_distance, remainder;
        float percent;
        if (follow_trip_id == -1) {
            trip_distance = 0; remainder = 0; percent = 0.0f;
        } else {
            trip_distance = summaryDB.getDistance(context, follow_trip_id);
            remainder = trip_distance - covered_distance;
            percent = (trip_distance == 0)? 0.0f: 100.0f * covered_distance / trip_distance;
        }
        Intent intent = new Intent(this, TripInfoActivity.class);
        intent.putExtra("covered_distance", covered_distance);
        intent.putExtra("trip_distance", trip_distance);
        intent.putExtra("remainder", remainder);
        intent.putExtra("percent", percent);
        startActivity(intent);
    }

    private class DrawTrack extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = new ArrayList<>();
        GoogleMap gMap;
        int draw_trip_id;
        int follow_trip_id;

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            draw_trip_id = (int) params[1];
            follow_trip_id = (int) params[2];
            // build the polylinelist for the current trip and a followed trip

            addToPolyList(polylineList, draw_trip_id, follow_trip_id);
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);
            addFirstLastMarker();
            String status =  "All segments";
            statusView.setText(status);
            setActionBarTitle();
        }
    } // end of class

    private void addToPolyList(ArrayList<PolylineOptions> polylineList, int draw_trip_id, int follow_trip_id) {
        // create a blue track for the followed trip
        if (follow_trip_id != -1) {
            buildPolyline(follow_details, polylineList, numberToColor(BLUE_TRACK));
            if (follow_details.size() > SMALL_MAP_SIZE) REDRAW_PERIOD = LONG_PERIOD;
        }

        // create a red track for the current trip
        ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, draw_trip_id, Constants.ALL_SEGMENTS);
        buildPolyline(details, polylineList, numberToColor(RED_TRACK));

        // set the start and end location for the map
        firstLocation = new LatLng(details.get(0).getLat() / Constants.MILLION,
                                   details.get(0).getLon() / Constants.MILLION);
        lastLocation = new LatLng(details.get(details.size() - 1).getLat() / Constants.MILLION,
                details.get(details.size() - 1).getLon() / Constants.MILLION);
    }

    private void buildPolyline(ArrayList<DetailProvider.Detail> details, ArrayList<PolylineOptions> polylineList, int colorValue) {
        int numDetails = details.size();
        int INC = getInc(numDetails);
        for (int i = INC; i < details.size(); i += INC) {
            double lat0 = details.get(i - INC).getLat() / Constants.MILLION;
            double lon0 = details.get(i - INC).getLon() / Constants.MILLION;
            double lat1 = details.get(i).getLat() / Constants.MILLION;
            double lon1 = details.get(i).getLon() / Constants.MILLION;
            PolylineOptions polylineOptions = new PolylineOptions();
            polylineOptions.add(new LatLng(lat0, lon0)).color(colorValue);
            polylineOptions.add(new LatLng(lat1, lon1)).color(colorValue);
            polylineList.add(polylineOptions);
        }
    }


    private void addFirstLastMarker() {
        // add the last marker and first markers
        gMap.addMarker(new MarkerOptions().position(lastLocation).title("Details").snippet("Last location"));
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(
                BitmapDescriptorFactory.HUE_GREEN);
        gMap.addMarker(new MarkerOptions().icon(bitmapDescriptor).position(firstLocation).title("Details")
                .snippet("First location"));
    }

    private int getInc(int numDetails) {
        return (numDetails < 100)  ? 1 :        // adjust the increment to speed up rendering
               (numDetails < 200)  ? 2 :        // of long trips
               (numDetails < 400)  ? 4 : 8;
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

    // called from the stop button
    public void cancelTimer() {
        if(timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        }
    }

    // display the title of the action bar
    private void setActionBarTitle() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            String title = "Current Trip Track";
            actionBar.setTitle(title);
        }
    }
}