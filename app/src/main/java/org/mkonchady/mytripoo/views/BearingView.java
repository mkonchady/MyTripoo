package org.mkonchady.mytripoo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.activities.BearingActivity;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;

public class BearingView extends View {

   // private final String TAG = "BearingView";
    private static final int INVALID_POINTER_ID = -1;

    // Constants for the bitmap
    final int BITMAP_X = 1024;
    final int BITMAP_Y = 1024;
    final int CENTER_X = BITMAP_X / 2;
    final int CENTER_Y = BITMAP_Y / 2;
    final int OUT_RADIUS = CENTER_X - 64;
    final int THIN_BORDER = 4;
    final int THICK_BORDER = 8;
    final int IN_RADIUS = OUT_RADIUS - THIN_BORDER * 2 - THICK_BORDER;
    final int TEXT_SIZE = 64;
    final int BOUNDING_BOX_SIZE = 36;
    final int SECTOR_DEGREES = 36;

    public Bitmap bitmap;           // bitmap to hold the bearing wheel
    private final int widthBitmap;
    private final int heightBitmap;
    public Canvas canvas1;          // canvas for the bitmap
    public String directionPercent = "";

    // trip details including bearing, histogram and max value
    DetailDB detailDB;
    public final int trip_id;
    int[] bearing_histogram;
    int max_value;
    private final RectF[] rectFs = new RectF[BOUNDING_BOX_SIZE];

    private final Matrix forwardTransform = new Matrix();     // matrix of forward transform.
    private final Matrix reverseTransform = new Matrix();     // matrix of reverse transform

    // scale and position of canvas on map to handle drag
    private float mxScaleFactor = 1.0f;
    private float myScaleFactor = 1.0f;
    private float mPosX;       private float mPosY;
    private float mLastTouchX; private float mLastTouchY;
    private int mActivePointerId = INVALID_POINTER_ID;
    private final ScaleGestureDetector mScaleDetector;

    // paints
    private final Paint backgroundPaint = new Paint();
    private final Paint darkBluePaint = new Paint();
    private final Paint blackPaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint[] arcPaints = new Paint[SECTOR_DEGREES / 2];

    private int VIEW_STATUS = 0;            // status indicator
    BearingActivity bearingActivity;

