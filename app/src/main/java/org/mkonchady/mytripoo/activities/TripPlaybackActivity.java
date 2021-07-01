package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.LongSparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import org.mkonchady.mytripoo.ProgressListener;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

/*
    Show the trip distance and speed in real time

    1. Build the hash of the all timestamps with the location and distance using BuildList
    2. Start DrawTracks with a timer, handle the forward, pause, and reverse buttons

    a. The timer is started from the forward and reverse buttons
    b. The timer is cancelled from onPause and pause button
 */
public class TripPlaybackActivity extends Activity  implements OnMapReadyCallback  {

    // time parameters
    private long currentTime = 0L;
    private Timer timer = null;

    // hash of timestamp with location
    int trip_id;
    private final TreeMap<Long, LocationDistance> mapTrip = new TreeMap<>(new LongComparator());
    private boolean buildIncomplete = true;
    private boolean drawingInProgress = false;

    // time parameters -- increment in seconds
    private int TIME_INCR = 1;
    private int MIN_TIME_INCR = 1;
    private long MAX_DURATION;

    // map and db parameters
    DetailDB detailDB;
    SummaryDB summaryDB;
    LatLng firstLocation, lastLocation;
    private int ZOOM;
    GoogleMap gMap;
    private TextView timeTextView = null;

