package org.mkonchady.mytripoo;

import android.os.Build;

public final class Constants {

    // Time constants
    public final static long MILLISECONDS_PER_DAY = 86400 * 1000;
    public final static long MILLISECONDS_PER_MINUTE = 60 * 1000;
    public final static long MILLISECONDS_PER_HOUR = 60 * MILLISECONDS_PER_MINUTE;
    public final static int HALF_MINUTE_SECONDS = 30;
    public final static int TWO_MINUTES_SECONDS = 120;

    public final static String UTC_TIMEZONE = "UTC";
    public final static double MILLION = 1000000.0;
    public final static float LARGE_FLOAT = 10000000.0f;
    public final static int LARGE_INT = 2147483647;
    public final static long LARGE_LONG = 9223372036854775807L;
    public final static float DEFAULT_ALTITUDE = 0.0f;

    // debug modes
    //  public final static String DEBUG_NO_MESSAGES = "0";
    public final static String DEBUG_LOCAL_FILE = "1";
    //  public final static String DEBUG_ANDROID_LOG = "2";

    // shared parameters for the trip
    public final static String PREF_FIRST_TRIP = "firstTime";
    public final static String PREF_LONGITUDE = "Longitude";
    public final static String PREF_LATITUDE = "Latitude";
    public final static String PREF_LOCATION_INIT_TIME = "first_location_time";
    public final static String PREF_TRIP_ID = "Trip_ID";
    public final static String PREF_TRIP_DURATION = "Duration";
    public final static String PREF_AVG_SPEED = "Avg_Speed";
    public final static String PREF_DISTANCE = "Distance";
    public final static String PREF_INS_SPEED = "Ins_Speed";
    public final static String PREF_SMS_TIME = "Sms_time";
    public final static String PREF_BATTERY = "Battery";
    public final static String PREF_BEEP_PERIOD = "Beep_period";

    public final static String PREF_LOG_FILE = "Log_file";
    public final static String PREF_FOLLOW_TRIP = "Follow_trip";
    public final static String PREF_FOLLOW_COUNTER = "Follow_counter";
    public final static String LOCATION_SERVICE_RUNNING = "Location_service";
    public final static String PREF_TIME_INCR = "Time_increment";
    public final static String PREF_FIRST_LOCATION_TIME = "Restart_time";
    public final static String PREF_LAST_LOCATION_TIME = "Last_location_time";
    public final static String PREF_DIRECTION_SCREEN_VISIBLE = "directions_screen_visible";

    // shared parameters based on the settings
    public final static String PREF_WALKING_SPEED = "Walking_speed";
    public final static String PREF_DEBUG_MODE = "Debug_mode";
    public final static String PREF_TRIP_TYPE = "Trip_type";
    public final static String PREF_PLOT_SPEED = "Plot_speed";
    public final static String PREF_NUMBER_TRIP_ROWS = "Number_of_trip_rows";
    public final static String PREF_NUMBER_TRIP_READINGS = "Number_of_trip_readings";
    public final static String PREF_WAKEUP_GPS = "wakeup_gps";

    // location preferences (shared parameters)
    public final static String PREF_LOCATION_PROVIDER = "Location_provider";
    public final static String PREF_LOCATION_ACCURACY = "Location_accuracy";
    public final static String PREF_FILE_FORMAT = "File_format";
    public final static String PREF_POLL_INTERVAL = "Poll_interval";
    public final static String PREF_GOOGLE_API_KEY = "google_api_key";
    public final static String PREF_OPENWEATHER_API_KEY = "openweather_api_key";
    public final static String PREF_AUTOSTOP = "autostop";
    public final static String PREF_FIRST_TIME = "first_time";


    // SMS preferences (shared parameters)
    public final static String PREF_SMS_NUMBER = "Sms_number";
    public final static String PREF_SMS_PERIOD = "Sms_period";
    public final static String PREF_SMS_SEND = "Send_sms";
    public final static String PREF_SMS_DISTANCE = "Sms_distance";

    // Bicycle preferences (shared parameters) must match the key in pref_bike.xml
    public final static String PREF_BIKE_RIDER = "Bike_rider_wt";
    public final static String PREF_BIKE_BIKE = "Bike_bike_wt";
    public final static String PREF_BIKE_FRONT = "Bike_front";
    public final static String PREF_BIKE_ROLLING = "Bike_rolling";
    public final static String PREF_BIKE_DRAG = "Bike_drag";
    public final static String PREF_BIKE_COMMIT = "Bike_commit";
    public final static String PREF_BIKE_COMMIT_DURATION = "Bike_commit_duration";

