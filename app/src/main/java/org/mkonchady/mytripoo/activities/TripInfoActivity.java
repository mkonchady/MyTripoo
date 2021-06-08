package org.mkonchady.mytripoo.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;

import org.mkonchady.mytripoo.R;

import java.text.DecimalFormat;

// show the legend for a plot in a window
public class TripInfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.trip_info);
        Intent intent = getIntent();

        // get the data from the intent
        String covered_distance = "" + intent.getIntExtra("covered_distance", 0) / 1000.0f + " kms.";
        String trip_distance = "" + intent.getIntExtra("trip_distance", 0) / 1000.0f + " kms.";
        String remainder = "" + intent.getIntExtra("remainder", 0) / 1000.0f + " kms.";
        DecimalFormat df = new DecimalFormat("#.##");
        String percent = "" + df.format(intent.getFloatExtra("percent", 0.0f));

        final TextView coveredView =  findViewById(R.id.plotTextView00b);
        coveredView.setText(covered_distance);
        final TextView tripView = findViewById(R.id.plotTextView01b);
        tripView.setText(trip_distance);
        final TextView remainderView = findViewById(R.id.plotTextView02b);
        remainderView.setText(remainder);
        final TextView percentView = findViewById(R.id.plotTextView03b);
        percentView.setText(percent);
    }
}
