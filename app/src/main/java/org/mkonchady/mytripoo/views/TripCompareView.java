package org.mkonchady.mytripoo.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
//import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

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
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

// compare two trips side by side
public class TripCompareView extends View {

    // canvas parameters
    private RectF rectF1, rectF2;
    public int width;
    public int height;
    private final int PADDING = 8;

    // time parameters
    private long currentTime = 0L;
    private Timer timer = null;

    // distance parameters
    private float upDistance = 0.0f;
    private float downDistance = 0.0f;

    // trip details and hash of timestamp with location
    private final int trip1;
    private final int trip2;
    private TripInfo tripInfo1, tripInfo2;
    private final TreeMap<Long, TimeLocation> upTrip = new TreeMap<>(new LongComparator());
    private final TreeMap<Long, TimeLocation> downTrip = new TreeMap<>(new LongComparator());
    private boolean upIncomplete, downIncomplete;

    private int timeIncr = 1;
    private final int MIN_TIME_INCR;
    private final int MAX_TIME_INCR;
    private final long MAX_DURATION;

    // Trip Play Buttons
    private final int RREVERSE = -2;
    private final int REVERSE = -1;
    private final int STOP = 0;
    private final int FORWARD = 1;
    private final int FFORWARD = 2;
    int PREV_BUTTON;

    // db parameters
    DetailDB detailDB;
    SummaryDB summaryDB;

    private final Paint paintUp;
    private final Paint paintDown;
    private final Paint blackPaint;
    private final Paint whitePaint;

    Context context;
    private TripCompareActivity tripCompareActivity = null;
    //private String TAG = "TripCompareView";

    public TripCompareView(Context c, AttributeSet attrs) {
        super(c, attrs);
        context = c;
        tripCompareActivity = (TripCompareActivity) context;

        detailDB = new DetailDB(DetailProvider.db);

        // Set the paint for the top trip
        paintUp = new Paint(); paintUp.setAntiAlias(true);
        paintUp.setStyle(Paint.Style.STROKE); paintUp.setStrokeJoin(Paint.Join.ROUND);
        paintUp.setStrokeWidth(8f); paintUp.setColor(Color.GREEN);

        // Set the paint for the lower trip
        paintDown = new Paint(); paintDown.setAntiAlias(true);
        paintDown.setStyle(Paint.Style.STROKE); paintDown.setStrokeJoin(Paint.Join.ROUND);
        paintDown.setStrokeWidth(8f); paintDown.setColor(Color.RED);

        // Set the black paint
        blackPaint = new Paint(); blackPaint.setAntiAlias(true); blackPaint.setColor(Color.BLACK);
        blackPaint.setStyle(Paint.Style.STROKE); blackPaint.setStrokeJoin(Paint.Join.ROUND);
        blackPaint.setStrokeWidth(4f);

        // Set the white paint
        whitePaint = new Paint(); whitePaint.setAntiAlias(true); whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.FILL); whitePaint.setStrokeJoin(Paint.Join.ROUND);
        whitePaint.setStrokeWidth(8f);

        trip1 = tripCompareActivity.getTrip_id1();
        trip2 = tripCompareActivity.getTrip_id2();

        // get the summary parameters
        summaryDB = new SummaryDB(SummaryProvider.db);
        SummaryProvider.Summary summary1 = summaryDB.getSummary(context, trip1);
        long duration1 = summary1.getEnd_time() - summary1.getStart_time();

        summaryDB = new SummaryDB(SummaryProvider.db);
        SummaryProvider.Summary summary2 = summaryDB.getSummary(context, trip2);
        long duration2 = summary2.getEnd_time() - summary2.getStart_time();
        MAX_DURATION = (duration1 < duration2)? duration1: duration2;

        if (MAX_DURATION < 1000 * 3600)          MIN_TIME_INCR = 1;
        else if (MAX_DURATION < 4 * 1000 * 3600) MIN_TIME_INCR = 2;
        else if (MAX_DURATION < 6 * 1000 * 3600) MIN_TIME_INCR = 4;
        else MIN_TIME_INCR = 8;
        MAX_TIME_INCR = 32 * MIN_TIME_INCR;

        // build the time and location / distance hashes for both trips
        upIncomplete = true;
        Object[] params1 = {context, trip1, true};
        new BuildLists().execute(params1);
        downIncomplete = true;
        Object[] params2 = {context, trip2, false};
        new BuildLists().execute(params2);

