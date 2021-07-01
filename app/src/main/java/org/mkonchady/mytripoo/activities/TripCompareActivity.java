// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.mkonchady.mytripoo.activities;

import android.app.ActionBar;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.views.MapsFragment1;
import org.mkonchady.mytripoo.views.MapsFragment2;
import org.mkonchady.mytripoo.views.TripCompareView;

public class TripCompareActivity extends AppCompatActivity {

    public MapsFragment1 map1;
    public MapsFragment2 map2;
    private FragmentManager fragmentManager;
    public int trip_id1 = 0;
    public int trip_id2 = 0;

    TripCompareView tripCompareView = null;
    private TextView timeTextview = null;
    private TextView timeDistanceView1 = null;
    private TextView timeDistanceView2 = null;
    private TextView timeDistanceView3 = null;
    public int map_width = 0;
    public int map_height = 0;

    String TAG = "TripCompareActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trip_id1 = getIntent().getIntExtra(Constants.PREF_TRIP_ID + "_1", -1);
        trip_id2 = getIntent().getIntExtra(Constants.PREF_TRIP_ID + "_2", -1);
        setContentView(R.layout.activity_trip_compare);
        fragmentManager = getSupportFragmentManager();
        map1 = (MapsFragment1) fragmentManager.findFragmentById(R.id.map1);
        map2 = (MapsFragment2) fragmentManager.findFragmentById(R.id.map2);
        timeTextview = findViewById(R.id.timeTextView);
        timeDistanceView1 = findViewById(R.id.timeDistanceView1);
        timeDistanceView2 = findViewById(R.id.timeDistanceView2);
        timeDistanceView3 = findViewById(R.id.timeDistanceView3);
        tripCompareView =  findViewById(R.id.tripCompareView);

        final int ACTION_BAR_HEIGHT = 56;   // in dp
        final int MESSAGE_BAR_HEIGHT = 30;  // in dp
        final int BUTTON_BAR_HEIGHT = 35;   // in dp
        final int OTHER_HEIGHT = ACTION_BAR_HEIGHT + MESSAGE_BAR_HEIGHT + BUTTON_BAR_HEIGHT;
        int pixel_density = (int) (getResources().getDisplayMetrics().density);
        int screen_width_dp = Resources.getSystem().getDisplayMetrics().widthPixels / pixel_density;
        int screen_height_dp = Resources.getSystem().getDisplayMetrics().heightPixels / pixel_density;
        map_width = screen_width_dp * pixel_density;
        map_height = (screen_height_dp - OTHER_HEIGHT) / 2 * pixel_density;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setActionBar();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        tripCompareView.stopTimer();
        map1.onDestroy();
        map2.onDestroy();
        finish();
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
    public void setDistance3Color(int color) {
        timeDistanceView3.setTextColor(color);
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

    public int getTrip_id2() {
        return trip_id2;
    }
    public int getTrip_id1() {
        return trip_id1;
    }

    public void setTripCompareView(TripCompareView tripCompareView) {
        this.tripCompareView = tripCompareView;
    }
    public TripCompareView getTripCompareView() {
        return tripCompareView;
    }

    public MapsFragment1 getMap1() {
        return map1;
    }
    public MapsFragment2 getMap2() {
        return map2;
    }
    public int getMap_width() {
        return map_width;
    }

    public int getMap_height() {
        return map_height;
    }

    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
        }
    }
}
