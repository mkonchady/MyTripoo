package org.mkonchady.mytripoo.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.activities.CompassActivity;

public class CompassView extends View {

    //private final String TAG = "CompassView";
    private static final int INVALID_POINTER_ID = -1;

    // bitmap to hold the map
    public Bitmap bitmap;
    private final int widthBitmap;
    private final int heightBitmap;
    public float azimuth = 0.0f;

    private final Matrix forwardTransform = new Matrix();     // matrix of forward transform.
    private final Matrix reverseTransform = new Matrix();     // matrix of reverse transform

    // scale and position of canvas on map to handle drag
    private float mxScaleFactor = 1.0f;
    private float myScaleFactor = 1.0f;
    private float mPosX;       private float mPosY;
    //
    private float mLastTouchX; private float mLastTouchY;
    private int mActivePointerId = INVALID_POINTER_ID;
    private final ScaleGestureDetector mScaleDetector;

    // paints for text and ovals
    private final Paint backgroundPaint = new Paint();
    private final CompassActivity compassActivity;

    // STATUS indicators
    private int VIEW_STATUS = 0;


    public CompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        compassActivity = (CompassActivity) context;

        // convert screen pixels to density independent pixels
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;

        // extract the scaled bitmap
        bitmap = decodeSampledBitmapFromResource(getResources(), R.drawable.compass_view,
                (int) (dpWidth), (int) (dpHeight));
        widthBitmap = bitmap.getWidth();
        heightBitmap = bitmap.getHeight();
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.BLACK);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int widthCanvas;
        int heightCanvas;
        final int MAP_LOADED = 1;
        final int RUNNING    = 2;

        super.onDraw(canvas);

        // fill in the background
        canvas.drawRect(0.0f, 0.0f, (float) getWidth(), (float) getHeight(), backgroundPaint);

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

        // draw the blank map
        canvas.drawBitmap(bitmap, forwardTransform, backgroundPaint);
    }

    // transform from bitmap to canvas
    private void forwardTransform() {
        forwardTransform.reset();
        forwardTransform.postTranslate(-widthBitmap / 2.0f, -heightBitmap / 2.0f);
        forwardTransform.postScale(mxScaleFactor, myScaleFactor);
        forwardTransform.postTranslate(mPosX, mPosY);
        forwardTransform.postRotate(-azimuth, mPosX, mPosY);
    }

    // transform from canvas to bitmap
    private void reverseTransform() {
        reverseTransform.reset();
        reverseTransform.postRotate(azimuth, mPosX, mPosY);
        reverseTransform.postTranslate(-mPosX, -mPosY);
        reverseTransform.postScale(mxScaleFactor, myScaleFactor);
        reverseTransform.postTranslate(widthBitmap / 2.0f, heightBitmap / 2.0f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
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

    /* Return a sampled bitmap that is smaller than the original resource bitmap */
    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
                                                         int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeResource(res, resId, options);
    }

    /* calculate the sample size - 1, 2, 4, ... as in full, half, quarter */
    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // the canvas must be rotated and the current bearing must be set
    public void setAzimuth(float azimuth) {
        this.azimuth = azimuth;
        if (compassActivity.bearingStatus != null) {
            int iAzimuth = Math.round(azimuth);
            if (iAzimuth < 0) iAzimuth = iAzimuth + 360;
            String bearing = "Azimuth " + iAzimuth + "° Bearing: ";
            switch (iAzimuth) {
                case 0: bearing += "N"; break;
                case 90: bearing += "E"; break;
                case 180: bearing += "S"; break;
                case 270: bearing += "W"; break;
            }
            if ( (0 < iAzimuth) && (iAzimuth < 90) ) {
                bearing += iAzimuth + "° E of N";
            } else if ( (90 < iAzimuth) && (iAzimuth < 180)) {
                iAzimuth = 180 - iAzimuth;
                bearing += iAzimuth + "° E of S";
            } else if ( (180 < iAzimuth) && (iAzimuth < 270)) {
                iAzimuth = iAzimuth - 180;
                bearing += iAzimuth + "° W of S";
            } else if ( (270 < iAzimuth) && (iAzimuth < 360) ){
                iAzimuth = 360 - iAzimuth;
                bearing += iAzimuth + "° W of N";
            }

            compassActivity.bearingStatus.setText(bearing);
        }
        invalidate();
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