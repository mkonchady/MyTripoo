package org.mkonchady.mytripoo.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.views.TripCompareView;


public class TripCompareActivity extends Activity  {
    int trip_id1;int trip_id2;
    TripCompareView tripCompareView = null;
    private TextView timeTextview = null;
    private TextView timeDistanceView1 = null;
    private TextView timeDistanceView2 = null;
    private TextView timeDistanceView3 = null;
    String TAG = "TripCompareActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trip_id1 = getIntent().getIntExtra(Constants.PREF_TRIP_ID + "_1", -1);
        trip_id2 = getIntent().getIntExtra(Constants.PREF_TRIP_ID + "_2", -1);
        setContentView(R.layout.activity_trip_compare);
        timeTextview = findViewById(R.id.timeTextView);
        timeDistanceView1 = findViewById(R.id.timeDistanceView1);
        timeDistanceView2 = findViewById(R.id.timeDistanceView2);
        timeDistanceView3 = findViewById(R.id.timeDistanceView3);
        tripCompareView =  this.findViewById(R.id.tripCompareView);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setActionBar();
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

    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
    }

    public void setTime(String timeText) {
        timeTextview.setText(timeText);
    }

    public void setDistance1(String distanceText) {
        timeDistanceView1.setText(distanceText);
    }

    public void setDistance2(String distanceText) {
        timeDistanceView2.setText(distanceText);
    }

    public void setDistance3(String distanceText) {
        timeDistanceView3.setText(distanceText);
    }

    public int getTrip_id2() {
        return trip_id2;
    }

    public int getTrip_id1() {
        return trip_id1;
    }

    public void pauseCompare(View v) {
        tripCompareView.stopTimer();
    }

    public void forwardCompare(View v) {
        tripCompareView.forwardTimer();
    }

    public void fforwardCompare(View v) {
        tripCompareView.fforwardTimer();
    }

    public void reverseCompare(View v) {
        tripCompareView.reverseTimer();
    }

    public void rreverseCompare(View v) {
        tripCompareView.rreverseTimer();
    }
}
