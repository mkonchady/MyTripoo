package org.mkonchady.mytripoo.gps;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.location.LocationListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.utils.RepairTrip;
import org.mkonchady.mytripoo.utils.SMSTracker;
import org.mkonchady.mytripoo.activities.MainActivity;
import org.mkonchady.mytripoo.activities.ShowDirectionsActivity;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LocationService extends Service implements LocationListener {

    // Unique Identification Number for the Notification.
    private NotificationManager mNotificationManager;
    private final int NOTIFICATION = 157;
    //private int MAX_READINGS;
    private long MAX_TRIP_TIME;
    private long COMMIT_PERIOD;
    private boolean COMMIT_IN_PROGRESS;
    private long NEXT_BEEP;        // in milliseconds
    private long BEEP_PERIOD;
    private boolean autostop;
    private int STOP_DISTANCE;      // in meters
    private TextToSpeech textToSpeech;

    public SummaryDB summaryTable = null;           // summary table DB handler
    public DetailDB detailTable = null;             // detail table DB handler

    private SharedPreferences sharedPreferences;
    int localLog = 0;
    LocationRequest mLocationRequest;
    Context context = null;
    Location mCurrentLocation = null;
    Location mPrevLocation = null;
    Location firstLocation = null;
    Vibrator vibrator = null;
    FusedLocationProviderClient mFusedLocationClient = null;
    LocationCallback mLocationCallback;

    // follow trip fields
    int follow_trip_id = -1;
    int follow_counter = -1;

    // keys contains a generalized lat/lon and associated detail timestamp
  //  final HashMap<String, Long> waypointLocationKeys = new HashMap<>();
    final HashMap<String, Long> waypointLocations = new HashMap<>();

    // keep track of distance to waypoint to detect if approaching or moving away
    int prev_waypoint_distance = 1000;
    Map.Entry<String, Long> prev_waypoint = null;

    public final String TAG = "LocationService";
    private final IBinder mBinder = new LocalBinder();

    private static class LocalBinder extends Binder {
        // LocationService getService() {
        //    return LocationService.this;
        //}
    }

    // called when the service is initiated, the lat and lon must be set in the preferences
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();

        // get the first lat and lon from the caller
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        double lat = sharedPreferences.getFloat(Constants.PREF_LATITUDE, 0.0f);
        double lon = sharedPreferences.getFloat(Constants.PREF_LONGITUDE, 0.0f);
        long locationTime = sharedPreferences.getLong(Constants.PREF_LOCATION_INIT_TIME, 0L);
        String beep = sharedPreferences.getString(Constants.PREF_BEEP_PERIOD, "600") ;
        BEEP_PERIOD = Integer.parseInt(beep) * 60 * 1000;       // in milliseconds
        NEXT_BEEP = System.currentTimeMillis() + BEEP_PERIOD;
        autostop = !sharedPreferences.getString(Constants.PREF_AUTOSTOP, "No").equals("No");
        STOP_DISTANCE = (autostop)? Integer.parseInt(sharedPreferences.getString(Constants.PREF_AUTOSTOP, "0")): 0;

        //MAX_READINGS = Integer.parseInt(this.sharedPreferences.getString(Constants.PREF_NUMBER_TRIP_READINGS, "0"));
        MAX_TRIP_TIME = this.sharedPreferences.getLong(Constants.PREF_TRIP_DURATION, Constants.MILLISECONDS_PER_HOUR * 4); // set in gps service from getLocationRequest
        COMMIT_PERIOD = this.sharedPreferences.getLong(Constants.PREF_BIKE_COMMIT_DURATION, Constants.MILLISECONDS_PER_HOUR * 4); // in msecs.
        COMMIT_IN_PROGRESS = false;

        //long currentTime = System.currentTimeMillis();
        mPrevLocation = createLocation(lat, lon, locationTime);
        mCurrentLocation = createLocation(lat, lon, locationTime);

        // initialize the parameters of the location request based on the preferences
        mLocationRequest = UtilsMap.getLocationRequest(context);
        Logger.d(TAG, "Set duration preference to: " + sharedPreferences.getLong(Constants.PREF_TRIP_DURATION,
                Constants.MILLISECONDS_PER_HOUR * 4), localLog);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    mCurrentLocation = location;
                    onLocationChanged(location);
                }
            }
        };

        checkPermission();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null /* Looper */);

        // check if an SMS number was given, if not set the SMS time to a large number
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String smsNumber = sharedPreferences.getString(Constants.PREF_SMS_NUMBER, "0");
        String sendSMS = sharedPreferences.getString(Constants.PREF_SMS_SEND, "no");
        if (sendSMS.equals("no") || smsNumber.length() < 9) {
            editor.putLong(Constants.PREF_SMS_TIME,
                    System.currentTimeMillis() + 365 * Constants.MILLISECONDS_PER_DAY);
        } else {
            String smsPeriod = sharedPreferences.getString(Constants.PREF_SMS_PERIOD, "60");
            int iPeriod = Integer.parseInt(smsPeriod);
            editor.putLong(Constants.PREF_SMS_TIME,
                    System.currentTimeMillis() + iPeriod * Constants.MILLISECONDS_PER_MINUTE);
            editor.putLong(Constants.PREF_SMS_DISTANCE, 0L);
        }

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        summaryTable = new SummaryDB(SummaryProvider.db);
        detailTable = new DetailDB(DetailProvider.db);

        // create a trip with running status and save the trip in shared preferences
        int trip_id = sharedPreferences.getInt(Constants.PREF_TRIP_ID, -1);
        boolean wakeup_gps = sharedPreferences.getBoolean(Constants.PREF_WAKEUP_GPS, false);

        // CREATE THE SUMMARY and first detail row for a new trip
        if (trip_id == -1 && !wakeup_gps) {
            createNewTrip();
            trip_id = this.sharedPreferences.getInt(Constants.PREF_TRIP_ID, -1);
        }
        //editor.putBoolean(Constants.RESTART, false);
        follow_trip_id = sharedPreferences.getInt(Constants.PREF_FOLLOW_TRIP, -1);
        follow_counter = sharedPreferences.getInt(Constants.PREF_FOLLOW_COUNTER, 0);
        if (follow_trip_id != -1) {
            // save a list of waypoint locations
            ArrayList<String> waypoints = UtilsJSON.getWayPoints(summaryTable.getExtras(context, follow_trip_id));
            for (String waypoint: waypoints) {
                String[] parts = waypoint.split(UtilsMap.DELIMITER);
                int latw = Integer.parseInt(parts[0]); int lonw = Integer.parseInt(parts[1]);
                DetailProvider.Detail detail = detailTable.getDetail(context, follow_trip_id, latw, lonw, Constants.ALL_SEGMENTS);
                if (detail != null)
                    waypointLocations.put(waypoint, detail.getTimestamp());
               // for (String key: UtilsMap.gen_keys_distance(latw, lonw, HUNDRED_METERS)) {
               //     waypointLocationKeys.put(key, detail.getTimestamp());
               // }
            }
            Logger.d(TAG, "Created " + waypointLocations.size() + " waypoints", localLog);
        }

        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (wakeup_gps)
            Logger.d(TAG, "---- Restart of GPS Location Service (Trip: " + trip_id + ") ------", localLog);
        else
            Logger.d(TAG, "---- Start of GPS Location Service (Trip: " + trip_id + ") ------", localLog);

        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) textToSpeech.setLanguage(Locale.ENGLISH);
            }
        });

        // location service flag is reset in onDestroy()
        editor.putBoolean(Constants.LOCATION_SERVICE_RUNNING, true);
        editor.putBoolean(Constants.PREF_WAKEUP_GPS, false);
        editor.apply();

        // Display a notification in the action bar
        showNotification();
    }

    // called when the service is started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d(TAG, "onStartCommand ...", localLog);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Logger.d(TAG, "onDestroy ...", localLog);
        //stopLocationUpdates();  // stop location updated here
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);

        // if a restart, then skip post-processing
        boolean wakeup_gps = sharedPreferences.getBoolean(Constants.PREF_WAKEUP_GPS, false);
        final int trip_id = sharedPreferences.getInt(Constants.PREF_TRIP_ID, -1);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(Constants.LOCATION_SERVICE_RUNNING, false);

        // repair the readings and update the summary status
        if (trip_id != -1 && !wakeup_gps) {
            cleanUp(trip_id, false);
            editor.putInt(Constants.PREF_TRIP_ID, -1);
            editor.putString(Constants.PREF_LOG_FILE, "");
        }
        editor.apply();
        //mGoogleApiClient.disconnect();
        mNotificationManager.cancel(NOTIFICATION);
        Logger.d(TAG, "onDestroy ", localLog);
    }

    @Override
    public void onLocationChanged(@NonNull  Location location) {
        Logger.d(TAG, "onLocationChanged: " + location.getLatitude() + "," + location.getLongitude(), localLog);
        if (COMMIT_IN_PROGRESS) {
            Logger.d(TAG, "Skipping readings till commit is complete", localLog);
            return;
        }

        // save the first location
        if (firstLocation == null) firstLocation = location;

        // Get the location
        SharedPreferences.Editor editor = sharedPreferences.edit();
        long lastLocationTime = sharedPreferences.getLong(Constants.PREF_LAST_LOCATION_TIME, System.currentTimeMillis());
        editor.putLong(Constants.PREF_LAST_LOCATION_TIME, System.currentTimeMillis());
        editor.putFloat(Constants.PREF_LATITUDE, (float) location.getLatitude());
        editor.putFloat(Constants.PREF_LONGITUDE, (float) location.getLongitude());
        editor.apply();
        int trip_id = sharedPreferences.getInt(Constants.PREF_TRIP_ID, -1);
        if (trip_id == -1) return;  // verify that the trip is in progress

        // update the distance in the summary record and create a new detail record
        mCurrentLocation = location;
        double dist = UtilsMap.getMeterDistance(mPrevLocation, mCurrentLocation);
        summaryTable.setSummaryCumulativeDistance(context, (int) dist, trip_id);

        // save the distance and avg. speed in the shared preferences, check if time to beep
        SummaryProvider.Summary summary = summaryTable.getSummary(context, trip_id);
        long currentTime = System.currentTimeMillis();
        int distance = summaryTable.getDistance(context, trip_id);
        editor.putInt(Constants.PREF_DISTANCE, distance);
        long currentTripDuration = (currentTime - summary.getStart_time());             // in milliseconds
        if (currentTime >= NEXT_BEEP) {          // crossed or equal a beep time
            //NEXT_BEEP = NEXT_BEEP + BEEP_PERIOD;
            NEXT_BEEP = System.currentTimeMillis() + BEEP_PERIOD;
            soundTime(currentTripDuration);
            Logger.d(TAG, "Sounded beep after ", localLog);
        }
        float avg_speed = (float) UtilsMap.calcKmphSpeed(distance, currentTripDuration);    // in kmph
        summary.setAvg_speed(avg_speed);
        editor.putFloat(Constants.PREF_AVG_SPEED, avg_speed);
        float ins_speed = (float) UtilsMap.calcKmphSpeed(dist, currentTime - lastLocationTime);
        editor.putFloat(Constants.PREF_INS_SPEED, ins_speed);
        int batteryLevel = UtilsMisc.getBatteryLevel(context);
        editor.putFloat(Constants.PREF_BATTERY, batteryLevel);

        // create a detail record with timestamp, location, and battery level
        createDetailRow(trip_id, currentTime, batteryLevel, 0.0f);

        // update the summary with other details
        updateSummary(trip_id, summary);

        // send an SMS if necessary
        long smsTime = sharedPreferences.getLong(Constants.PREF_SMS_TIME, -1L);
        if ((smsTime > 0) && (currentTime > smsTime)) {
            String smsNumber = sharedPreferences.getString(Constants.PREF_SMS_NUMBER, "0");
            long smsDistance = sharedPreferences.getLong(Constants.PREF_SMS_DISTANCE, 0L);
            smsDistance += distance;
            String message = " " + UtilsMisc.formatFloat(smsDistance / 1000.0f, 1) + " kms." +
                    " https://maps.google.com/maps?q=" +
                    mCurrentLocation.getLatitude() + "+" + mCurrentLocation.getLongitude();
            SMSTracker.sendSMSMessage(smsNumber, message);
            Logger.d(TAG, "SMS: !" + smsNumber + "! message: !" + message + "!", localLog);

            // update the period for the next SMS
            String smsPeriod = sharedPreferences.getString(Constants.PREF_SMS_PERIOD, "60");
            int iPeriod = Integer.parseInt(smsPeriod);
            editor.putLong(Constants.PREF_SMS_TIME, currentTime + iPeriod * Constants.MILLISECONDS_PER_MINUTE);
        }

        // check if it is time to automatically stop location updates based on end time,
        // status change from main activity, or there exist no running trips. Also change trip status
        if (endTrip(summary, currentTripDuration) )  {
            textToSpeech.speak("Stopping trip.", TextToSpeech.QUEUE_FLUSH, null);
            soundTime(currentTripDuration);
            stopSelf();                                 // triggers onDestroy()
            summaryTable.setSummaryStatus(context, Constants.FINISHED, trip_id);
        }

        // check if we are following a trip and raise the alarm if necessary
        final int NUM_FOLLOW_ALARM = 2;                 // number of times to sound the alarm when going off the followed route
        final int MIN_FOLLOW_DISTANCE = 500;            // minimum meter  distance from a followed route
        follow_counter = sharedPreferences.getInt(Constants.PREF_FOLLOW_COUNTER, 0);
        long iduration = System.currentTimeMillis() - sharedPreferences.getLong(Constants.PREF_FIRST_LOCATION_TIME, 0L);
        if ( (follow_trip_id != -1) && (iduration > Constants.MILLISECONDS_PER_MINUTE * 2) ) {
           int lat = (int) Math.round(location.getLatitude() * Constants.MILLION);
           int lon = (int) Math.round(location.getLongitude() * Constants.MILLION);
            // sound the alarm, if we are off track
            int[] closeLatLon = detailTable.getClosestDetailByLocation(context, follow_trip_id, lat, lon, Constants.ALL_SEGMENTS, 1);
            if (UtilsMap.getMeterDistance(lat, lon, closeLatLon[0], closeLatLon[1]) > MIN_FOLLOW_DISTANCE &&
                    follow_counter < NUM_FOLLOW_ALARM) {
                editor.putInt(Constants.PREF_FOLLOW_COUNTER, follow_counter + 1);
                Logger.d(TAG, "Sounded alarm", localLog);
                UtilsMisc.genSounds(context, R.raw.alarm);
            }

            // check if we are nearing any waypoints
            if (waypointLocations.size() > 0) {
                Map.Entry<String, Long> closest_waypoint = find_closest_waypoint(lat, lon, avg_speed);
                if (closest_waypoint != null) {
                    String[] parts = closest_waypoint.getKey().split(UtilsMap.DELIMITER);
                    int lat0 = Integer.parseInt(parts[0]);
                    int lon0 = Integer.parseInt(parts[1]);
                    int m_distance = (int) UtilsMap.getMeterDistance(lat0, lon0, lat, lon);
                    if (m_distance < prev_waypoint_distance) { // sound alarm if we are nearing a waypoint
                        prev_waypoint_distance = m_distance;
                        prev_waypoint = closest_waypoint;
                        Logger.d(TAG, "Making call to sound directions", localLog);
                        sound_alarm(closest_waypoint);
                    } else {  // we are moving away from the waypoint, remove the waypoint
                        prev_waypoint_distance = 1000;
                        prev_waypoint = null;
                        waypointLocations.remove(closest_waypoint.getKey());
                        Logger.d(TAG, "Removed waypoint " + closest_waypoint.getKey() + " Size: " + waypointLocations.size(), localLog);
                    }
                }
            }
        }
        editor.apply();
        mPrevLocation = mCurrentLocation;

        // check if this is a long trip and create a new trip
        //if (summaryTable.getDetailCount(context, trip_id) > MAX_READINGS || currentTripDuration > COMMIT_PERIOD) {
        if (currentTripDuration > COMMIT_PERIOD) {
            COMMIT_IN_PROGRESS = true;
            cleanUp(trip_id, true);
        }
    }

    // find a waypoint that is close to the passed lat/lon or return null
    private Map.Entry<String, Long> find_closest_waypoint(int lat, int lon, float speed) {
        final int MIN_WAYPOINT_DISTANCE = getMinDistance(speed);      // minimum meter distance from a waypoint
        for (Map.Entry<String, Long> waypoint: waypointLocations.entrySet()) {
            String[] parts = waypoint.getKey().split(UtilsMap.DELIMITER);
            int lat0 = Integer.parseInt(parts[0]);
            int lon0 = Integer.parseInt(parts[1]);
            int m_distance = (int) UtilsMap.getFastMeterDistance(lat0, lon0, lat, lon);
            if (m_distance <= MIN_WAYPOINT_DISTANCE) {
                Logger.d(TAG, "Speed: " + speed + " Distance: " + m_distance + " from Waypoint: " + waypoint.getKey(), localLog);
                return waypoint;
            }
        }
        return prev_waypoint;
    }

    private void soundTime(final long currentTripDuration) {
        int hours = UtilsDate.getTimeHours(currentTripDuration);
        int minutes = UtilsDate.getTimeMinutes(currentTripDuration - (hours * 3600 * 1000));
        int seconds = UtilsDate.getTimeSeconds(currentTripDuration - (hours * 3600 * 1000) - (minutes * 60 * 1000));
        String speak = minutes + " minutes and " + seconds + " seconds";
        if (hours > 0)
            speak = hours + " hours and " + speak;
        textToSpeech.speak(speak, TextToSpeech.QUEUE_FLUSH, null);
    }

    // check if the trip is about to end -- at least 5 minute trip, distance to first location is less than 50 meters
    private boolean endTrip(SummaryProvider.Summary summary, long tripDuration) {

        if (!autostop || tripDuration < Constants.MILLISECONDS_PER_MINUTE * 5)
            return false;

        if (summary.getStatus().equalsIgnoreCase(Constants.FINISHED)) {
            Logger.d(TAG, "Ending trip because trip has been manually stopped ", localLog);
            return true;
        }

        if (!summaryTable.isTripinProgress(context)) {
            Logger.d(TAG, "Ending trip because no trip is in progress", localLog);
            return true;
        }

        if (UtilsMap.getMeterDistance(mCurrentLocation, firstLocation) < STOP_DISTANCE) {
            Logger.d(TAG, "Stopping at " + UtilsMap.getMeterDistance(mCurrentLocation, firstLocation) + " meters from start.", localLog);
            return true;
        }

        if ( (System.currentTimeMillis() - summary.getEnd_time()) > Constants.MILLISECONDS_PER_MINUTE) {
            Logger.d(TAG, "Ending trip because current time: " + System.currentTimeMillis() + " exceeds end time: " + summary.getEnd_time(), localLog);
            return true;
        }

        return false;
    }

    // update the trip status and run repair trip to fix the readings
    private void cleanUp(int trip_id, boolean createNewTrip) {
        Logger.d(TAG, "Last recorded cum. distance: " + summaryTable.getDistance(context, trip_id), localLog);
        summaryTable.setSummaryStatus(context, Constants.FINISHED, trip_id);
        summaryTable.setSummaryEndTimestamp(context, System.currentTimeMillis(), trip_id);
        // Parms - null ProgressListener, trip id, not imported, calc. segments
        try {
            final Object[] params = {trip_id, createNewTrip};
            new AsyncRepair().execute(params).get();
        } catch (InterruptedException ie) {
            Logger.d(TAG, " Repair of trip : " + trip_id + " was interrupted " + ie.getMessage(), localLog);
        }
        catch (java.util.concurrent.ExecutionException ce) {
            Logger.d(TAG, " Concurrent Execution of trip: " + trip_id + " error " + ce.getMessage(), localLog);
        }
    }

    // get the minimum distance to a waypoint based on speed and polling interval
    // if the polling interval is high, then the minimum distance is lower
    // if the speed is high, then minimum distance is higher
    private int getMinDistance(float speed) {
        int poll_interval = UtilsMap.get_poll_interval(sharedPreferences);
        int factor = 1;
        switch (poll_interval) {
            //case 1000: factor = 1; break;
            //case 5000: factor = 1; break;
            case 10000: factor = 2; break;
            case 20000: factor = 4; break;
        }
        final int FIFTY_METERS = 50 * factor;
        final int HUNDRED_METERS = 100 * factor;
        final int TWO_HUNDRED_METERS = 200 * factor;

        if (speed >= 0 && speed < 20) return FIFTY_METERS;
        else if (speed >= 20 && speed < 40) return HUNDRED_METERS;
        else return TWO_HUNDRED_METERS;
    }

    // sound an alarm with the directions to take
    private void sound_alarm(Map.Entry<String, Long> waypoint) {
        final int WAYPOINT_NEIGHBOUR_DISTANCE = 2;      // find detail records 2 before and after waypoint
        long timestamp = waypoint.getValue();           // get the timestamp
        // get the detail associated with the waypoint and find a pair of neighbours PREF_DISTANCE before and after waypoint.
        DetailProvider.Detail detail = detailTable.getDetail(context, timestamp, follow_trip_id);
        DetailProvider.Detail[] details = detailTable.getDetailNeighbours(
                context, follow_trip_id, detail.getIndex(), WAYPOINT_NEIGHBOUR_DISTANCE);
        if (details[0] != null && details[1] != null) {
            Logger.d(TAG, "Played audio .... ", localLog);
            int bearing0 = details[0].getBearing(); int bearing1 = details[1].getBearing();
            UtilsMisc.genSounds(context, UtilsMap.getResid(bearing0, bearing1));
            vibrator.vibrate(2000);
            showDirections(bearing0, bearing1);
        } else {
            Logger.d(TAG, "Did not find details within waypoint range", localLog);
        }
    }

    // show the directions on a screen
    private void showDirections(int bearing0, int bearing1) {
        boolean screenShowing = sharedPreferences.getBoolean(Constants.PREF_DIRECTION_SCREEN_VISIBLE, false);
        if (!screenShowing) {
            String directions = UtilsMap.getDirection(bearing0, bearing1);
            Intent intent = new Intent(getBaseContext(), ShowDirectionsActivity.class);
            intent.putExtra(Constants.DIRECTIONS, directions);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            context.startActivity(intent);
        }
    }

    // update summary
    private void updateSummary(int trip_id, SummaryProvider.Summary summary) {
        int num_readings = summaryTable.getDetailCount(context, trip_id);
        long time_gap = (mCurrentLocation.getTime() - mPrevLocation.getTime());
        //if (time_readings < 0) time_readings = 0.0f;

        // calculate the speed if the time between readings is at least 5 seconds
        float dist_readings = mCurrentLocation.distanceTo(mPrevLocation);
        //float speed = (time_gap >= 5000L) ? (3600.0f *  dist_readings) / time_gap: 0.0f;
        float speed = (float) UtilsMap.calcKmphSpeed(dist_readings, time_gap);
        float min_speed  = (speed > 0) && (speed < summary.getMin_speed()) ? speed: summary.getMin_speed();
        float max_speed  = Math.max(speed, summary.getMax_speed());
        float avg_speed = summary.getAvg_speed();       // calculated in caller
        //float avg_speed = ((summary.getAvg_speed() * (num_readings - 1)) + speed) / num_readings;

        float elevation = Math.round(mCurrentLocation.getAltitude());
        float min_elevation  = (elevation < summary.getMin_elevation()) ? elevation: summary.getMin_elevation();
        float max_elevation  = (elevation > summary.getMax_elevation()) ? elevation: summary.getMax_elevation();

        float accuracy = (mCurrentLocation.hasAccuracy()) ? mCurrentLocation.getAccuracy(): 100.0f;
        float min_accuracy  = Math.min(accuracy, summary.getMin_accuracy());
        float max_accuracy  = Math.max(accuracy, summary.getMax_accuracy());
        float avg_accuracy  = ((summary.getAvg_accuracy() * (num_readings - 1)) + accuracy) / num_readings;

        float min_time_readings  = (time_gap > 0) && (time_gap < summary.getMin_time_readings()) ?
                time_gap: summary.getMin_time_readings();
        float max_time_readings  = (time_gap > summary.getMax_time_readings()) ?
                time_gap: summary.getMax_time_readings();
        float avg_time_readings = ((summary.getAvg_time_readings() * (num_readings - 1)) + time_gap) / num_readings;

        float min_dist_readings  = (dist_readings > 0) && (dist_readings < summary.getMin_dist_readings()) ?
                dist_readings: summary.getMin_dist_readings();
        float max_dist_readings  = Math.max(dist_readings, summary.getMax_dist_readings());
        float avg_dist_readings  = ((summary.getAvg_dist_readings() * (num_readings - 1)) + dist_readings) / num_readings;

        ContentValues values = new ContentValues();
        values.put(SummaryProvider.START_TIME, summary.getStart_time());
        values.put(SummaryProvider.NAME, summary.getName());
        values.put(SummaryProvider.END_TIME, summary.getEnd_time());
        values.put(SummaryProvider.DISTANCE, summary.getDistance());
        values.put(SummaryProvider.AVG_LAT, summary.getAvg_lat());
        values.put(SummaryProvider.AVG_LON, summary.getAvg_lon());
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
        values.put(SummaryProvider.STATUS, summary.getStatus());
        values.put(SummaryProvider.EXTRAS, summary.getExtras());
        summaryTable.setSummaryParameters(context, values, trip_id);
    }

    // create a new trip
    private void createNewTrip() {
        int trip_id = this.summaryTable.addSummary(this.context, SummaryProvider.createSummary(MAX_TRIP_TIME, true));
        Logger.d(TAG, "Set the max trip time for the new trip " + trip_id + ": " + MAX_TRIP_TIME, localLog);
        SummaryProvider.Summary summary = summaryTable.getSummary(context, trip_id);
        Logger.d(TAG, "End time for trip " + trip_id + ": " + summary.getEnd_time(), localLog);
        createDetailRow(trip_id, System.currentTimeMillis(), UtilsMisc.getBatteryLevel(this.context), 0.0f);
        SharedPreferences.Editor editor = this.sharedPreferences.edit();
        // update the cumulative distance in preferences
        long smsDistance = sharedPreferences.getLong(Constants.PREF_SMS_DISTANCE, 0L);
        int distance = sharedPreferences.getInt(Constants.PREF_DISTANCE, 0);
        smsDistance += distance;
        editor.putLong(Constants.PREF_SMS_DISTANCE, smsDistance);
        editor.putInt(Constants.PREF_TRIP_ID, trip_id);
        editor.apply();
    }

    // store a detail record with the lat and lon in micro-degrees
    private void createDetailRow(int trip_id, long timestamp, int ibattery, float gradient) {
        int lat = (int) Math.round(mCurrentLocation.getLatitude() * Constants.MILLION);
        int lon = (int) Math.round(mCurrentLocation.getLongitude() * Constants.MILLION);
        float altitude = Constants.DEFAULT_ALTITUDE;
        if (mCurrentLocation.hasAltitude())
           altitude = Math.round(mCurrentLocation.getAltitude());
        int segment = 1;
        float speed = mCurrentLocation.getSpeed();
        int accuracy = Math.round( mCurrentLocation.hasAccuracy()? mCurrentLocation.getAccuracy(): 100);
        int bearing = 0;
        float satinfo = sharedPreferences.getFloat(Constants.GPS_SATINFO, 0.0f);
        int used_satellites = sharedPreferences.getInt(Constants.GPS_USED_SATELLITES, 0);
        DetailProvider.Detail detail = new DetailProvider.Detail(trip_id, segment, lat, lon, ibattery,
                timestamp, altitude, speed, bearing, accuracy, gradient, satinfo, used_satellites, "", "");
        detailTable.addDetail(context, detail);
    }

    private Location createLocation(double lat, double lon, long locationTime) {
        Location location = new Location("dummyProvider");
        location.setLatitude(lat);
        location.setLongitude(lon);
        location.setTime(locationTime);
        if ( (System.currentTimeMillis() - locationTime) > 100000)
            location.setAccuracy(500.0f);
        return location;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // Show a notification while this service is running.
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.locationService);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        if (Constants.preOreo) {
            Notification notification = new Notification.Builder(this)
                    .setContentTitle(getText(R.string.locationServiceLabel))  // the label of the entry
                    .setContentText(text)  // the contents of the entry
                    .setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                    .setSmallIcon(R.drawable.status)  // the status icon
                    .setTicker(text)  // the status text
                    .setWhen(System.currentTimeMillis())  // the time stamp
                    .build();
            mNotificationManager.notify(NOTIFICATION, notification); // Send the notification.
        } else {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("default", "Channel name", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("GPS Trip Analyzer");
            notificationManager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "default")
                    .setContentTitle(getText(R.string.locationServiceLabel))  // the label of the entry
                    .setContentText(text)  // the contents of the entry
                    .setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                    .setSmallIcon(R.drawable.status)  // the status icon
                    .setTicker(text)  // the status text
                    .setWhen(System.currentTimeMillis())  // the time stamp
                    .build();
            startForeground(NOTIFICATION, notification);
        }

    }

    private class AsyncRepair extends AsyncTask<Object, Void, Void>  {
        int trip_id;
        boolean createNewTrip;

        @Override
        protected Void doInBackground(Object... params) {
            trip_id = (int) params[0];
            createNewTrip = (boolean) params[1];
            new RepairTrip(context, null, trip_id, false, true).run();    // verify the readings
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            Logger.d(TAG, "------ End of GPS Location Service (Trip: " + trip_id + ") ------", localLog);
            if (createNewTrip) {
                createNewTrip();
                Logger.d("LocationService", "---- Start of GPS Location Service (Trip: " +
                        sharedPreferences.getInt(Constants.PREF_TRIP_ID, -1) + ") ------", localLog);
            }
            Logger.forceIndex(context);
            COMMIT_IN_PROGRESS = false;
        }
    }

}
