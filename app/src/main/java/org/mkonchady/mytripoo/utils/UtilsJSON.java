package org.mkonchady.mytripoo.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.database.SummaryProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UtilsJSON {

    // JSON Node names for Google Responses
    public static final String TAG_POINTS = "snappedPoints";
    public static final String TAG_LOCATION = "location";
    public static final String TAG_LAT = "latitude";
    public static final String TAG_LON = "longitude";
    public static final String TAG_LAT_ALT = "lat";
    public static final String TAG_LON_ALT = "lng";
    public static final String TAG_INDEX = "originalIndex";
    // public static final String TAG_PLACE = "placeId";
    public static final String TAG_STATUS = "status";
    public static final String TAG_RESULTS = "results";
    public static final String TAG_ELEVATION = "elevation";
    public static final String NO_RESPONSE = "no_response";

    private final static String TAG = "JSON_Utilities";
    /**
     * no default constructor
     */
    private UtilsJSON() {
        throw new AssertionError();
    }

    // add the list of waypoints to the extras string
    public static String addWayPoints(ArrayList<String> wayPoints, String extras) {
        String result = "";
        try {
            JSONObject mainObject = (extras.length() > 0) ? new JSONObject(extras): new JSONObject();
            for (int i = 0; i < wayPoints.size(); i++)
                mainObject.put("wpt_" + i, wayPoints.get(i));
            result = mainObject.toString();
        } catch (JSONException je) {
                Logger.e(TAG, "JSON error adding waypoints" + je.getMessage());
        }
        return result;
    }

    // add the list of waypoints to the extras string
    public static String addWayPoints(Set<String> waypoints, String extras) {
        String result = "";
        try {
            JSONObject mainObject = (extras.length() > 0) ? new JSONObject(extras): new JSONObject();
            int i = 0;
            for (String waypoint : waypoints)
                mainObject.put("wpt_" + i++, waypoint);
            result = mainObject.toString();
        } catch (JSONException je) {
            Logger.e(TAG, "JSON error adding waypoints" + je.getMessage());
        }
        return result;
    }


    // delete the list of waypoints from the extras string
    public static String delWayPoints(String extras) {
        String result = "";
        if (extras.length() == 0) return result;
        try {
            JSONObject mainObject = new JSONObject(extras);
            Iterator<String> keys = mainObject.keys();
            ArrayList<String> removeKeys = new ArrayList<>();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("wpt"))
                    removeKeys.add(key);
            }
            for (String key: removeKeys)
                mainObject.remove(key);

            result = mainObject.toString();
        } catch (JSONException je) {
            Logger.e(TAG, "JSON error removing waypoints" + je.getMessage());
        }
        return result;
    }


    // extract the list of waypoints from the extras string
    public static ArrayList<String> getWayPoints(String extras) {
        ArrayList<String> waypoints = new ArrayList<>();
        if (extras.length() == 0) return waypoints;
        try {
            JSONObject mainObject = new JSONObject(extras);
            Iterator<String> keys = mainObject.keys();
            while (keys.hasNext()){
                String key = keys.next();
                if (key.startsWith("wpt"))
                    waypoints.add((String) mainObject.get(key));
            }
        } catch (JSONException je) {
            Logger.e(TAG, "JSON error extracting waypoints" + je.getMessage());
        }
        return waypoints;
    }

    public static String buildExtras(SummaryProvider.Summary summary, boolean edited, int num_segments,
                                     long[] cum_times, float[] cum_distances, String poll_interval, String gps_accuracy, float mov_average) {
        String extras = summary.getExtras().trim();
        try {

            JSONObject obj = (extras.length() > 0)? new JSONObject(extras): new JSONObject();
            obj.put("edited", edited);
            for (int segment = 1; segment <= num_segments; segment++) {
                obj.put("time_" + segment, cum_times[segment - 1]);
                obj.put("distance_" + segment, cum_distances[segment - 1]);
                float speed = (float) UtilsMap.calcKmphSpeed(cum_distances[segment - 1] * 1000.f, cum_times[segment - 1]);
                obj.put("speed_" + segment, speed);
                obj.put("poll_interval", poll_interval);
                obj.put("gps_accuracy", gps_accuracy);
            }
            obj.put("mov_avg_speed", mov_average);
            extras = obj.toString();
        } catch (JSONException je)  {
            Logger.e(TAG, "JSON formatting error in encoding Summary extras " + je.getMessage());
        }
        return extras;
    }

    public static String buildWahooExtras(SummaryProvider.Summary summary, String wahooSpeeds) {
        String extras = summary.getExtras().trim();
        try {
            JSONObject obj = (extras.length() > 0)? new JSONObject(extras): new JSONObject();
            obj.put("wahoo", wahooSpeeds);
            extras = obj.toString();
        } catch (JSONException je)  {
            Logger.e(TAG, "JSON formatting error in encoding Wahoo extras " + je.getMessage());
        }
        return extras;
    }

    public static String getWahooExtras(String extras) {
        String wahooSpeeds = "";
        try {
            JSONObject obj = (extras.length() > 0)? new JSONObject(extras): new JSONObject();
            wahooSpeeds = obj.getString("wahoo");
        } catch (JSONException je)  {
            Logger.e(TAG, "JSON formatting error in getting Wahoo extras " + je.getMessage());
        }
        return wahooSpeeds;
    }

    public static String buildInitialExtras() {
        String extras = "";
        try {
            JSONObject obj = new JSONObject();
            obj.put("edited", false);
            obj.put("mytripoo", Constants.VERSION);
            extras = obj.toString();
        } catch (JSONException je)  {
            Logger.e(TAG, "JSON formatting error in building initial extras " + je.getMessage());
        }
        return extras;
    }

    // if the extras field has a key mytripoo, then it is not an external file
    public static boolean isImported(String extras) {
        try {
            if (extras.length() > 0) {
                JSONObject mainObject = new JSONObject(extras);
                Iterator<String> keys = mainObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.startsWith("mytripoo"))
                        return false;
                }
            }
        } catch (JSONException je) {
            Logger.d(TAG, "JSON error checking extras for import " + je.getMessage());
        }
        return true;
    }


    // if the extras field has a category_edited
    public static boolean isCategoryEdited(String extras, String categoryEditedKey) {
        try {
            if (extras.length() > 0) {
                JSONObject mainObject = new JSONObject(extras);
                Iterator<String> keys = mainObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.startsWith(categoryEditedKey))
                        return true;
                }
            }
        } catch (JSONException je) {
            Logger.d(TAG, "JSON error checking extras for category edit " + je.getMessage());
        }
        return false;
    }

    // if the extras field has a location_edited
    public static boolean isLocationEdited(String extras, String locationEditedKey) {
        try {
            if (extras.length() > 0) {
                JSONObject mainObject = new JSONObject(extras);
                Iterator<String> keys = mainObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.startsWith(locationEditedKey))
                        return true;
                }
            }
        } catch (JSONException je) {
            Logger.d(TAG, "JSON error checking extras for location edit " + je.getMessage());
        }
        return false;
    }

    // if the extras field has a elevation_edited
    public static boolean isElevationEdited(String extras, String elevationEditedKey) {
        try {
            if (extras.length() > 0) {
                JSONObject mainObject = new JSONObject(extras);
                Iterator<String> keys = mainObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.startsWith(elevationEditedKey))
                        return true;
                }
            }
        } catch (JSONException je) {
            Logger.d(TAG, "JSON error checking extras for elevation edit " + je.getMessage());
        }
        return false;
    }

    // submit the request and get a JSON response
    public static String oldgetJSON(String address) {
        StringBuilder sb = new StringBuilder();
        try {
            URL url = new URL(address);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String nextLine;
            while ((nextLine = reader.readLine()) != null) {
                sb.append(nextLine);
            }
        } catch (Exception je) {
            Logger.d(TAG, "JSON error in getting a response " + je.getMessage());
            return NO_RESPONSE;
        }
        return sb.toString();
    }

    // make a synchronous network call
    public static String getJSON(String url) {
       // SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
       // int localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "1"));

        OkHttpClient client = new OkHttpClient();
        Request.Builder builder = new Request.Builder();
        builder.url(url);
        Request request = builder.build();
        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        }catch (IOException ie){
            Logger.e(TAG, "IO Exception: " + ie.getMessage());
        }
        return NO_RESPONSE;
    }

    public static Map<String, String> parseOpenWeatherJSON(String jsonString) {
        Map<String, String> map = new HashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);

            // get the weather and description
            JSONArray weather_data = jsonObject.getJSONArray("weather");
            JSONObject jsonObj = weather_data.getJSONObject(0);
            String weather_main = get_value(jsonObj, "main");
            map.put("weather_main", weather_main);
            String weather_description = get_value(jsonObj, "description");
            map.put("weather_description", weather_description);

            // get the temperature
            jsonObj = new JSONObject(jsonObject.getString("main"));
            String temp = get_value(jsonObj, "temp");
            map.put("temp", temp);
            String temp_min = get_value(jsonObj,"temp_min");
            map.put("temp_min", temp_min);
            String temp_max = get_value(jsonObj, "temp_max");
            map.put("temp_max", temp_max);
            String humidity = get_value(jsonObj, "humidity");
            map.put("humidity", humidity);

            // get the wind
            jsonObj = new JSONObject(jsonObject.getString("wind"));
            String wind_speed = get_value(jsonObj, "speed");
            map.put("wind_speed", wind_speed);
            String wind_deg = get_value(jsonObj, "deg");
            map.put("wind_deg", wind_deg);

            // get the clouds
            jsonObj = new JSONObject(jsonObject.getString("clouds"));
            String clouds_all = get_value(jsonObj, "all");
            map.put("clouds_all", clouds_all);

            // get the timestamp
            String unix_timestamp = jsonObject.getString("dt");
            map.put("open_timestamp", unix_timestamp);

            // get the sunset and sunrise
            jsonObj = new JSONObject(jsonObject.getString("sys"));
            int sunrise = Integer.parseInt(get_value(jsonObj, "sunrise"));
            map.put("sunrise_timestamp", sunrise + "");
            int sunset = Integer.parseInt(get_value(jsonObj, "sunset"));
            map.put("sunset_timestamp", sunset + "");

            String name = jsonObject.getString("name");
            map.put("name", name);

        }catch (JSONException je) {
            return map;
        }

        return map;
    }

    public static String buildDetailExtras(String extras, Number temp, Number humidity, Number wind_speed, Number wind_deg) {
        try {
            JSONObject obj = (extras.length() > 0)? new JSONObject(extras): new JSONObject();
            obj.put("temp", UtilsMisc.formatFloat(temp.floatValue(), 2));
            obj.put("humidity", UtilsMisc.formatFloat(humidity.floatValue(), 2));
            obj.put("wind_speed", UtilsMisc.formatFloat(wind_speed.floatValue(), 2));
            obj.put("wind_deg", UtilsMisc.formatFloat(wind_deg.floatValue(), 2));
            extras = obj.toString();
        } catch (JSONException je)  {
            Logger.e(TAG, "JSON formatting error in building detail extras " + je.getMessage());
        }
        return extras;
    }

    // add the power to the extras string
    public static String put_JSON_string(String value, String key, String extras) {
        String result = "";
        try {
            JSONObject mainObject = (extras.length() > 0) ? new JSONObject(extras): new JSONObject();
            mainObject.put(key, value);
            result = mainObject.toString();
        } catch (JSONException je) {
            Logger.e(TAG, "JSON error adding power" + je.getMessage());
        }
        return result;
    }

    // add the moving average to the extras string
    public static String put_JSON_float(float value, String key, String extras) {
        String result = "";
        try {
            JSONObject mainObject = (extras.length() > 0) ? new JSONObject(extras): new JSONObject();
            mainObject.put(key, value);
            result = mainObject.toString();
        } catch (JSONException je) {
            Logger.e(TAG, "JSON error adding power" + je.getMessage());
        }
        return result;
    }

    public static String get_JSON_value(String json_str, String key) {
        String result = "";
        try {
            JSONObject mainObject = (json_str.length() > 0) ? new JSONObject(json_str): new JSONObject();
            result = get_value(mainObject, key);
        } catch (JSONException je) {
            Logger.e(TAG, "JSON error getting value" + je.getMessage());
        }
        return result;
    }

/*
    private String getExtrasParmString(String parm) {
        String parmValue = "";
        try {
            if (extras.length() > 0) {
                JSONObject jsonObject = new JSONObject(extras);
                parmValue = String.valueOf(jsonObject.getString(parm));
            }
        } catch(JSONException je){
            //Log.d("JSON", "Could not parse " + parm + " from Detail extras " + je.getMessage());
        }
        return parmValue;
    }
  */

    private static String get_value(JSONObject jsonObject, String key) throws JSONException {
        if (jsonObject.isNull(key))
            return "0";
        return jsonObject.getString(key);
    }

    public static boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }
}