    Context context;
    SharedPreferences sharedPreferences;
    String TAG = "TripPlaybackActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trip_id = getIntent().getIntExtra(Constants.PREF_TRIP_ID, -1);
        MapsInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_trip_playback);
        timeTextView = findViewById(R.id.timeTextView);

        // get the summary parameters
        context = this;
        summaryDB = new SummaryDB(SummaryProvider.db);
        detailDB = new DetailDB(DetailProvider.db);
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        MAX_DURATION = summary.getEnd_time() - summary.getStart_time();

        // set the minimum time increment
        MIN_TIME_INCR = (MAX_DURATION > 10 * Constants.MILLISECONDS_PER_HOUR)? 30:    // > 10 hours
                        (MAX_DURATION > Constants.MILLISECONDS_PER_HOUR)? 10:1;       // 1-10 hours

        // set the time increment
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        TIME_INCR = sharedPreferences.getInt(Constants.PREF_TIME_INCR, 10);  // default of 10 seconds

        // build the time and location / distance hash for the trip in the treemap -- mapTrip
        Object[] params1 = {context, trip_id};
        new BuildList(this).execute(params1);

        currentTime = 0;
        ZOOM = UtilsMap.calcZoom(summary.getDistance());
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setActionBar();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_playback_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delay_1:
                setTIME_INCR(1);
                break;
            case R.id.action_delay_10:
                setTIME_INCR(10);
                break;
            case R.id.action_delay_30:
                setTIME_INCR(30);
                break;
            case R.id.action_delay_60:
                setTIME_INCR(60);
                break;
            case R.id.action_delay_300:
                setTIME_INCR(300);
                break;
            case R.id.action_delay_600:
                setTIME_INCR(600);
                break;
            case R.id.action_delay_3600:
                setTIME_INCR(3600);
                break;
            case R.id.action_map_pause:
                cancelTimer();
                break;
            case R.id.action_map_play:
                forwardTimer();
                break;
            case R.id.action_map_reverse:
                reverseTimer();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(Constants.PREF_TIME_INCR, TIME_INCR);
        editor.apply();
        return true;
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        gMap = map;
        int[] bounds = detailDB.getBoundaries(context, trip_id);
        UtilsMap.centerMap(map, bounds, ZOOM, context);
    }

    private class DrawTracks extends AsyncTask<Object, Integer, String> {
        ArrayList<PolylineOptions> polylineList = null;
        GoogleMap gMap;
        int trip_id;
        float cum_distance;
        boolean buildTripDone;

        @Override
        protected String doInBackground(Object... params) {
            buildTripDone = true;
            if (buildIncomplete || drawingInProgress) {
                buildTripDone = false;
                return "";
            }
            drawingInProgress = true;
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];

            // build the polyline
            polylineList = new ArrayList<>();
            long firstTime = 0;
            cum_distance = 0.0f;
            if (currentTime == 0) return "";
            double prev_lat = 0; double prev_lon = 0;

            Set<Long> keySet =  mapTrip.keySet();
            PolylineOptions polylineOptions = new PolylineOptions();
            long lastTime = 0;
            for(Long timeKey: keySet) {
                LocationDistance tm = mapTrip.get(timeKey);
                if (firstTime == 0) {
                    firstTime = timeKey;
                    prev_lat = tm.lat / Constants.MILLION;
                    prev_lon = tm.lon / Constants.MILLION;
                } else if (currentTime > 0 && timeKey % TIME_INCR == 0) {
                    cum_distance = tm.getDistance() / 1000.0f;
                    double curr_lat = tm.lat / Constants.MILLION;
                    double curr_lon = tm.lon / Constants.MILLION;
                    polylineOptions.add(new LatLng(prev_lat, prev_lon)).color(Color.RED);
                    polylineOptions.add(new LatLng(curr_lat, curr_lon)).color(Color.RED);
                    prev_lat = curr_lat;
                    prev_lon = curr_lon;
                }
                lastTime = timeKey;
                if ((timeKey - firstTime) >= (currentTime * 1000L) )
                    break;

            }
            // add the last reading if necessary
            if (currentTime + TIME_INCR >= MAX_DURATION / 1000) {
                LocationDistance tm = mapTrip.get(lastTime);
                polylineOptions.add(new LatLng(tm.lat / Constants.MILLION, tm.lon / Constants.MILLION)).color(Color.RED);
            }

            polylineList.add(polylineOptions);

            // lookahead and blank out any old tracks for 8x seconds
            polylineOptions = new PolylineOptions();
            long runTime = lastTime;
            long endTime = lastTime + 8 * MIN_TIME_INCR * 1000;
            while (runTime <= endTime) {
                runTime += 1000L;
                boolean containsKey =  mapTrip.containsKey(runTime);
                if (containsKey) {
                    LocationDistance tm = mapTrip.get(runTime);
                    double curr_lat = tm.getLat() / Constants.MILLION;
                    double curr_lon = tm.getLon() / Constants.MILLION;
                    polylineOptions.add(new LatLng(prev_lat, prev_lon)).color(Color.WHITE);
                    polylineOptions.add(new LatLng(curr_lat, curr_lon)).color(Color.WHITE);
                    polylineList.add(polylineOptions);
                    prev_lat = curr_lat; prev_lon = curr_lon;
                }
            }

            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            if (!buildTripDone) return;
            gMap.clear();
            for (PolylineOptions polylineOptions : polylineList)
                gMap.addPolyline(polylineOptions);
            addFirstLastMarker();
            String status = "D: " + UtilsMisc.formatFloat(cum_distance, 1) + " kms. " +
                            "T: " + UtilsDate.getTimeDurationHHMMSS(currentTime * 1000L, false) + " " +
                            "S: " + UtilsMisc.formatFloat((float) UtilsMap.calcKmphSpeed(cum_distance, currentTime),1) + " kmph.";
            timeTextView.setText(status);
            updateCurrentTime();
            drawingInProgress = false;
        }


        private void addFirstLastMarker() {
            gMap.addMarker(new MarkerOptions().position(lastLocation)); // add last marker
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(
                    BitmapDescriptorFactory.HUE_GREEN);   // add first marker
            gMap.addMarker(new MarkerOptions().icon(bitmapDescriptor).position(firstLocation));
        }
    }

    // increment the current time within time bounds
    public void updateCurrentTime() {
        currentTime += TIME_INCR;
        if (currentTime < 0) {
            currentTime = 0;
        } else if (currentTime * 1000L > MAX_DURATION) {
            currentTime = MAX_DURATION / 1000;
        }
    }

    // called from the stop button
    public void cancelTimer() {
        if(timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    // called from the forward button
    public void forwardTimer() {
        if (TIME_INCR < 0) TIME_INCR = -TIME_INCR;
        if (timer == null) startTimer();
    }

    // called from the reverse button
    public void reverseTimer() {
        if (TIME_INCR > 0) TIME_INCR = -TIME_INCR;
        if (timer == null) startTimer();
    }

    // start the timer to draw tracks if not null
    private void startTimer() {
        if (gMap == null) return;
        if (timer == null) {
            timer = new Timer();
            final long DELAY = 0;
            final long PERIOD = 1000L;
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    final Object[] param1 = {gMap, trip_id};
                    new DrawTracks().execute(param1);
                }
            }, DELAY, PERIOD);
        }
    }

    public void setTIME_INCR(int TIME_INCR) {
        if (TIME_INCR >= MIN_TIME_INCR) {
            this.TIME_INCR = TIME_INCR;
            currentTime = TIME_INCR;        // start over again if the time increment changes
        }
    }

    public int getTrip_id() {
        return trip_id;
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

    // display the action bar
    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
        }
    }

    // Build the times and locations array lists for the trip
    private static class BuildList extends AsyncTask<Object, Integer, String> implements ProgressListener {
        private LongSparseArray<LocationDistance> localMapTrip = new LongSparseArray<>();
        WeakReference<TripPlaybackActivity> activityReference;

        // only retain a weak reference to the activity
        BuildList(TripPlaybackActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        // parameters
        // 1. context 2. trip id
        protected String doInBackground(Object...params) {
            //Log.d(TAG, "Started build list");
            Context context = (Context) params[0];
            int tripId = (int) params[1];
            ArrayList<DetailProvider.Detail> details = activityReference.get().detailDB.getDetails(context, tripId);
            DetailProvider.Detail firstDetail = details.get(0);
            DetailProvider.Detail lastDetail = details.get(details.size()-1);
            activityReference.get().firstLocation = new LatLng(firstDetail.getLat() / Constants.MILLION,  // set the start and end location for the map
                                       firstDetail.getLon() / Constants.MILLION);
            activityReference.get().lastLocation = new LatLng(lastDetail.getLat() / Constants.MILLION,
                                      lastDetail.getLon() / Constants.MILLION);

            long lastTime = 0;
            long cumTime = 0;
            int cumDistance = 0;
            localMapTrip.put(details.get(0).getTimestamp(),
                    activityReference.get().new LocationDistance(firstDetail.getLat(), firstDetail.getLon(), 0));  // populate the map trip
            for (int i = 1; i < details.size(); i++) {
                // get the start and end times, lats, lons
                long startTime = details.get(i-1).getTimestamp();
                long endTime = details.get(i).getTimestamp();
                int startLat = details.get(i-1).getLat();
                int endLat = details.get(i).getLat();
                int startLon = details.get(i-1).getLon();
                int endLon = details.get(i).getLon();
                float distance = (float) UtilsMap.getMeterDistance(startLat, startLon, endLat,  endLon);

                // calculate the rate of change of lat, lon, and distance for the two readings
                double latRate = 1.0 * (endLat - startLat) / (endTime - startTime);
                double lonRate = 1.0 * (endLon - startLon) / (endTime - startTime);
                double distRate = distance / (endTime - startTime);
                // increment time in milliseconds and update the location on second boundaries
                long currentTime = startTime;
                while (currentTime < endTime) {
                    if ((currentTime % 1000) == 0) {
                        // use the rate of change and time to update the lat / lon / and distance
                        double currentLat = startLat + (currentTime - startTime) * latRate;
                        double currentLon = startLon + (currentTime - startTime) * lonRate;
                        double currentDist = cumDistance + (currentTime - startTime) * distRate;
                        LocationDistance locationDistance = activityReference.get().new LocationDistance(
                                (int) Math.round(currentLat), (int) Math.round(currentLon), (int) Math.round(currentDist) );
                        localMapTrip.put(currentTime, locationDistance);  // populate the map trip
                        lastTime = currentTime;
                    }
                    currentTime++;
                }
                cumDistance += Math.round(distance);
                cumTime += (endTime - startTime);
                if (cumTime >= activityReference.get().MAX_DURATION)
                    break;

                if (i % 25 == 0) reportProgress(Math.round(100.0f * i / details.size()));
                //Log.d(TAG, "Cum distance: " + cumDistance + " " +  Constants.getTimeDurationHHMMSS(cumTime, false)  );
            }

            //  copy from high time resolution localMapTrip to possibly lower time resolution mapTrip
            for (int i = 0; i < localMapTrip.size(); i++) {
                if (localMapTrip.keyAt(i) % activityReference.get().MIN_TIME_INCR == 0)
                    activityReference.get().mapTrip.put(localMapTrip.keyAt(i), localMapTrip.valueAt(i));
            //for (Map.Entry<Long, LocationDistance> entry : localMapTrip.entrySet()) {
                //if (entry.getKey() % MIN_TIME_INCR == 0)
                //    mapTrip.put(entry.getKey(), entry.getValue());
            }
            activityReference.get().mapTrip.put(lastTime, localMapTrip.get(lastTime));

            //Log.d(TAG, "finished build list");
            return "Finished build";
        }

        @Override
        protected void onPreExecute() {
            activityReference.get().buildIncomplete = true;
            String status = "Loading trip details...";
            activityReference.get().timeTextView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            activityReference.get().buildIncomplete = false;
            localMapTrip = null;
            String status = "Press Play to start...";
            activityReference.get().timeTextView.setText(status);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Loading trip details ... " + progress[0] + "%";
            activityReference.get().timeTextView.setText(status);
        }
    }

    // For sorting the LocationDistance by ascending order of timestamp
    private class LongComparator implements Comparator<Long> {
        @Override
        public int compare(Long a, Long b) {
            return a < b ? -1 : a.equals(b) ? 0 : 1;
        }
    }

    // stored in the treemap along with an associated timestamp
    private final class LocationDistance {
        private final int lat;
        private final int lon;
        private final int distance;
        LocationDistance(int lat, int lon, int distance) {
            this.lat = lat;
            this.lon = lon;
            this.distance = distance;
        }
        public int getLat() {
            return lat;
        }
        public int getLon() {
            return lon;
        }
        public int getDistance() {
            return distance;
        }
    }
}