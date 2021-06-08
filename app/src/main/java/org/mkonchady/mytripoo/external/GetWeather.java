package org.mkonchady.mytripoo.external;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.ProgressListener;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/*
  Get the weather from openweather

  Update some of the details from openweather and interpolate the numbers as needed

  2. In Repair, calculate the power and update the detail extras if category is cycling
  3. Show power in map
  4. Show power in plot
  5. Show power in analysis
 */
public class GetWeather {

    private final Context context;
    private DetailDB detailsTable = null;
    private final String OPENWEATHER_URL;
    private ProgressListener progressListener = null;
    SharedPreferences sharedPreferences;
    private int localLog = 0;
    private float progressStart = 0.0f;
    private float progressEnd = 100.0f;
   // private Hashtable<String, String> statusExplanation = new Hashtable<>();

    public GetWeather(Context context, ProgressListener progressListener, float progressStart, float progressEnd) {
        this.context = context;
        this.progressListener = progressListener;
        this.progressStart = progressStart;
        this.progressEnd = progressEnd;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

        final String OPENWEATHER_SITE = "http://api.openweathermap.org/data/2.5/weather";
        final String OPENWEATHER_APPID = UtilsMisc.getOpenWeatherAPIKey(sharedPreferences);
        OPENWEATHER_URL = OPENWEATHER_SITE + "?appid=" + OPENWEATHER_APPID;

        detailsTable = new DetailDB(DetailProvider.db);
     //   statusExplanation.put("OK", "the API request was successful");
     //   statusExplanation.put("INVALID_REQUEST", "the API request was malformed");
     //   statusExplanation.put("OVER_QUERY_LIMIT", "the requestor has exceeded quota");
     //   statusExplanation.put("REQUEST_DENIED", "the API did not complete the request");
     //   statusExplanation.put("UNKNOWN_ERROR", "an unknown error");
    }

    /* use the openweather service to get the weather data */
    public String updateDetails(int trip_id) {

        final String TAG = "GetWeather";

        // 1. get the details (lat, lon, etc.) for the trip
        ArrayList<DetailProvider.Detail> allDetails = detailsTable.getDetails(context, trip_id);
        int numDetails = allDetails.size();
        float[] temps = new float[numDetails]; Arrays.fill(temps, 0.0f);
        float[] humiditys = new float[numDetails]; Arrays.fill(humiditys, 0.0f);
        float[] wind_degrees = new float[numDetails]; Arrays.fill(wind_degrees, 0.0f);
        float[] wind_speeds = new float[numDetails]; Arrays.fill(wind_speeds, 0.0f);
        int[] lats = new int[numDetails]; Arrays.fill(lats, 0);
        int[] lons = new int[numDetails]; Arrays.fill(lons, 0);
        long[] timestamps = new long[numDetails]; Arrays.fill(timestamps, 0);
        String[] extras = new String[numDetails]; Arrays.fill(extras, "");
        float start1 = progressStart;
        float end1 = progressStart + (progressEnd - progressStart) / 2.0f;

        // 2. Loop through and update the details
        final int INTEROP_GAP = 3;
        for (int i = 0; i < numDetails; i++) {

            DetailProvider.Detail detail = allDetails.get(i);
            lats[i] = detail.getLat();
            lons[i] = detail.getLon();
            timestamps[i] = allDetails.get(i).getTimestamp();

            // keep the first and last and skip in the middle
            if ( (i != numDetails-1) && (i % INTEROP_GAP != 0) )continue;

            // 3. call openweather
            float lat = (float) (lats[i] / Constants.MILLION);
            float lon = (float) (lons[i] / Constants.MILLION);
            final String OPENWEATHER_PARMS = "&lat=" + lat + "&lon=" + lon + "&units=metric";
            String url = OPENWEATHER_URL + OPENWEATHER_PARMS;
            String jsonResponse = UtilsJSON.getJSON(url);
            if (jsonResponse.equals(UtilsJSON.NO_RESPONSE)) {
                Logger.e(TAG, "Could not get a response from Openweather", localLog);
                return UtilsJSON.NO_RESPONSE;
            }

            // 4. Try and parse the response
            Map <String,String> map = UtilsJSON.parseOpenWeatherJSON(jsonResponse);
            temps[i] = Float.parseFloat(map.get(Constants.OPENW_TEMP));
            humiditys[i] = Float.parseFloat(map.get(Constants.OPENW_HUMIDITY));
            wind_speeds[i] = Float.parseFloat(map.get(Constants.OPENW_WIND_SPEED));
            wind_degrees[i] = Float.parseFloat(map.get(Constants.OPENW_WIND_DEG));
            extras[i] = detail.getExtras();
            float completedFrac = (i + 1.0f) / numDetails;
            progressListener.reportProgress(Math.round(start1 + (end1 - start1) * completedFrac));
        }

        // 5. interpolate the results in the arrays
        Number[] temp_nums = UtilsMisc.Interpolate(UtilsMisc.toNumber(temps), true);
        Number[] humid_nums = UtilsMisc.Interpolate(UtilsMisc.toNumber(humiditys), true);
        Number[] wind_speed_nums = UtilsMisc.Interpolate(UtilsMisc.toNumber(wind_speeds), true);
        Number[] wind_degrees_nums = UtilsMisc.Interpolate(UtilsMisc.toNumber(wind_degrees), true);


        // 6. update the details in the extras
        float start2 = end1;
        float end2 = start2 + (progressEnd - progressStart) / 2.0f;
        for (int i = 0; i < numDetails; i++) {
           String extra = UtilsJSON.buildDetailExtras(extras[i], temp_nums[i], humid_nums[i], wind_speed_nums[i], wind_degrees_nums[i]);
           detailsTable.setDetailExtras(context, extra, trip_id, timestamps[i]);
           float completedFrac = (i + 1.0f) / numDetails;
           progressListener.reportProgress(Math.round(start2 + (end2 - start2) * completedFrac));
        }

        return "";
    }
}