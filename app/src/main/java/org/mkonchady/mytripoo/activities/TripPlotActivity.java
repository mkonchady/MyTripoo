package org.mkonchady.mytripoo.activities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
//import com.androidplot.xy.XYPlot;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.utils.PlotterData;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

// build the plots for the trip
public class TripPlotActivity extends Activity {

    public Context context;
    int trip_id;
    DetailDB detailDB;
    SummaryDB summaryDB;
    int localLog = 0;
    int dataIndex = 0;
    private int NUM_SEGMENTS;
    private int currentSegment = 1;

    private Timer timer = null;
    private boolean isExternal = false;
    int prev_action = -1;
    final int FORWARD = 0;
    final int REVERSE = 1;
    PlotterData plotterData = null;
    final String TAG = "TripPlotActivity";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        context = this;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        trip_id = getIntent().getIntExtra(Constants.PREF_TRIP_ID, -1);
        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
        NUM_SEGMENTS = summaryDB.getSegmentCount(context, trip_id);
        isExternal = summaryDB.isImported(context, trip_id);
        plotterData = new PlotterData(context, trip_id);
        speed_plot();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_plot_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        cancelTimer();
        switch (item.getItemId()) {
            case R.id.action_map_pause:
                cancelTimer();
                return true;
            case R.id.action_map_forward:  // use the timer for speed by segments alone
                if ((NUM_SEGMENTS <= 1) || (NUM_SEGMENTS > Constants.MAX_SEGMENTS) || isExternal) break;
                if (dataIndex == Constants.DATA_SPEED_SEGMENT) {
                    if (prev_action == REVERSE) incCurrentSegment(true, 2);
                    handleTimer(true);
                    prev_action = FORWARD;
                    return true;
                }
                else dataIndex = (dataIndex + 1) % Constants.NUM_PLOTS;
                break;
            case R.id.action_map_reverse:
                if ((NUM_SEGMENTS <= 1) || (NUM_SEGMENTS > Constants.MAX_SEGMENTS)  || isExternal) break;
                if (dataIndex == Constants.DATA_SPEED_SEGMENT) {
                    if (prev_action == FORWARD) incCurrentSegment(false, 2);
                    handleTimer(false);
                    prev_action = REVERSE;
                    return true;
                }
                else dataIndex = (dataIndex == 0)? Constants.DATA_PACE: (dataIndex - 1);
                break;
            case R.id.action_speed_plot:  dataIndex = Constants.DATA_SPEED; break;
            case R.id.action_elevation_plot: dataIndex = Constants.DATA_ALTITUDE; break;
            case R.id.action_speed_segment_plot: dataIndex = Constants.DATA_SPEED_SEGMENT; break;
            case R.id.action_gradient_plot: dataIndex = Constants.DATA_GRADIENT; break;
            case R.id.action_battery_plot: dataIndex = Constants.DATA_BATTERY; break;
            case R.id.action_satinfo_plot: dataIndex = Constants.DATA_SATINFO;  break;
            case R.id.action_satellites_plot: dataIndex = Constants.DATA_SATELIITES; break;
            case R.id.action_accuracy_plot: dataIndex = Constants.DATA_ACCURACY; break;
            case R.id.action_reading_time_plot: dataIndex = Constants.DATA_TIME_PER_READING; break;
            case R.id.action_reading_distance_plot: dataIndex = Constants.DATA_DISTANCE_PER_READING; break;
            case R.id.action_power_plot: dataIndex = Constants.DATA_POWER;  break;
            case R.id.action_power_histo_plot: dataIndex = Constants.DATA_HISTO_POWER;  break;
            case R.id.action_vam_plot: dataIndex = Constants.DATA_VAM;  break;
            case R.id.action_pace_plot: dataIndex = Constants.DATA_PACE; break;
            case R.id.action_head_wind_plot: dataIndex = Constants.DATA_HEAD_WIND;  break;
            default: return super.onOptionsItemSelected(item);
        }

