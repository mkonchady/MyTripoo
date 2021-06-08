package org.mkonchady.mytripoo.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;

public class BearingActivity extends Activity  {

    //private final String TAG = "BearingActivity";
    public TextView bearingStatus = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setActionBar();
        setContentView(R.layout.activity_bearing);
        bearingStatus = this.findViewById(R.id.statusView2);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_map_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_speed_map:
                returnCaller(Constants.DRAW_SPEEDS);
                break;
            case R.id.action_tracks_map:
                returnCaller(Constants.DRAW_TRACKS);
                break;
            case R.id.action_elevation_map:
                returnCaller(Constants.DRAW_ELEVATION);
                break;
            case R.id.action_gradient_map:
                returnCaller(Constants.DRAW_GRADIENT);
                break;
            case R.id.action_satinfo_map:
                returnCaller(Constants.DRAW_SATINFO);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // save the previous caller type
    public void returnCaller(int mapIndex) {
        Intent data = getIntent();
        data.putExtra("MAPINDEX", mapIndex);
        setResult(Activity.RESULT_OK, data);
        finish();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    // display the action bar
    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setTitle("Bearings Map");
            actionBar.setDisplayUseLogoEnabled(true);
        }
    }
}