    public BearingView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // get the trip id and database handle
        bearingActivity = (BearingActivity) context;
        trip_id = bearingActivity.getIntent().getIntExtra(Constants.PREF_TRIP_ID, -1);
        detailDB = new DetailDB(DetailProvider.db);
        ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id);

        // build the list of bearings
        ArrayList<Integer> bearings_list = new ArrayList<>();
        for (DetailProvider.Detail detail: details) {
            bearings_list.add(detail.getBearing());
        }

        // build a histogram of the bearings
        bearing_histogram = new int[SECTOR_DEGREES];
        for (int i = 0; i < SECTOR_DEGREES; i++) bearing_histogram[i] = 0;
        for (int bearing: bearings_list) {
            int index = bearing / 10;
            index = (index == SECTOR_DEGREES)? 0: index;
            bearing_histogram[index]++;
        }

        // build the percent of N, S, E, and W
        float north, east, west, south;
        north = east = south = west = 0.0f;
        for (int bearings: bearings_list) {
            if ( (0 <= bearings) && (bearings <= 90) ) {
                int x = 90 - bearings;
                north += Math.sin(Math.toRadians(x)); east += Math.cos(Math.toRadians(x));
            }
            if ( (91 <= bearings) && (bearings <= 180) ) {
                int x = 180 - bearings;
                east += Math.sin(Math.toRadians(x)); south += Math.cos(Math.toRadians(x));
            }
            if ( (181 <= bearings) && (bearings <= 270) ) {
                int x = 270 - bearings;
                south += Math.sin(Math.toRadians(x)); west += Math.cos(Math.toRadians(x));
            }
            if ( (270 <= bearings) && (bearings <= 360) ) {
                int x = 360 - bearings;
                west += Math.sin(Math.toRadians(x)); north += Math.cos(Math.toRadians(x));
            }
        }
        float total = north + south + east + west;
        directionPercent = "N: " + UtilsMisc.formatDouble(UtilsMisc.calcPercentage(0, total, north), 1) +
                "% E: " + UtilsMisc.formatDouble(UtilsMisc.calcPercentage(0, total, east), 1) +
                "% S: " + UtilsMisc.formatDouble(UtilsMisc.calcPercentage(0, total, south), 1) +
                "% W: " + UtilsMisc.formatDouble(UtilsMisc.calcPercentage(0, total, west), 1) + "%";

        // get the max. value
        max_value = (UtilsMisc.getMinMax(bearing_histogram))[1];

        // build the bounding boxes
        for (int i = 0; i < BOUNDING_BOX_SIZE; i++) {
            // build the bounding box
            int bounds = Math.round((1.0f * bearing_histogram[i] / max_value) * IN_RADIUS);
            rectFs[i] = new RectF(CENTER_X - bounds, CENTER_Y - bounds, CENTER_X + bounds, CENTER_Y + bounds);
        }

        // build the paints
        backgroundPaint.setStyle(Paint.Style.FILL); backgroundPaint.setColor(Color.WHITE);
        blackPaint.setStyle(Paint.Style.FILL); blackPaint.setColor(Color.BLACK);
        blackPaint.setAntiAlias(true);
        textPaint.setColor(Color.BLACK); textPaint.setTextSize(TEXT_SIZE);
        linePaint.setStyle(Paint.Style.FILL); linePaint.setColor(Color.GRAY); linePaint.setStrokeWidth(5);
        darkBluePaint.setStyle(Paint.Style.FILL); darkBluePaint.setColor(Color.BLUE);
        blackPaint.setStyle(Paint.Style.FILL); blackPaint.setColor(Color.BLACK);

        // build the paints for the 18 colors
        SparseIntArray colorMap = UtilsMap.buildColorRamp();
        for (int i = 0; i < SECTOR_DEGREES / 2; i++) {
            arcPaints[i] = new Paint();
            arcPaints[i].setStyle(Paint.Style.FILL);
            int index = Math.round( (i * 1.0f / 18.0f) * colorMap.size());
            arcPaints[i].setColor(colorMap.get(index));
        }

        bitmap = Bitmap.createBitmap(BITMAP_X, BITMAP_Y, Bitmap.Config.ARGB_8888);
        widthBitmap = bitmap.getWidth();
        heightBitmap = bitmap.getHeight();

        canvas1 = new Canvas(bitmap);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int widthCanvas;
        int heightCanvas;
        final int MAP_LOADED = 1;
        final int RUNNING    = 2;
        super.onDraw(canvas);

        canvas1.drawColor(Color.WHITE); // draw the background
        canvas1.drawCircle(CENTER_X, CENTER_Y, OUT_RADIUS, blackPaint); // fill in the outer border
        canvas1.drawCircle(CENTER_X, CENTER_Y, OUT_RADIUS - THIN_BORDER, darkBluePaint); // fill in the border
        canvas1.drawCircle(CENTER_X, CENTER_Y, OUT_RADIUS - THIN_BORDER - THICK_BORDER, blackPaint);  // fill in the inner border
        canvas1.drawCircle(CENTER_X, CENTER_Y, IN_RADIUS, backgroundPaint); // fill in the inner circle

        // draw North symbol
        canvas1.drawText("N", CENTER_X - (TEXT_SIZE / 4), CENTER_Y - OUT_RADIUS - 4, textPaint);

        // draw the colored arcs
        for (int i = 0; i < SECTOR_DEGREES; i++) {
            float start_angle = ( (i * 10) + 270) % 360;
            int paint_index = (i < SECTOR_DEGREES / 2)? i % (SECTOR_DEGREES / 2): SECTOR_DEGREES - 1 -i;
            canvas1.drawArc(rectFs[i], start_angle, 10.0f, true, arcPaints[paint_index]);
        }

        // draw the lines from the center
        for (int i = 0; i < SECTOR_DEGREES; i++) {
            int xDisp = (int) Math.round(IN_RADIUS * Math.cos(Math.toRadians(i * 10)));
            int yDisp = (int) Math.round(IN_RADIUS * Math.sin(Math.toRadians(i * 10)));
            canvas1.drawLine(CENTER_X, CENTER_Y, CENTER_X + xDisp, CENTER_Y + yDisp, linePaint);
        }

        // initial position of map
        if (VIEW_STATUS < MAP_LOADED) {
            widthCanvas = getWidth();
            heightCanvas = getHeight();
            if (widthCanvas < heightCanvas) {
                mxScaleFactor = (float) widthCanvas / widthBitmap;
                myScaleFactor = mxScaleFactor;
            } else {
                myScaleFactor = (float) heightCanvas / heightBitmap;
                mxScaleFactor = myScaleFactor;
            }
            mPosX = widthCanvas / 2; mPosY = heightCanvas / 2;
            VIEW_STATUS = RUNNING;
        }

        // build the forward / reverse transforms
        forwardTransform();
        reverseTransform();

        // fill in the background
        bearingActivity.bearingStatus.setText(directionPercent);
        canvas.drawRect(0.0f, 0.0f, (float) getWidth(), (float) getHeight(), backgroundPaint);
        canvas.drawBitmap(bitmap, forwardTransform, backgroundPaint);
    }

    // transform from bitmap to canvas
    private void forwardTransform() {
        forwardTransform.reset();
        forwardTransform.postTranslate(-widthBitmap / 2.0f, -heightBitmap / 2.0f);
        forwardTransform.postScale(mxScaleFactor, myScaleFactor);
        forwardTransform.postTranslate(mPosX, mPosY);
    }

    // transform from canvas to bitmap
    private void reverseTransform() {
        reverseTransform.reset();
        reverseTransform.postTranslate(-mPosX, -mPosY);
        reverseTransform.postScale(mxScaleFactor, myScaleFactor);
        reverseTransform.postTranslate(widthBitmap / 2.0f, heightBitmap / 2.0f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the ScaleGestureDetector inspect all events.
        mScaleDetector.onTouchEvent(ev);
        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
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