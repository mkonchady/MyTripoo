package org.mkonchady.mytripoo.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.ProgressListener;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

// fix the detail and summary information of a trip
 public class RepairTrip {

    private final Context context;
    SummaryDB summaryDB;
    DetailDB detailDB;
    SummaryProvider.Summary summary = null;
    private final ProgressListener progressListener;
    private final SharedPreferences sharedPreferences;
    private final long MIN_TIME_PER_READING;            // min. time between readings
    private final int MIN_DIST_PER_READING;             // min. distance between readings

    int localLog = 0;
    final String TAG = "RepairTrip";    // number of readings before and after turn
    private final int NUM_DRAG_FORCES = 4;

    // repair parameters
    private final int trip_id;
    private String trip_category;
    private final boolean isImported;         // an imported file
    private final boolean calc_segments;
    private final boolean isManuallyEdited;           // manually edited file
    private final boolean isLocationEdited;           // Snap to Road corrections made?
    private final boolean isElevationEdited;          // was elevation corrections made

    public RepairTrip(Context context, ProgressListener progressListener, int trip_id, boolean isImported, boolean calc_segments) {
        this.context = context;
        this.progressListener = progressListener;
        summaryDB = new SummaryDB(SummaryProvider.db);
        detailDB = new DetailDB(DetailProvider.db);
        this.trip_id = trip_id;
        this.isImported = isImported;
        this.calc_segments = calc_segments;
        isManuallyEdited = summaryDB.isEdited(context, trip_id);
        isLocationEdited = summaryDB.isLocationEdited(context, trip_id);
        isElevationEdited = summaryDB.isElevationEdited(context, trip_id);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        MIN_TIME_PER_READING = UtilsMisc.getMinTimePerReading(sharedPreferences);
        MIN_DIST_PER_READING = UtilsMisc.getMinDistPerReading(sharedPreferences);
    }

    /*
        Running Trip:
            Details: Correct details, interpolate alt., acc., satinfo, Calculate speed, bearings, gradients
                     Build segments
            Summary: Repair summary fields and build extras

        Imported File:
             Details: Correct details, interpolate alt., acc., satinfo, Calculate speed, bearings, gradients
                     If not external file Build segments
            Summary: If external file Repair summary fields and build extras
     */

    public String run() {

        Logger.d(TAG, "Start Repair: " + trip_id, localLog);
        if (progressListener != null) progressListener.reportProgress(10);

        // if a trip has no detail records, then delete it
        if (summaryDB.getDetailCount(context, trip_id) <= 0) {
            summaryDB.delSummary(context, trip_id, false);
            return "Empty trip deleted";
        }

        // get the summary data
        summary = summaryDB.getSummary(context, trip_id);

        // flag an imported file using the summary extras field
        //isImported = summaryDB.isImported(context, trip_id);

        // *---------- BEGIN DETAIL REPAIR --------------*
        // create a new set of corrected detail records -- correctedDetails
        // remove readings that are too close and of poor accuracy
        if (progressListener != null) progressListener.reportProgress(15);
        ArrayList<DetailProvider.Detail> correctedDetails = setCorrectedDetails();

        // calculate the distances between detail records and cumulative distance
        double cum_distance = 0;
        double[] distances = new double[correctedDetails.size()];
        distances[0] = 0.0f;
        long moving_time = 0L;
        float walking_speed = Float.parseFloat(sharedPreferences.getString(Constants.PREF_WALKING_SPEED, "5"));
        for (int i = 1; i < correctedDetails.size(); i++) {
            distances[i] = UtilsMap.getMeterDistance(correctedDetails.get(i-1).getLat(), correctedDetails.get(i-1).getLon(),
                                                      correctedDetails.get(i).getLat(),   correctedDetails.get(i).getLon());
            long time_between_readings = correctedDetails.get(i).getTimestamp() - correctedDetails.get(i-1).getTimestamp() ;
            float speed = (float) UtilsMap.calcKmphSpeed( distances[i], time_between_readings);
            if (speed > (walking_speed / 2))
                moving_time += time_between_readings;
            cum_distance += distances[i];
        }

        // interpolate altitude, accuracy, and satinfo
        calc_interpolated_data(correctedDetails);

        // set the trip start and end time based on the first and last detail record
        summary.setStart_time(correctedDetails.get(0).getTimestamp());
        summary.setEnd_time(correctedDetails.get(correctedDetails.size()-1).getTimestamp());

        // calculate the instantaneous speeds, min, max, and avg speeds
        float avg_speed = (float) UtilsMap.calcKmphSpeed( cum_distance, summary.getEnd_time() - summary.getStart_time());
        float moving_avg_speed = (float) UtilsMap.calcKmphSpeed( cum_distance, moving_time); // use moving time to calculate average speed

        double[] min_max = calc_speeds(cum_distance, avg_speed, correctedDetails);
        float min_speed = (float) min_max[0]; float max_speed = (float) min_max[1];

        // calculate the gradient in each detail
        calc_Gradients(correctedDetails);

        // calculate the bearings in each detail
        int[] bearings = calc_bearings(correctedDetails);

        // build the segments of the trip
        if (calc_segments && !isImported)
            buildSegments(bearings, avg_speed, correctedDetails);

        // calculate the power during the trip
        trip_category = (summaryDB.isCategoryEdited(context, trip_id))? summary.getCategory():
                        UtilsMisc.getCategory(walking_speed, avg_speed, max_speed);
        boolean isElevationCorrected = summaryDB.isElevationEdited(context, trip_id);
        if (trip_category.equals(Constants.CAT_CYCLING)) {
            buildBikePower(correctedDetails, isElevationCorrected);
        } else if (trip_category.equals((Constants.CAT_JOGGING))) {
            buildJogPower(correctedDetails, isElevationCorrected);
        }  else if (trip_category.equals((Constants.CAT_WALKING))) {
            buildWalkPower(correctedDetails, isElevationCorrected);
        }

        // delete all the old detail records for the trip and add the corrected details
        if (progressListener != null) progressListener.reportProgress(20);
        synchronized (this) {
            detailDB.deleteDetails(context, trip_id);
            if (progressListener != null) progressListener.reportProgress(30);

            // insert the corrected detail records -- takes 50% of the time
            int i = 0; int size = correctedDetails.size();
            for (DetailProvider.Detail detail : correctedDetails) {
                detailDB.addDetail(context, detail);
                if (++i % 100 == 0 && progressListener != null)
                    progressListener.reportProgress( (i * 50 / size) + 30);
            }
        }
        // *--------- END DETAIL REPAIR ----------------*

        // *--------- BEGIN SUMMARY REPAIR -------------*
        Logger.d(TAG, "Repair Summary: ", localLog);
        if (progressListener != null) progressListener.reportProgress(85);
        repairSummary(correctedDetails, distances, min_speed, max_speed, (float) cum_distance, avg_speed);
        if (!isImported) buildSummaryExtras(moving_avg_speed);

        Logger.d(TAG, "End repair: " + trip_id, localLog);
        if (progressListener != null) progressListener.reportProgress(100);
        // *---------- END SUMMARY REPAIR --------------*

        return "Finished repair of trip: " + trip_id;
    }

    // create a subset of all the readings in correctedDetails after removing outlier readings
    private ArrayList<DetailProvider.Detail> setCorrectedDetails() {

        ArrayList<DetailProvider.Detail> correctedDetails = new ArrayList<>();
        ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id);
        if (details.size() == 0) return null;

        // add first detail
        DetailProvider.Detail current_detail = details.get(0);
        if (details.size() > 0)
            correctedDetails.add(current_detail);

        // set the INC depending on the maximum number of readings per trip, process all but the last reading
        final int INC = Math.round(details.size() / UtilsMisc.getMaxReadings(sharedPreferences)) + 1;
        int prev_lat = current_detail.getLat(); int prev_lon = current_detail.getLon();
        long prev_timestamp = current_detail.getTimestamp();
        for (int i = 1; i < details.size()-1; i += INC) {
            current_detail = details.get(i);

            // remove readings that are too close in time and space prefs.
            // for a trip that was corrected with snap to road, each reading must be separated by min. distance
            long timeSeparation = current_detail.getTimestamp() - prev_timestamp;
            double distSeparation = UtilsMap.getMeterDistance(current_detail.getLat(), current_detail.getLon(), prev_lat, prev_lon);
             if (isValidDetail(distSeparation, timeSeparation)) {
                 correctedDetails.add(current_detail);
                 prev_timestamp = current_detail.getTimestamp();
                 prev_lat = current_detail.getLat();
                 prev_lon = current_detail.getLon();
             }
        }

        // add the last detail
        correctedDetails.add(details.get(details.size()-1));

        // sort the corrected details by timestamp in ascending order
        if (correctedDetails.size() > 1) {
            Collections.sort(correctedDetails, new Comparator<DetailProvider.Detail>() {
                @Override
                public int compare(DetailProvider.Detail detail1, DetailProvider.Detail detail2) {
                    return comparator(detail1.getTimestamp(), detail2.getTimestamp());
                }

                private int comparator(long time1, long time2) {
                    return (time1 == time2)? 0: (time1 < time2)? -1: 1;
                }
            });
        }

        // save the detail count in the index
        int detailCount = 1;
        for (DetailProvider.Detail detail: correctedDetails)
            detail.setIndex("" + detailCount++);
        Logger.d(TAG, "No. of original details: " + details.size() + " no. of corrected details: " + correctedDetails.size(), localLog);
        return correctedDetails;
    }

    // Calculate speeds in a sequence
    //      --> Calculate raw speeds if within preferred time and space parameters
    //      --> Weight readings with low accuracy less than other readings
    //      --> Smooth readings with Gaussian kernel for +/- 3 neighbor readings
    //      --> limit readings to the speed limit
    //      --> Error correction of speeds to match cumulative distance from locations
    private double[] calc_speeds(double cum_distance, float avg_speed, ArrayList<DetailProvider.Detail> correctedDetails) {

        // calculate the raw speeds and average speed
        double [] raw_speeds = new double[correctedDetails.size()];
        //float avg_speed = (float) UtilsMap.calcKmphSpeed(cum_distance, summary.getEnd_time() - summary.getStart_time());
        // set the minimum distance and time per reading when calculating speed based on the average speed
        // a lower average speed means more time and distance between readings
        int min_dist_reading = (avg_speed >= 50)? 1: (avg_speed >= 40)? 2: (avg_speed >= 30)? 3: (avg_speed >= 20)? 4: 5;
        int min_time_reading = min_dist_reading * 1000;

        long[] timestamps = new long[correctedDetails.size()];
        timestamps[0] = 0;
        long prev_timestamp = correctedDetails.get(0).getTimestamp();
        int prev_lat = correctedDetails.get(0).getLat(); int prev_lon = correctedDetails.get(0).getLon();
        raw_speeds[0] = avg_speed;
        for (int i = 1; i < correctedDetails.size(); i++) {
            timestamps[i] = correctedDetails.get(i).getTimestamp()  - correctedDetails.get(i-1).getTimestamp();
            long timeSeparation = correctedDetails.get(i).getTimestamp() - prev_timestamp;
            double distSeparation = UtilsMap.getMeterDistance(prev_lat, prev_lon,
                    correctedDetails.get(i).getLat(),   correctedDetails.get(i).getLon());
            if  ( (timeSeparation > min_time_reading && distSeparation > min_dist_reading) || isImported) {
                raw_speeds[i] = UtilsMap.calcKmphSpeed(distSeparation, timeSeparation);
                prev_lat = correctedDetails.get(i).getLat(); prev_lon = correctedDetails.get(i).getLon();
                prev_timestamp = correctedDetails.get(i).getTimestamp();
            } else {
                raw_speeds[i] = avg_speed;
            }
        }

        // speed limit is 5 times standard deviation to detect outliers
        raw_speeds = UtilsMisc.removeOutliers(raw_speeds, 5);

        // Set the weights for the speeds based on the accuracy of the reading
        for (int i = 1; i < correctedDetails.size(); i++) {
            int accuracy = correctedDetails.get(i).getAccuracy();  // use the accuracy to weight the speed calculations
            // when accuracy is high (<= 10m) use the speed as-is
            // otherwise, use a sigmoid function to add a percentage of
            // the avg. speed, A higher % of avg. speed for low accuracy readings
            if (accuracy > 10) {
                double factor = 1.0 / (1.0 + Math.exp(-(accuracy - 20)));
                raw_speeds[i] = factor * avg_speed + (1.0 - factor) * raw_speeds[i];
            }
        }

        // Smooth the calculation of the speeds
        double[] smooth_speeds = UtilsMisc.smoothValues(raw_speeds, timestamps, (int) MIN_TIME_PER_READING);

        // speed limit is 5 times standard deviation to detect outliers
        smooth_speeds = UtilsMisc.removeOutliers(smooth_speeds, 5);

        // calculate the cumulative distance with smoothed speeds
        double smooth_cum_distance = 0.0f;
        for (int i = 1; i < correctedDetails.size(); i++) {
             smooth_cum_distance += (smooth_speeds[i] * 1000.0f * timestamps[i] / (3600.0f * 1000.0f));
        }

        // error correction to ensure that cumulative distance calculated
        // with smoothed speeds matches cumulative distance from readings
        // calculate the difference fraction with the location cumulative distance
        double diff_fraction =  (smooth_cum_distance > 0.0f) ? (cum_distance - smooth_cum_distance) / smooth_cum_distance: 0.0f;
        for (int i = 0; i < correctedDetails.size(); i++) {
            smooth_speeds[i] = (float) (smooth_speeds[i] * (1.0f + diff_fraction));
            correctedDetails.get(i).setSpeed((float) smooth_speeds[i]);
        }
        //Log.d(TAG, "Smooth " + smooth_cum_distance + " Raw: " + cum_distance + " diff " + diff_fraction);
        return (UtilsMisc.getMinMax(smooth_speeds));
    }

    // calculate the bearing at each reading
    private int[] calc_bearings (ArrayList<DetailProvider.Detail> correctedDetails) {
        int[] bearings = new int[correctedDetails.size()];
        if (correctedDetails.size() > 2) {
            for (int i = 0; i < correctedDetails.size()-1; i++) {
                int lat0 = correctedDetails.get(i).getLat();
                int lon0 = correctedDetails.get(i).getLon();
                int lat1 = correctedDetails.get(i+1).getLat();
                int lon1 = correctedDetails.get(i+1).getLon();
                bearings[i] = UtilsMap.getBearing(lat0, lon0, lat1, lon1);
                correctedDetails.get(i).setBearing(bearings[i]);
            }
            bearings[correctedDetails.size()-1] = bearings[correctedDetails.size()-2];    // copy the second last bearing to the last bearing
            correctedDetails.get(correctedDetails.size()-1).setBearing(bearings[correctedDetails.size()-1]);
        } else {
            correctedDetails.get(0).setBearing(0);
        }

        return bearings;
    }

    // repair the altitude, accuracy, satellites, and satinfo fields
    private void calc_interpolated_data(ArrayList<DetailProvider.Detail> correctedDetails) {
        Number[] altitudes = new Number[correctedDetails.size()];
        for (int i = 0; i < correctedDetails.size(); i++)
            altitudes[i] = correctedDetails.get(i).getAltitude();
        Number[] iAltitudes = UtilsMisc.Interpolate(altitudes, true);
        for (int k = 0; k < correctedDetails.size(); k++)
            correctedDetails.get(k).setAltitude(iAltitudes[k].floatValue());

        Number[] accuracies = new Number[correctedDetails.size()];
        for (int i = 0; i < correctedDetails.size(); i++)
            accuracies[i] = correctedDetails.get(i).getAccuracy();
        Number[] iAccuracy = UtilsMisc.Interpolate(accuracies, true);
        for (int k = 0; k < correctedDetails.size(); k++)
            correctedDetails.get(k).setAccuracy(iAccuracy[k].intValue());

        Number[] satellites = new Number[correctedDetails.size()];
        for (int i = 0; i < correctedDetails.size(); i++)
            satellites[i] = correctedDetails.get(i).getSatellites();
        Number[] iSatellites = UtilsMisc.Interpolate(satellites, true);
        for (int k = 0; k < correctedDetails.size(); k++)
            correctedDetails.get(k).setSatellites(iSatellites[k].intValue());

        Number[] satinfo = new Number[correctedDetails.size()];
        for (int i = 0; i < correctedDetails.size(); i++)
            satinfo[i] = correctedDetails.get(i).getSatinfo();
        Number[] iSatinfo = UtilsMisc.Interpolate(satinfo, true);
        for (int k = 0; k < correctedDetails.size(); k++)
            correctedDetails.get(k).setSatinfo(iSatinfo[k].floatValue());
    }

    // calculate the gradient at each reading
    private void calc_Gradients(ArrayList<DetailProvider.Detail> correctedDetails) {
        // first gradient is 0
        double[] gradients = new double[correctedDetails.size()];
        correctedDetails.get(0).setGradient(0.0f);
        gradients[0] = 0.0f; float cum_gradient = 0.0f;
        for (int i = 1; i < correctedDetails.size(); i++) {
           // rise in meters, run in meters, gradient in percent
           float rise = correctedDetails.get(i).getAltitude() - correctedDetails.get(i-1).getAltitude();
           double run = UtilsMap.getMeterDistance(correctedDetails.get(i).getLat(),   correctedDetails.get(i).getLon(),
                                                   correctedDetails.get(i-1).getLat(), correctedDetails.get(i-1).getLon());
            // gradient percent is 100 * rise / run, minimum of 1 meter per run
            gradients[i] = (run > 1.0) ? (rise / run) * 100.0: 0.0;
            cum_gradient += gradients[i];
        }

        // limit is 3 times standard deviation to detect outliers
        gradients = UtilsMisc.removeOutliers(gradients, 3);
        for (int i = 0; i < correctedDetails.size(); i++) {
            correctedDetails.get(i).setGradient((float) gradients[i]);
        }
    }

    // calculate the bike power at each detail
    // F_total = F_air + F_slope + F_roll + F_accel / efficiency
    private void buildBikePower(ArrayList<DetailProvider.Detail> correctedDetails, boolean isElevationCorrected) {
        final double rider_wt = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_RIDER, "70.0"));
        final double bike_wt  = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_BIKE, "12.0"));
        final double front_area = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_FRONT, "0.5"));
        final double c_r = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_ROLLING, "0.015"));
        final double c_d = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_DRAG, "1.15"));
        final double m = (rider_wt + bike_wt);

        // calculate the average of each of the drag forces
        int num_details = correctedDetails.size();
        double[][] forces = new double[NUM_DRAG_FORCES][num_details];
        double[] tot_forces = {0.0, 0.0, 0.0, 0.0};
        for (int i = 0; i < NUM_DRAG_FORCES; i++) forces[i][0] = 0.0;

        long[] timestamps = new long[num_details];
        timestamps[0] = 0;
        for (int i = 1; i < num_details; i++) {
            DetailProvider.Detail currD = correctedDetails.get(i);
            DetailProvider.Detail prevD = correctedDetails.get(i-1);
            timestamps[i] = currD.getTimestamp() - prevD.getTimestamp();
            double[] drag_forces = UtilsMap.get_bike_drag_forces(m, c_r, c_d, front_area, currD, prevD, isElevationCorrected);
            for (int j = 0; j < NUM_DRAG_FORCES; j++) {
                tot_forces[j] += drag_forces[j];
                forces[j][i] = drag_forces[j];
            }
        }
        double[] avg_forces = {0.0, 0.0, 0.0, 0.0};
        for (int i = 0; i < NUM_DRAG_FORCES; i++)
            avg_forces[i] = tot_forces[i] / num_details;

        // accuracy estimates on a scale of 1 to 10 in order from AIR, SLOPE, ROLL, ACCEL
        int[] accuracy = {6, 10, 8, 9};
        for (int i = 0; i < NUM_DRAG_FORCES; i++)
            forces[i] = UtilsMap.filter_numbers(forces[i], avg_forces[i], accuracy[i], 10);

        // calculate the power from the drag forces and the bike velocity
        double[] raw_power = new double[num_details];
        for (int i = 0; i < num_details; i++) {
            double[] detail_force = {forces[0][i], forces[1][i], forces[2][i], forces[3][i]};
            raw_power[i] = UtilsMap.get_power(detail_force, UtilsMap.kmphTomps(correctedDetails.get(i).getSpeed()), 0.95);
        }

        double[] smooth_power = UtilsMisc.smoothValues(raw_power, timestamps, (int) MIN_TIME_PER_READING);
        for (int i = 0; i < num_details; i++) {
            String power = UtilsMisc.formatDouble(smooth_power[i], 1);
            correctedDetails.get(i).setPower(power);
        }

    }

    // calculate the jogging power at each detail
    // F_total = F_air + F_slope + F_roll + F_accel / efficiency
    private void buildJogPower(ArrayList<DetailProvider.Detail> correctedDetails, boolean isElevationCorrected) {
        final double m = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_RIDER, "70.0"));

        // calculate the average of each of the drag forces
        int num_details = correctedDetails.size();
        double[][] forces = new double[NUM_DRAG_FORCES][num_details];
        double[] tot_forces = {0.0, 0.0, 0.0, 0.0};
        for (int i = 0; i < NUM_DRAG_FORCES; i++) forces[i][0] = 0.0;

        long[] timestamps = new long[num_details];
        timestamps[0] = 0;
        for (int i = 1; i < num_details; i++) {
            DetailProvider.Detail currD = correctedDetails.get(i);
            DetailProvider.Detail prevD = correctedDetails.get(i-1);
            timestamps[i] = currD.getTimestamp() - prevD.getTimestamp();
            double[] drag_forces = UtilsMap.get_jog_drag_forces(m, currD, prevD, isElevationCorrected);
            for (int j = 0; j < NUM_DRAG_FORCES; j++) {
                tot_forces[j] += drag_forces[j];
                forces[j][i] = drag_forces[j];
            }
        }
        double[] avg_forces = {0.0, 0.0, 0.0, 0.0};
        for (int i = 0; i < NUM_DRAG_FORCES; i++)
            avg_forces[i] = tot_forces[i] / num_details;

        // accuracy estimates on a scale of 1 to 10 in order from AIR, SLOPE, ROLL, ACCEL
        int[] accuracy = {6, 10, 8, 9};
        for (int i = 0; i < NUM_DRAG_FORCES; i++)
            forces[i] = UtilsMap.filter_numbers(forces[i], avg_forces[i], accuracy[i], 10);

        // calculate the power from the drag forces and the bike velocity
        double[] raw_power = new double[num_details];
        for (int i = 0; i < num_details; i++) {
            double[] detail_force = {forces[0][i], forces[1][i], forces[2][i], forces[3][i]};
            raw_power[i] = UtilsMap.get_power(detail_force, UtilsMap.kmphTomps(correctedDetails.get(i).getSpeed()), 1.0);
        }

        double[] smooth_power = UtilsMisc.smoothValues(raw_power, timestamps, (int) MIN_TIME_PER_READING);
        for (int i = 0; i < num_details; i++) {
            String power = UtilsMisc.formatDouble(smooth_power[i], 1);
            correctedDetails.get(i).setPower(power);
        }

    }

    // calculate the walking power at each detail
    // F_total = F_air + F_slope + F_roll + F_accel / efficiency
    private void buildWalkPower(ArrayList<DetailProvider.Detail> correctedDetails, boolean isElevationCorrected) {
        final double m = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_RIDER, "70.0"));

        // calculate the average of each of the drag forces
        int num_details = correctedDetails.size();
        double[][] forces = new double[NUM_DRAG_FORCES][num_details];
        double[] tot_forces = {0.0, 0.0, 0.0, 0.0};
        for (int i = 0; i < NUM_DRAG_FORCES; i++) forces[i][0] = 0.0;

        long[] timestamps = new long[num_details];
        timestamps[0] = 0;
        for (int i = 1; i < num_details; i++) {
            DetailProvider.Detail currD = correctedDetails.get(i);
            DetailProvider.Detail prevD = correctedDetails.get(i-1);
            timestamps[i] = currD.getTimestamp() - prevD.getTimestamp();
            double[] drag_forces = UtilsMap.get_walk_drag_forces(m, currD, prevD, isElevationCorrected);
            for (int j = 0; j < NUM_DRAG_FORCES; j++) {
                tot_forces[j] += drag_forces[j];
                forces[j][i] = drag_forces[j];
            }
        }
        double[] avg_forces = {0.0, 0.0, 0.0, 0.0};
        for (int i = 0; i < NUM_DRAG_FORCES; i++)
            avg_forces[i] = tot_forces[i] / num_details;

        // accuracy estimates on a scale of 1 to 10 in order from AIR, SLOPE, ROLL, ACCEL
        int[] accuracy = {6, 10, 8, 9};
        for (int i = 0; i < NUM_DRAG_FORCES; i++)
            forces[i] = UtilsMap.filter_numbers(forces[i], avg_forces[i], accuracy[i], 10);

        // calculate the power from the drag forces and the bike velocity
        double[] raw_power = new double[num_details];
        for (int i = 0; i < num_details; i++) {
            double[] detail_force = {forces[0][i], forces[1][i], forces[2][i], forces[3][i]};
            raw_power[i] = UtilsMap.get_power(detail_force, UtilsMap.kmphTomps(correctedDetails.get(i).getSpeed()), 1.0);
        }

        double[] smooth_power = UtilsMisc.smoothValues(raw_power, timestamps, (int) MIN_TIME_PER_READING);
        for (int i = 0; i < num_details; i++) {
            String power = UtilsMisc.formatDouble(smooth_power[i], 1);
            correctedDetails.get(i).setPower(power);
        }

    }

    // compute the summary parameters and update the DB table
    private void repairSummary(ArrayList<DetailProvider.Detail> correctedDetails, double[] distances, float min_speed,
                               float max_speed, float cum_distance, float avg_speed) {
        int num_readings = correctedDetails.size();

        // get the start and end times
        long start_time = correctedDetails.get(0).getTimestamp();
        long end_time = correctedDetails.get(num_readings-1).getTimestamp();

        // get the time and distance between readings
        float min_time_readings = Constants.LARGE_FLOAT; float max_time_readings = 0.0f; float tot_time_readings = 0.0f;
        float min_dist_readings = Constants.LARGE_FLOAT; float max_dist_readings = 0.0f; float tot_dist_readings = 0.0f;
        for (int i = 1; i < correctedDetails.size(); i++) {
            long time_readings = (correctedDetails.get(i).getTimestamp() - correctedDetails.get(i-1).getTimestamp());
            if (time_readings <= 0L) continue;

            // calculate min., max., avg. time between readings
            min_time_readings = (time_readings > 0) && (time_readings < min_time_readings) ?
                    time_readings : min_time_readings;
            max_time_readings = (time_readings > max_time_readings) ?
                    time_readings : max_time_readings;
            tot_time_readings += time_readings;

            // calculate min., max., avg. distance between readings
            float dist_readings = (float) distances[i-1];
            min_dist_readings = (dist_readings > 0) && (dist_readings < min_dist_readings) ?
                    dist_readings : min_dist_readings;
            max_dist_readings = (dist_readings > max_dist_readings) ?
                    dist_readings : max_dist_readings;
            tot_dist_readings += dist_readings;

        }
        // get the accuracy and elevation limits
        float min_accuracy = Constants.LARGE_FLOAT; float max_accuracy = 0.0f; float tot_accuracy = 0.0f;
        float min_elevation = Constants.LARGE_INT; float max_elevation = 0;
        for (int i = 0; i < correctedDetails.size(); i++) {
            // calculate min., max. elevations
            float elevation = correctedDetails.get(i).getAltitude();
            min_elevation = (elevation < min_elevation) ? elevation : min_elevation;
            max_elevation = (elevation > max_elevation) ? elevation : max_elevation;

            // calculate min., max. accuracies
            int accuracy = correctedDetails.get(i).getAccuracy();
            min_accuracy = (accuracy < min_accuracy) ? accuracy : min_accuracy;
            max_accuracy = (accuracy > max_accuracy) ? accuracy : max_accuracy;
            tot_accuracy += accuracy;
        }

        // fix the min. numbers
        if (min_elevation == Constants.LARGE_INT) min_elevation = 0;
        if (Float.compare(min_speed, Constants.LARGE_FLOAT) == 0) min_speed = 0.0f;
        if (Float.compare(min_time_readings, Constants.LARGE_FLOAT) == 0) min_time_readings = 0.0f;
        if (Float.compare(min_dist_readings, Constants.LARGE_FLOAT) == 0) min_dist_readings = 0.0f;

        // compute the averages and re-compute the new average speed
        float avg_time_readings = (tot_time_readings) / num_readings;
        float avg_dist_readings = (tot_dist_readings) / num_readings;
        //float avg_speed = (float) UtilsMap.calcKmphSpeed(cum_distance, Math.round(tot_time_readings));

        // get the central location closest to the average
        int[] average = detailDB.getAverageDetailLocation(context, trip_id);
        int[] nearest = detailDB.getClosestDetailByLocation(context, trip_id, average[0], average[1], -1, 1);
        int avg_lat = nearest[0]; int avg_lon = nearest[1];

        // set the accuracy values
        float avg_accuracy =  tot_accuracy / num_readings;

        // build the content values
        ContentValues values = new ContentValues();
        //values.put(SummaryProvider.START_TIME, summary.getStart_time());
        //values.put(SummaryProvider.END_TIME, summary.getEnd_time());
        values.put(SummaryProvider.START_TIME, start_time);
        values.put(SummaryProvider.END_TIME, end_time);
        values.put(SummaryProvider.NAME, summary.getName());
        values.put(SummaryProvider.CATEGORY, trip_category);
        values.put(SummaryProvider.DISTANCE, cum_distance);
        values.put(SummaryProvider.AVG_LAT, avg_lat);
        values.put(SummaryProvider.AVG_LON, avg_lon);
        values.put(SummaryProvider.MIN_ELEVATION, min_elevation);
        values.put(SummaryProvider.MAX_ELEVATION, max_elevation);
        values.put(SummaryProvider.MIN_SPEED, min_speed);
        values.put(SummaryProvider.MAX_SPEED, max_speed);
        values.put(SummaryProvider.AVG_SPEED, avg_speed);
        values.put(SummaryProvider.MIN_ACCURACY, min_accuracy);
        values.put(SummaryProvider.MAX_ACCURACY, max_accuracy);
        values.put(SummaryProvider.AVG_ACCURACY, avg_accuracy);
        values.put(SummaryProvider.MIN_TIME_READINGS, min_time_readings);
        values.put(SummaryProvider.MAX_TIME_READINGS, max_time_readings);
        values.put(SummaryProvider.AVG_TIME_READINGS, avg_time_readings);
        values.put(SummaryProvider.MIN_DIST_READINGS, min_dist_readings);
        values.put(SummaryProvider.MAX_DIST_READINGS, max_dist_readings);
        values.put(SummaryProvider.AVG_DIST_READINGS, avg_dist_readings);
        values.put(SummaryProvider.STATUS, Constants.FINISHED);
        values.put(SummaryProvider.EXTRAS, summary.getExtras());
        summaryDB.setSummaryParameters(context, values, trip_id);

    }

    // update the extras with segment summary information
    private void buildSummaryExtras(float moving_avg_speed) {

        // get the number of segments
        int num_segments = summaryDB.getSegmentCount(context, trip_id);

        // for each segment calculate the duration, distance, and avg. speed in kmph.
        long[] cum_times = new long[num_segments];
        float[] cum_distances = new float[num_segments];
        long last_time = 0L; int last_lat = 0, last_lon = 0;
        for (int segment = 1; segment <= num_segments; segment++) {
            long cum_time = 0L; float cum_distance = 0;

            // get the list of details in ascending order of time
            ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id, segment);
            for (int i = 1; i < details.size(); i++) {
                cum_time += details.get(i).getTimestamp() - details.get(i-1).getTimestamp();
                cum_distance += UtilsMap.getKmDistance(details.get(i-1).getLat(), details.get(i-1).getLon(),
                                                        details.get(i).getLat(),   details.get(i).getLon());
            }
            cum_times[segment-1] = cum_time;
            cum_distances[segment-1] = cum_distance;

            // add the distance / time from the last reading of the previous segment
            // to the first reading of the current segment
            if (segment > 1) {
                cum_times[segment-2] +=  details.get(0).getTimestamp() - last_time;
                cum_distances[segment-2] += UtilsMap.getKmDistance(details.get(0).getLat(),
                                                                    details.get(0).getLon(), last_lat, last_lon);
            }
            // save the last lat, lon, and timestamp of the last detail record for the segment
            last_lat = details.get(details.size()-1).getLat();
            last_lon = details.get(details.size()-1).getLon();
            last_time = details.get(details.size()-1).getTimestamp();
        }

        // save in the extras of the summary
        boolean edited = summaryDB.isEdited(context, trip_id);
        String poll_interval = sharedPreferences.getString(Constants.PREF_POLL_INTERVAL, "low");
        String gps_accuracy = sharedPreferences.getString(Constants.PREF_LOCATION_ACCURACY, "low");
        String extras = UtilsJSON.buildExtras(summary, edited, num_segments, cum_times, cum_distances, poll_interval, gps_accuracy, moving_avg_speed);
        summaryDB.setSummaryExtras(context, extras, trip_id);
    }

    // identify the segment locations from u-turns and loops
    private void buildSegments(int[] bearings, float avg_speed, ArrayList<DetailProvider.Detail> correctedDetails) {

        final int TOLERANCE = 8;            // U-turn degree must be within 180 +/- for TOLERANCE
        final float SPEED_LEVEL_1 = 25.0f;
        final float SPEED_LEVEL_2 = 30.0f;

        // save a hashmap of the locations that have been visited in the route
        HashMap<String, Long> seenLocations = new HashMap<>();
        long lastTurnTime = correctedDetails.get(0).getTimestamp();   // track the time of the last turn
        final int NUM_READINGS = correctedDetails.size();

        // set the minimum segment size based on the number of readings
        final int MIN_SEGMENT_SIZE = (NUM_READINGS < 256)? 8: (int) Math.round(Math.sqrt(NUM_READINGS));
        int lastSegmentPoint = 0;
        int segment = 1;
        for (int reading_num = 0; reading_num < NUM_READINGS; reading_num++) {
            // get time and location of the reading
            long readingTime = correctedDetails.get(reading_num).getTimestamp();
            int lat = correctedDetails.get(reading_num).getLat();
            int lon = correctedDetails.get(reading_num).getLon();

            // approximate the micro lat and lon to the hundreds place with a round precision of 2
            // i.e. about 11 meters, to make some hash keys for the NW, NE, SW, and SE corners
            int round_precision = (avg_speed > SPEED_LEVEL_1) ? 3 : 2;
            String[] keys = UtilsMap.gen_keys(lat, lon, round_precision);
            boolean new_segment = false;

            // if this same location was seen earlier outside the TIME_GAP, then start a new segment
            // also ensure that there are more readings in the trip for another segment
            if (UtilsMap.containsKey(keys, seenLocations)) {
                long lastTime = UtilsMap.getKeyTime(keys, seenLocations);
                // set the minimum period in milliseconds between segments
                final long SEGMENT_TIME_GAP = (avg_speed > SPEED_LEVEL_2)? Constants.MILLISECONDS_PER_MINUTE:
                                              (avg_speed > SPEED_LEVEL_1)? Constants.MILLISECONDS_PER_MINUTE * 2:
                                                                           Constants.MILLISECONDS_PER_MINUTE * 3;
                final long READING_TIME_GAP = readingTime - lastTime;
                if ( (READING_TIME_GAP > SEGMENT_TIME_GAP)
                        && validSegmentPoint(reading_num, NUM_READINGS, lastSegmentPoint, MIN_SEGMENT_SIZE)  &&
                        (segment <= Constants.MAX_SEGMENTS) ) {
                    new_segment = true;
                    Logger.d(TAG, "Loop at : " + reading_num + " http://maps.google.com/maps?q=loc:" +
                            lat / Constants.MILLION + "," + lon / Constants.MILLION, localLog);
                } else {

                    /*
                             check for U-turn going from left to right
                              --------> i-3, i-2, i-1 --|
                                                    i   |
                              <-------- i+3, i+2, i+1 --|
                    */
                    for (int j = 1; j < 4; j++) {
                        int start_turn = reading_num - j;
                        int end_turn = reading_num + j;
                        // check for start and end conditions
                        if ((start_turn < 0) || (end_turn > (correctedDetails.size() - 1)))
                            continue;

                        // calculate the range of the bearing for a u-turn given
                        // the current start turn
                        int opp_bearing = (bearings[start_turn] + 180);// % 360;
                        int start_range = (opp_bearing - TOLERANCE);// % 360;
                        int end_range = (opp_bearing + TOLERANCE);// % 360;
                        if ( start_range <= bearings[end_turn]              &&  // check if the end of the turn is within
                             bearings[end_turn] <= end_range                &&  // range +/- TOLERANCE
                             segment <= Constants.MAX_SEGMENTS              &&
                             (readingTime - lastTurnTime) > 5 * Constants.MILLISECONDS_PER_MINUTE) { // must be at least 5 minutes between turns
                            // also check the distance between the start and end of the U-turn
                            int startLat = correctedDetails.get(start_turn).getLat();
                            int startLon = correctedDetails.get(start_turn).getLon();
                            int endLat = correctedDetails.get(end_turn).getLat();
                            int endLon = correctedDetails.get(end_turn).getLon();

                            // compute the distance between the start and end points of the u-turn
                            double distance = UtilsMap.getMeterDistance(startLat, startLon, endLat, endLon);
                            final int DISTANCE_GAP = (avg_speed > SPEED_LEVEL_1)? 200: 100;  // set the minimum distance in meters between segments
                            if (distance > DISTANCE_GAP)
                                break;

                            // the start of the turn should be a location seen earlier
                            keys = UtilsMap.gen_keys(startLat, startLon, round_precision);
                            if (!UtilsMap.containsKey(keys, seenLocations))
                                break;

                            if (validSegmentPoint(reading_num, NUM_READINGS, lastSegmentPoint, MIN_SEGMENT_SIZE) &&
                                legitimate_turn(reading_num, bearings)) {
                                new_segment = true;
                                lastTurnTime = readingTime;
                                Logger.d(TAG, "U-turn at " + reading_num + ": http://maps.google.com/maps?q=loc:" +
                                        lat / Constants.MILLION + "," + lon / Constants.MILLION, localLog);
                                break;
                            }
                        }
                    }   // end of for
                }
            }

            // add a new segment if necessary
            if (new_segment) {
                correctedDetails.get(reading_num).setSegment(segment);
                ++segment;
                //Log.d(TAG, "Size of seen locations hash: " + seenLocations.size(), localLog);
                seenLocations = new HashMap<>(); // initialize the hash for the next segment
                lastSegmentPoint = reading_num;
            } else {
                correctedDetails.get(reading_num).setSegment(segment);
            }

            // add to the list of seen locations
            for (String key: keys)
                if (!seenLocations.containsKey(key))
                    seenLocations.put(key, readingTime);


        } // end of outer for size ...
    }

    // is this a valid point to create a new segment
    private boolean validSegmentPoint(int reading_num, int NUM_READINGS, int last_segment_point, int MIN_SEGMENT_SIZE) {
        return ( ( (NUM_READINGS - reading_num)       >= MIN_SEGMENT_SIZE) &&  // leave enough readings at the end of the trip
                 ( (reading_num - last_segment_point) >= MIN_SEGMENT_SIZE) );  // leave enough readings between segment points
    }

    // verify that this is a legitimate U-turn
    private boolean legitimate_turn(int reading_num, int[] bearings) {

        int size = bearings.length;
        final int N = 10; // there should be at least N readings before and after the turn
        if ( (reading_num - N < 0) || (reading_num + N >= size) ) return false;

        // calculate the cumulative bearing difference between the N pairs of points
        float cum_bearing = 0; float cum_weight = 0.0f;
        for (int i = 1; i <= N; i++) {
            float weight = (1.0f * i) / N;
            cum_weight += weight;
            cum_bearing += Math.abs(Math.abs(bearings[reading_num - i] - bearings[reading_num + i]) - 180.0) * weight;
        }

        final float MAX_BEARING_DIFF = 30.0f;   // max. average weighted difference between pairs of locations on the turn
        float avg_bearing = cum_bearing / cum_weight;
        return (avg_bearing < MAX_BEARING_DIFF);
    }

    // check if the detail is valid
    private boolean isValidDetail(double distSeparation, long timeSeparation) {
        if (isModified()) return true;
        if (isLocationEdited && distSeparation >= MIN_DIST_PER_READING) return true;
        return (timeSeparation >= MIN_TIME_PER_READING && distSeparation >= MIN_DIST_PER_READING);
    }

    private boolean isModified() {
        return isImported || isManuallyEdited || isElevationEdited;
    }
}