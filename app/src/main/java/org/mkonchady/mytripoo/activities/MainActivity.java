package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.CustomGrid;
import org.mkonchady.mytripoo.gps.GPSService;
import org.mkonchady.mytripoo.gps.LocationService;
import org.mkonchady.mytripoo.gps.LocationStartup;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.utils.RepairTrip;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.SMSTracker;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.text.DecimalFormat;
import java.util.List;

import static org.mkonchady.mytripoo.gps.GPSService.isLocationEnabled;

/*
    Show the button grid to view trips, calendar, satellites, settings, calculator, and compass
 */
public class MainActivity extends Activity {

    public String locationStatus = "";                      // status of location setup
    public String distanceSpeed = "";                       // distance and speed
    private final Handler guiHandler = new Handler();             // to update GUI
    public SummaryDB summaryTable = null;                   // summary table
    public int localLog = 0;                                // log to a local file
    public SharedPreferences sharedPreferences = null;
    public LocationStartup locationStartup = null;
    public boolean checkedGPSPermissions = false;
    public boolean gpsPermissionGranted = false;
    public boolean checkedFSPermissions = false;
    public boolean fsPermissionGranted = false;
    public boolean smsPermissionGranted = false;
    public boolean checkedSMSPermissions = false;

    private Menu menu;
    public Context context;
    public Thread coordinateThread = null;
    public Thread speedThread = null;
    private String tripStatus = Constants.NOT_STARTED;
    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isGooglePlayServicesAvailable()) finish();
        context = getApplicationContext();

        // load the preferences from the settings
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        if (sharedPreferences.getBoolean(Constants.PREF_FIRST_TIME, true)) {
            showGPSPermissionDialog();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(Constants.PREF_FIRST_TIME, false);
            editor.apply();
        }

        if (!isLocationEnabled(context)) {
            showLocationServicesDialog();
        }

        // initialize the shared parameters for a fresh start
        summaryTable = new SummaryDB(SummaryProvider.db);
        tripStatus = getTripStatus(context);
        if (tripStatus.equals(Constants.NOT_STARTED))
            UtilsMisc.initializeSharedParams(context);
        setProvidersSensors(context);

        // start the log file
        if (!tripStatus.equals(Constants.RUNNING)) new Logger(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

        // initial layout
        setContentView(R.layout.activity_main);
        setActionBar();

        // set the icon grid and text
        GridView grid;
        final String[] icon_text = { "Trips", "Calendar", "Satellites","Settings", "Compass", "Calculator" };
        final int[] imageId = { R.drawable.trips, R.drawable.calendar, R.drawable.satellites, R.drawable.settings,
                R.drawable.compass, R.drawable.about };
        final int[] icon_ids = { R.id.action_trips, R.id.action_calendar, R.id.action_satellites,
                R.id.action_settings, R.id.action_compass, R.id.action_calculator };

        CustomGrid adapter = new CustomGrid(MainActivity.this, icon_text, imageId);
        grid = findViewById(R.id.grid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                for (int i = 0; i < icon_text.length; i++) {
                    if (position == i)
                        onOptionsItemSelected(menu.findItem(icon_ids[i]));
                }
            }
        });

        getGPSPermission();
        getForegroundServicePermission();
        //getSMSServicePermission();

        /* set the main button depending on whether a trip is in progress or not
           Trip Control Flow
              NOT_STARTED --> STARTED ---> RUNNING ---> FINISHED
           1. NOT_STARTED: No GPS coordinates located so far
           2. STARTED: GPS Coordinates found, but no trip in progress
           3. RUNNING: GPS Coordinates found and a trip is in progress
           4. FINISHED: A trip is complete and the app must be restarted for a new trip
        */

        switch (tripStatus) {
            case Constants.NOT_STARTED:
                if (gpsPermissionGranted) {
                    locationStartup = new LocationStartup(context, this);
                    getCoordinates(0);
                    getSpeedDistance();
                }
                break;
            case Constants.STARTED:
                guiHandler.post(doUpdateLocationStatus);
                break;
            case Constants.RUNNING:
                locationStatus = getString(R.string.trip_in_progress);
                guiHandler.post(doUpdateLocationStatus);
                break;
            case Constants.FINISHED:
                locationStatus = getString(R.string.trip_ended);
                guiHandler.post(doUpdateLocationStatus);
                break;
        }
        //Log.d(TAG, "onCreate Status " + tripStatus, localLog);
        setButtonLabel();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_bar, menu);
        this.menu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // start the GPS service, if it is not running
        if (gpsPermissionGranted && !UtilsMisc.isServiceRunning(context, GPSService.class))
            context.startService(new Intent(MainActivity.this, GPSService.class));
        tripStatus = getTripStatus(context);

        // toggle the button to start / stop
        setButtonLabel();
        ImageButton mb = findViewById(R.id.mainButton);
        View.OnClickListener mbLis = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tripStatus.equals(Constants.RUNNING)) {
                    locationStatus = getString(R.string.trip_cleanup);
                    guiHandler.post(doUpdateLocationStatus);
                    stopTracking(getApplicationContext());
                } else {
                    startTracking(getApplicationContext());
                }
                setButtonLabel();
            }
        };
        mb.setOnClickListener(mbLis);

        // set the trip status
        switch (tripStatus) {
            case Constants.RUNNING:
                locationStatus = getString(R.string.trip_in_progress);
                getSpeedDistance();
                break;
            case Constants.FINISHED:
                locationStatus = getString(R.string.trip_ended);
                break;
        }
        guiHandler.post(doUpdateLocationStatus);
        setButtonLabel();
        Logger.d(TAG, "onResume Status " + tripStatus, localLog);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_calculator:
                intent = new Intent(MainActivity.this, CalculatorActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_calendar:
                intent = new Intent(MainActivity.this, CalendarActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_compass:
                intent = new Intent(MainActivity.this, CompassActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_satellites:
                intent = new Intent(MainActivity.this, SatellitesActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_settings:
                intent = new Intent(MainActivity.this, PreferencesActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_trips:
                intent = new Intent(MainActivity.this, TripActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_privacy_policy:
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://bit.ly/38LY6O6"));
                startActivity(intent);
                return true;
            case R.id.action_setLocation:
                intent = new Intent(MainActivity.this, TripSetLocationActivity.class);
                startActivityForResult(intent, Constants.LOCATION_CODE);
                return true;
            case R.id.action_sms_location:
                String smsNumber = sharedPreferences.getString(Constants.PREF_SMS_NUMBER, "0");
                float lat = sharedPreferences.getFloat(Constants.PREF_LATITUDE, 0f);
                float lon = sharedPreferences.getFloat(Constants.PREF_LONGITUDE, 0f);
                String message = " SMS from " + " https://maps.google.com/maps?q=" + lat + "+" + lon;
                SMSTracker.sendSMSMessage(smsNumber, message);
                Logger.d(TAG, "SMS: !" + smsNumber + "! message: !" + message + "!", localLog);
                Toast.makeText(context, "Sent SMS Message...", Toast.LENGTH_LONG).show();
                return true;
            case R.id.action_wakeup:    // wake up gps only if a trip is in progress
                if (tripStatus.equals(Constants.RUNNING)) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(Constants.PREF_WAKEUP_GPS, true);
                    editor.apply();
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (UtilsMisc.isServiceRunning(context, LocationService.class)) {
                                context.stopService(new Intent(MainActivity.this, LocationService.class));
                                sleep(3000);
                            }
                            context.startService(new Intent(MainActivity.this, LocationService.class));
                        }
                    });
                }
                return true;
            case R.id.action_current:
                intent = new Intent(MainActivity.this, TripCurrentActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //*-------------------------------------------------
    // get the distance and speed in the background
    //*-------------------------------------------------
    private void getSpeedDistance() {
        speedThread = new Thread(null, getSpeedDistanceBackground, "GetSpeedDistance");
        speedThread.start();
    }

    private final Runnable getSpeedDistanceBackground = new Runnable() {
        public void run() {
            // if not running, show a default speed and distance
            if (!(tripStatus.equals(Constants.RUNNING))) {
                distanceSpeed = formatSpeedDistance(0, 0.0f, 0.0f);
                guiHandler.post(doUpdateDistanceSpeed);
                return;
            }
            while (tripStatus.equals(Constants.RUNNING)) {
                int iDistance = sharedPreferences.getInt(Constants.PREF_DISTANCE, 0);
                float aSpeed = sharedPreferences.getFloat(Constants.PREF_AVG_SPEED, 0.0f);
                float iSpeed = sharedPreferences.getFloat(Constants.PREF_INS_SPEED, 0.0f);
                distanceSpeed = formatSpeedDistance(iDistance, aSpeed, iSpeed);
                guiHandler.post(doUpdateDistanceSpeed);
                sleep(1000L);
            }
        }
    };

    // update the status on the GUI
    private final Runnable doUpdateDistanceSpeed = new Runnable() {
        @Override
        public void run() {
            TextView tv = findViewById(R.id.statusView);
            tv.setText(distanceSpeed);
        }
    };

    //*-----------------------------------------------------------------
    // get the coordinates of the first location in the background
    //*------------------------------------------------------------------
    private void getCoordinates(long delay) {
        coordinateThread = new Thread(null, getCoordinatesBackground, "GetCoordinates");
        sleep(delay);
        coordinateThread.start();
        TextView coordinates = findViewById(R.id.statusView);
        coordinates.setText( getString(R.string.getting_coordinates) );
    }

    // run location start up to get the current coordinates
    private final Runnable getCoordinatesBackground = new Runnable() {
      public void run() {
          locationStartup.startConnection();
          int i = 0;
          while (i < Constants.TWO_MINUTES_SECONDS) {
              locationStatus = getString(R.string.getting_coordinates) + " " + i++ + " seconds";
              guiHandler.post(doUpdateLocationStatus);
              sleep(1000L);
              if (locationStartup.isFreshLocation()) // a fresh location was found
                  i = Constants.TWO_MINUTES_SECONDS;
          }

          // update with latest location status
          Location initialLocation = locationStartup.getLocation();
          if (initialLocation == null) {
              locationStatus = getString(R.string.no_coordinates) + " " + Constants.TWO_MINUTES_SECONDS + " seconds ..." ;
          } else {
              SharedPreferences.Editor editor = sharedPreferences.edit();
              editor.putFloat(Constants.PREF_LATITUDE, (float) initialLocation.getLatitude());
              editor.putFloat(Constants.PREF_LONGITUDE, (float) initialLocation.getLongitude());
              editor.putLong(Constants.PREF_LOCATION_INIT_TIME, System.currentTimeMillis());
              editor.apply();
              locationStatus = "" + formatLocation(initialLocation);
              tripStatus = Constants.STARTED;
          }
          locationStartup.stopConnection();
          guiHandler.post(doUpdateLocationStatus);
      }
    };

    // update the status on the GUI
    private final Runnable doUpdateLocationStatus = new Runnable() {
        @Override
        public void run() {
        TextView tv = findViewById(R.id.statusView);
        tv.setText(locationStatus);
        setButtonLabel();
        }
    };

    // draw the button label
    // If a trip is in progress (running) show the stop button
    // If a trip is finished show the empty button
    // Otherwise, show the start / empty button
    private void setButtonLabel() {
        ImageButton mb = findViewById(R.id.mainButton);
        int resId;
        boolean enabled;
        switch (tripStatus) {
            case Constants.RUNNING:
                resId = R.drawable.stop_button;
                enabled = true;
                break;
            case Constants.FINISHED:
                resId = R.drawable.empty_button;
                enabled = false;
                break;
            default:
                if (locationStartup == null || locationStartup.noInitialCoordinates()) {
                    resId = R.drawable.empty_button;
                    enabled = false;
                } else {
                    resId = R.drawable.start_button;
                    enabled = true;
                }
                break;
        }
        mb.setImageResource(resId);
        mb.setEnabled(enabled);
    }

    // Start the location service, when the start button is pressed
    public void startTracking(Context context) {
        locationStatus = getString(R.string.trip_in_progress);
        guiHandler.post(doUpdateLocationStatus);
        tripStatus = Constants.RUNNING;
        getSpeedDistance();
        if (gpsPermissionGranted) { // start the location service
            context.startService(new Intent(MainActivity.this, LocationService.class));
            SharedPreferences.Editor editor = sharedPreferences.edit();  // save the start time
            editor.putLong(Constants.PREF_FIRST_LOCATION_TIME, System.currentTimeMillis());
            editor.apply();
        }
    }

    // Stop the location service when the stop button is pressed
    public void stopTracking(Context context) {
        if (locationStartup != null)
            locationStartup.setmCurrentLocation(null);
        locationStatus = getString(R.string.trip_ended);
        guiHandler.post(doUpdateLocationStatus);
        distanceSpeed = "";
        if (UtilsMisc.isServiceRunning(context, LocationService.class))
            // repair trip is called in LocationService
            context.stopService(new Intent(MainActivity.this, LocationService.class));
        else {
            int trip_id = summaryTable.getRunningSummaryID(context);
            if (trip_id != -1)
                // Repair trip parms - null ProgressListener, trip id, not imported, calc. segments
                new RepairTrip(context, null, trip_id, false, true).run();    // verify the readings
        }
        tripStatus = Constants.FINISHED;
    }

    // find out if a trip is in progress
    public String getTripStatus(Context context) {
       int trip_id = summaryTable.getRunningSummaryID(context);
       SharedPreferences.Editor editor = sharedPreferences.edit();
       editor.putInt(Constants.PREF_TRIP_ID, trip_id);
       editor.apply();
       if (trip_id != -1) return Constants.RUNNING;
       if (tripStatus.equals(Constants.RUNNING))     // to handle back button where old status
           return Constants.FINISHED;                // is restored -- confusing??
       return tripStatus;
    }

    // find the list of all providers / sensors
    private void setProvidersSensors(final Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // if no location providers, then alert
        List<String> matchingProviders = locationManager.getAllProviders();
        if (matchingProviders == null || matchingProviders.size() == 0) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(context);
            dialog.setMessage(context.getResources().getString(R.string.enable_gps));
            dialog.setPositiveButton(context.getResources().getString(R.string.ok),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                            Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            context.startActivity(myIntent);
                        }
                    });
            dialog.setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                }
            });
            dialog.show();

        }

        // set the shared preferences
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return;
        boolean temperature_enabled = false;
        boolean pressure_enabled = false;
        boolean compass_enabled = false;

        List<Sensor> temperature = sm.getSensorList(Sensor.TYPE_AMBIENT_TEMPERATURE);
        if (temperature.size() > 0) temperature_enabled = true;
        List<Sensor> pressure = sm.getSensorList(Sensor.TYPE_PRESSURE);
        if (pressure.size() > 0) pressure_enabled = true;
        List<Sensor> accelerometer = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
        List<Sensor> magnetic_field = sm.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
        if ( (accelerometer.size() > 0) && (magnetic_field.size() > 0) )
            compass_enabled = true;

        // set the shared preferences for sensors
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(getString(R.string.temperature_enabled), temperature_enabled);
        editor.putBoolean(getString(R.string.pressure_enabled), pressure_enabled);
        editor.putBoolean(getString(R.string.compass_enabled), compass_enabled);
        editor.apply();
    }

    private boolean isGooglePlayServicesAvailable() {
        final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if(result != ConnectionResult.SUCCESS) {
            if(googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            }
            return false;
        }
        return true;
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

    private String formatLocation(Location location) {
        String latLon;
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        DecimalFormat df = new DecimalFormat("#.000000");
        latLon = "Lat: " + df.format(lat) + "° Lon: " + df.format(lon) + "°";
        return latLon;
    }

    private String formatSpeedDistance(int iDistance, float aSpeed, float iSpeed) {
        String distance = UtilsMisc.formatFloat(iDistance / 1000.0f, 1);
        long iduration = System.currentTimeMillis() - sharedPreferences.getLong(Constants.PREF_FIRST_LOCATION_TIME, 0L);
        String duration = UtilsDate.getTimeDurationHHMMSS(iduration, false);

        // if in the first minute of the trip, show 0
        float display_speed;
        if (iduration < Constants.MILLISECONDS_PER_MINUTE) {
            display_speed = 0.0f;
        } else {
            // check if the trip is other, then use instantaneous speed, otherwise average speed
            float walking_speed = Float.parseFloat(sharedPreferences.getString(Constants.PREF_WALKING_SPEED, "5"));
            display_speed = (UtilsMisc.getCategory(walking_speed, aSpeed).equals(Constants.CAT_OTHER))? iSpeed: aSpeed;
        }
        String str_speed = UtilsMisc.formatFloat(display_speed, 1);
        return "Current: " + distance + " kms. " + str_speed + " kmph. " + duration;
    }

    private void sleep(long period) {
        try {
            Thread.sleep(period);
        } catch (InterruptedException ie) {
            //Log.d(TAG, "Could not sleep for " + period + " " + ie.getMessage(), localLog);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Logger.d(TAG, "onPause Status " + tripStatus, localLog);
        // stop any running threads, stop the gps monitor and the force the log index
        // if a trip is in progress, then stop the speed thread alone
        // else stop the GPS service
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if (tripStatus.equals(Constants.RUNNING)) {
                    if (speedThread != null) speedThread.interrupt();
                    //Log.d(TAG, "Pausing with running trip", localLog);
                } else {
                    if (UtilsMisc.isServiceRunning(context, GPSService.class))
                        context.stopService(new Intent(MainActivity.this, GPSService.class));
                }
                Logger.forceIndex(context);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    // ask for GPS permission in Android 6.0+
    // check if GPS Permission has been granted
    public void getGPSPermission() {
        if (Constants.preMarshmallow ||
                (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            gpsPermissionGranted = true;
            return;
        }

        if (!gpsPermissionGranted && !checkedGPSPermissions) {
              //showGPSPermissionDialog();
        }
    }

    public void showGPSPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Location Permissions")
                .setMessage("This app collects location data to enable ride analysis and collects GPS data in the background. " +
                        "Data collection begins when the start button is pressed and ends when the stop button is pressed.")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (Constants.postMarshmallow)
                            setGPSPermissions();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(context, "This app cannot run without GPS permission", Toast.LENGTH_LONG).show();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public void showLocationServicesDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Location Services")
                .setMessage("This app uses Location services which needs to be turned on. ")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(context, "This app cannot run without GPS permission", Toast.LENGTH_LONG).show();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public void setGPSPermissions() {
        Logger.d(TAG, "Getting GPS Permission", localLog);
        checkedGPSPermissions = true;
        Intent intent = new Intent(getBaseContext(), PermissionActivity.class);
        intent.putExtra("permission", Manifest.permission.ACCESS_FINE_LOCATION);
        startActivityForResult(intent, Constants.PERMISSION_CODE);

    }

    // ask for Foreground service permission in Android 9.0+
    // check if foreground service Permission has been granted
    public void getForegroundServicePermission() {
        fsPermissionGranted = Constants.prePie;
        if (Constants.postPie && checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE)
                == PackageManager.PERMISSION_GRANTED)
            fsPermissionGranted = true;

        if (!fsPermissionGranted && !checkedFSPermissions) {
            Logger.d(TAG, "Getting Foreground service permission", localLog);
            checkedFSPermissions = true;
            Intent intent = new Intent(getBaseContext(), PermissionActivity.class);
            intent.putExtra("permission", Manifest.permission.FOREGROUND_SERVICE);
            startActivityForResult(intent, Constants.PERMISSION_CODE);
        }
    }

    public void getSMSServicePermission() {
        smsPermissionGranted = Constants.prePie;
        if (Constants.postPie && checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED)
            smsPermissionGranted = true;

        if (!smsPermissionGranted && !checkedSMSPermissions) {
            Logger.d(TAG, "Getting SMS service permission", localLog);
            checkedSMSPermissions = true;
            Intent intent = new Intent(getBaseContext(), PermissionActivity.class);
            intent.putExtra("permission", Manifest.permission.SEND_SMS);
            startActivityForResult(intent, Constants.PERMISSION_CODE);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //Log.d(TAG, "Returned from Permission Activity", localLog);
        if (requestCode == Constants.PERMISSION_CODE) {
            String granted = data.getStringExtra("granted");
            //Log.d(TAG, "GPS permission Granted: " + granted);
            if (granted.equals("true")) {
                gpsPermissionGranted = true;
                locationStartup = new LocationStartup(context, this);
                getCoordinates(0);
                getSpeedDistance();
            } else {
                String result = "Cannot run without GPS Permission";
                Toast.makeText(context, result, Toast.LENGTH_LONG).show();
                finish();
            }
        }

        if (requestCode == Constants.LOCATION_CODE) {
            double lat = sharedPreferences.getFloat(Constants.PREF_LATITUDE, 0.0f);
            double lon = sharedPreferences.getFloat(Constants.PREF_LONGITUDE, 0.0f);
            Location location = new Location("dummyprovider");
            location.setLatitude(lat); location.setLongitude(lon);
            locationStatus = "" + formatLocation(location);
            guiHandler.post(doUpdateLocationStatus);
        }
    }

}