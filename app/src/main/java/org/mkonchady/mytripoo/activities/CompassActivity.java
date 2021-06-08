package org.mkonchady.mytripoo.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.TextView;

import org.mkonchady.mytripoo.views.CompassView;
import org.mkonchady.mytripoo.R;

public class CompassActivity extends Activity implements SensorEventListener {

    //private final String TAG = "CompassActivity";
    public CompassView compassView = null;
    public TextView bearingStatus = null;
    public static SharedPreferences sharedPreferences;
    public static SensorManager mSensorManager;
    public Sensor accelerometer = null;
    public Sensor magnetometer = null;
    public static float[] mAccelerometer = null;
    public static float[] mGeomagnetic = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set the sensor vars
        setActionBar();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sharedPreferences.getBoolean(getString(R.string.compass_enabled), false)) {
            accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        setContentView(R.layout.activity_compass);
        compassView = this.findViewById(R.id.compassView);
        bearingStatus = this.findViewById(R.id.statusView1);

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccelerometer = event.values;
        }

        // use both sensors to get orientation
        if (mAccelerometer != null && mGeomagnetic != null) {
            float[] rotation = new float[9];
            float[] inclination = new float[9];
            boolean success = SensorManager.getRotationMatrix(rotation, inclination,
                    mAccelerometer, mGeomagnetic);

            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotation, orientation);
                float azimuth = (float) (180.0 * orientation[0] / Math.PI);
                compassView.setAzimuth(azimuth);
                //float pitch = orientation[1];
                //float roll = orientation[2];
            }
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // display the action bar
    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setTitle("Compass");
            actionBar.setDisplayUseLogoEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sharedPreferences.getBoolean(getString(R.string.compass_enabled), false)) {
            mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sharedPreferences.getBoolean(getString(R.string.compass_enabled), false)) {
            mSensorManager.unregisterListener(this, accelerometer);
            mSensorManager.unregisterListener(this, magnetometer);
        }
    }
}









