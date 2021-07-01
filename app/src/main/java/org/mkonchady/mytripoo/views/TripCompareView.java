package org.mkonchady.mytripoo.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.LatLng;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Log;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.activities.TripCompareActivity;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

/**
 * View to compare two trips
 */
public class TripCompareView extends View {

    // distance parameters
    public float upDistance = 0.0f;
    public float downDistance = 0.0f;

    // trip details and hash of timestamp with location
    public int trip1;
    public LatLng firstLocation1, lastLocation1;
    public int trip2;
    public LatLng firstLocation2, lastLocation2;

    public final TreeMap<Long, LocationDistance> upTrip = new TreeMap<>(new LongComparator());
    public final TreeMap<Long, LocationDistance> downTrip = new TreeMap<>(new LongComparator());
    public boolean upIncomplete, downIncomplete;
    private final int greenColor;
    private final int redColor;

    // time parameters
    private long currentTime = 0L;
    private Timer timer = null;
    private int TIME_INCR = 1;
    private int MIN_TIME_INCR = 1;
    private final long MAX_DURATION;

    // Trip Play Buttons
    private final int RREVERSE = -2;
    private final int REVERSE = -1;
    private final int STOP = 0;
    private final int FORWARD = 1;
    private final int FFORWARD = 2;
    int PREV_BUTTON;

    private TripCompareActivity tripCompareActivity = null;
    private MapsFragment1 maps1 = null;
    private MapsFragment2 maps2 = null;

    // db parameters
    public DetailDB detailDB;
    public SummaryDB summaryDB;
    private String TAG = "Compare";

    public TripCompareView(Context context, AttributeSet attrs) {
        super(context, attrs);
        tripCompareActivity = (TripCompareActivity) context;
        tripCompareActivity.setTripCompareView(this);
        trip1 = tripCompareActivity.getTrip_id1();
        trip2 = tripCompareActivity.getTrip_id2();

        // get the db parameters
        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
        SummaryProvider.Summary summary1 = summaryDB.getSummary(context, trip1);
        long duration1 = summary1.getEnd_time() - summary1.getStart_time();
        SummaryProvider.Summary summary2 = summaryDB.getSummary(context, trip2);
        long duration2 = summary2.getEnd_time() - summary2.getStart_time();
        MAX_DURATION = (duration1 < duration2)? duration1: duration2;
        MIN_TIME_INCR = (MAX_DURATION > 10 * Constants.MILLISECONDS_PER_HOUR)? 30:    // > 10 hours
                (MAX_DURATION > Constants.MILLISECONDS_PER_HOUR)? 10:1;       // 1-10 hours

        // set the time increment
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        TIME_INCR = 30;  // default of 30 seconds
        greenColor = ContextCompat.getColor(tripCompareActivity, R.color.darkgreen);
        redColor = ContextCompat.getColor(tripCompareActivity, R.color.darkred);
        /*
        MIN_TIME_INCR = 15;
        if (MAX_DURATION < 4 * 1000 * 3600) MIN_TIME_INCR = 30;
        else if (MAX_DURATION < 6 * 1000 * 3600) MIN_TIME_INCR = 60;
        MAX_TIME_INCR = 32 * MIN_TIME_INCR;
         */

        // build the time and location / distance hashes for both trips
        upIncomplete = true;
        Object[] params1 = {context, trip1, true};
        new BuildLists().execute(params1);
        downIncomplete = true;
        Object[] params2 = {context, trip2, false};
        new BuildLists().execute(params2);

        currentTime = 0;
        PREV_BUTTON = FORWARD;
        startTimer(TIME_INCR);

    }
    // Set the trip parameters before drawing on the canvas
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        maps1 = tripCompareActivity.getMap1();
        maps2 = tripCompareActivity.getMap2();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //Log.d(TAG, "Start onDraw: " + UtilsDate.getDateTimeDeciSec(System.currentTimeMillis(), 13023364, 77577801));

        // set the current time and distance
        tripCompareActivity.setTime(UtilsDate.getTimeDurationHHMMSS(currentTime * 1000L, false));
        tripCompareActivity.setDistance1(trip1 + ": " + UtilsMisc.formatFloat(upDistance, 2) + "km.");
        tripCompareActivity.setDistance2(trip2 + ": " + UtilsMisc.formatFloat(downDistance, 2) + "km.");
        tripCompareActivity.setDistance3("Diff : " +
                UtilsMisc.formatFloat(Math.abs(upDistance - downDistance), 2) + "km.");
        if (upDistance > downDistance)
            tripCompareActivity.setDistance3Color(redColor);
        else
            tripCompareActivity.setDistance3Color(greenColor);