        currentTime = 0;
        PREV_BUTTON = FORWARD;
        startTimer(MIN_TIME_INCR * 4);

    }

    // Set the trip parameters before drawing on the canvas
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int HEIGHT = 464;

        super.onSizeChanged(w, h, oldw, oldh);
        width = w; height = h;
        final int PIXEL_PADDING = dpToPx(PADDING);

        // get the x, y coordinates for the top frame
        final int x1_left = PIXEL_PADDING; final int y1_top = PIXEL_PADDING;
        final int x1_right = width - PIXEL_PADDING; final int y1_bottom = y1_top + dpToPx(HEIGHT / 2) - PIXEL_PADDING * 4;
        rectF1 = new RectF(x1_left, y1_top, x1_right, y1_bottom);

        // get the x, y coordinates for the bottom frame
        final int x2_left = x1_left; final int y2_top = y1_bottom + PIXEL_PADDING;
        final int x2_right = x1_right; final int y2_bottom = y2_top + dpToPx(HEIGHT / 2) - PIXEL_PADDING * 4;
        rectF2 = new RectF(x2_left, y2_top, x2_right, y2_bottom);

        // create a trip for the top
        tripInfo1 = new TripInfo(detailDB.getBoundaries(context, trip1));
        int[] boundaries1 = {x1_left, x1_right, y1_top, y1_bottom};
        tripInfo1.setCoordinates(boundaries1);

        // create a trip for the bottom
        tripInfo2 = new TripInfo(detailDB.getBoundaries(context, trip2));
        int[] boundaries2 = {x2_left, x2_right, y2_top, y2_bottom};
        tripInfo2.setCoordinates(boundaries2);
    }

    //
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateTimer();

        // set the current time and distance
        tripCompareActivity.setTime(UtilsDate.getTimeDurationHHMMSS(currentTime * 1000L, false));
        tripCompareActivity.setDistance1(trip1 + ": " + UtilsMisc.formatFloat(upDistance, 1) + "km.");
        tripCompareActivity.setDistance2(trip2 + ": " + UtilsMisc.formatFloat(downDistance, 1) + "km.");
        tripCompareActivity.setDistance3("Diff : " + UtilsMisc.formatFloat(upDistance - downDistance, 2) + "km.");

        // draw the frames for the two trips
        canvas.drawRoundRect(rectF1, dpToPx(PADDING), dpToPx(PADDING), blackPaint);
        canvas.drawRoundRect(rectF2, dpToPx(PADDING), dpToPx(PADDING), blackPaint);

        // update the routes
        createPath(canvas, true);
        createPath(canvas, false);

        // simulate a stop button press, when the max. time is reached
        if (currentTime * 1000L >= MAX_DURATION) stopTimer();
    }

    // increment the current time within time bounds
    public void updateTimer() {
        currentTime += timeIncr;
        if (currentTime < 0) {
            currentTime = 0;
            timeIncr = 1;
            PREV_BUTTON = STOP;
        } else if (currentTime * 1000L > MAX_DURATION) {
            currentTime = MAX_DURATION / 1000;
        }
    }

    // create the path for the upper trip
    private void  createPath(Canvas canvas, boolean upper) {
        if (upper && upIncomplete) return;
        if (!upper && downIncomplete) return;

        long firstTime = 0; long lastTime = 0;
        float prev_x = 0; float prev_y = 0;
        Set<Long> keySet = upper? upTrip.keySet(): downTrip.keySet();
        for(Long timeKey: keySet) {
            // get the x, y location
            TimeLocation tm = upper ? upTrip.get(timeKey): downTrip.get(timeKey);
            UtilsMisc.XYPoint point = upper? tripInfo1.getXY(tm): tripInfo2.getXY(tm);
            if (upper) upDistance = tm.getDistance() / 1000.0f;
            else downDistance = tm.getDistance() / 1000.0f;
            float curr_x = (float) point.getX();
            float curr_y = (float) point.getY();

            if (firstTime == 0) firstTime = timeKey;
            else canvas.drawLine(prev_x, prev_y, curr_x, curr_y, upper? paintUp: paintDown);
            lastTime = timeKey;
            if ((timeKey - firstTime) > (currentTime * 1000L) ) {
                break;
            }
            prev_x = (float) point.getX();
            prev_y = (float) point.getY();
        }

        // lookahead and blank out any old tracks for 8x seconds
        long runTime = lastTime;
        long endTime = lastTime + 8 * MIN_TIME_INCR * 1000;
        float last_x; float last_y;
        while (runTime <= endTime) {
            runTime += 1000L;
            boolean containsKey = upper? upTrip.containsKey(runTime): downTrip.containsKey(runTime);
            if (containsKey) {
                TimeLocation tm = upper ? upTrip.get(runTime): downTrip.get(runTime);
                UtilsMisc.XYPoint point = upper? tripInfo1.getXY(tm): tripInfo2.getXY(tm);
                last_x = (float) point.getX();
                last_y = (float) point.getY();
                canvas.drawLine(prev_x, prev_y, last_x, last_y, whitePaint);
                prev_x = last_x;
                prev_y = last_y;
            }
        }
    }

    // called from the stop button
    public void stopTimer() {
        if(timer != null) {
            timer.cancel();
            timer = null;
            timeIncr = 1;
        }
        PREV_BUTTON = STOP;
    }

    // called from the fast forward button
    public void fforwardTimer() {
        if (PREV_BUTTON == FFORWARD || PREV_BUTTON == FORWARD) setTimeIncr(timeIncr * 4);
        else timeIncr = MIN_TIME_INCR;
        PREV_BUTTON = FFORWARD;
        if (timer == null) startTimer(MIN_TIME_INCR);
    }

    // called from the forward button
    public void forwardTimer() {
        if (PREV_BUTTON == FFORWARD || PREV_BUTTON == FORWARD) setTimeIncr(timeIncr * 2);
        else timeIncr = MIN_TIME_INCR;
        PREV_BUTTON = FORWARD;
        if (timer == null) startTimer(MIN_TIME_INCR);
    }

    // called from the reverse button
    public void reverseTimer() {
        if (PREV_BUTTON == REVERSE || PREV_BUTTON == RREVERSE) setTimeIncr(timeIncr * 2);
        else timeIncr = -MIN_TIME_INCR;
        PREV_BUTTON = REVERSE;
        if (timer == null) startTimer(-MIN_TIME_INCR);
    }

    // called from the fast reverse button
    public void rreverseTimer() {
        if (PREV_BUTTON == REVERSE || PREV_BUTTON == RREVERSE) setTimeIncr(timeIncr * 4);
        else timeIncr = -MIN_TIME_INCR;
        PREV_BUTTON = RREVERSE;
        if (timer == null) startTimer(-MIN_TIME_INCR);
    }

    public void startTimer(int TIME_INCR) {
        setTimeIncr(TIME_INCR);
        timer = null;
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                postInvalidate();
            }
        }, 0, 1000L);
        //Log.d(TAG, "Started timer" + PREF_TIME_INCR);
    }

    public void setTimeIncr(int timeIncr) {
        int absIncr = Math.abs(timeIncr);
        if (absIncr <= MAX_TIME_INCR) {
            this.timeIncr = timeIncr;
            return;
        }
        absIncr /= 2;
        if (absIncr <= MAX_TIME_INCR)
            this.timeIncr = timeIncr / 2;
    }

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    /*
    public static int pxToDp(int px) {
        return (int) (px / Resources.getSystem().getDisplayMetrics().density);
    } */

    // save the lat / lon limits of the trip
    final class TripInfo {

        int min_lat, max_lat, min_lon, max_lon;     // map boundaries of the trip
        int xleft, xright, ytop, ybottom;           // screen boundaries of the trip
        float dlat, dlon;                           // pixel scale of the screen

        TripInfo(int[] tripBoundaries) {
            if (tripBoundaries.length < 4) return;
            // max, min lat followed by max, min lon
            max_lat = tripBoundaries[0]; min_lat = tripBoundaries[1];
            max_lon = tripBoundaries[2]; min_lon = tripBoundaries[3];
            xleft = xright = ytop = ybottom = 0;
            dlat = dlon = 0.0f;
        }

        void setCoordinates(int[] mapBoundaries) {
            xleft = mapBoundaries[0] + PADDING; xright = mapBoundaries[1] - PADDING;
            ytop = mapBoundaries[2] + PADDING; ybottom = mapBoundaries[3] - PADDING;
            // calculate the scale
            dlon = 1.0f * (xright - xleft) / (max_lon - min_lon);
            dlat = 1.0f * (ybottom - ytop) / (max_lat - min_lat);
        }


        // given the lat and lon in microdegrees, return the x,y location
        UtilsMisc.XYPoint getXY(TimeLocation timeLocation) {
            int lat = timeLocation.getLat(); int lon = timeLocation.getLon();
            UtilsMisc.XYPoint point = new UtilsMisc.XYPoint(0, 0);
            // verify that the lat and lon lie within the range
            if (!(min_lat <= lat && lat <= max_lat))
                return point;
            if (!(min_lon <= lon && lon <= max_lon))
                return point;

            float y = ybottom - (lat - min_lat) * dlat;
            float x = xleft + (lon - min_lon) * dlon;
            //float y = ytop + (lat - min_lat) * dlat;
            //float x = xright - (lon - min_lon) * dlon;
            point.setXY(x, y);
            return point;
        }
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
                       TimeLocation timeLocation = new TimeLocation(
                               (int) Math.round(currentLat),
                               (int) Math.round(currentLon),
                               (int) Math.round(currentDist) );
                       // populate up or down TreeMaps for the trips
                       if (topTrip) upTrip.put(currentTime, timeLocation);
                       else downTrip.put(currentTime, timeLocation);
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

    private static final class TimeLocation {
        private final int lat;
        private final int lon;
        private final int distance;
        TimeLocation(int lat, int lon, int distance) {
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

    private static class LongComparator implements Comparator<Long> {
        @Override
        public int compare(Long a, Long b) {
            return a < b ? -1 : a.equals(b) ? 0 : 1;
        }
    }

}