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
import java.util.Hashtable;
import java.util.List;

/*
  Get the altitudes for lat / lon points from Google
 */
public class GetAltitude {

    private final Context context;
    DetailDB detailsTable;
    private final ProgressListener progressListener;
    SharedPreferences sharedPreferences;
    private final int localLog;
    private final Hashtable<String, String> statusExplanation = new Hashtable<>();

    public GetAltitude(Context context, ProgressListener progressListener) {
        this.context = context;
        this.progressListener = progressListener;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

        detailsTable = new DetailDB(DetailProvider.db);
        statusExplanation.put("OK", "the API request was successful");
        statusExplanation.put("INVALID_REQUEST", "the API request was malformed");
        statusExplanation.put("OVER_QUERY_LIMIT", "the requestor has exceeded quota");
        statusExplanation.put("REQUEST_DENIED", "the API did not complete the request");
        statusExplanation.put("UNKNOWN_ERROR", "an unknown error");
    }

    /* use the elevation API Google service to correct the altitude */
    public String updateDetails(int trip_id) {

        final String TAG = "GetAltitude";

        // 1. get the details (lat, lon, altitude, etc.) for the trip
        ArrayList<DetailProvider.Detail> allDetails = detailsTable.getDetails(context, trip_id);

        // 2. break up the list into chunks of NUM_POINTS
        int[] partition_sizes = UtilsMisc.getPartitions(allDetails.size(), Constants.GOOGLE_MAX_POINTS, Constants.GOOGLE_MIN_POINTS);
        List<List<DetailProvider.Detail>> partitions = new ArrayList<>();
        int start = 0;
        for (int partition_size: partition_sizes) {
            partitions.add(allDetails.subList(start, start + partition_size));
            start += partition_size;
        }

        // 3. submit requests to Google
        String url_prefix = "https://maps.googleapis.com/maps/api/elevation/json?locations=";
        String url_suffix = "&key=" + UtilsMisc.getGoogleAPIKey(sharedPreferences);
        for (int i = 0; i < partitions.size(); i++) {

            List<DetailProvider.Detail> details = partitions.get(i);
            String[] locations = new String[details.size()];
            //int[] altitudes = new int[details.size()];
            int j = 0;
            for (DetailProvider.Detail detail : details) {
                locations[j++] = detail.getLat() / Constants.MILLION + "," +
                                 detail.getLon() / Constants.MILLION;
                //altitudes[j++] = detail.getAltitude();
            }
            // 3a. Build and submit the URL with lats and lons
            String url = url_prefix + TextUtils.join("%7C", locations) + url_suffix;
            String jsonResponse = UtilsJSON.getJSON(url);
            if (jsonResponse.equals(UtilsJSON.NO_RESPONSE)) {
                Logger.e(TAG, "Could not get a response from Google for altitude request", localLog);
                return UtilsJSON.NO_RESPONSE;
            }

            // 3b. Try and parse the response
            progressListener.reportProgress(Math.round(100.0f * (i + 1.0f) / partitions.size()));
            JSONArray results;
            try {
                JSONObject jsonObj = new JSONObject(jsonResponse);
                results = jsonObj.getJSONArray(UtilsJSON.TAG_RESULTS);
                String status = jsonObj.getString(UtilsJSON.TAG_STATUS);
                if (!(status.equals("OK")))
                    throw new JSONException(status + ": " + statusExplanation.get(status));
            } catch (JSONException e) {
                Logger.e(TAG, "Failed to parse JSON response from Google: " + e.getMessage(), localLog);
                return UtilsJSON.NO_RESPONSE;
            }

            // 3c. Loop through all received results
            for (int k = 0; k < results.length(); k++) {
                try {
                    JSONObject result = results.getJSONObject(k);
                    double elevation = result.getDouble(UtilsJSON.TAG_ELEVATION);
                    float altitude = Math.round(elevation * 10.0f) / 10.0f;
                    JSONObject location = result.getJSONObject(UtilsJSON.TAG_LOCATION);
                    double lat = location.getDouble(UtilsJSON.TAG_LAT_ALT);
                    double lon = location.getDouble(UtilsJSON.TAG_LON_ALT);
                    int iLat = (int) Math.round(lat * Constants.MILLION);
                    int iLon = (int) Math.round(lon * Constants.MILLION);

                    // update detail set altitude = elevation where lat = lat and lon = lon and tripid = trip_id
                    detailsTable.setDetailAltitude(context, altitude, trip_id, iLat, iLon);

                } catch (JSONException e) {
                    Logger.e(TAG, "Failed to extract results from JSON response: " + e.getMessage(), localLog);
                    return UtilsJSON.NO_RESPONSE;
                }
            }

            // update the summary to indicate that the elevations have been changed
            SummaryDB summaryTable = new SummaryDB(SummaryProvider.db);
            summaryTable.setSummaryElevationEdited(context, trip_id, true);
        }


        return "";
    }
}