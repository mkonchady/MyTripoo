package org.mkonchady.mytripoo.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.Utils;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.activities.PlotInfoActivity;
import org.mkonchady.mytripoo.activities.TripPlotActivity;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.views.BarMarkerView;
import org.mkonchady.mytripoo.views.LineMarkerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

/**
 * Extract / Plot data from the details table and return to caller
 */

public class PlotterData implements
        OnChartGestureListener, OnChartValueSelectedListener {
    Context context;
    private final TripPlotActivity tripPlotActivity;
    ArrayList<DetailProvider.Detail> details = null;
    int trip_id;
    private int dataIndex = 0;
    public Bundle bundle = null;
   // private Paint transparentPaint = null;
   //private LineChart lChart;

    private final int NUM_SEGMENTS;

    private final int Y_LABELS = 11;
    private int currentSegment = 1;
    private final int ANIMATE_TIME = 1500; // milliseconds

    DetailDB detailDB;
    SummaryDB summaryDB;
    //final String TAG = "PlotterData";

    public PlotterData(Context context, int trip_id) {
        this.context = context;
        this.tripPlotActivity = (TripPlotActivity) context;
        this.trip_id = trip_id;
      //  transparentPaint = new Paint(); transparentPaint.setAlpha(0);
        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
        NUM_SEGMENTS = summaryDB.getSegmentCount(context, trip_id);
    }

    // get the list of details
    public ArrayList<DetailProvider.Detail> getDetails(int trip_id, int segment) {
        details = detailDB.getDetails(context, trip_id, segment);
        return details;
    }

    //*-----------------------------------------------------------------
    // return a bundle containing the times and requested data type
    //*-----------------------------------------------------------------
    public Bundle getData(int trip_id, int segment, boolean refresh) {
        // get details if it has not been retrieved
        if (refresh || (details == null))
            details = detailDB.getDetails(context, trip_id, segment);
        long start_time = details.get(0).getTimestamp();
        int[] times = new int[details.size()];
        int[] distances = new int[details.size()];
        float[] ydata = new float[details.size()];
        int[] idata = new int[details.size()];
        float[] cum_distance = new float[details.size()];
        float[] altitude = new float[details.size()];
        Arrays.fill(cum_distance, 0.0f);
        for (int i = 0; i < details.size(); i++) {
            DetailProvider.Detail detail = details.get(i);
            times[i] = UtilsDate.getTimeSeconds(detail.getTimestamp() - start_time);
            if (i > 0 && times[i] == times[i-1])
                times[i] = times[i-1] + 1;
            altitude[i] = detail.getAltitude();
            distances[i] = 0;
            if (i > 0) {
                int lat1 = details.get(i-1).getLat(); int lon1 = details.get(i-1).getLon();
                int lat2 = detail.getLat(); int lon2 = detail.getLon();
                double distance = UtilsMap.getMeterDistance(lat1, lon1, lat2, lon2);
                distances[i] = (int)  distance;
                cum_distance[i] = (float) (cum_distance[i-1] +  distance);
            }

            switch (dataIndex) {
                case Constants.DATA_ACCURACY: ydata[i] = detail.getAccuracy(); break;
                case Constants.DATA_ALTITUDE: ydata[i] = detail.getAltitude(); break;
                case Constants.DATA_BATTERY:  ydata[i] = detail.getBattery(); break;
                case Constants.DATA_GRADIENT: ydata[i] = detail.getGradient(); break;
                case Constants.DATA_SATINFO:  ydata[i] = detail.getSatinfo(); break;
                case Constants.DATA_SATELIITES: idata[i] = detail.getSatellites(); break;
                case Constants.DATA_SPEED: ydata[i] = detail.getSpeed(); break;
                case Constants.DATA_POWER: ydata[i] = (float) detail.getPower(); break;
                case Constants.DATA_VAM: ydata[i] = (i > 0) ? ((altitude[i] - altitude[i-1]) * 3600) / (times[i] - times[i-1]): 0; break;
                case Constants.DATA_TIME_PER_READING: idata[i] = (i > 0) ? times[i] - times[i-1]: 0; break;
                case Constants.DATA_DISTANCE_PER_READING: idata[i] = (i > 0) ? distances[i]: 0; break;
                case Constants.DATA_HEAD_WIND: ydata[i] = (float) UtilsMap.mpsTokmph(
                        UtilsWind.get_head_wind(  detail.getWindSpeed(), detail.getWindDeg(),
                                UtilsMap.kmphTomps(detail.getSpeed()),   UtilsMap.bearingToDegrees(detail.getBearing()) )); break;
                default: break;
            }
        }

        Bundle bundle = new Bundle();
        bundle.putIntArray("times", times);
        bundle.putFloatArray("ydata", ydata);
        bundle.putIntArray("idata", idata);
        bundle.putFloatArray("cum_distance", cum_distance);
        return bundle;
    }

    //*------------------------------------------------------------------
    // place the smoothed data in the bundle
    //*------------------------------------------------------------------
    public void smooth_plot(Bundle bundle, ArrayList<DetailProvider.Detail> details,
                     long start_time, boolean spliceCurve) {

        // break up the list into chunks of NUM_POINTS based on the number of curves
        List<List<DetailProvider.Detail>> partitions = new ArrayList<>();

        int segments = summaryDB.getSegmentCount(context, trip_id);
        boolean multipleSegments = (segments > 1);
        if ((1 < segments) && (segments <= Constants.MAX_SEGMENTS)) {
            for (int segment = 1; segment <= segments; segment++)
                partitions.add(detailDB.getDetails(context, trip_id, segment));
        } else {
            int NUM_CURVES = (int) Math.round(Math.log10(details.size())) * 2;
            int NUM_POINTS = (NUM_CURVES == 0) ? 1 : details.size() / NUM_CURVES;
            for (int i = 0; i < details.size(); i += NUM_POINTS)
                partitions.add(details.subList(i, Math.min(i + NUM_POINTS, details.size())));
        }

        // for each partition, get 4 control points
        ArrayList<UtilsMisc.XYPoint> fourPoints = new ArrayList<>();
        int color = 0;
        for (int i = 0; i < partitions.size(); i++) {
            //  verify that there are at least four points
            if (partitions.get(i).size() < 4) continue;

            // get the subset of details
            List<DetailProvider.Detail> subDetails = partitions.get(i);
            UtilsMisc.XYPoint[] p = new UtilsMisc.XYPoint[4];

            // set the first and last control points
            double x, y;
            int first = (i == 0) ? 1 : 0;
            x = (subDetails.get(first).getTimestamp() - start_time) / 1000.0;
            //y = (subDetails.get(first).getSpeed());
            y = getYCoordinate(subDetails, first);
            p[0] = new UtilsMisc.XYPoint(x, y, color);

            int last = subDetails.size() - 1;
            x = (subDetails.get(last).getTimestamp() - start_time) / 1000.0;
            //y = (subDetails.get(last).getSpeed());
            y = getYCoordinate(subDetails, last);
            p[3] = new UtilsMisc.XYPoint(x, y, color);

            // find the min. and max values for the second and third control points
            // in between the first and last control points
            int min_index = 0;
            int max_index = 0;
            float max_sub_value = 0.0f;
            float min_sub_value = Constants.LARGE_FLOAT;
            for (int j = 1; j < subDetails.size() - 1; j++) {
                float value = getYCoordinate(subDetails, j);
                if (value < min_sub_value) {
                    min_index = j;
                    min_sub_value = value;
                }
                if (value > max_sub_value) {
                    max_index = j;
                    max_sub_value = value;
                }
            }
            double xmin = (subDetails.get(min_index).getTimestamp() - start_time) / 1000.0;
            //double ymin = (subDetails.get(min_index).getSpeed());
            double ymin = getYCoordinate(subDetails, min_index);
            double xmax = (subDetails.get(max_index).getTimestamp() - start_time) / 1000.0;
            double ymax = getYCoordinate(subDetails, max_index);
            //double ymax = (subDetails.get(max_index).getSpeed());

            if (max_index > min_index) {
                p[1] = new UtilsMisc.XYPoint(xmin, ymin, color);
                p[2] = new UtilsMisc.XYPoint(xmax, ymax, color);
            } else {
                p[2] = new UtilsMisc.XYPoint(xmin, ymin, color);
                p[1] = new UtilsMisc.XYPoint(xmax, ymax, color);
            }

            fourPoints.addAll(Arrays.asList(p));            // build the list of main points
            if (multipleSegments) color = ++color % 3;      // cycle through 3 colors
        }

        // build the list of control points,
        ArrayList<UtilsMisc.XYPoint> controlPoints = new ArrayList<>();
        for (int i = 0; i < fourPoints.size(); i++) {
            controlPoints.add(fourPoints.get(i));
            // in a spliced curve, add a center control point for every alternate pair of points
            if (spliceCurve && (i % 2 == 0) && (i > 0) && (i + 3) < fourPoints.size()) {
                controlPoints.add(UtilsMisc.center(fourPoints.get(i), fourPoints.get(i + 1)));
            }
        }

        // for a spliced curve do the 4 point bezier interpolation from 0..3, 3..6, 6..9
        // otherwise, do the 4 point bezier interpolation from 0..3, 4..7, 8..11
        UtilsMisc.XYPoint p1, p2, p3, p4;
        ArrayList<UtilsMisc.XYPoint> allPoints = new ArrayList<>();
        int INCREMENT = spliceCurve ? 3 : 4;
        for (int i = 0; i < controlPoints.size(); i += INCREMENT) {
            if ((i + 3) < controlPoints.size()) {
                p1 = controlPoints.get(i);
                p2 = controlPoints.get(i + 1);
                p3 = controlPoints.get(i + 2);
                p4 = controlPoints.get(i + 3);
                allPoints.addAll(UtilsMisc.BezierInterpolate(p1, p2, p3, p4));
            }
        }

        // remove duplicate x coordinates
        ArrayList<Integer> times_list = new ArrayList<>();
        ArrayList<Float> values_list = new ArrayList<>();
        Hashtable<Integer, Boolean> seenInt = new Hashtable<>();
        for (int i = 0; i < allPoints.size(); i++) {
            int xloc = (int) Math.round(allPoints.get(i).getX());
            if (seenInt.containsKey(xloc)) continue;
            seenInt.put(xloc, true);
            times_list.add(xloc);
            values_list.add((float) allPoints.get(i).getY());
        }

        // convert array list to array
        int[] smooth_times = UtilsMisc.convertIntegers(times_list);
        float[] smooth_values = UtilsMisc.convertFloats(values_list);

        bundle.putIntArray("smooth_xdata", smooth_times);
        bundle.putFloatArray("smooth_ydata", smooth_values);
    }

    // called by smooth plot
    private float getYCoordinate(List<DetailProvider.Detail> details, int index) {
        if (dataIndex == Constants.DATA_VAM) {
            float vam = 0;
            if (index > 0) {
                DetailProvider.Detail prev_detail = details.get(index - 1);
                DetailProvider.Detail curr_detail = details.get(index);
                float alt_diff = curr_detail.getAltitude() - prev_detail.getAltitude();
                int time_diff = UtilsDate.getTimeSeconds(curr_detail.getTimestamp() - prev_detail.getTimestamp());
                if (time_diff == 0) time_diff = 1;  // time difference must be at least one second
                vam = (alt_diff * 3600) / time_diff;
            }
            return vam;
        } else if (dataIndex == Constants.DATA_HEAD_WIND) {
            return (float) UtilsMap.mpsTokmph(UtilsWind.get_head_wind(  details.get(index).getWindSpeed(),  details.get(index).getWindDeg(),
                    UtilsMap.kmphTomps(details.get(index).getSpeed()),   UtilsMap.bearingToDegrees(details.get(index).getBearing()) ));
        }
        return (dataIndex == Constants.DATA_SPEED) ? details.get(index).getSpeed() :
                (dataIndex == Constants.DATA_ALTITUDE) ? details.get(index).getAltitude() :
                (dataIndex == Constants.DATA_GRADIENT) ? details.get(index).getGradient() :
                (dataIndex == Constants.DATA_SATINFO) ? details.get(index).getSatinfo() :
                (dataIndex == Constants.DATA_POWER) ? (float) details.get(index).getPower() :
                (dataIndex == Constants.DATA_SATELIITES) ? details.get(index).getSatellites() : 0.0f;
    }

    // build an mpandroid plot using the parameters passed in the bundle
    public void buildPlot(final Bundle bundle) {
        final int X_LABELS = 5;

        setBundle(bundle);
        // set the x axis limits
        int xlimit = bundle.getInt("xLimit");
        int xorigin = bundle.getInt("xorigin");
        while (((xlimit - xorigin) % (X_LABELS - 1)) != 0) xlimit++;

        // set the y axis limits
        int ylimit = bundle.getInt("yLimit");
        int yorigin = bundle.getInt("yorigin");

        boolean toggle = true;
        while (((ylimit - yorigin) % (Y_LABELS - 1)) != 0) {
            if (toggle) ylimit++;
            else {
                if (dataIndex == Constants.DATA_GRADIENT || dataIndex == Constants.DATA_VAM)
                    yorigin--;
                else {
                    if (yorigin > 0) yorigin--;
                }
            }
            toggle = !toggle;
        }

        // set the titles and get the data
        String plotTitle = bundle.getString("yLabel") + " vs. " + bundle.getString("xLabel");
        int[] raw_xdata = bundle.getIntArray("raw_xdata");
        float[] raw_ydata = null;

        // optional average data
        int[] average_xdata = bundle.getIntArray("raw_xdata");
        float[] average_ydata = bundle.getFloatArray("avg_ydata");

        // optional smooth data
        int[] smooth_xdata = bundle.getIntArray("smooth_xdata");
        float[] smooth_ydata = bundle.getFloatArray("smooth_ydata");

        float[] min_max;
        switch (dataIndex) {
            case Constants.DATA_ACCURACY:
            case Constants.DATA_ALTITUDE:
            case Constants.DATA_SPEED:
            case Constants.DATA_GRADIENT:
            case Constants.DATA_VAM:
            case Constants.DATA_HEAD_WIND:
            case Constants.DATA_SATINFO:
            case Constants.DATA_POWER:
            case Constants.DATA_BATTERY:
                raw_ydata = bundle.getFloatArray("raw_ydata");
                bundle.putInt("num_readings", raw_ydata != null ? raw_ydata.length : 0);
                raw_ydata = UtilsMisc.removeOutliers(raw_ydata, 5);
                min_max = UtilsMisc.getMinMax(raw_ydata);
                float range = min_max[1] - min_max[0];
                ylimit  = Math.round(min_max[1] + UtilsMisc.calcRange(range, 0.10f));
                yorigin = Math.round(min_max[0] - UtilsMisc.calcRange(range, 0.10f));
                if (ylimit == yorigin) {
                    ylimit++; yorigin--;
                }
           //     raw_xySeries = new SimpleXYSeries(UtilsMisc.toNumber(raw_xdata), UtilsMisc.toNumber(raw_ydata), lineTitle1);
                if (dataIndex == Constants.DATA_BATTERY) {
                    ylimit = 100; yorigin = 0;
                    bundle.putString("min_battery", UtilsMisc.formatFloat(min_max[0], 2));
                    bundle.putString("max_battery", UtilsMisc.formatFloat(min_max[1], 2));
                    bundle.putString("used_battery", UtilsMisc.formatFloat(min_max[1]-min_max[0], 2));
                    bundle.putBoolean("battery_data", true);
                }

                break;
            case Constants.DATA_SATELIITES:
            case Constants.DATA_TIME_PER_READING:
            case Constants.DATA_DISTANCE_PER_READING:
            case Constants.DATA_PACE:
            case Constants.DATA_HISTO_POWER:
                int[] raw_ydata1 = bundle.getIntArray("raw_ydata");
                raw_ydata1 = UtilsMisc.removeOutliers(raw_ydata1, 2);
                raw_ydata = UtilsMisc.toFloat(raw_ydata1);
                min_max = UtilsMisc.getMinMax(raw_ydata);
                range = min_max[1] - min_max[0];
                ylimit  = Math.round(min_max[1] + UtilsMisc.calcRange(range, 0.10f));
                yorigin = Math.round(min_max[0] - UtilsMisc.calcRange(range, 0.10f));
                bundle.putInt("num_readings", raw_ydata1 != null ? raw_ydata1.length : 0);
                if (dataIndex == Constants.DATA_PACE) {
                    bundle.putString("min_pace", UtilsDate.getTimeDurationHHMMSS((long) min_max[0] * 1000, false));
                    bundle.putString("max_pace", UtilsDate.getTimeDurationHHMMSS((long) min_max[1] * 1000, false));
                    bundle.putString("avg_pace", UtilsDate.getTimeDurationHHMMSS((long) average_ydata[0] * 1000, false));
                    bundle.putBoolean("pace_data", true);
                }
                break;
            default:
                break;
        }

        // set data
        int layoutResId, titleResId;
        if (dataIndex == Constants.DATA_PACE || dataIndex == Constants.DATA_HISTO_POWER) {
            layoutResId = R.layout.activity_barchart;
            titleResId = R.id.barTitleView;
            setBarChart(layoutResId, titleResId, plotTitle, xorigin, xlimit, ylimit, yorigin, raw_xdata, raw_ydata);
        } else {
            layoutResId = R.layout.activity_linechart;
            titleResId = R.id.lineTitleView;
            setLineChart(layoutResId, titleResId, plotTitle, xorigin, xlimit, ylimit, yorigin,
                    raw_xdata, raw_ydata, average_xdata, average_ydata, smooth_xdata, smooth_ydata);

        }

    }

    // build the line chars from the passed parameters
    private void setLineChart(int layoutResId, int titleResId, String plotTitle, int xorigin,
                               int xlimit, float ylimit, float yorigin, int[] raw_xdata, float[] raw_ydata,
                               int[] average_xdata, float[] average_ydata, int[] smooth_xdata, float[] smooth_ydata) {

        LineChart lChart;
        tripPlotActivity.setContentView(layoutResId);
        TextView titleView = tripPlotActivity.findViewById(titleResId);
        titleView.setText(plotTitle);

        lChart = tripPlotActivity.findViewById(R.id.lineChart);
        lChart.setOnChartGestureListener(this);
        lChart.setOnChartValueSelectedListener(this);
        lChart.setDrawGridBackground(false);

        lChart.getDescription().setEnabled(false);
        lChart.setTouchEnabled(true);
        lChart.setDragEnabled(true);
        lChart.setScaleEnabled(true);
        lChart.setPinchZoom(true);
        lChart.setBackgroundColor(Color.WHITE);

        LineMarkerView lmv = new LineMarkerView(tripPlotActivity, R.layout.plot_marker);
        lmv.setChartView(lChart);
        lChart.setMarker(lmv);

        // set up axes
        YAxis leftAxis = lChart.getAxisLeft();
        leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
        leftAxis.setAxisMaximum(ylimit);
        leftAxis.setAxisMinimum(yorigin);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawZeroLine(false);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return UtilsDate.getDecimalFormat(value);
                //return Utils.formatNumber(value,1,true);
            }
        });

        // set up x axis
        XAxis bottomAxis = lChart.getXAxis();
        bottomAxis.enableGridDashedLine(10f, 10f, 0f);
        bottomAxis.setDrawLabels(true);

        lChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        bottomAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
        bottomAxis.setAxisMaximum(xlimit);
        bottomAxis.setAxisMinimum(xorigin);
        bottomAxis.enableGridDashedLine(10f, 10f, 0f);
        bottomAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return UtilsDate.getTimeDurationHHMMSS((long) value * 1000, false);
                //return Utils.formatNumber(value,1,true);
            }
        });

        lChart.setData(setLineData(raw_xdata, raw_ydata, average_xdata, average_ydata, smooth_xdata, smooth_ydata));
        lChart.animateX(ANIMATE_TIME);
        Legend l = lChart.getLegend();
        l.setForm(Legend.LegendForm.LINE);
    }

    // build the linedata for the line chart
    private LineData setLineData(int[] raw_xdata, float[] raw_ydata, int[] average_xdata, float[] average_ydata,
                                 int[] smooth_xdata, float[] smooth_ydata) {

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();

        // create a raw dataset
        ArrayList<Entry> values = new ArrayList<>();
        for (int i = 0; i < raw_xdata.length; i++)
            values.add(new Entry(raw_xdata[i], raw_ydata[i]));
        LineDataSet rawData = new LineDataSet(values, "Raw");
        rawData.setDrawIcons(false);
        rawData.setDrawCircles(false);
        rawData.setDrawValues(false);
        rawData.disableDashedLine();
        rawData.setLineWidth(1f);
        if (Utils.getSDKInt() >= 18 && average_ydata == null && smooth_ydata == null) {
            Drawable drawable = ContextCompat.getDrawable(tripPlotActivity.context, R.drawable.fade_raw);
            rawData.setDrawFilled(true);
            rawData.setFillDrawable(drawable);
            rawData.setColor(tripPlotActivity.getResources().getColor(R.color.DeepSkyBlue));
        }
        else {
            rawData.setColor(tripPlotActivity.getResources().getColor(R.color.SkyBlue));
        }
        dataSets.add(rawData);


        if (average_ydata != null) {
            values = new ArrayList<>();
            for (int i = 0; i < average_xdata.length; i++)
                values.add(new Entry(average_xdata[i], average_ydata[i]));
            LineDataSet averageData = new LineDataSet(values, "Average");
            averageData.setDrawIcons(false);
            averageData.setDrawCircles(false);
            averageData.setDrawValues(false);
            averageData.disableDashedLine();
            averageData.setColor(tripPlotActivity.getResources().getColor(R.color.Maroon));
            averageData.setLineWidth(2f);
            dataSets.add(averageData);
        }

        if (smooth_ydata != null) {
            values = new ArrayList<>();
            for (int i = 0; i < smooth_xdata.length; i++)
                values.add(new Entry(smooth_xdata[i], smooth_ydata[i]));
            LineDataSet smoothData = new LineDataSet(values, "Smoothed");
            smoothData.setDrawIcons(false);
            smoothData.setDrawCircles(false);
            smoothData.setDrawValues(false);
            smoothData.disableDashedLine();
            smoothData.setColor(tripPlotActivity.getResources().getColor(R.color.MidnightBlue));
            smoothData.setLineWidth(3f);
            dataSets.add(smoothData);
        }

        // create a data object with the datasets
        return new LineData(dataSets);
    }


    private void setBarChart(int layoutResId, int titleResId, String plotTitle, int xorigin,
                                   int xlimit, int ylimit, int yorigin, int[] raw_xdata,
                                   float[] raw_ydata) {
        BarChart bChart;
        TextView titleView;

        tripPlotActivity.setContentView(layoutResId);
        titleView = tripPlotActivity.findViewById(titleResId);

        bChart = tripPlotActivity.findViewById(R.id.barChart);
        bChart.setOnChartGestureListener(this);
        bChart.setOnChartValueSelectedListener(this);
        bChart.getDescription().setEnabled(false);
        bChart.setTouchEnabled(true);
        bChart.setDragEnabled(true);
        bChart.setScaleEnabled(true);
        bChart.setPinchZoom(true);
        bChart.setBackgroundColor(Color.WHITE);

        BarMarkerView bmv = new BarMarkerView(tripPlotActivity, R.layout.plot_marker);
        bmv.setChartView(bChart);
        bChart.setMarker(bmv);

        // set up y axis
        YAxis leftAxis = bChart.getAxisLeft();
        leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
        leftAxis.setAxisMaximum(ylimit);
        leftAxis.setAxisMinimum(yorigin);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawZeroLine(false);
        if (dataIndex == Constants.DATA_PACE)
            leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return UtilsDate.getTimeDurationHHMMSS((long) value * 1000, false);
                }
            });
        else
            leftAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return UtilsDate.getDecimalFormat(value);
                }
            });

        // set up x axis
        XAxis bottomAxis = bChart.getXAxis();
        bottomAxis.enableGridDashedLine(10f, 10f, 0f);
        bottomAxis.setDrawLabels(true);
        bottomAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
        bottomAxis.setAxisMaximum(xlimit);
        bottomAxis.setAxisMinimum(xorigin);
        bottomAxis.enableGridDashedLine(10f, 10f, 0f);
        bottomAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return UtilsDate.getDecimalFormat(value);
            }
        });

        // set data
        bChart.getAxisRight().setEnabled(false);
        bChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        bChart.setData(setBarData(raw_xdata, raw_ydata));
        titleView.setText(plotTitle);

        bChart.animateX(ANIMATE_TIME);
        Legend l = bChart.getLegend();
        l.setEnabled(false);
    }

    private BarData setBarData(int[] raw_xdata, float[] raw_ydata) {

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        BarDataSet rawData;

        ArrayList<BarEntry> values = new ArrayList<>();
        for (int i = 0; i < raw_xdata.length; i++) {
            float val = raw_ydata[i];
            values.add(new BarEntry(raw_xdata[i], val));
        }

        // create a dataset and give it a type
        rawData = new BarDataSet(values, "");
        rawData.setDrawIcons(false);
        rawData.setDrawValues(false);
        if (dataIndex == Constants.DATA_PACE)
            rawData.setColor(tripPlotActivity.getResources().getColor(R.color.darkblue));
        else
            rawData.setColor(tripPlotActivity.getResources().getColor(R.color.darkblue));
        dataSets.add(rawData); // add the datasets

        // create a data object with the datasets
        return new BarData(dataSets);
    }

    // build an android plot using the parameters passed in the bundle
    public void buildTimerPlot () {
        String plotTitle = "Segment " + currentSegment + " Speed vs. Time";
        int[] raw_xdata = null, average_xdata = null;
        float[] raw_ydata = null, average_ydata = null;
        for (int segment = 1; segment <= NUM_SEGMENTS; segment++) {
            if (segment == currentSegment) {
                average_ydata = bundle.getFloatArray("avg_ydata" + currentSegment);
                average_xdata = bundle.getIntArray("raw_xdata" + currentSegment);
                raw_xdata = bundle.getIntArray("raw_xdata" + segment);
                raw_ydata = bundle.getFloatArray("raw_ydata" + segment);
                bundle.putInt("num_readings", raw_ydata != null? raw_ydata.length: 0);
            }
        }
        final int xlimit = bundle.getInt("xLimit");
        final int xorigin = bundle.getInt("xorigin");

        // fix the range of y values for 10 lines
        int ylimit = bundle.getInt("yLimit");
        int yorigin = bundle.getInt("yorigin");
        boolean toggle = true;
        while ( ( (ylimit - yorigin) % (Y_LABELS-1)) != 0) {
            if (toggle) ylimit++;
            else { if (yorigin > 0) yorigin--;
            }
            toggle = !toggle;
        }

        int layoutResId = R.layout.activity_linechart;
        int titleResId = R.id.lineTitleView;
        setLineChart(layoutResId, titleResId, plotTitle, xorigin, xlimit, ylimit, yorigin,
                raw_xdata, raw_ydata, average_xdata, average_ydata, null, null);

    }

    public void setDataIndex(int dataIndex) {
        this.dataIndex = dataIndex;
    }
    public void setBundle(Bundle bundle) {
        this.bundle = bundle;
    }
    public void setCurrentSegment(int segment) {
        this.currentSegment = segment;
    }

    @Override
    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
    //    Log.i("Gesture", "START, x: " + me.getX() + ", y: " + me.getY());
    }

    @Override
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
      //  Log.i("Gesture", "END, lastGesture: " + lastPerformedGesture);
        // un-highlight values after the gesture is finished and no single-tap
       // if(lastPerformedGesture != ChartTouchListener.ChartGesture.SINGLE_TAP)
       //     lChart.highlightValues(null); // or highlightTouch(null) for callback to onNothingSelected(...)

    }

    @Override
    public void onChartLongPressed(MotionEvent me) {
    //    Log.i("LongPress", "Chart longpressed.");
        showLegend();
    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {
     //   Log.i("DoubleTap", "Chart double-tapped.");
    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {
     //   Log.i("SingleTap", "Chart single-tapped.");
    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
      //  Log.i("Fling", "Chart flinged. VeloX: " + velocityX + ", VeloY: " + velocityY);
    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
        // Log.i("Scale / Zoom", "ScaleX: " + scaleX + ", ScaleY: " + scaleY);
    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {
        //Log.i("Translate / Move", "dX: " + dX + ", dY: " + dY);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        //Log.i("Entry selected", e.toString());
        //Log.i("LOWHIGH", "low: " + lChart.getLowestVisibleX() + ", high: " + lChart.getHighestVisibleX());
        //Log.i("MIN MAX", "xmin: " + lChart.getXChartMin() + ", xmax: " + lChart.getXChartMax() + ", ymin: " + lChart.getYChartMin() + ", ymax: " + lChart.getYChartMax());
    }

    @Override
    public void onNothingSelected() {
        //Log.i("Nothing selected", "Nothing selected.");
    }

    private void showLegend() {
        Intent intent = new Intent(tripPlotActivity, PlotInfoActivity.class);
        intent.putExtra("raw_line", bundle.getString("lineTitle1"));
        intent.putExtra("smoothed_line", bundle.getString("lineTitle2"));
        intent.putExtra("average_line", bundle.getString("lineTitle3"));
        intent.putExtra("num_readings", bundle.getInt("num_readings"));
        intent.putExtra("min_battery", bundle.getString("min_battery"));
        intent.putExtra("max_battery", bundle.getString("max_battery"));
        intent.putExtra("used_battery", bundle.getString("used_battery"));
        intent.putExtra("battery_data", bundle.getBoolean("battery_data", false));
        intent.putExtra("min_pace", bundle.getString("min_pace"));
        intent.putExtra("max_pace", bundle.getString("max_pace"));
        intent.putExtra("avg_pace", bundle.getString("avg_pace"));
        intent.putExtra("pace_data", bundle.getBoolean("pace_data", false));
        tripPlotActivity.startActivity(intent);
    }

}