    // Openweather fields for each detail
    public final static String OPENW_TEMP = "temp";
    public final static String OPENW_HUMIDITY = "humidity";
    public final static String OPENW_WIND_SPEED = "wind_speed";
    public final static String OPENW_WIND_DEG = "wind_deg";
    public final static String BIKE_POWER = "power";

    // constants for the trip status
    public final static String NOT_STARTED = "not_started";
    public final static String STARTED = "started";
    public final static String RUNNING = "running";
    public final static String FINISHED = "finished";

    // constants for plots
    public final static int DATA_SPEED = 0;
    public final static int DATA_GRADIENT = 1;
    public final static int DATA_BATTERY = 2;
    public final static int DATA_ALTITUDE = 3;
    public final static int DATA_SPEED_SEGMENT = 4;
    public final static int DATA_ACCURACY = 5;
    public final static int DATA_SATINFO = 6;
    public final static int DATA_SATELIITES = 7;
    public final static int DATA_PACE = 8;
    public final static int DATA_TIME_PER_READING = 9;
    public final static int DATA_DISTANCE_PER_READING = 10;
    public final static int DATA_POWER = 11;
    public final static int DATA_HISTO_POWER = 12;
    public final static int DATA_VAM = 13;
    public final static int DATA_HEAD_WIND = 14;
    public final static int NUM_PLOTS = 15;

    // constants for maps
    public final static int DRAW_SPEEDS = 0;
    public final static int DRAW_ELEVATION = 1;
    public final static int DRAW_BEARINGS = 2;
    public final static int DRAW_TRACKS = 3;
    public final static int DRAW_GRADIENT = 4;
    public final static int DRAW_SATINFO = 5;
    public final static int DRAW_POWER = 6;
    public final static int DRAW_WINDS = 7;
    public final static int ALL_SEGMENTS = -1;
    public final static int MAX_SEGMENTS = 16;
    public final static String DIRECTIONS = "directions";

    public final static String DIRECTION_U_TURN = "Make a U-Turn";
    public final static String DIRECTION_SHARP_LEFT = "Take a sharp left";
    public final static String DIRECTION_LEFT = "Take a left";
    public final static String DIRECTION_STRAIGHT = "Head straight";
    public final static String DIRECTION_RIGHT = "Take a right";
    public final static String DIRECTION_SHARP_RIGHT = "Take a sharp right";

    // constants for GPS Satellites
    public final static String GPS_SATELLITES = "gps_satellites";
    public final static String GPS_USED_SATELLITES = "gps_used_satellites";
    public final static String GPS_SNR = "gps_snr";

    public final static String GPS = "GPS";
    public final static String GPS_AZIMUTH = "gps_azimuth";
    public final static String GPS_ELEVATION = "gps_elevation";
    public final static String GPS_PRN = "gps_prn";
    public final static String GPS_TYPE = "gps_type";
    public final static String GPS_SATINFO = "gps_satinfo";

    // android versions
    public final static boolean preMarshmallow  =  Build.VERSION.SDK_INT < Build.VERSION_CODES.M; // < 23
    public final static boolean postMarshmallow =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.M; // >= 23
    public final static boolean postNougat =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.N; // >= 24
    public final static boolean preOreo =  Build.VERSION.SDK_INT < Build.VERSION_CODES.O; // < 26
    public final static boolean postOreo =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.O; // >= 26
    public final static boolean prePie =  Build.VERSION.SDK_INT < Build.VERSION_CODES.P; // < 28
    public final static boolean postPie =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.P; // >= 28
    public final static boolean preAndroid10 =  Build.VERSION.SDK_INT < Build.VERSION_CODES.Q; // < 29
    public final static boolean postAndroid10 =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.R; // >= 30


    // for activities started from main
    public final static int PERMISSION_CODE = 100;
    public final static int LOCATION_CODE = 200;

    // trip categories
    public final static String CAT_WALKING = "Walking";
    public final static String CAT_JOGGING = "Jogging";
    public final static String CAT_CYCLING = "Cycling";
    public final static String CAT_OTHER = "Other";
    public final static String TRIP_CATEGORY = "category";     // type of trips
    public final static String CAT_ALL = "All";
    public final static String TRIP_FROM_DATE = "from_date";   // from date timestamp
    public final static String SAMPLE_GPX_FILE = "sample.gpx"; // sample file name

    public final static String DELIMITER = "!!!";
    public final static String VERSION = "0.1";
    public final static int GOOGLE_MAX_POINTS = 100;
    public final static int GOOGLE_MIN_POINTS = 10;
    public final static int SPLIT_TRIP_RESULT_CODE = 24;
    public final static int EDIT_TRIP_RESULT_CODE = 25;
    public final static String NEWLINE = System.getProperty("line.separator");

    /**
     * no default constructor
     */
    private Constants() {
        throw new AssertionError();
    }

}
