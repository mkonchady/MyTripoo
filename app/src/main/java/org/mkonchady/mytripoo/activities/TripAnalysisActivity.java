package org.mkonchady.mytripoo.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.database.SummaryProvider.Summary;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.Hashtable;

// show a window with the analysis of the summary and details of the trip
public class TripAnalysisActivity extends Activity implements View.OnClickListener {

    // Table fields
    SummaryDB summaryTable = null;      // summary table DB handler
    ArrayList<TableRow> rows = new ArrayList<>();
    LayoutInflater inflater = null;
    Context context = null;
    final int MIN_TURN_ANGLE = 45;
    int trip_id;
    TableLayout tableLayout = null;
    private SharedPreferences sharedPreferences = null;

    // colors for the table rows
    int rowBackColor;
    int rowHighlightColor;
    String TAG = "TripAnalysisActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_analysis);
        context = this;
        trip_id = getIntent().getIntExtra(Constants.PREF_TRIP_ID, -1);
        summaryTable = new SummaryDB(SummaryProvider.db);
        tableLayout = findViewById(R.id.tableAnalysislayout);
        inflater = getLayoutInflater();
        rowBackColor = ContextCompat.getColor(context, R.color.row_background);
        rowHighlightColor = ContextCompat.getColor(context, R.color.row_highlight_background);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        setActionBar();
        new FetchSummary().execute(this, " where trip_id = " + trip_id, null);
    }

    // Load the list of summaries asynchronously and then build the table rows
    private class FetchSummary extends AsyncTask <Object, Integer, String> {

        ArrayList<Summary> summaries = null;
        @Override
        protected String doInBackground(Object...params) {
            Context context = (Context) params[0];
            String whereClause = (String) params[1];
            String sortOrder = (String) params[2];
            summaries = summaryTable.getSummaries(context, whereClause, sortOrder,  trip_id);
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            for (TableRow row : rows) tableLayout.removeView(row);
            rows = new ArrayList<>();
            buildTableRows(summaries.get(0));
        }
    }

    // build the list of table rows
    private void buildTableRows(Summary summary) {

        String distance = "" + UtilsMisc.formatFloat(summary.getDistance() / 1000.0f, 1);
        int segmentCount = summaryTable.getSegmentCount(context, summary.getTrip_id());
        String segments = "" + segmentCount;
        Hashtable<String, String> parms = get_parms(summary.getTrip_id(), segmentCount, summary.getCategory());

        addRow("Distance ", distance + " kms.", false, "Turns ", parms.get("num_turns"), false, true);

        String startDate = UtilsDate.getTimeDurationHHMMSS(summary.getStart_time(), true, summary.getAvg_lat(), summary.getAvg_lon());
        String endDate = UtilsDate.getTimeDurationHHMMSS(summary.getEnd_time(), true, summary.getAvg_lat(), summary.getAvg_lon());
        addRow("Start ", startDate, true, "End ", endDate, true, true);

        String numReadings = "" + summaryTable.getDetailCount(context, trip_id);
        String duration = UtilsDate.getTimeDurationHHMMSS(summary.getEnd_time() - summary.getStart_time(), false);
        addRow("Readings ", numReadings, false, "Duration ", duration , false, true);
        addRow("Descent ", parms.get("descent"), true, " Ascent ", parms.get("ascent"), true, true);
        addRow("Des.Time ", parms.get("descent_time"), false, " Asc.Time ", parms.get("ascent_time"), false, true);

        //String pollInterval = UtilsDate.getTimeDurationHHMMSS(UtilsMisc.getReadingTime(summary.getPollInterval()), false);
        //String pollDistance = UtilsMisc.getReadingDist(summary.getPollDistancel()) + " m";
        //addRow("Poll Time", pollInterval, false, "Poll Dist", pollDistance, false, true);

        float min_altitude = summary.getMin_elevation();
        if (min_altitude >= Constants.LARGE_INT) min_altitude = 0.0f;
        float max_altitude = summary.getMax_elevation();
        addRow("Low Alt ", min_altitude + " m.", true, "High Alt ", max_altitude + " m.", true, true);

        // print the normalized power and category
        addRow("N.Power ", parms.get("norm_power"), false, "Category ", summary.getCategory(),
                false, true);

        // print the no. segments and METs
        //addRow("Segments ", segments, true, "METs ", parms.get("mets"), true, true);
        addRow("Calories ", parms.get("calories"), true, "METs ", parms.get("mets"), true, true);

        // print the battery usage and moving average speed
        String moving_average = summary.getMovingAverage(summary.getExtras());
        String mov_avg_speed = "" + UtilsMisc.formatFloat(Float.valueOf(moving_average), 1) + " kmph.";
        addRow("Used Batt.", parms.get("battery_used"), false, "Mov.Speed", mov_avg_speed, false, true);

        addTitleRow("Feature", "Minimum", "Maximum", "Average");

        String minSpeed = "" + UtilsMisc.formatFloat(summary.getMin_speed(), 1) + " kmph.";
        String maxSpeed = "" + UtilsMisc.formatFloat(summary.getMax_speed(), 1) + " kmph.";
        String avgSpeed = "" + UtilsMisc.formatFloat(summary.getAvg_speed(), 1) + " kmph.";
        addRow("Speed", minSpeed, false, maxSpeed, avgSpeed, false, true);

        String minAccuracy = "" + summary.getMin_accuracy();
        String maxAccuracy = "" + summary.getMax_accuracy();
        String avgAccuracy = "" +  UtilsMisc.formatFloat(summary.getAvg_accuracy(), 1);
        addRow("Accuracy ", minAccuracy + " m.", true, maxAccuracy + " m.", avgAccuracy + " m.",  true, true);

        String minTime = "" + UtilsMisc.formatFloat(summary.getMin_time_readings() / 1000.0f, 1);
        String maxTime = "" + UtilsMisc.formatFloat(summary.getMax_time_readings() / 1000.0f, 1);
        String avgTime = "" + UtilsMisc.formatFloat(summary.getAvg_time_readings() / 1000.0f, 1);
        //String suffix = (summary.getMin_time_readings() == 1000L)? " sec.": " secs.";
        addRow("Time / Fix", minTime + " s.", false, maxTime + " s.", avgTime + " s.",  false, true);


        String minDistance = "" + UtilsMisc.formatFloat(summary.getMin_dist_readings(), 1);
        String maxDistance = "" + UtilsMisc.formatFloat(summary.getMax_dist_readings(), 1);
        String avgDistance = "" + UtilsMisc.formatFloat(summary.getAvg_dist_readings(), 1);
        addRow("Dist / Fix", minDistance + " m.", true, maxDistance + " m.", avgDistance + " m.",  true, true);

        addRow("Gradient ", parms.get("min_gradient"), false,
                parms.get("max_gradient"), parms.get("avg_gradient"),  false, true);

        addRow("Satellites", parms.get("min_satellites"), true,
                parms.get("max_satellites"), parms.get("avg_satellites"), true, true);

        addRow("Signal", parms.get("min_snr"), false,
                parms.get("max_snr"), parms.get("avg_snr"), false, true);

        addRow("Power", parms.get("min_power"), false,
                parms.get("max_power"), parms.get("avg_power"), false, true);

        for (int i = 1; i <= segmentCount; i++) {
            String min_speed = UtilsMisc.formatFloat(Float.valueOf(parms.get("min_speed_" + i)), 1);
            String max_speed = UtilsMisc.formatFloat(Float.valueOf(parms.get("max_speed_" + i)), 1);
            //int startIndex = (i - 1) * 3;
            String fSpeed = UtilsMisc.formatFloat(summaryTable.getSegmentSpeed(context, trip_id, i), 1) + " kmph.";
            //String fSpeed = UtilsMisc.formatFloat(Float.valueOf(parts[startIndex + 2]), 1) + " kmph.";
            addRow("Seg. " + i, min_speed + " kmph.", true, max_speed + " kmph.", fSpeed, true, true);
        }
    }

    private void addRow(String label1, String description1, boolean background1,
                        String label2, String description2, boolean background2, boolean drawLine) {
        final TableRow tr = (TableRow)inflater.inflate(R.layout.table_analysis_row, tableLayout, false);
        final TextView labelView1 = tr.findViewById(R.id.label1);
        final TextView descriptionView1 = tr.findViewById(R.id.description1);
        labelView1.setText(label1); descriptionView1.setText(description1);
        if (background1) {
            labelView1.setBackgroundColor(ContextCompat.getColor(context, R.color.LightBlue));
            descriptionView1.setBackgroundColor(ContextCompat.getColor(context, R.color.LightBlue));
        }

        final TextView labelView2 =  tr.findViewById(R.id.label2);
        final TextView descriptionView2 =  tr.findViewById(R.id.description2);
        labelView2.setText(label2); descriptionView2.setText(description2);
        if (background2) {
            labelView2.setBackgroundColor(ContextCompat.getColor(context, R.color.LightBlue));
            descriptionView2.setBackgroundColor(ContextCompat.getColor(context, R.color.LightBlue));
        }

        final int MAXLEN = 10;
        if ( label1.length() > MAXLEN  || description1.length() > MAXLEN ||
             label2.length() > MAXLEN  || description2.length() > MAXLEN ) {
            labelView1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            descriptionView1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            labelView2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            descriptionView2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        }

        rows.add(tr);                   // save the collection of rows
        tableLayout.addView(tr);        // add to the table

        if (drawLine) {
            View v = new View(this);
            v.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, 1));
            v.setBackgroundColor(ContextCompat.getColor(context, R.color.Black));
            tableLayout.addView(v);
        }
    }

    private void addTitleRow(String label1, String description1, String label2, String description2) {
        final TableRow tr = (TableRow)inflater.inflate(R.layout.table_analysis_row, tableLayout, false);

        //tr.setClickable(true);
        final TextView labelView1 =  tr.findViewById(R.id.label1);
        final TextView descriptionView1 =  tr.findViewById(R.id.description1);
        labelView1.setText(label1); descriptionView1.setText(description1);
        labelView1.setBackgroundColor(ContextCompat.getColor(context, R.color.header_background));
        descriptionView1.setBackgroundColor(ContextCompat.getColor(context, R.color.header_background));
        labelView1.setTextColor(ContextCompat.getColor(context, R.color.white));
        descriptionView1.setTextColor(ContextCompat.getColor(context, R.color.white));

        final TextView labelView2 =  tr.findViewById(R.id.label2);
        final TextView descriptionView2 =  tr.findViewById(R.id.description2);
        labelView2.setText(label2); descriptionView2.setText(description2);
        labelView2.setBackgroundColor(ContextCompat.getColor(context, R.color.header_background));
        descriptionView2.setBackgroundColor(ContextCompat.getColor(context, R.color.header_background));
        labelView2.setTextColor(ContextCompat.getColor(context, R.color.white));
        descriptionView2.setTextColor(ContextCompat.getColor(context, R.color.white));

        tr.setPadding(0, 45, 0, 0);
        rows.add(tr);                   // save the collection of rows
        tableLayout.addView(tr);        // add to the table

        View v = new View(this);
        v.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(ContextCompat.getColor(context, R.color.Black));
        tableLayout.addView(v);
    }

    // get a list of other parms
    private Hashtable<String, String> get_parms(int trip_id, int segmentCount, String category) {
        DetailDB detailDB = new DetailDB(DetailProvider.db);

        // initialize the hash that is returned and other parameters
        Hashtable<String, String> parms= new Hashtable<>();
        float ascent = 0; float descent = 0; float prev_altitude = -1;      // altitude parms
        float max_gradient   = -Constants.LARGE_FLOAT; float min_gradient   = Constants.LARGE_FLOAT; float tot_gradient = 0.0f;
        int max_satellites = -Constants.LARGE_INT;     int min_satellites = Constants.LARGE_INT;     int tot_satellites = 0;
        float max_snr   = -Constants.LARGE_FLOAT;      float min_snr   = Constants.LARGE_FLOAT;      float tot_snr = 0.0f;
        float max_power   = -Constants.LARGE_FLOAT;    float min_power   = Constants.LARGE_FLOAT;    float tot_power = 0.0f;

        int prev_bearing = -1; int num_turns = 0;

        // initialize the min. max. and avg. speeds for the segments
        for (int i = 1; i <= segmentCount; i++) {
            parms.put("min_speed_" + i, "10000.0");
            parms.put("max_speed_" + i, "-10000.0");
        }

        //int detailCount = 0;
        ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id);
        int num_details = details.size();
        long elapsed_time_msec = details.get(num_details-1).getTimestamp() - details.get(0).getTimestamp();
        int elapsed_time_sec = UtilsDate.getTimeSeconds(elapsed_time_msec);
        int elapsed_time_min = UtilsDate.getTimeMinutes(elapsed_time_msec);
        long start_time = details.get(0).getTimestamp();
        long ascent_time = 0; long descent_time = 0;
        long prev_time = 0;

        // initialize the array to calculate the normalized power
        Number[] norm_power = new Number[elapsed_time_sec+1];
        for (int i = 0; i < elapsed_time_sec; i++) norm_power[i] = 0;

        for (DetailProvider.Detail detail: details) {

            // calculate the ascent and descent
            float current_altitude = detail.getAltitude();
            long current_time = detail.getTimestamp();
            if (current_altitude < 0.0f) continue;
            if (prev_altitude != -1) {
                float diff_altitude = current_altitude - prev_altitude;
                if (diff_altitude > 0) {
                    ascent += diff_altitude;
                    ascent_time += current_time - prev_time;
                } else {
                    descent += diff_altitude;
                    descent_time += current_time - prev_time;
                }
            }
            prev_altitude = current_altitude;

            // calculate the gradients
            float current_gradient = detail.getGradient();
            tot_gradient += current_gradient;
            if (current_gradient > max_gradient) max_gradient = current_gradient;
            if (current_gradient < min_gradient) min_gradient = current_gradient;

            // calculate the num satellites
            int current_satellites = detail.getSatellites();
            tot_satellites += current_satellites;
            if (current_satellites > max_satellites) max_satellites = current_satellites;
            if (current_satellites < min_satellites) min_satellites = current_satellites;

            // calculate the signal
            float current_snr = detail.getSatinfo();
            tot_snr += current_snr;
            if (current_snr > max_snr) max_snr = current_snr;
            if (current_snr < min_snr) min_snr = current_snr;

            // calculate the power
            float current_power = (float) detail.getPower();
            tot_power += current_power;
            if (current_power > max_power) max_power = current_power;
            if (current_power < min_power) min_power = current_power;

            // populate the norm_power array
            double power = detail.getPower();
            long timestamp = detail.getTimestamp() - start_time;
            int elapsed_seconds = UtilsDate.getTimeSeconds(timestamp);
            norm_power[elapsed_seconds] = power;

            // calculate the bearing difference
            int bearing = detail.getBearing();
            if (prev_bearing != -1 && UtilsMap.getAngle(prev_bearing, bearing) > MIN_TURN_ANGLE)
                num_turns++;
            prev_bearing = bearing;

            // fill up the parms
            int segment = detail.getSegment();
            float speed = detail.getSpeed();
            float min_speed = Float.valueOf(parms.get("min_speed_" + segment));
            if (speed < min_speed)
                parms.put("min_speed_" + segment, speed + "");
            float max_speed = Float.valueOf(parms.get("max_speed_" + segment));
            if (speed > max_speed)
                parms.put("max_speed_" + segment, speed + "");
            prev_time = current_time;
        }

        parms.put("battery_used", (details.get(0).getBattery() - details.get(num_details-1).getBattery())  + "%");
        parms.put("ascent_time", UtilsDate.getTimeDurationHHMMSS(ascent_time, false));
        parms.put("descent_time", UtilsDate.getTimeDurationHHMMSS(descent_time, false));


        // interpolate the power numbers to the second
        norm_power = UtilsMisc.Interpolate(norm_power, true);

        // calculate the running total for the first 29 seconds
        double running_total = 0;
        if (norm_power.length > Constants.HALF_MINUTE_SECONDS) {
            for (int i = 0; i < Constants.HALF_MINUTE_SECONDS - 1; i++)
                running_total += norm_power[i].doubleValue();
        }

        final double rider_wt = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_RIDER, "70.0"));
        double normalized_power = 0.0;
        double mets = 0;
        ArrayList<Double> moving_average = new ArrayList<>();
        double avg_power = 0.0;
        if ( category.equals(Constants.CAT_CYCLING) || category.equals(Constants.CAT_JOGGING) || category.equals(Constants.CAT_WALKING)
                && (elapsed_time_sec > Constants.TWO_MINUTES_SECONDS) ) {
            // add the first moving average
            moving_average.add(running_total / Constants.HALF_MINUTE_SECONDS);
            // compute the moving averages
            for (int i = Constants.HALF_MINUTE_SECONDS; i < elapsed_time_sec; i++) {
                running_total -= norm_power[i - Constants.HALF_MINUTE_SECONDS].doubleValue(); // remove the oldest value
                running_total += norm_power[i].doubleValue();                                 // add the newest value
                moving_average.add(running_total / Constants.HALF_MINUTE_SECONDS);
            }

            // get the average of the fourth power
            int moving_average_len = moving_average.size();
            double total_moving_average = 0;
            for (int i = 0; i < moving_average_len; i++) {
                double x = moving_average.get(i);
                total_moving_average += (x * x * x * x);
            }
            normalized_power = Math.sqrt(Math.sqrt(total_moving_average / moving_average_len));

            double total_power = 0.0;
            for (int i = 0; i < norm_power.length; i++)
                total_power += norm_power[i].doubleValue();
            avg_power = total_power / norm_power.length;
            parms.put("avg_power", UtilsMisc.formatFloat((float) avg_power, 1) + " w");

            // calculate METs
            mets = (avg_power * 0.84) / (rider_wt * 0.24);
        }

        parms.put("norm_power",  UtilsMisc.formatFloat((float) normalized_power, 1) + " w");
        parms.put("mets", UtilsMisc.formatFloat((float) mets, 1));
        //double calories = (mets * 3.5 * rider_wt * elapsed_time_min) / 200.0;  // method 1

        double calories = (elapsed_time_sec * avg_power) / (0.25 * 1000 * 4.2);  // method 2
        parms.put("calories",  UtilsMisc.formatFloat((float) calories, 0) );
        parms.put("ascent",  UtilsMisc.formatFloat(ascent, 1) + " m.");
        parms.put("descent", UtilsMisc.formatFloat(Math.abs(descent), 1) + " m.");

        float avg_gradient = tot_gradient / details.size();
        parms.put("min_gradient", UtilsMisc.formatFloat(min_gradient, 1) + " %");
        parms.put("max_gradient", UtilsMisc.formatFloat(max_gradient, 1) + " %");
        parms.put("avg_gradient", UtilsMisc.formatFloat(avg_gradient, 1) + " %");

        float avg_satellites = tot_satellites / details.size();
        parms.put("min_satellites", min_satellites + "");
        parms.put("max_satellites", max_satellites + "");
        parms.put("avg_satellites", UtilsMisc.formatFloat(avg_satellites, 1));

        float avg_snr = tot_snr / details.size();
        parms.put("min_snr", UtilsMisc.formatFloat(min_snr, 1) + " db");
        parms.put("max_snr", UtilsMisc.formatFloat(max_snr, 1) + " db");
        parms.put("avg_snr", UtilsMisc.formatFloat(avg_snr, 1) + " db");


        parms.put("min_power", UtilsMisc.formatFloat(min_power, 1) + " w");
        parms.put("max_power", UtilsMisc.formatFloat(max_power, 1) + " w");
        if (moving_average.size() == 0) {       // calculated earlier?
            parms.put("avg_power", UtilsMisc.formatFloat(tot_power / details.size(), 1) + " w");
        }

        parms.put("num_turns", num_turns + "");

        return parms;
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
    @Override
    public void onClick(View view) {

    }
}