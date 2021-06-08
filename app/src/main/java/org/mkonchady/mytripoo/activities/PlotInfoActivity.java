package org.mkonchady.mytripoo.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;

import org.mkonchady.mytripoo.R;

// show the legend for a plot in a window
public class PlotInfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.plot_info);
        Intent intent = getIntent();

        boolean battery_data = intent.getBooleanExtra("battery_data", false);
        boolean pace_data = intent.getBooleanExtra("pace_data", false);
        if (battery_data) {
           show_battery_data(intent);
        } else if (pace_data) {
            show_pace_data(intent);
        } else {
           show_legend(intent);
        }
    }

    private void show_battery_data(Intent intent) {
        String min_battery = intent.getStringExtra("min_battery");
        String max_battery = intent.getStringExtra("max_battery");
        String used_battery = intent.getStringExtra("used_battery");
        final TextView numReadings = findViewById(R.id.plotTextView00b);
        String num_readings = "" + intent.getIntExtra("num_readings", 0);
        numReadings.setText(num_readings);

        final TextView rawTitle = findViewById(R.id.plotTextView01a);
        rawTitle.setText(getText(R.string.battery_min));
        final TextView rawTitle1 = findViewById(R.id.plotTextView01b);
        rawTitle1.setText(min_battery);
        final TextView smoothTitle =  findViewById(R.id.plotTextView02a);
        smoothTitle.setText(getText(R.string.battery_max));
        final TextView smoothTitle1 =  findViewById(R.id.plotTextView02b);
        smoothTitle1.setText(max_battery);
        final TextView averageTitle =  findViewById(R.id.plotTextView03a);
        averageTitle.setText(getText(R.string.battery_used));
        final TextView averageTitle1 =  findViewById(R.id.plotTextView03b);
        averageTitle1.setText(used_battery);

    }

    private void show_pace_data(Intent intent) {
        String min_pace = intent.getStringExtra("min_pace");
        String max_pace = intent.getStringExtra("max_pace");
        String avg_pace = intent.getStringExtra("avg_pace");
        final TextView numReadings = findViewById(R.id.plotTextView00b);
        String num_readings = "" + intent.getIntExtra("num_readings", 0);
        numReadings.setText(num_readings);

        final TextView rawTitle = findViewById(R.id.plotTextView01a);
        rawTitle.setText(getText(R.string.pace_min));
        final TextView rawTitle1 = findViewById(R.id.plotTextView01b);
        rawTitle1.setText(min_pace);
        final TextView smoothTitle =  findViewById(R.id.plotTextView02a);
        smoothTitle.setText(getText(R.string.pace_max));
        final TextView smoothTitle1 =  findViewById(R.id.plotTextView02b);
        smoothTitle1.setText(max_pace);
        final TextView averageTitle =  findViewById(R.id.plotTextView03a);
        averageTitle.setText(getText(R.string.pace_avg));
        final TextView averageTitle1 =  findViewById(R.id.plotTextView03b);
        averageTitle1.setText(avg_pace);
    }


    private void show_legend(Intent intent) {
        // get the data from the intent
        String num_readings = "" + intent.getIntExtra("num_readings", 0);
        String rawLine = intent.getStringExtra("raw_line");
        String smoothLine = intent.getStringExtra("smoothed_line");
        String averageLine = intent.getStringExtra("average_line");

        boolean showRaw = (rawLine != null && rawLine.length() > 0);
        boolean showSmooth = (smoothLine != null && smoothLine.length() > 0);
        boolean showAverage = (averageLine != null && averageLine.length() > 0);

        // get the number of readings
        final TextView numReadings = findViewById(R.id.plotTextView00b);
        numReadings.setText(num_readings);

        // if a raw line and no smooth line, then switch to show the brighter blue line
        if (showRaw) {
            if (showSmooth) {
                // set the raw line title
                final TextView rawTitle =  findViewById(R.id.plotTextView01a);
                rawTitle.setText(rawLine);
                final TextView rawTitle1 = findViewById(R.id.plotTextView01b);
                rawTitle1.setText(R.string.seven_underscores);
                // set the smooth line title
                final TextView smoothTitle = findViewById(R.id.plotTextView02a);
                smoothTitle.setText(smoothLine);
                final TextView smoothTitle1 = findViewById(R.id.plotTextView02b);
                smoothTitle1.setText(R.string.seven_underscores);
            } else {
                // set the smooth line title
                final TextView smoothTitle = findViewById(R.id.plotTextView02a);
                smoothTitle.setText(rawLine);
                final TextView smoothTitle1 = findViewById(R.id.plotTextView02b);
                smoothTitle1.setText(R.string.seven_underscores);
            }
        }

        // set the average line title
        if (showAverage) {
            final TextView averageTitle =  findViewById(R.id.plotTextView03a);
            averageTitle.setText(averageLine);
            final TextView averageTitle1 =  findViewById(R.id.plotTextView03b);
            averageTitle1.setText(R.string.seven_underscores);
        }
    }

}