        // simulate a stop button press, when the max. time is reached
        updateCurrentTime();
        if (currentTime * 1000L >= MAX_DURATION) stopTimer();
        //Log.d(TAG, "End onDraw: " + UtilsDate.getDateTimeDeciSec(System.currentTimeMillis(), 13023364, 77577801));
    }

    // called from the stop button
    public void stopTimer() {
        if(timer != null) {
            timer.cancel(); timer = null; TIME_INCR = 1;
        }
        PREV_BUTTON = STOP;
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

    // called from the fast forward button
    public void fforwardTimer() {
        if (PREV_BUTTON == FFORWARD || PREV_BUTTON == FORWARD) setTIME_INCR(TIME_INCR * 4);
        else TIME_INCR = MIN_TIME_INCR;
        PREV_BUTTON = FFORWARD;
        if (timer == null) startTimer(MIN_TIME_INCR);
    }

    // called from the forward button
    public void forwardTimer() {
        if (PREV_BUTTON == FFORWARD || PREV_BUTTON == FORWARD) setTIME_INCR(TIME_INCR * 2);
        else TIME_INCR = MIN_TIME_INCR;
        PREV_BUTTON = FORWARD;
        if (timer == null) startTimer(MIN_TIME_INCR);
    }

    // called from the reverse button
    public void reverseTimer() {
        if (PREV_BUTTON == REVERSE || PREV_BUTTON == RREVERSE) setTIME_INCR(TIME_INCR * 2);
        else TIME_INCR = -MIN_TIME_INCR;
        PREV_BUTTON = REVERSE;
        if (timer == null) startTimer(-MIN_TIME_INCR);
    }

    // called from the fast reverse button
    public void rreverseTimer() {
        if (PREV_BUTTON == REVERSE || PREV_BUTTON == RREVERSE) setTIME_INCR(TIME_INCR * 4);
        else TIME_INCR = -MIN_TIME_INCR;
        PREV_BUTTON = RREVERSE;
        if (timer == null) startTimer(-MIN_TIME_INCR);
    }

    public void startTimer(int timeIncr) {
        setTIME_INCR(timeIncr);
        timer = null;
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (maps1 != null && maps2 != null) { // update the routes
                    maps1.createPath(currentTime);
                    maps2.createPath(currentTime);
                }
            }
        }, 0, 1000L);
    }

    public void setTIME_INCR(int TIME_INCR) {
        //if (TIME_INCR >= MIN_TIME_INCR) {
            this.TIME_INCR = TIME_INCR;
        //}
        Log.d(TAG, "Time incr: " + this.TIME_INCR);
    }

    // Build the times and locations array lists for the trip
    private class BuildLists extends AsyncTask<Object, Integer, String> {

        boolean topTrip;
        @Override
        // parameters
        // 1. context 2. trip id 3. True if upper trip on canvas
        protected String doInBackground(Object...params) {

            Context context = (Context) params[0];
            int tripId = (int) params[1];
            topTrip = (boolean) params[2];

            ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, tripId);
            DetailProvider.Detail firstDetail = details.get(0);
            DetailProvider.Detail lastDetail = details.get(details.size()-1);
            if (topTrip) {
                firstLocation1 = new LatLng(firstDetail.getLat() / Constants.MILLION,  // set the start and end location for the map
                        firstDetail.getLon() / Constants.MILLION);
                lastLocation1 = new LatLng(lastDetail.getLat() / Constants.MILLION,
                        lastDetail.getLon() / Constants.MILLION);
            } else {
                firstLocation2 = new LatLng(firstDetail.getLat() / Constants.MILLION,  // set the start and end location for the map
                        firstDetail.getLon() / Constants.MILLION);
                lastLocation2 = new LatLng(lastDetail.getLat() / Constants.MILLION,
                        lastDetail.getLon() / Constants.MILLION);
            }

            long cumTime = 0;
            int cumDistance = 0;
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
                        LocationDistance locationDistance = new LocationDistance(
                                (int) Math.round(currentLat),
                                (int) Math.round(currentLon),
                                (int) Math.round(currentDist) );
                        // populate up or down TreeMaps for the trips
                        if (topTrip) upTrip.put(currentTime, locationDistance);
                        else downTrip.put(currentTime, locationDistance);
                    }
                    currentTime++;
                }
                cumDistance += Math.round(distance);
                //Log.d(TAG, "Cum. Distance: " + cumDistance);
                cumTime += (endTime - startTime);
                if (cumTime >= MAX_DURATION)
                    break;
            }
            return "Finished build";
        }

        @Override
        protected void onPreExecute() {
            if (topTrip) upIncomplete = true;
            else downIncomplete = true;
        }

        @Override
        protected void onPostExecute(String result) {
            if (topTrip) upIncomplete = false;
            else downIncomplete = false;
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
        }
    }

    // stored in the treemap along with an associated timestamp
    public final class LocationDistance {
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

    public static class LongComparator implements Comparator<Long> {
        @Override
        public int compare(Long a, Long b) {
            return a < b ? -1 : a.equals(b) ? 0 : 1;
        }
    }

    public void setUpDistance(float upDistance) {
        this.upDistance = upDistance;
    }
    public void setDownDistance(float downDistance) {
        this.downDistance = downDistance;
    }

    public int getTIME_INCR() {
        return TIME_INCR;
    }
    public int getMIN_TIME_INCR() {
        return MIN_TIME_INCR;
    }
    public long getMAX_DURATION() {
        return MAX_DURATION;
    }

    public LatLng getFirstLocation1() {
        return firstLocation1;
    }
    public LatLng getLastLocation1() {
        return lastLocation1;
    }
    public LatLng getFirstLocation2() {
        return firstLocation2;
    }
    public LatLng getLastLocation2() {
        return lastLocation2;
    }
}