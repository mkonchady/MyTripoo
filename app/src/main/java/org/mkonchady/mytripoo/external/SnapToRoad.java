package org.mkonchady.mytripoo.external;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.ProgressListener;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.List;

/*
    Use Google's Snap to Road service to correct the GPS lat/lon values to
    more accurate values on the road and interpolate if needed
 */
public class SnapToRoad {

    private final Context context;

    private ProgressListener progressListener = null;
    private int localLog = 0;
    SharedPreferences sharedPreferences;

    public SnapToRoad(Context context, ProgressListener progressListener) {
        this.context = context;
        this.progressListener = progressListener;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
    }

    public String updateDetails(int trip_id) {

        final String TAG = "SnapRoad";
        SummaryDB summaryTable = new SummaryDB(SummaryProvider.db);
        DetailDB detailsTable = new DetailDB(DetailProvider.db);
        final long START_TIME = summaryTable.getStartTime(context, trip_id); // save the start and end times
        final long END_TIME = summaryTable.getEndTime(context, trip_id);

        // 1. get all the details for the trip into an array list
        ArrayList<DetailProvider.Detail> allDetails = detailsTable.getDetails(context, trip_id);
        ArrayList<DetailProvider.Detail> allCorrectedDetails = new ArrayList<>();

        // 2. break up the list into chunks of NUM_POINTS
        int[] partition_sizes = UtilsMisc.getPartitions(allDetails.size(), Constants.GOOGLE_MAX_POINTS, Constants.GOOGLE_MIN_POINTS);
        List<List<DetailProvider.Detail>> partitions = new ArrayList<>();
        int start = 0;
        for (int partition_size: partition_sizes) {
            partitions.add(allDetails.subList(start, start + partition_size));
            start += partition_size;
        }

        // 3a. Check if we should interpolate or not
        SummaryProvider.Summary summary = summaryTable.getSummary(context, trip_id);
        //boolean interpolate = (summary.getDistance() < 25000.0f);    // interpolate if less 25 km
        boolean interpolate = false;

        // 3b. submit requests to Google
        String url_prefix = "https://roads.googleapis.com/v1/snapToRoads?path=";
        String url_suffix = "&interpolate=" + interpolate + "&key=" + UtilsMisc.getGoogleAPIKey(sharedPreferences);
        for (int i = 0; i < partitions.size(); i++) {

            List<DetailProvider.Detail> details = partitions.get(i);
            String[] locations = new String[details.size()];
            int j = 0;
            for (DetailProvider.Detail detail : details) {
                locations[j++] = detail.getLat() / Constants.MILLION + "," +
                        detail.getLon() / Constants.MILLION;
            }
            // 3a. Build and submit the URL with lats and lons
            String url = url_prefix + TextUtils.join("%7C", locations) + url_suffix;
            String jsonResponse = UtilsJSON.getJSON(url);
            if (jsonResponse.equals(UtilsJSON.NO_RESPONSE)) {
                Logger.e(TAG, "Could not get a response from Google for Snap To Road request", localLog);
                return UtilsJSON.NO_RESPONSE;
            }

            // 3b. parse the JSON response
            progressListener.reportProgress(Math.round(100.0f * (i + 1.0f) / partitions.size()));
            JSONArray points;
            try {
                JSONObject jsonObj = new JSONObject(jsonResponse);
                points = jsonObj.getJSONArray(UtilsJSON.TAG_POINTS);
            } catch (JSONException e) {
                Logger.e(TAG, "Failed to extract points from JSON response: " + e.getMessage(), localLog);
                return UtilsJSON.NO_RESPONSE;
            }

            // 3c. loop through all received points
            ArrayList<DetailProvider.Detail> correctedDetails = new ArrayList<>();
            int num_points = (points != null) ? points.length() : 0;
            for (int k = 0; k < num_points; k++) {
                try {

                    // replace the lat/lon with the corrected lat/lon
                    JSONObject point = points.getJSONObject(k);
                    JSONObject location = point.getJSONObject(UtilsJSON.TAG_LOCATION);
                    double lat = location.getDouble(UtilsJSON.TAG_LAT);
                    double lon = location.getDouble(UtilsJSON.TAG_LON);
                    int microLat = (int) Math.round(lat * Constants.MILLION);
                    int microLon = (int) Math.round(lon * Constants.MILLION);

                    // fields calculated in the repair function
                    float speed = 0.0f;
                    int segment = 1;
                    int bearing = 0;
                    float gradient = 0.0f;
                    String index = "";
                    String extras = "";

                    // interpolated fields, if not returned from SnapToRoad
                    long timestamp = 0;
                    int battery = 0;

                    // interpolated fields in repair trip, if not returned from SnapToRoad
                    int accuracy = 0;
                    float altitude = -1;
                    float satinfo = 0.0f;
                    int satellites = 0;

                    // build the new detail list using original values where
                    // original index was provided
                    int originalIndex = -1;
                    if (point.has(UtilsJSON.TAG_INDEX))
                        originalIndex = point.getInt(UtilsJSON.TAG_INDEX);

                    // verify that the first / last location has non-zero values
                    if (originalIndex == -1) {
                        if (k == 0) originalIndex = 0;
                        if (k == (num_points - 1)) originalIndex = num_points - 1;
                    }

                    // if the original index was provided, use it to get existing info
                    if (originalIndex != -1) {
                        accuracy = details.get(originalIndex).getAccuracy();
                        altitude = details.get(originalIndex).getAltitude();
                        battery = details.get(originalIndex).getBattery();
                        bearing = details.get(originalIndex).getBearing();
                        index = details.get(originalIndex).getIndex();
                        extras = details.get(originalIndex).getExtras();
                        gradient = details.get(originalIndex).getGradient();
                        satellites = details.get(originalIndex).getSatellites();
                        satinfo = details.get(originalIndex).getSatinfo();
                        segment = details.get(originalIndex).getSegment();
                        timestamp = details.get(originalIndex).getTimestamp();
                    }

                    DetailProvider.Detail detail = new DetailProvider.Detail(trip_id, segment,
                            microLat, microLon, battery, timestamp, altitude, speed, bearing,
                            accuracy, gradient, satinfo, satellites, index, extras);
                    correctedDetails.add(detail);
                } catch (JSONException e) {
                    Logger.e(TAG, "Failed to parse JSON response from Google: " + e.getMessage(), localLog);
                    return UtilsJSON.NO_RESPONSE;
                }
            }

            //  4. Interpolate fields in the detail records.
            //  First, extract the time,  battery fields
            Number[] timestamp  = new Number[correctedDetails.size()];
            Number[] battery    = new Number[correctedDetails.size()];
            for (int k = 0; k < correctedDetails.size(); k++) {
                timestamp[k] = correctedDetails.get(k).getTimestamp();
                battery[k] = correctedDetails.get(k).getBattery();
            }

            // 5. Use linear interpolation to handle invalid values 0, -1, etc.
            // Assign the interpolated values to the detail records
            Number[] iTimestamp = UtilsMisc.Interpolate(timestamp, true);
            Number[] iBattery = UtilsMisc.Interpolate(battery, true);
            for (int k = 0; k < correctedDetails.size(); k++) {
                correctedDetails.get(k).setBattery(iBattery[k].intValue());
                correctedDetails.get(k).setTimestamp(iTimestamp[k].longValue());
            }
            allCorrectedDetails.addAll(correctedDetails);
        }

        synchronized (this) {
            // 5. delete all the old detail records for the trip
            detailsTable.deleteDetails(context, trip_id);

            // 5a. keep the same start and end times
            int num_details = allCorrectedDetails.size();
            allCorrectedDetails.get(0).setTimestamp(START_TIME);
            allCorrectedDetails.get(num_details-1).setTimestamp(END_TIME);

            // 6. insert the corrected detail records
            for (DetailProvider.Detail detail : allCorrectedDetails)
                detailsTable.addDetail(context, detail);

        }

        Logger.d(TAG, "Before Snap to Road details: " + allDetails.size(), localLog);
        Logger.d(TAG, "After Snap to Road details: " + allCorrectedDetails.size(), localLog);

        // remove any waypoints, since the detail locations have changed
        String extras = summaryTable.getExtras(context, trip_id);
        summaryTable.setSummaryExtras(context, UtilsJSON.delWayPoints(extras), trip_id);
        summaryTable.setSummaryExtrasEdited(context, trip_id, false);

        // update the summary to indicate that the locations have been changed
        summaryTable.setSummaryLocationEdited(context, trip_id, true);
        return "";
    }
}

