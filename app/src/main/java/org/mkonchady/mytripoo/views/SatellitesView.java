package org.mkonchady.mytripoo.views;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.gps.Satellite;
import org.mkonchady.mytripoo.activities.SatelliteInfoActivity;
import org.mkonchady.mytripoo.activities.SatellitesActivity;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class SatellitesView extends View {

    //private final String TAG = "SatellitesView";
    //private int localLog = 0;
    private static final int INVALID_POINTER_ID = -1;

    // bitmap to hold the satellites image
    public Bitmap bitmap;
    private  float widthBitmap;
    private  float heightBitmap;

    private final Matrix canvasForwardTransform = new Matrix();     // bitmap to canvas transform.
    private final Matrix spaceForwardTransform = new Matrix();      // space to bitmap transform
    private final UtilsMisc.XYPoint[] savePoints;                   // x,y coordinates of satellites
    final int MEDIUM_TEXT = 16;
    final int LARGE_TEXT = 20;

    // scale and position of canvas on map to handle drag
    private float mxScaleFactor = 1.0f;
    private float myScaleFactor = 1.0f;
    private float mPosX;
    private float mPosY;
    private float mLastTouchX;
    private float mLastTouchY;
    private int mActivePointerId = INVALID_POINTER_ID;
    private final ScaleGestureDetector mScaleDetector;
    private final float PIXEL_SCALE;

    // paints
    private final Paint backgroundPaint = new Paint();
    private final Paint largeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint();
    private final Paint redPaint = new Paint();
    private final Paint greenPaint = new Paint();
    private final Paint yellowPaint = new Paint();
    private final Paint bluePaint = new Paint();

    private Canvas canvas1;
    private final SharedPreferences sharedPreferences;
    private final Context context;
    private final SatellitesActivity satellitesActivity;
    private final long startTime;
    public Timer timer;
    private ArrayList<Satellite> satellites = null;
    private ArrayList<Satellite> old_satellites = null;
    private final HashMap<String, Integer> country_stats = new HashMap<>();
    private int used_satellites;

    // STATUS indicators
    private int VIEW_STATUS = 0;
    private final int MAP_LOADED = 1;
    private final int RUNNING = 2;

    public SatellitesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        satellitesActivity = (SatellitesActivity) context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        //localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

        backgroundPaint.setStyle(Paint.Style.FILL); backgroundPaint.setColor(Color.BLACK);
        largeTextPaint.setStyle(Paint.Style.FILL);  largeTextPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);       textPaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.STROKE);    whitePaint.setColor(Color.WHITE);
        redPaint.setStyle(Paint.Style.FILL);        redPaint.setColor(Color.RED);
        greenPaint.setStyle(Paint.Style.FILL);      greenPaint.setColor(Color.GREEN);
        yellowPaint.setStyle(Paint.Style.FILL);     yellowPaint.setColor(Color.YELLOW);
        bluePaint.setStyle(Paint.Style.FILL);       bluePaint.setColor(Color.BLUE);

        PIXEL_SCALE = context.getResources().getDisplayMetrics().density;
        textPaint.setTextSize((int) (MEDIUM_TEXT * PIXEL_SCALE));
        largeTextPaint.setTextSize((int) (LARGE_TEXT * PIXEL_SCALE));
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());

        // allocate space to save the satellite locations
        final int MAX_VISIBLE_SATELLITES = 36;
        savePoints = new UtilsMisc.XYPoint[MAX_VISIBLE_SATELLITES];
        for (int i = 0; i < MAX_VISIBLE_SATELLITES; i++)
            savePoints[i] = new UtilsMisc.XYPoint(0, 0, 0);

        // periodically update the view with a timer, the timer is stopped in satellites activity
        // the onDraw method is called every 1 seconds
        final int PERIOD = 1;
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                postInvalidate();
            }
        }, 0, PERIOD * 1000L);
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // First time
        if (VIEW_STATUS < MAP_LOADED) {
            bitmap = Bitmap.createBitmap(getWidth(), getHeight(),  Bitmap.Config.ARGB_8888); // this creates a MUTABLE bitmap
            widthBitmap = bitmap.getWidth();
            heightBitmap = bitmap.getHeight();
            canvas1 = new Canvas(bitmap);
            mxScaleFactor = 1.0f;
            myScaleFactor = 1.0f;
            mPosX = widthBitmap / 2;
            mPosY = heightBitmap / 2;
            VIEW_STATUS = RUNNING;
        }

        // canvas image constants
        final int PIXEL_BORDER_X = 80;
        final int PIXEL_BORDER_Y = 80;
        final int PIXEL_ROW_HEIGHT = 80;
        final int PIXEL_IMAGE_HEIGHT = 600;     // image size to show satellites
        final int PIXEL_IMAGE_WIDTH = 600;
        final int PIXEL_WINDOW_HEIGHT = 2 * (PIXEL_ROW_HEIGHT + PIXEL_BORDER_Y) + PIXEL_IMAGE_HEIGHT;
        final int PIXEL_WINDOW_WIDTH = 2 * PIXEL_BORDER_X + PIXEL_IMAGE_WIDTH;
        float SCALE_X = widthBitmap / PIXEL_WINDOW_WIDTH;
        float SCALE_Y = heightBitmap / PIXEL_WINDOW_HEIGHT;

        // conversion from pixel size to canvas size
        final float IMAGE_WIDTH = PIXEL_IMAGE_WIDTH * SCALE_X;
        final float IMAGE_WIDTH_BY2 = IMAGE_WIDTH / 2.0f;
        final float IMAGE_HEIGHT = PIXEL_IMAGE_HEIGHT * SCALE_Y;
        final float IMAGE_HEIGHT_BY2 = IMAGE_HEIGHT / 2.0f;
        final float ROW_HEIGHT = PIXEL_ROW_HEIGHT * SCALE_Y;
        final float BORDER_X = PIXEL_BORDER_X * SCALE_X ;
        final float BORDER_Y = PIXEL_BORDER_Y * SCALE_Y;
        final float CENTER_X = BORDER_X + IMAGE_WIDTH_BY2;
        final float CENTER_Y = BORDER_Y + 2 * ROW_HEIGHT + IMAGE_HEIGHT_BY2;
        final float RADIUS = IMAGE_WIDTH_BY2;
        final float DISPLACEMENT = 10.0f;

        // get the satellite data from preferences that is updated in GPS Service
        getSatellitePreferences();

        // get the x,y coordinates of the NW and SE corners and the number of used satellites
        float xmin = Float.MAX_VALUE;
        float xmax = -Float.MAX_VALUE;
        float ymin = Float.MAX_VALUE;
        float ymax = -Float.MAX_VALUE;
        used_satellites = 0;
        boolean valid_satellites = check_satellites(satellites);

        // if there were no valid satellites, use the old satellites
        if (!valid_satellites && old_satellites != null) {
            satellites = new ArrayList<>(old_satellites.size());
            satellites.addAll(old_satellites);
        }

        for (Satellite satellite : satellites) {
            if (satellite.isUsed() && satellite.isValidSatellite()) {
                used_satellites++;
                if (xmin > satellite.getX()) xmin = (float) satellite.getX();
                if (xmax < satellite.getX()) xmax = (float) satellite.getX();
                if (ymin > satellite.getY()) ymin = (float) satellite.getY();
                if (ymax < satellite.getY()) ymax = (float) satellite.getY();
            }
        }

        // set the maximum x and y value in either direction
        float maxX = Math.abs(xmin) > Math.abs(xmax) ? Math.abs(xmin) : Math.abs(xmax);
        float maxY = Math.abs(ymin) > Math.abs(ymax) ? Math.abs(ymin) : Math.abs(ymax);

        // build the space transform to convert x_world, y_world coordinates to bitmap coordinates
        if (used_satellites > 0) {
            float xrange = 2.0f * maxX;
            float yrange = 2.0f * maxY;
            float xscale = IMAGE_WIDTH / xrange;
            float yscale = IMAGE_WIDTH / yrange;        // a circular image of satellites
            spaceForwardTransform.reset();
            spaceForwardTransform.postScale(xscale, -yscale);
            spaceForwardTransform.postTranslate(CENTER_X, CENTER_Y);
        }


        // clean out the old image and set the new status - rectangle: left, top, right bottom
        canvas1.drawRect(0, 0, widthBitmap, heightBitmap, backgroundPaint);
        String canvasStatus = "Using " + used_satellites + " of " + satellites.size() + " satellites" +
                "       Time: " + UtilsDate.getTimeDurationHHMMSS(System.currentTimeMillis() - startTime, false);
        canvas1.drawText(canvasStatus, (int) DISPLACEMENT, ROW_HEIGHT, largeTextPaint);

        String countryStats = UtilsMap.getCountryStats(country_stats);
        canvas1.drawText(countryStats, (int) DISPLACEMENT, ROW_HEIGHT + 60.0f, textPaint);

        canvas1.drawCircle(CENTER_X, CENTER_Y, 10.0f, textPaint);        // draw the center filled circle
        canvas1.drawCircle(CENTER_X, CENTER_Y, RADIUS / 2, whitePaint);  // draw the inner circle
        canvas1.drawCircle(CENTER_X, CENTER_Y, RADIUS, whitePaint);      // draw the outer circle
        canvas1.drawLine(CENTER_X, CENTER_Y - RADIUS, CENTER_X, CENTER_Y + RADIUS, whitePaint); // draw the v. line
        canvas1.drawLine(CENTER_X - RADIUS, CENTER_Y, CENTER_X + RADIUS, CENTER_Y, whitePaint); // draw the h.line
        canvas1.drawText("N", CENTER_X - DISPLACEMENT, CENTER_Y - RADIUS - DISPLACEMENT, textPaint);

        // create the new satellite locations in save points, convert satellite x,y coordinates
        // to bitmap x,y coordinates using the space transform
        int saveIndex = 0;
        int[] savePrn = new int[used_satellites];
        Paint[] savePaint = new Paint[used_satellites];
        for (Satellite satellite : satellites) {
            if (satellite.isUsed() && satellite.isValidSatellite()) {
                float[] points = new float[]{(float) satellite.getX(), (float) satellite.getY()};
                spaceForwardTransform.mapPoints(points);
                savePoints[saveIndex].setX(points[0]);
                savePoints[saveIndex].setY(points[1]);
                savePrn[saveIndex] = satellite.getPrn();
                savePaint[saveIndex] = getPaint(satellite.getSignalStrength());
                saveIndex++;
            }
        }

        // compute the pixel distance between all pairs of satellites
        double[][] satDistance = new double[used_satellites][used_satellites];
        for (int i = 0; i < used_satellites; i++)
            for (int j = i; j < used_satellites; j++)
                if (i == j) satDistance[i][j] = 0;
                else satDistance[i][j] = satDistance[j][i] = computePixelDistance(i, j);

        // on even seconds show the satellite with the smaller index
        boolean evenTime = ((UtilsDate.getTimeSeconds(System.currentTimeMillis()) % 2) == 0);
        boolean[] nonOverlap = new boolean[used_satellites];
        Arrays.fill(nonOverlap, Boolean.TRUE);              // initialize the array to true
        final double pixelThreshold = 50;                   // minimum pixel distance between satellites
        for (int i = 0; i < used_satellites-1; i++) {
            for (int j = i+1; j < used_satellites; j++) {
                if (satDistance[i][j] < pixelThreshold) {
                    if (evenTime) nonOverlap[i] = false;
                    else nonOverlap[j] = false;
                }
            }
        }

        // show only the non overlapping satellites
        for (int i = 0; i < used_satellites; i++) {
            if (nonOverlap[i]) {
                canvas1.drawCircle((float) savePoints[i].getX(), (float) savePoints[i].getY(), 10.0f, savePaint[i]);
                canvas1.drawText(savePrn[i] + "", (float) savePoints[i].getX() + 15.0f,
                                                  (float) savePoints[i].getY() + 15.0f, textPaint);
            }
        }

        // build the forward  transforms
        forwardTransform();
        canvas.drawBitmap(bitmap, canvasForwardTransform, backgroundPaint);

        // save the satellites for the next display
        if (satellites != null) {
            old_satellites = new ArrayList<>();
            old_satellites.addAll(satellites);
        }

    }

    // compute the pixel distance between i and j satellites
    private double computePixelDistance(int i, int j) {
        double x1 = savePoints[i].getX();  double y1 = savePoints[i].getY();
        double x2 = savePoints[j].getX();  double y2 = savePoints[j].getY();
        double dx = x2 - x1;        double dy = y2 - y1;
        return Math.sqrt(dx*dx+dy*dy);
    }

    // return true if at least one satellite was valid
    private boolean check_satellites(ArrayList<Satellite> satellites) {
        for (Satellite satellite : satellites)
            if (satellite.isUsed() && satellite.isValidSatellite())
                return true;
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the ScaleGestureDetector inspect all events.
        mScaleDetector.onTouchEvent(ev);
        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            // possible click on a satellite
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                if (VIEW_STATUS == RUNNING) {
                    handleUserClick(x, y);
                }
                mLastTouchX = x;
                mLastTouchY = y;
                mActivePointerId = ev.getPointerId(0);
                invalidate();
                break;
            }
            // a possible drag
            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

               // checkBounds(x, y, 0.0f);
                // Only move if the ScaleGestureDetector isn't processing a gesture.
                if (!mScaleDetector.isInProgress()) {
                    final float dx = x - mLastTouchX;
                    final float dy = y - mLastTouchY;
                    mPosX += dx;
                    mPosY += dy;
                    invalidate();
                }
                mLastTouchX = x;
                mLastTouchY = y;
                break;
            }

            case MotionEvent.ACTION_UP: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                        >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mLastTouchX = ev.getX(newPointerIndex);
                    mLastTouchY = ev.getY(newPointerIndex);
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    // handle the user click on a node
    // wait till cities file has been read
    public void handleUserClick(float x, float y) {
        final int T_RANGE = 40;                     // touch pixel range
        for (int i = 0; i < used_satellites; i++) {
            float[] map = {(float) savePoints[i].getX(), (float) savePoints[i].getY()};
            canvasForwardTransform.mapPoints(map);          // transform the satellite coordinates
            float x_c = map[0]; float y_c = map[1];         // to canvas coordinates
            if (    (x_c - T_RANGE) < x && x < (x_c + T_RANGE) &&
                    (y_c - T_RANGE) < y && y < (y_c + T_RANGE) ) {
                Satellite satellite = getMatchingSatellite(
                        (float) savePoints[i].getX(), (float) savePoints[i].getY());
                if (satellite != null) {
                    satellitesActivity.setSatInfoRequested(true);
                    Intent intent = new Intent(satellitesActivity, SatelliteInfoActivity.class);
                    intent.putExtra(Constants.GPS_PRN, satellite.getPrn());
                    intent.putExtra(Constants.GPS_SNR, satellite.getSnr());
                    intent.putExtra(Constants.GPS_AZIMUTH, satellite.getAzimuth());
                    intent.putExtra(Constants.GPS_ELEVATION, satellite.getElevation());
                    intent.putExtra(Constants.GPS_TYPE, satellite.getType() + "");
                    satellitesActivity.startActivity(intent);
                }
            }
        }
    }

    private Satellite getMatchingSatellite(float x, float y) {
        for (Satellite satellite: satellites) {
            float[] points = new float[]{(float) satellite.getX(), (float) satellite.getY()};
            spaceForwardTransform.mapPoints(points);
            if ( (Float.compare(x, points[0]) == 0) &&
                 (Float.compare(y, points[1]) == 0) ) {
                return  satellite;
            }
        }
        return null;
    }

    // Get the satellite data from preferences (set in GPS Service)
    private void getSatellitePreferences() {
        int numSatellites = sharedPreferences.getInt(Constants.GPS_SATELLITES, 0);
        String[] countries = UtilsMap.getCountries(context);
        for (String country : countries) country_stats.put(country, 0);
        satellites = new ArrayList<>(numSatellites);
        for (int i = 0; i < numSatellites; i++) {
            Satellite satellite = new Satellite(0.0f, 0.0f, 0.0f, 0, false);
            satellite.setAzimuth(sharedPreferences.getFloat(Constants.GPS_AZIMUTH + "_" + i, 0.0f));
            satellite.setElevation(sharedPreferences.getFloat(Constants.GPS_ELEVATION + "_" + i, 0.0f));
            satellite.setSnr(sharedPreferences.getFloat(Constants.GPS_SNR + "_" + i, 0.0f));
            satellite.setPrn(sharedPreferences.getInt(Constants.GPS_PRN + "_" + i, 0));
            satellite.setUsed(sharedPreferences.getBoolean(Constants.GPS_USED_SATELLITES + "_" + i, false));
            satellite.setType(sharedPreferences.getInt(Constants.GPS_TYPE + "_" + i,  0));
            satellite.setValidSatellite();
            if (satellite.isValidSatellite())   // set the x and y coordinate for a visible satellite
                UtilsMap.setCartesianCoords(satellite);
            satellite.setSignalStrength();
            satellites.add(satellite);
            String country = UtilsMap.getSatelliteCountry(context, sharedPreferences.getInt(Constants.GPS_TYPE + "_" + i, 0));
            country_stats.put(country, country_stats.get(country) + 1);
        }
    }


    // transform from bitmap to canvas
    private void forwardTransform() {
        canvasForwardTransform.reset();
        canvasForwardTransform.postTranslate(-widthBitmap / 2.0f, -heightBitmap / 2.0f);
        canvasForwardTransform.postScale(mxScaleFactor, myScaleFactor);
        canvasForwardTransform.postTranslate(mPosX, mPosY);
    }

    private Paint getPaint(int signalStrength) {
        if (signalStrength == 4) return redPaint;
        if (signalStrength == 3) return greenPaint;
        if (signalStrength == 2) return yellowPaint;
        if (signalStrength == 1) return bluePaint;
        return backgroundPaint;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // Don't let the object get too small or too large.
            mxScaleFactor *= detector.getScaleFactor();
            mxScaleFactor = Math.max(0.1f, Math.min(mxScaleFactor, 5.0f));
            myScaleFactor *= detector.getScaleFactor();
            myScaleFactor = Math.max(0.1f, Math.min(myScaleFactor, 5.0f));
            invalidate();
            return true;
        }
    }
}
