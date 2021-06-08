package org.mkonchady.mytripoo.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.utils.UtilsMap;

public class ShowDirectionsActivity extends Activity {

    Context context;
    int trip_id;
    int localLog = 0;
    String directions;
    SharedPreferences sharedPreferences;
    final String TAG = "ShowDirectionsActivity";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        context = this;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        directions = getIntent().getStringExtra(Constants.DIRECTIONS);
        setContentView(R.layout.directions);
        setActionBar();
        final Window win= getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                     WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                     WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView textView = this.findViewById(R.id.directionsTrip);
        textView.setText(directions);
        ImageView imageView = this.findViewById(R.id.directionsView);
        imageView.setImageResource(UtilsMap.getDirectionImage(directions));
        setScreenVisible(true);
        Logger.d(TAG, "Showing directions: ", localLog);
    }

    @Override
    protected void onPause() {
        super.onPause();
        setScreenVisible(false);
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

    private void setScreenVisible(boolean showScreen) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(Constants.PREF_DIRECTION_SCREEN_VISIBLE, showScreen);
        editor.apply();
    }
}