        switch (dataIndex) {
            case Constants.DATA_SPEED: speed_plot(); return true;
            case Constants.DATA_GRADIENT: gradient_plot(); return  true;
            case Constants.DATA_BATTERY: battery_plot(); return true;
            case Constants.DATA_ALTITUDE: elevation_plot(); return true;
            case Constants.DATA_SPEED_SEGMENT: speed_segment_plot(); return true;
            case Constants.DATA_ACCURACY: accuracy_plot(); return true;
            case Constants.DATA_SATINFO: satinfo_plot(); return true;
            case Constants.DATA_SATELIITES: satellites_plot(); return true;
            case Constants.DATA_TIME_PER_READING: time_per_reading_plot(); return true;
            case Constants.DATA_DISTANCE_PER_READING: distance_per_reading_plot(); return true;
            case Constants.DATA_POWER: power_plot(); return true;
            case Constants.DATA_HISTO_POWER: histo_power_plot(); return true;
            case Constants.DATA_VAM: vam_plot(); return true;
            case Constants.DATA_HEAD_WIND: head_wind_plot(); return true;
            case Constants.DATA_PACE: pace_plot(); return true;
            default: break;
        }
        return  true;
    }

    // build a plot of the speeds
    public void speed_plot() {
        // populate the bundle with parameters for the plot
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Speed vs. Time");
        bundle.putString("lineTitle1", "Raw Speed");
        bundle.putString("lineTitle2", "Smoothed Speed");
        bundle.putString("lineTitle3", "Average Speed");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Speed (kmph)");

        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

      //  int range = Math.round(summary.getMax_speed() - summary.getMin_speed());
      //  range = (range == 0)? MIN_INT_RANGE: range;
      //  int yminVal = Math.round(summary.getMin_speed()) + UtilsMisc.calcRange(range, -0.50f);
      //  yminVal = (yminVal < 0)? 0: yminVal;
      //  bundle.putInt("yorigin", yminVal);
        //bundle.putInt("yorigin", 0);
      //  int max_speed = Math.round(summary.getMax_speed());
      //  bundle.putInt("yLimit", UtilsMisc.calcRange(max_speed, 1.1f));

        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        plotterData.setDataIndex(Constants.DATA_SPEED);

        //*------------ get a bundle of data from plotterData
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_speeds = bundle1.getFloatArray("ydata");
        float[] cum_distance = bundle1.getFloatArray("cum_distance");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_speeds);

        if (raw_speeds != null && raw_speeds.length > 0 && cum_distance != null && raw_times != null) {
            float[] avg_speeds = new float[cum_distance.length];
            final int AVG_SIZE = get_avg_size(cum_distance.length);

            float running_total = 0.0f;
            for (int i = 0; i < AVG_SIZE; i++) running_total += raw_speeds[i]; // calculate the running total for the first AVG_SIZE
            float INIT_RAW_AVG = running_total / AVG_SIZE;                      // get the moving average size

            for (int i = 0; i < cum_distance.length; i++)
                if (i < AVG_SIZE) {
                    avg_speeds[i] = INIT_RAW_AVG;
                } else {
                    running_total -= raw_speeds[i - AVG_SIZE];
                    running_total += raw_speeds[i];
                    avg_speeds[i] = running_total / AVG_SIZE;
                }
            bundle.putFloatArray("avg_ydata", avg_speeds);
        }

        // compute the smoothed values in the bundle
        plotterData.smooth_plot(bundle, details, start_time, true);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the altitude readings
    public void elevation_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Elevation vs. Time");
        bundle.putString("lineTitle1", "Raw Elevation");
        bundle.putString("lineTitle2", "Smoothed Elevation");
        bundle.putString("lineTitle3", "");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Altitude (meters)");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        // get the max. altitudes from the details
        //ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id);
        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        plotterData.setDataIndex(Constants.DATA_ALTITUDE);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_altitudes = bundle1.getFloatArray("ydata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_altitudes);
        float[] min_max_Altitude = UtilsMisc.getMinMax(raw_altitudes);
        float min_altitude = min_max_Altitude[0]; float max_altitude = min_max_Altitude[1];
        bundle.putInt("yorigin", Math.round(min_altitude));
        bundle.putInt("yLimit", Math.round(max_altitude));

        plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the speeds by segment
    public void speed_segment_plot() {

        final int MIN_INT_RANGE = 5;
        // check if more than MAX_SEGMENTS or just one segment, then return the regular speed plot
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        if ( (NUM_SEGMENTS <= 1) || (NUM_SEGMENTS > Constants.MAX_SEGMENTS) ) {
            speed_plot();
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putString("lineTitle1", "Current Segment Speed");
        bundle.putString("lineTitle2", "");
        bundle.putString("lineTitle3", "Average Segment Speed");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Speed (kmph)");

        // get the duration of the longest segment
        long longestDuration = 0;
        for (int i = 1; i <= NUM_SEGMENTS; i++) {
            long duration = summaryDB.getSegmentTime(context, trip_id, i);
            if (duration > longestDuration)
                longestDuration = duration;
        }

        // set the origin and limits
        int xfactor = UtilsDate.getTimeSeconds(longestDuration);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        int range = Math.round(summary.getMax_speed() - summary.getMin_speed());
        range = (range == 0)? MIN_INT_RANGE: range;
        int yminVal = Math.round(summary.getMin_speed()) - UtilsMisc.calcRange(range, 0.1f);
        yminVal = (yminVal < 0)? 0: yminVal;
        bundle.putInt("yorigin", yminVal);
        int max_speed = Math.round(summary.getMax_speed());
        bundle.putInt("yLimit", max_speed + UtilsMisc.calcRange(max_speed, 0.1f));

        for (int segment = 1; segment <= NUM_SEGMENTS; segment++) {
            plotterData.setDataIndex(Constants.DATA_SPEED);
            Bundle bundle1 = plotterData.getData(trip_id, segment, true);
            int[] raw_times = bundle1.getIntArray("times");
            float[] raw_speeds = bundle1.getFloatArray("ydata");
            float[] cum_distance = bundle1.getFloatArray("cum_distance");
            bundle.putIntArray("raw_xdata" + segment, raw_times);
            bundle.putFloatArray("raw_ydata" + segment, raw_speeds);

            if (raw_speeds != null && raw_speeds.length > 0 && cum_distance != null && raw_times != null) {
                //float[] avg_speeds = new float[cum_distance.length];
                //final int INIT_RAW_AVG = (cum_distance.length > 100)? 10: 20;
                //for (int i = 0; i < cum_distance.length; i++)
                //    avg_speeds[i] = (i < INIT_RAW_AVG)? raw_speeds[i]: (float) UtilsMap.calcKmphSpeed(cum_distance[i], raw_times[i] * 1000);

                float[] avg_speeds = new float[cum_distance.length];
                final int AVG_SIZE = get_avg_size(cum_distance.length);

                float running_total = 0.0f;
                for (int i = 0; i < AVG_SIZE; i++) running_total += raw_speeds[i]; // calculate the running total for the first AVG_SIZE
                float INIT_RAW_AVG = running_total / AVG_SIZE;                      // get the moving average size

                for (int i = 0; i < cum_distance.length; i++)
                    if (i < AVG_SIZE) {
                        avg_speeds[i] = INIT_RAW_AVG;
                    } else {
                        running_total -= raw_speeds[i - AVG_SIZE];
                        running_total += raw_speeds[i];
                        avg_speeds[i] = running_total / AVG_SIZE;
                    }
                bundle.putFloatArray("avg_ydata" + segment, avg_speeds);
            }
        }

        // build the plot
        plotterData.setBundle(bundle);
        plotterData.setCurrentSegment(1);
        handleTimer(true);
    }

    // build a plot of the gradient readings
    public void gradient_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Gradient vs. Time");
        bundle.putString("lineTitle1", "Raw Gradient");
        bundle.putString("lineTitle2", "Smoothed Gradient");
        bundle.putString("lineTitle3", "Average Gradient");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Gradient %");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        plotterData.setDataIndex(Constants.DATA_GRADIENT);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_gradients = bundle1.getFloatArray("ydata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_gradients);
        /*
        float[] min_max_Gradient = UtilsMisc.getMinMax(raw_gradients);
        float min_gradient = min_max_Gradient[0]; float max_gradient = min_max_Gradient[1];

        // set the y axis limits
        float range = max_gradient - min_gradient;
        range = (Float.compare(range, 0.0f) == 0)? MIN_FLOAT_RANGE : range;
        int yminVal = Math.round(min_gradient + UtilsMisc.calcRange(range, -0.25f));
        bundle.putInt("yorigin", yminVal);
        int ymaxVal = Math.round(max_gradient + UtilsMisc.calcRange(range, 0.25f));
        bundle.putInt("yLimit", ymaxVal);
*/
        if (raw_gradients != null && raw_gradients.length > 0 && raw_times != null) {
            float avg_gradient = UtilsMisc.getSum(raw_gradients) / raw_gradients.length;
            float[] avg_gradients = new float[raw_gradients.length];
            Arrays.fill(avg_gradients, avg_gradient);
            bundle.putFloatArray("avg_ydata", avg_gradients);
        }

        plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the satellite info readings
    public void satinfo_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Signal vs. Time");
        bundle.putString("lineTitle1", "Raw Signal");
        bundle.putString("lineTitle2", "Smoothed Signal");
        bundle.putString("lineTitle3", "Avg. Signal");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Avg. Signal to Noise (Db)");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        plotterData.setDataIndex(Constants.DATA_SATINFO);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_satinfo = bundle1.getFloatArray("ydata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_satinfo);
        /*
        float[] min_max_Satinfo = UtilsMisc.getMinMax(raw_satinfo);
        float min_satinfo = min_max_Satinfo[0]; float max_satinfo = min_max_Satinfo[1];

        // set the y axis limits
        float range = max_satinfo - min_satinfo;
        range = (Float.compare(range, 0.0f) == 0)? MIN_FLOAT_RANGE : range;
        int yminVal = Math.round(min_satinfo + UtilsMisc.calcRange(range, -0.25f));
        bundle.putInt("yorigin", yminVal);
        int ymaxVal = Math.round(max_satinfo + UtilsMisc.calcRange(range, 0.25f));
        bundle.putInt("yLimit", ymaxVal);
*/
        // find the average signal strength
        if (raw_satinfo != null && raw_satinfo.length > 0) {
            float avg_sat = UtilsMisc.getSum(raw_satinfo) / raw_satinfo.length;
            float[] avg_satinfo = new float[raw_satinfo.length];
            Arrays.fill(avg_satinfo, avg_sat);
            bundle.putFloatArray("avg_ydata", avg_satinfo);
        }
        plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the head wind
    public void head_wind_plot() {
        // populate the bundle with parameters for the plot
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Head Wind Speed vs. Time");
        bundle.putString("lineTitle1", "Raw Head Wind");
        bundle.putString("lineTitle2", "Smoothed Head Wind");
        bundle.putString("lineTitle3", "Average Head WInd");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Head Wind Speed (kmph)");

        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        //*------------ get a bundle of data from plotterData
        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        plotterData.setDataIndex(Constants.DATA_HEAD_WIND);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_head_wind = bundle1.getFloatArray("ydata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_head_wind);

        if (raw_head_wind != null && raw_head_wind.length > 0 && raw_times != null) {
            float avg_head_wind = UtilsMisc.getSum(raw_head_wind) / raw_head_wind.length;
            float[] avg_head_winds = new float[raw_head_wind.length];
            Arrays.fill(avg_head_winds, avg_head_wind);
            bundle.putFloatArray("avg_ydata", avg_head_winds);
        }

        plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the power info readings
    public void power_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Power vs. Time");
        bundle.putString("lineTitle1", "Raw Power");
        bundle.putString("lineTitle2", "Smoothed Power");
        bundle.putString("lineTitle3", "Avg. Power");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Power (Watts)");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        plotterData.setDataIndex(Constants.DATA_POWER);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_power = bundle1.getFloatArray("ydata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_power);

        // find the average power
        if (raw_power != null && raw_power.length > 0) {
            float avg_power = UtilsMisc.getSum(raw_power) / raw_power.length;
            float[] avg_powers = new float[raw_power.length];
            Arrays.fill(avg_powers, avg_power);
            bundle.putFloatArray("avg_ydata", avg_powers);
        }
        plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a histogram power plot
    public void histo_power_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Frequency vs. Power");
        bundle.putString("lineTitle1", "Power");
        bundle.putString("lineTitle2", "");
        bundle.putString("lineTitle3", "Average Power");
        bundle.putString("xLabel", "Watts");
        bundle.putString("yLabel", "Frequency");

        // extract the detail records
        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        plotterData.setDataIndex(Constants.DATA_POWER);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_power = bundle1.getFloatArray("ydata");
        if (raw_power == null) return;

        // cap max. power at 2048
        for (int i = 0; i < raw_power.length; i++)
            raw_power[i] = (raw_power[i] > 2048)? 2048: raw_power[i];
        float max_power = UtilsMisc.getMax(raw_power);
        int imax_power = Math.round(max_power);

        // set the range for the x axis
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", imax_power);

        // build the power bins values
        int[] power_bins = new int[imax_power];
        for (int i = 0; i < imax_power; i++)
            power_bins[i] = i;

        // build the histograms counts
        final int RESOLUTION = 25;
        int[] power_counts = new int[imax_power];
        Arrays.fill(power_counts, 0);
        for (int i = 0; i < raw_power.length; i++) {
            int ipower = Math.round(raw_power[i]);
            int index = ipower / RESOLUTION;
            int start_index = index * RESOLUTION;
            for (int j = start_index; j < start_index+RESOLUTION-1; j++)
                if (j < imax_power)
                    power_counts[j]++;
        }

        bundle.putIntArray("raw_ydata", power_counts);
        bundle.putIntArray("raw_xdata", power_bins);
/*
        float avg_time = UtilsMisc.getSum(raw_times) / raw_times.length;
        float[] avg_times = new float[raw_times.length];
        Arrays.fill(avg_times, avg_time);
        bundle.putIntArray("avg_xdata", raw_kms);
        bundle.putFloatArray("avg_ydata", avg_times);
*/
        plotterData.setDataIndex(Constants.DATA_HISTO_POWER);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the vertical ascent / descent speed in mph
    public void vam_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "VAM vs. Time");
        bundle.putString("lineTitle1", "Raw VAM");
        bundle.putString("lineTitle2", "Smoothed VAM");
        bundle.putString("lineTitle3", "Average VAM");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "VAM (meters / hour)");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        plotterData.setDataIndex(Constants.DATA_VAM);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_vams = bundle1.getFloatArray("ydata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_vams);

        if (raw_vams != null && raw_vams.length > 0 && raw_times != null) {
            float avg_vam = UtilsMisc.getSum(raw_vams) / raw_vams.length;
            float[] avg_vams = new float[raw_vams.length];
            Arrays.fill(avg_vams, avg_vam);
            bundle.putFloatArray("avg_ydata", avg_vams);
        }

        plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the number of the satellites readings
    public void satellites_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "No. of Satellites vs. Time");
        bundle.putString("lineTitle1", "No. of Satellites");
        bundle.putString("lineTitle2", "");
        bundle.putString("lineTitle3", "Avg No. of Satellites");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "No. of Satellites");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        plotterData.setDataIndex(Constants.DATA_SATELIITES);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, false);
        int[] raw_times = bundle1.getIntArray("times");
        int[] raw_satellites = bundle1.getIntArray("idata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putIntArray("raw_ydata", raw_satellites);
        int[] min_max_Satellites = UtilsMisc.getMinMax(raw_satellites);
        int max_satellites = min_max_Satellites[1];

        // set the y axis limits
        int yminVal = 0;
        bundle.putInt("yorigin", yminVal);
        int ymaxVal = max_satellites + 1;
        bundle.putInt("yLimit", ymaxVal);

        // find the average number of satellites
        if (raw_satellites != null && raw_satellites.length > 0) {
            float avg_satellite = UtilsMisc.getSum(raw_satellites) / raw_satellites.length;
            float[] avg_satellites = new float[raw_satellites.length];
            Arrays.fill(avg_satellites, avg_satellite);
            bundle.putFloatArray("avg_ydata", avg_satellites);
        }

        //plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the battery level
    public void battery_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Battery Level vs. Time");
        bundle.putString("lineTitle1", "Raw Battery Level");
        bundle.putString("lineTitle2", "");
        bundle.putString("lineTitle3", "");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Percentage");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        //ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id);
        bundle.putInt("yorigin", 0);
        bundle.putInt("yLimit", 110);

        //ArrayList<DetailProvider.Detail> details = dataExtractor.getData(trip_id, -1);
        plotterData.setDataIndex(Constants.DATA_BATTERY);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, true);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_battery = bundle1.getFloatArray("ydata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_battery);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the accuracy readings
    public void accuracy_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Accuracy vs. Time");
        bundle.putString("lineTitle1", "Raw Accuracy");
        bundle.putString("lineTitle2", "");
        bundle.putString("lineTitle3", "Average Accuracy");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Accuracy (meters)");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        plotterData.setDataIndex(Constants.DATA_ACCURACY);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, true);
        int[] raw_times = bundle1.getIntArray("times");
        float[] raw_accuracies = bundle1.getFloatArray("ydata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putFloatArray("raw_ydata", raw_accuracies);
        /*
        float[] min_max_Accuracy = UtilsMisc.getMinMax(raw_accuracies);
        float min_accuracy = min_max_Accuracy[0]; float max_accuracy = min_max_Accuracy[1];

        float range = max_accuracy - min_accuracy;
        range = (Float.compare(range, 0.0f) == 0)? MIN_FLOAT_RANGE : range;
        int yminVal = Math.round(min_accuracy + UtilsMisc.calcRange(range, -0.10f));
        yminVal = (yminVal < 0)? 0: yminVal;
        bundle.putInt("yorigin", yminVal);
        int ymaxVal = Math.round(max_accuracy + UtilsMisc.calcRange(range, 0.10f));
        bundle.putInt("yLimit", ymaxVal);
*/
        // find the average accuracy
        if (raw_accuracies != null && raw_accuracies.length > 0) {
            float avg_accuracy = UtilsMisc.getSum(raw_accuracies) / raw_accuracies.length;
            float[] avg_accuracies = new float[raw_accuracies.length];
            Arrays.fill(avg_accuracies, avg_accuracy);
            bundle.putFloatArray("avg_ydata", avg_accuracies);
        }
        //smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the time per readings
    public void time_per_reading_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Time per Reading vs. Time");
        bundle.putString("lineTitle1", "Raw Time per Reading");
        bundle.putString("lineTitle2", "");
        bundle.putString("lineTitle3", "Average Time per Reading");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Per Reading Time (seconds)");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        plotterData.setDataIndex(Constants.DATA_TIME_PER_READING);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, true);
        int[] raw_times = bundle1.getIntArray("times");
        int[] raw_time_per_reading = bundle1.getIntArray("idata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putIntArray("raw_ydata", raw_time_per_reading);
        int[] min_max_time_per_reading = UtilsMisc.getMinMax(raw_time_per_reading);
        int max_time_per_reading = min_max_time_per_reading[1];

        // set the y axis limits
        int yminVal = 0;
        bundle.putInt("yorigin", yminVal);
        int ymaxVal = max_time_per_reading + 1;
        bundle.putInt("yLimit", ymaxVal);

        // find the average number of satellites
        if (raw_time_per_reading != null && raw_time_per_reading.length > 0) {
            float avg_time_per_reading = UtilsMisc.getSum(raw_time_per_reading) / raw_time_per_reading.length;
            float[] avg_times = new float[raw_time_per_reading.length];
            Arrays.fill(avg_times, avg_time_per_reading);
            bundle.putFloatArray("avg_ydata", avg_times);
        }

        //plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the distance per readings
    public void distance_per_reading_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Distance per Reading vs. Time");
        bundle.putString("lineTitle1", "Raw Distance per Reading");
        bundle.putString("lineTitle2", "");
        bundle.putString("lineTitle3", "Average Distance per Reading");
        bundle.putString("xLabel", "Time (seconds)");
        bundle.putString("yLabel", "Distance (meters)");

        // extract from summary
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);
        long start_time = summary.getStart_time();
        int xfactor = UtilsDate.getTimeSeconds(summary.getEnd_time() - start_time);
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", UtilsMisc.calcRange(xfactor, 1.1f));

        plotterData.setDataIndex(Constants.DATA_DISTANCE_PER_READING);
        Bundle bundle1 = plotterData.getData(trip_id, Constants.ALL_SEGMENTS, true);
        int[] raw_times = bundle1.getIntArray("times");
        int[] raw_distance_per_reading = bundle1.getIntArray("idata");

        // pass the raw values
        bundle.putIntArray("raw_xdata", raw_times);
        bundle.putIntArray("raw_ydata", raw_distance_per_reading);
        int[] min_max_distance_per_reading = UtilsMisc.getMinMax(raw_distance_per_reading);
        int max_distance_per_reading = min_max_distance_per_reading[1];

        // set the y axis limits
        int yminVal = 0;
        bundle.putInt("yorigin", yminVal);
        int ymaxVal = max_distance_per_reading + 1;
        bundle.putInt("yLimit", ymaxVal);

        // find the average number of satellites
        if (raw_distance_per_reading != null && raw_distance_per_reading.length > 0) {
            float avg_time_per_reading = UtilsMisc.getSum(raw_distance_per_reading) / raw_distance_per_reading.length;
            float[] avg_times = new float[raw_distance_per_reading.length];
            Arrays.fill(avg_times, avg_time_per_reading);
            bundle.putFloatArray("avg_ydata", avg_times);
        }

        //plotterData.smooth_plot(bundle, details, start_time, false);
        plotterData.buildPlot(bundle);
    }

    // build a plot of the pace per kilometer
    public void pace_plot() {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", "Time vs. Kilometer");
        bundle.putString("lineTitle1", "Pace per Kilometer");
        bundle.putString("lineTitle2", "");
        bundle.putString("lineTitle3", "Average Pace");
        bundle.putString("xLabel", "Kilometers");
        bundle.putString("yLabel", "Pace per Kilometer (seconds)");

        // extract the detail records
        ArrayList<DetailProvider.Detail> details = plotterData.getDetails(trip_id, Constants.ALL_SEGMENTS);
        final int OTHER_SPEED_LIMIT = 40;     // speed limit for other categories

        // get the distances  and timestamps from the detail records
        double[] distance  = new double[details.size()];
        long[] timestamp = new long[details.size()];
        distance[0] = 0; timestamp[0] = details.get(0).getTimestamp();
        boolean fast_trip = false;
        for (int i = 1; i < details.size(); i++) {
            distance[i] = distance[i-1] + UtilsMap.getKmDistance(details.get(i-1).getLat(), details.get(i-1).getLon(),
                    details.get(i).getLat(), details.get(i).getLon() );
            timestamp[i] = details.get(i).getTimestamp();
            double speed = (distance[i] - distance[i-1]) / (timestamp[i] - timestamp[i-1]);
            if (distance[i] - distance[i-1] > 1.0 && speed > OTHER_SPEED_LIMIT)
                fast_trip = true;    // flag a trip with more than 1 km. between readings
        }

        // set the range for the x axis
        int distanceLimit = (int) Math.round(Math.ceil(distance[distance.length-1]));
        bundle.putInt("xorigin", 0);
        bundle.putInt("xLimit", distanceLimit);

        // find the timestamp nearest to a whole kilometer number
        //int km_marker = (distance[details.size()-1] > 500)? 10: 1;
        int km_marker = 1;
        ArrayList<Integer> kilometers = new ArrayList<>();
        ArrayList<Long> km_times = new ArrayList<>();
        for (int i = 1; i < distance.length; i++) {
            if (fast_trip) {
                if (distance[i] - distance[i-1] > 1.0) {
                    int startKm = (int) UtilsMisc.round(distance[i-1], 0);
                    int endKm = (int) UtilsMisc.round(distance[i], 0);
                    long timePerKm = (timestamp[i] - timestamp[i-1]) / (endKm - startKm);
                    for (int j = startKm; j <= endKm; j++) {
                        km_times.add(timestamp[i-1] + (j - startKm) * timePerKm);
                        kilometers.add(j);
                    }
                }
                continue;
            }
            if (distance[i] > km_marker) {
                // speed near the km marker  (kms per millisecond)
                double speed = (distance[i] - distance[i-1]) / (timestamp[i] - timestamp[i-1]);
                // distance to the km marker (kms.)
                double dist_km_marker = km_marker - distance[i-1];
                // time to the km marker from closest marker (milliseconds)
                long time_km_marker = Math.round( (dist_km_marker / speed));
                // update the kilometers and times per kilometer arrays
                km_times.add(timestamp[i-1] + time_km_marker);
                kilometers.add(km_marker);
                km_marker++;
            }
        }

        // fix the km times array: compute the elapsed time
        for (int i = km_times.size()-1; i >= 0; i--) {
            if (i == 0)
                km_times.set(i, (long) UtilsDate.getTimeSeconds(km_times.get(i) - timestamp[0]));
            else {
                long duration = km_times.get(i) - km_times.get(i-1);
                km_times.set(i, (long) UtilsDate.getTimeSeconds(duration));
            }
        }

        int[] raw_times = UtilsMisc.convertToIntegers(km_times);
       // int[] minMax = UtilsMisc.getMinMax(raw_times);
        int[] raw_kms = UtilsMisc.convertIntegers(kilometers);

        // set the range for the y axis
      //  bundle.putInt("yorigin", minMax[0] + UtilsMisc.calcRange(minMax[0], -0.10f) );
      //  bundle.putInt("yLimit", minMax[1] + UtilsMisc.calcRange(minMax[1], 0.10f) );

        bundle.putIntArray("raw_ydata", raw_times);
        bundle.putIntArray("raw_xdata", raw_kms);

        float avg_time = UtilsMisc.getSum(raw_times) / raw_times.length;
        float[] avg_times = new float[raw_times.length];
        Arrays.fill(avg_times, avg_time);
        bundle.putIntArray("avg_xdata", raw_kms);
        bundle.putFloatArray("avg_ydata", avg_times);

        plotterData.setDataIndex(Constants.DATA_PACE);
        plotterData.buildPlot(bundle);
    }

    public void handleTimer(final boolean positiveIncr) {
        if ((NUM_SEGMENTS == 1) || (NUM_SEGMENTS > Constants.MAX_SEGMENTS)) {
            plotterData.buildTimerPlot();
        } else {
            cancelTimer();
            timer = new Timer();
            final long DELAY = 0;
            final long PERIOD = 3000;
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Object[] param1 = {currentSegment, positiveIncr};
                    new DrawSpeeds().execute(param1);  // draw a specific segment
                }
            }, DELAY, PERIOD);
        }
    }

    private int get_avg_size(int num_elements) {
        if (num_elements < 10) return num_elements;
        if (num_elements < 20) return 10;
        if (num_elements < 30) return 15;
        if (num_elements < 40) return 20;
        if (num_elements < 50) return 20;
        if (num_elements < 100) return 30;
        if (num_elements < 200) return 40;
        return 50;
    }

    private class DrawSpeeds extends AsyncTask<Object, Integer, String> {
        boolean positiveIncr;
        int segment;

        @Override
        protected String doInBackground(Object... params) {
            segment = (int) params[0];
            positiveIncr = (boolean) params[1];
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            incCurrentSegment(positiveIncr, 1);
            plotterData.setCurrentSegment(segment);
            plotterData.buildTimerPlot();
        }
    }

    public void cancelTimer() {
        if (timer != null)
            timer.cancel();
    }

    private void incCurrentSegment(boolean positiveIncr, int increment) {
        currentSegment = (positiveIncr)? currentSegment + increment: currentSegment - increment;
        if (positiveIncr) {
            if (currentSegment == 0)
                currentSegment = 1;
            else if (currentSegment > NUM_SEGMENTS)
                currentSegment = 1;
        } else {
            if (currentSegment <= 0)
                currentSegment = NUM_SEGMENTS;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelTimer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelTimer();
    }

}