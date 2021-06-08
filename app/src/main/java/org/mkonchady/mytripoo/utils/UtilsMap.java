package org.mkonchady.mytripoo.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.GnssStatus;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.SparseIntArray;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.gps.Satellite;

// map utilities
 public final class UtilsMap {

    private final static SparseIntArray colorMap = new SparseIntArray();
    public final static String DELIMITER = ":";
    private final static double DEGREE_DISTANCE = 110.25;           // in kms. per degree
    private final static int EARTH_RADIUS_KMS = 6367;   //radius in km
    private final static double EARTH_RADIUS = 6378137.0;   //radius in m

    private final static int NW = 0;
    private final static int N = 1;
    private final static int NE = 2;
    private final static int E = 3;
    private final static int SE = 4;
    private final static int S = 5;
    private final static int SW = 6;
    private final static int W = 7;
    private final static int DIRS = 8;
    //private final static HashMap<Integer, String> direction = new HashMap() {{
    //     put(NW,"NorthWest");  put(N,"North");  put(NE,"NorthEast");  put(E,"East");
    //     put(SE,"SouthEast");  put(S,"South");  put(SW,"SouthWest");  put(W,"West");
    // }};

     // direction matrix starting row cell numbers
     private final static int ROW0 = 0;
     private final static int ROW1 = 8;
     private final static int ROW2 = 16;
     private final static int ROW3 = 24;
     private final static int ROW4 = 32;
     private final static int ROW5 = 40;
     private final static int ROW6 = 48;
     private final static int ROW7 = 56;

     // drag forces
     private final static int AIR = 0;
     private final static int SLOPE = 1;
     private final static int ROLL = 2;
     private final static int ACCEL = 3;
     private final static int NUM_DRAG_FORCES = 4;

    //private final static String TAG = "Map_Utilities";

    /**
     * no default constructor
     */
    private UtilsMap() {
        throw new AssertionError();
    }

    // set the satellite signal strength
     public static int setSatSignalStrength(float snr) {
        int signalStrength = 0;
        if ( (25 < snr)  ) signalStrength = 4;
        if ( (15 < snr) && (snr <= 25) ) signalStrength = 3;
        if ( (10 < snr) && (snr <= 15) ) signalStrength = 2;
        if ( (0 <= snr ) && (snr <= 10) ) signalStrength = 1;
        return signalStrength;
    }

    public static String getSatelliteCountry(Context context, int itype) {
        if (itype == GnssStatus.CONSTELLATION_GPS)
            return context.getString(R.string.usa);
        else if (itype == GnssStatus.CONSTELLATION_GLONASS)
            return context.getString(R.string.russia);
        else if (itype == GnssStatus.CONSTELLATION_BEIDOU)
            return context.getString(R.string.china);
        else if (itype == GnssStatus.CONSTELLATION_QZSS)
            return context.getString(R.string.japan);
        else if (itype == GnssStatus.CONSTELLATION_GALILEO)
            return context.getString(R.string.europe);
        else if (itype == GnssStatus.CONSTELLATION_SBAS)
            return context.getString(R.string.unknown);
        else if (itype == GnssStatus.CONSTELLATION_IRNSS)
            return context.getString(R.string.india);
        else if (itype == GnssStatus.CONSTELLATION_UNKNOWN)
            return context.getString(R.string.unknown);
        return context.getString(R.string.unknown);
    }

    public static String[] getCountries(Context context) {
        String[] countries = {context.getString(R.string.usa), context.getString(R.string.russia), context.getString(R.string.china),
                context.getString(R.string.japan), context.getString(R.string.europe), context.getString(R.string.india),
                context.getString(R.string.unknown)};
        return countries;
    }

    public static String getCountryStats(HashMap<String, Integer> stats) {
        String out = "";
        TreeMap<String, Integer> sorted = new TreeMap<>(stats);
        Set<Map.Entry<String, Integer>> mappings = sorted.entrySet();
        for(Map.Entry<String, Integer> mapping : mappings) {
            if (mapping.getValue() > 0)
                out = out + mapping.getKey() + ": " + mapping.getValue() + " ";
        }
        return out;
    }

    // convert clockwise bearing (0 as N ) to counterclockwise degrees (0 as E)
    public static int bearingToDegrees(int bearing) {
        return (450 - bearing) % 360;
    }

    // convert counterclockwise degrees (0 as E) to clockwise bearing (0 as N )
    public static int degreesToBearings(int degrees) {
        return (450 - degrees) % 360;
    }

    // return the degree in the opposite direction
    public static int flip_direction(int angle) {
        return (((angle + 360) - 180) % 360);
    }

    // return the degree in the opposite direction
    public static int flip_direction(double angle) {
        int angle_i = (int) Math.round(angle);
        return flip_direction(angle_i);
    }

    // get  the angle between two vectors in degress
    public static int get_angle(double v1_x, double v1_y, double v2_x, double v2_y) {
        double angle = Math.toDegrees(Math.atan2(v2_y, v2_x) - Math.atan2(v1_y, v1_x));
        return (int) Math.round(angle);
    }

    // get the dot product of two vectors
    private static double get_dot_product(double v1_x, double v1_y, double v2_x, double v2_y) {
        return v1_x * v2_x + v1_y * v2_y;
    }

    // get the magnitude of a vector
    private static double get_magnitude(double v_x, double v_y) {
        return Math.sqrt(v_x * v_x + v_y * v_y);
    }

    // convert kmph to meters per second
     public static double kmphTomps(double kmph) {
        return (0.277777777778 * kmph);
    }

    // convert meters per second to kmph
    public static double mpsTokmph(double mps) {
        return (3.6 * mps);
    }

    // calculate speed in kmph given meters and milliseconds
     public static double calcKmphSpeed(double distance, long duration) {
        return (duration <= 0L || distance <= 0.0f)?  0.0f:(3600.0 * distance) / duration;
        //if (duration < 1000) duration = 1000;   // no sub-second speed calculations
    }

    // calculate speed in mps given meters and milliseconds
    public static double calcmpsSpeed(double distance, long duration) {
        return kmphTomps((duration <= 0L || distance <= 0.0f)?  0.0f:(3600.0 * distance) / duration);
        //if (duration < 1000) duration = 1000;   // no sub-second speed calculations
    }

    /*
         public static float getMileDistance(int lat1, int lon1, int lat2, int lon2) {
            return 0.625f * getKmDistance(lat1, lon1, lat2, lon2);
        }
    */
    public static double getMeterDistance(Location loc1, Location loc2) {
        return getMeterDistance(loc1.getLatitude() * Constants.MILLION, loc1.getLongitude() * Constants.MILLION,
                loc2.getLatitude() * Constants.MILLION, loc2.getLongitude() * Constants.MILLION);
    }


     public static double getMeterDistance(LatLng latLng1, LatLng latLng2) {
        return getMeterDistance(latLng1.latitude * Constants.MILLION, latLng1.longitude * Constants.MILLION,
                latLng2.latitude * Constants.MILLION, latLng2.longitude * Constants.MILLION);
    }

     private static double getMeterDistance(double lat1, double lon1, double lat2, double lon2) {
        return getMeterDistance((int) Math.round(lat1), (int) Math.round(lon1),
                (int) Math.round(lat2), (int) Math.round(lon2));
    }

     public static double getMeterDistance(int lat1, int lon1, int lat2, int lon2) {
        return 1000.0f * getKmDistance(lat1, lon1, lat2, lon2);
    }

    // calculate the great circle distance in kms. between 2 locations specified in micro-degrees
     public static double getKmDistance(int lat1, int lon1, int lat2, int lon2) {
        if ((lat1 == lat2) && (lon1 == lon2)) return 0;

        double x1 = Math.toRadians(lat1 / Constants.MILLION); double y1 = Math.toRadians(lon1 / Constants.MILLION);
        double x2 = Math.toRadians(lat2 / Constants.MILLION); double y2 = Math.toRadians(lon2 / Constants.MILLION);

        // Haverside formula
        double a = Math.pow(Math.sin((x2 - x1) / 2), 2)
                + Math.cos(x1) * Math.cos(x2) * Math.pow(Math.sin((y2 - y1) / 2), 2);

        // great circle distance in radians
        double angle = 2 * Math.asin(Math.min(1, Math.sqrt(a)));
        angle = Math.toDegrees(angle);

        // distance = r * theta where theta is the angle between the 2 locations and r is 60 nautical miles
        double distance = 60 * angle;

        // convert distance from nautical miles to kms.
        return distance * 1.852;
    }

    // calculate the Euclidean distance in kms. between 2 locations in micro-degrees
    public static double getFastMeterDistance(int lat1, int lon1, int lat2, int lon2) {
        return getFastKmDistance(lat1, lon1, lat2, lon2) * 1000.0;
    }

    // calculate the Euclidean distance in kms. between 2 locations in micro-degrees
     public static double getFastKmDistance(int lat1, int lon1, int lat2, int lon2) {
        double x1 = lat1 / Constants.MILLION; double y1 = lon1 / Constants.MILLION;
        double x2 = lat2 / Constants.MILLION; double y2 = lon2 / Constants.MILLION;
        double dx = x2 - x1;        double dy = y2 - y1;
        return (DEGREE_DISTANCE * Math.sqrt(dx*dx + dy*dy));
    }

    // get the bearing between two location in micro-degrees
    // where bearing ranges from 0 to 359, 0 being directly north and measured clockwise
     public static int getBearing(int ilat1, int ilon1, int ilat2, int ilon2) {
        double lat1 = ilat1 / Constants.MILLION; double lon1 = ilon1 / Constants.MILLION;
        double lat2 = ilat2 / Constants.MILLION; double lon2 = ilon2 / Constants.MILLION;
        lat1 = Math.toRadians(lat1); lon1 = Math.toRadians(lon1);
        lat2 = Math.toRadians(lat2); lon2 = Math.toRadians(lon2);
        double dLon = lon2 - lon1;
        double y = Math.cos(lat2) * Math.sin(dLon);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double angle = Math.toDegrees(Math.atan2(y, x));
        angle = (angle + 360) % 360;
        return (int) Math.round(angle);
    }

    // return the bearing of a and b
     public static int getBearing(DetailProvider.Detail detail_a, DetailProvider.Detail detail_b) {
        return getBearing(detail_a.getLat(), detail_a.getLon(), detail_b.getLat(), detail_b.getLon() );
    }

    // return the angle <abc, i.e. angle between ab and bc
     public static int getAngle(DetailProvider.Detail detail_a, DetailProvider.Detail detail_b, DetailProvider.Detail detail_c) {
        return getAngle(getBearing(detail_b.getLat(), detail_b.getLon(), detail_a.getLat(), detail_a.getLon()),
                        getBearing(detail_b.getLat(), detail_b.getLon(), detail_c.getLat(), detail_c.getLon()) );
    }

     public static int getAngle(int bearing1, int bearing2) {
        return Math.min((bearing1 - bearing2 + 360) % 360, (bearing2 - bearing1 + 360) % 360);
    }

    /*
    From https://en.wikipedia.org/wiki/Decimal_degrees

    Scale at equator is higher than other latitudes

    decimal places   	decimal degrees	DMS	qualitative         scale
    0	            1.0		        country or large region	111.32 km
    1	            0.1		        large city or district	11.132 km
    2	            0.01		    town or village	        1.1132 km
    3	            0.001	 	    neighborhood, street	111.32 m
    4	            0.0001		    individual street,  	11.132 m
    5	            0.00001		    individual trees	    1.1132 m
    6	            0.000001	    individual humans	    111.32 mm

    decimal places = 6 - precision (parameter)
    roundToNumber(13023567, 2, true)  gives 13023600
    roundToNumber(13023567, 2, false) gives 13023500
    roundToNumber(13023567, 3, true)  gives 13024000
    roundToNumber(13023567, 3, false) gives 13023000

    Build 4 locations using the rounded lats and lons, any location in
    the x by x region will be detected

    <---------- x meters ------->
  ↑  lat2,lon1           lat2,lon2
  |  .............................
  x  .............................
  ↓  lat1,lon1           lat1,lon2

     */
     public static String[] gen_keys(int lat, int lon, int precision) {
        int lat1 = UtilsMisc.roundToNumber(lat, precision, true); // ceiling is true
        int lon1 = UtilsMisc.roundToNumber(lon, precision, true);
        int lat2 = UtilsMisc.roundToNumber(lat, precision, false); // ceiling is false
        int lon2 = UtilsMisc.roundToNumber(lon, precision, false);

        // create 4 possible locations using the lat and lon
        String[] keys = new String[4];
        keys[0] = lat1 + DELIMITER + lon1;
        keys[1] = lat1 + DELIMITER + lon2;
        keys[2] = lat2 + DELIMITER + lon1;
        keys[3] = lat2 + DELIMITER + lon2;
        return keys;
    }

    public static String[] gen_keys_distance(int lat, int lon, int distance) {
        double lat1 = Math.toRadians(lat / Constants.MILLION);
        double lon1 = Math.toRadians(lon / Constants.MILLION);
        double r90 = Math.toRadians(90);
        double r180 = Math.toRadians(180);
        double r270 = Math.toRadians(270);
        double d_ratio = Math.sqrt(distance * distance / 2) / EARTH_RADIUS;

        double lat_n = Math.asin( Math.sin(lat1) * Math.cos(d_ratio) +
                                  Math.cos(lat1) * Math.sin(d_ratio) * Math.cos(0) );
        long ilat_n = Math.round(Math.toDegrees(lat_n) * Constants.MILLION);

        double lat_s = Math.asin( Math.sin(lat1) * Math.cos(d_ratio) +
                                  Math.cos(lat1) * Math.sin(d_ratio) * Math.cos(r180) );
        long ilat_s = Math.round(Math.toDegrees(lat_s) * Constants.MILLION);

        double lon_e = lon1 + Math.atan2(Math.sin(r90) * Math.sin(d_ratio) * Math.cos(lat1),
                                         Math.cos(d_ratio) - Math.sin(lat1) * Math.sin(lat1) );
        long ilon_e = Math.round(Math.toDegrees(lon_e) * Constants.MILLION);

        double lon_w = lon1 + Math.atan2(Math.sin(r270) * Math.sin(d_ratio) * Math.cos(lat1),
                                         Math.cos(d_ratio) - Math.sin(lat1) * Math.sin(lat1) );
        long ilon_w = Math.round(Math.toDegrees(lon_w) * Constants.MILLION);
        /*
        var φ2 = Math.asin( Math.sin(φ1)*Math.cos(d/R) +
                Math.cos(φ1)*Math.sin(d/R)*Math.cos(brng) );
        var λ2 = λ1 + Math.atan2(Math.sin(brng)*Math.sin(d/R)*Math.cos(φ1),
                Math.cos(d/R)-Math.sin(φ1)*Math.sin(φ2));
        */

        // create 4 possible locations using the lat and lon
        String[] keys = new String[4];
        keys[0] = ilat_n + DELIMITER + ilon_e;
        keys[1] = ilat_n + DELIMITER + ilon_w;
        keys[2] = ilat_s + DELIMITER + ilon_e;
        keys[3] = ilat_s + DELIMITER + ilon_w;
        return keys;
    }

    // return true if the key was seen earlier
     public static boolean containsKey(String[] keys, HashMap<String, Long> seenLocations) {
        boolean found = false;
        for (String key : keys)
            if (seenLocations.containsKey(key))
                found = true;
        return found;
    }

    // return the oldest time for a matching key, warning: hashmap must contain key
     public static long getKeyTime(String[] keys, HashMap<String, Long> seenLocations) {
        long keyTime = Constants.LARGE_LONG;
        for (String key : keys) {
            if (seenLocations.containsKey(key))
                if (seenLocations.get(key) < keyTime)
                    keyTime = seenLocations.get(key);
        }
        return keyTime;
    }

    // return the footprint bitmap
     public static Bitmap getFootprintBitmap(int color) {
        final int width = 10;
        final int height = 20;
        Bitmap footprint = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int bitColor = (footprintBitmap[row][col] == 0)? Color.TRANSPARENT: color;
                footprint.setPixel(col, row, bitColor);
            }
        }
        footprint = Bitmap.createScaledBitmap(footprint, width * 4, height * 4, false);
        return footprint;
    }

    // binary footprint
    private static final int[][] footprintBitmap = new int[][]{
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 1, 1, 1, 1, 0, 0 },
            { 0, 0, 0, 0, 1, 1, 1, 1, 0, 0 },
            { 0, 0, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 1, 0, 1, 1, 1, 1, 1, 0, 0 },
            { 0, 0, 1, 1, 1, 1, 1, 1, 0, 0 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 0, 0 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 0, 0 },
            { 0, 1, 1, 1, 1, 1, 1, 0, 0, 0 },
            { 0, 1, 1, 1, 1, 1, 0, 0, 0, 0 },
            { 0, 1, 1, 1, 1, 0, 0, 0, 0, 0 },
            { 0, 0, 1, 1, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 1, 1, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 1, 1, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 1, 1, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 1, 1, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 1, 1, 1, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }
    };

    // return the circle bitmap
     public static Bitmap getCircleBitmap(int color) {
        final int width = 16;
        final int height = 16;
        Bitmap circle = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int bitColor = (circleBitmap[row][col] == 0)? Color.TRANSPARENT: color;
                circle.setPixel(col, row, bitColor);
            }
        }
        circle = Bitmap.createScaledBitmap(circle, width * 2, height * 2, false);
        return circle;
    }

    // binary circle
    private static final int[][] circleBitmap = new int[][]{
            { 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0 },
            { 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0 },
            { 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0 },
            { 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 }
    };

    // return the arrow bitmap
     public static Bitmap getArrowBitmap(int color) {
        final int width = 16;
        final int height = 16;
        Bitmap arrow = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int bitColor = (arrowBitmap[row][col] == 0)? Color.TRANSPARENT: color;
                arrow.setPixel(col, row, bitColor);
            }
        }
        arrow = Bitmap.createScaledBitmap(arrow, width * 2, height * 2, false);
        return arrow;
    }

    // binary arrow
    private static final int[][] arrowBitmap = new int[][]{
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0 },
            { 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0 }
    };

    // generate the color map with 1276 entries
     public static SparseIntArray buildColorRamp() {

        int count = 0;
        int red = 0;
        int green = 0;
        int blue;

        // bright blue to light blue    (128 entries)
        for (blue = 128; blue < 256; blue++)
            colorMap.put(count++, Color.rgb(red, green, blue));
        // light blue to teal   (255 entries)
        for (blue = 255, green = 1; green < 256; green++)
            colorMap.put(count++, Color.rgb(red, green, blue));
        // teal to green    (255 entries)
        for (green = 255, blue = 254; blue >= 0; blue--)
            colorMap.put(count++, Color.rgb(red, green, blue));
        // green to yellow  (255 entries)
        for (blue = 0, red = 1; red < 256; red++)
            colorMap.put(count++, Color.rgb(red, green, blue));
        // yellow to light red  (255 entries)
        for (red = 255, green = 254; green >= 0; green--)
            colorMap.put(count++, Color.rgb(red, green, blue));
        // light red to bright red  (128 entries)
        for (green = 0, red = 254; red >= 127; red--)
            colorMap.put(count++, Color.rgb(red, green, blue));

        return colorMap;
    }


     // hashmap for a matrix of old direction (row) , to new direction (column)
     // "S" - Straight, "R" - Right, "SR" - Sharp right, "L" - Left, "SL" - Sharp Left
     //         NW  N   NE  E   SE  S   SW  W
     //     NW  S   R   R   SR  U   SL  L   L
     //      N  .............................
    private final static HashMap<Integer, String> directions = new HashMap<Integer, String>() {{
     put(ROW0+NW,"S");   put(ROW0+N,"R");   put(ROW0+NE,"R");  put(ROW0+E,"SR"); put(ROW0+SE,"U");  put(ROW0+S,"SL"); put(ROW0+SW,"L");  put(ROW0+W,"L");
     put(ROW1+NW,"L");   put(ROW1+N,"S");   put(ROW1+NE,"R");  put(ROW1+E,"R");  put(ROW1+SE,"SR"); put(ROW1+S,"U");  put(ROW1+SW,"SL"); put(ROW1+W,"L");
     put(ROW2+NW,"L");   put(ROW2+N,"L");   put(ROW2+NE,"S");  put(ROW2+E,"R");  put(ROW2+SE,"R");  put(ROW2+S,"SR"); put(ROW2+SW,"U");  put(ROW2+W,"SL");
     put(ROW3+NW,"SL");  put(ROW3+N,"L");   put(ROW3+NE,"L");  put(ROW3+E,"S");  put(ROW3+SE,"R");  put(ROW3+S,"R");  put(ROW3+SW,"SR"); put(ROW3+W,"U");
     put(ROW4+NW,"U");   put(ROW4+N,"SL");  put(ROW4+NE,"L");  put(ROW4+E,"L");  put(ROW4+SE,"S");  put(ROW4+S,"R");  put(ROW4+SW,"R");  put(ROW4+W,"SR");
     put(ROW5+NW,"SR");  put(ROW5+N,"U");   put(ROW5+NE,"SL"); put(ROW5+E,"L");  put(ROW5+SE,"L");  put(ROW5+S,"S");  put(ROW5+SW,"R");  put(ROW5+W,"R");
     put(ROW6+NW,"R");   put(ROW6+N,"SR");  put(ROW6+NE,"U");  put(ROW6+E,"SL"); put(ROW6+SE,"L");  put(ROW6+S,"L");  put(ROW6+SW,"S");  put(ROW6+W,"R");
     put(ROW7+NW,"R");   put(ROW7+N,"R");   put(ROW7+NE,"SR"); put(ROW7+E,"U");  put(ROW7+SE,"SL"); put(ROW7+S,"L");  put(ROW7+SW,"L");  put(ROW7+W,"S");
    }};

    // get the resource id for the bearing
    public static int getResid(int bearing1, int bearing2) {
        // get the direction index for each bearing
        int direction1 = getDirection(bearing1);
        int direction2 = getDirection(bearing2);
        //Log.d("UtilsMap", "D1: " + direction.get(direction1) + " D2: " + direction.get(direction2), 1);
        int key = direction1 * DIRS + direction2;
        String route = directions.get(key);

        switch (route) {
            case "U": return R.raw.head_u;
            case "SL": return R.raw.head_sl;
            case "L": return R.raw.head_l;
            case "S": return R.raw.head_s;
            case "R": return R.raw.head_r;
            case "SR": return R.raw.head_sr;
        }
        return R.raw.alarm;
    }

    public static String getDirection(int bearing1, int bearing2) {
        int direction1 = getDirection(bearing1);
        int direction2 = getDirection(bearing2);
        int key = direction1 * DIRS + direction2;
        String route = directions.get(key);
        switch (route) {
            case "U": return Constants.DIRECTION_U_TURN;
            case "SL": return Constants.DIRECTION_SHARP_LEFT;
            case "L": return Constants.DIRECTION_LEFT;
            case "S": return Constants.DIRECTION_STRAIGHT;
            case "R": return Constants.DIRECTION_RIGHT;
            case "SR": return Constants.DIRECTION_SHARP_RIGHT;
        }
        return "";
    }

    public static int getDirectionImage(String direction) {
        switch (direction) {
            case Constants.DIRECTION_U_TURN: return R.drawable.u_turn;
            case Constants.DIRECTION_LEFT: return R.drawable.left_turn;
            case Constants.DIRECTION_SHARP_LEFT: return R.drawable.left_turn;
            case Constants.DIRECTION_STRAIGHT: return  R.drawable.straight;
            case Constants.DIRECTION_RIGHT: return R.drawable.right_turn;
            case Constants.DIRECTION_SHARP_RIGHT: return R.drawable.right_turn;
        }
        return R.drawable.right_turn;
    }

    // return one of the 8 directions
    public static String getDirection(double bearing) {
        if (bearing >= 337.5  || bearing < 22.5)   return "N";
        if (bearing >= 22.5   && bearing < 67.5)   return "NE";
        if (bearing >= 67.5   && bearing < 112.5)  return "E";
        if (bearing >= 112.5  && bearing < 157.5)  return "SE";
        if (bearing >= 157.5  && bearing < 202.5)  return "S";
        if (bearing >= 202.5  && bearing < 247.5)  return "SW";
        if (bearing >= 247.5  && bearing < 292.5)  return "W";
        return "NW";
    }

    // return one of the 8 directions
    private static int getDirection(int bearing) {
        if (bearing >= 337.5  || bearing < 22.5)   return N;
        if (bearing >= 22.5   && bearing < 67.5)   return NE;
        if (bearing >= 67.5   && bearing < 112.5)  return E;
        if (bearing >= 112.5  && bearing < 157.5)  return SE;
        if (bearing >= 157.5  && bearing < 202.5)  return S;
        if (bearing >= 202.5  && bearing < 247.5)  return SW;
        if (bearing >= 247.5  && bearing < 292.5)  return W;
        return NW;
    }

     public static LocationRequest getLocationRequest(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        LocationRequest mLocationRequest = LocationRequest.create();
        String locationProvider = sharedPreferences.getString(Constants.PREF_LOCATION_PROVIDER, "");
        switch (locationProvider) {
            case "gps": mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); break;
            default: mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY); break;
        }

        // set the smallest displacement in meters before location update is called
        String location_separation = sharedPreferences.getString(Constants.PREF_LOCATION_ACCURACY, "");
        int min_separation;
        switch (location_separation) {
            case "high": min_separation = 20; break;
            case "medium": min_separation = 10; break;
            default: min_separation = 5; break;
        }
        mLocationRequest.setSmallestDisplacement(min_separation);

        // set the time interval in msecs. for location updates based on the cost
        mLocationRequest.setInterval(get_poll_interval(sharedPreferences));
        mLocationRequest.setFastestInterval(1000);

        // set the max trip duration based on settings
        String tripType = sharedPreferences.getString(Constants.PREF_TRIP_TYPE, "");
        long duration;
        switch (tripType) {
            case "medium": duration = Constants.MILLISECONDS_PER_HOUR * 4; break;
            case "long": duration = Constants.MILLISECONDS_PER_HOUR * 6; break;
            case "really_long": duration = Constants.MILLISECONDS_PER_HOUR * 48; break;
            default: duration = Constants.MILLISECONDS_PER_HOUR * 6; break;
        }
        mLocationRequest.setExpirationDuration(duration);

        // set the commit period
        String commit_period = sharedPreferences.getString(Constants.PREF_BIKE_COMMIT, "48");
        long commit_time;
        switch (commit_period) {
            case "0.5": commit_time = Constants.MILLISECONDS_PER_HOUR / 2; break;
            case "1": commit_time = Constants.MILLISECONDS_PER_HOUR; break;
            case "2": commit_time = Constants.MILLISECONDS_PER_HOUR * 2; break;
            case "3": commit_time = Constants.MILLISECONDS_PER_HOUR * 3; break;
            case "4": commit_time = Constants.MILLISECONDS_PER_HOUR * 4; break;
            default: commit_time = Constants.MILLISECONDS_PER_HOUR * 48; break;
        }

        // place the duration in the preferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(Constants.PREF_TRIP_DURATION, duration);
        editor.putLong(Constants.PREF_BIKE_COMMIT_DURATION, commit_time);
        editor.apply();

        return mLocationRequest;

    }

    public static int get_poll_interval(SharedPreferences sharedPreferences) {
        String pollInterval = sharedPreferences.getString(Constants.PREF_POLL_INTERVAL, "low");
        int interval;
        switch (pollInterval) {
            case "really_low":interval = 1000; break;
            case "low": interval = 1000 * 5; break;
            case "medium": interval = 1000 * 10; break;
            default: interval = 1000 * 5; break;
        }
        return interval;
    }

    // the elevation is measured upwards from horizon to vertical from 0 to 90
    // the azimuth is measured from N in a clockwise direction from 0 to 360
    // set the x, y, and z cartesian coordinates
     public static void setCartesianCoords(Satellite satellite) {

        float azimuth = satellite.getAzimuth();
        float elevation = satellite.getElevation();

        // convert azimuth angle to standard anti-clockwise angle
        double aclock_azimuth = azimuth;
        if      ((0   <= azimuth) && (azimuth <=   90)) aclock_azimuth = 90 - azimuth;
        else if ((90  < azimuth) && (azimuth <=  180)) aclock_azimuth = 270 + (180 - azimuth);
        else if ((180 < azimuth) && (azimuth <=  270)) aclock_azimuth = 180 + (270 - azimuth);
        else if ((270 < azimuth) && (azimuth <=  360)) aclock_azimuth = 90  + (360 - azimuth);

        // get the x, y, and z cartesian coordinates
        double ele_radians = Math.toRadians(elevation);
        double azi_radians = Math.toRadians(aclock_azimuth);
        double r =  EARTH_RADIUS_KMS + getOrbitRadius(satellite.getType());
        double x_world = r * Math.cos(ele_radians) * Math.cos(azi_radians);
        double y_world = r * Math.cos(ele_radians) * Math.sin(azi_radians);
        double z_world = r * Math.sin(ele_radians);

        // one point perspective projection from camera
        int d = 100000;
        if (Double.compare(z_world, 0) == 0) {
            satellite.x = x_world; satellite.y = y_world; satellite.z = 0;
        } else {
            satellite.x =  ( x_world * d / (z_world + d) );
            satellite.y =  ( y_world * d / (z_world + d) );
            satellite.z = 0;
        }
    }

    public static double[] get_walk_drag_forces(double m, DetailProvider.Detail currD, DetailProvider.Detail prevD, boolean isElevationCorrected) {

        double rh = currD.getHumidity();

        // calculate air density rho
        double ele = currD.getAltitude();
        double temp = currD.getTemp();
        double rho = get_density(ele, temp, rh);
        double[] forces = new double[NUM_DRAG_FORCES];

        // calculate air drag
        int wind_degree = (int) Math.round(currD.getWindDeg());
        double wind_speed = currD.getWindSpeed();       // wind speed is saved in m/sec
        int walk_degree = currD.getBearing();
        double walk_speed = kmphTomps(currD.getSpeed());    // bike speed is saved in kmph.
        final double c_d_times_a = 0.24;
        forces[AIR] = UtilsWind.get_head_wind2(wind_speed, bearingToDegrees(wind_degree), walk_speed, bearingToDegrees(walk_degree))
                * (0.5d * rho * c_d_times_a);

        // calculate slope + rolling resistance
        double gradient = ((double) currD.getGradient()) * 0.01d;
        forces[SLOPE] = (9.81d * m) * gradient;
        double slope_efficiency = (45.6 + 1.1622 * gradient * 100.0);
        forces[SLOPE] = forces[SLOPE] * slope_efficiency / 100.0;
        if (! isElevationCorrected) forces[SLOPE] = 0.0d;
        double c_r = 0.98;              // kilo joules / kg-km
        forces[ROLL] = m * c_r;

        // calculate acceleration
        long t_curr = currD.getTimestamp() / 1000;
        long t_prev = prevD.getTimestamp() / 1000;
        double v_prev = kmphTomps(prevD.getSpeed());
        forces[ACCEL] = t_curr - t_prev == 0 ? 0.0d : ((walk_speed - v_prev) * m) / ((double) (t_curr - t_prev));
        return forces;
    }

    public static double[] get_jog_drag_forces(double m, DetailProvider.Detail currD, DetailProvider.Detail prevD, boolean isElevationCorrected) {

        double rh = currD.getHumidity();
        //if (Double.compare(rh, 0.0) == 0) return  zero_force;  // if weather data has not been collected return zeroes

        // calculate air density rho
        double ele = currD.getAltitude();
        double temp = currD.getTemp();
        double rho = get_density(ele, temp, rh);
        double[] forces = new double[NUM_DRAG_FORCES];

        // calculate air drag
        int wind_degree = (int) Math.round(currD.getWindDeg());
        double wind_speed = currD.getWindSpeed();       // wind speed is saved in m/sec
        int jog_degree = currD.getBearing();
        double jog_speed = kmphTomps(currD.getSpeed());    // bike speed is saved in kmph.
        final double c_d_times_a = 0.24;
        forces[AIR] = UtilsWind.get_head_wind2(wind_speed, bearingToDegrees(wind_degree), jog_speed, bearingToDegrees(jog_degree))
                * (0.5d * rho * c_d_times_a);

        // calculate slope + rolling resistance
        double gradient = ((double) currD.getGradient()) * 0.01d;
        forces[SLOPE] = (9.81d * m) * gradient;
        double slope_efficiency = (45.6 + 1.1622 * gradient * 100.0);
        forces[SLOPE] = forces[SLOPE] * slope_efficiency / 100.0;
        if (! isElevationCorrected) forces[SLOPE] = 0.0d;           // slope calculation if off without correction
        double c_r = 0.98;              // kilo joules / kg-km
        forces[ROLL] = m * c_r;

        // calculate acceleration
        long t_curr = currD.getTimestamp() / 1000;
        long t_prev = prevD.getTimestamp() / 1000;
        double v_prev = kmphTomps(prevD.getSpeed());
        forces[ACCEL] = t_curr - t_prev == 0 ? 0.0d : ((jog_speed - v_prev) * m) / ((double) (t_curr - t_prev));
        return forces;
    }


    // correction of front area due to side wind from Isvan paper
    // Isvan, O. Wind speed, wind yaw and the aerodynamic drag acting on a bicycle and rider Journal of Science and Cycling
    public static double get_ka_factor(double wind_mag, double wind_deg, double bike_mag, double bike_deg) {
        final double mu = 1.2;
        double[] apparent = UtilsWind.get_apparent(wind_mag, wind_deg, bike_mag, bike_deg);
        double theta = Math.toRadians(apparent[3]);
        double cos_t = Math.cos(theta); double sin_t = Math.sin(theta);
        return cos_t * cos_t + mu * sin_t * sin_t;
    }

    public static double[] get_bike_drag_forces(double m, double c_r, double c_d, double front_area,
                                                DetailProvider.Detail currD, DetailProvider.Detail prevD, boolean isElevationCorrected) {

        double[] zero_force = {0.0, 0.0, 0.0, 0.0};
        double rh = currD.getHumidity();
        //if (Double.compare(rh, 0.0) == 0) return  zero_force;  // if weather data has not been collected return zeroes

        // calculate air density rho
        double ele = currD.getAltitude();
        double temp = currD.getTemp();
        double rho = get_density(ele, temp, rh);
        double[] forces = new double[NUM_DRAG_FORCES];

        // calculate air drag
        int wind_degree = (int) Math.round(currD.getWindDeg());
        double wind_speed = currD.getWindSpeed();       // wind speed is saved in m/sec
        int bike_degree = currD.getBearing();
        double bike_speed = kmphTomps(currD.getSpeed());    // bike speed is saved in kmph.
        //double ka_factor = get_ka_factor(wind_speed, wind_degree, bike_speed, bike_degree);
        double ka_factor = get_ka_factor(wind_speed, bearingToDegrees(wind_degree), bike_speed, bearingToDegrees(bike_degree));
        forces[AIR] = UtilsWind.get_head_wind2(wind_speed, bearingToDegrees(wind_degree), bike_speed, bearingToDegrees(bike_degree))
                * (((0.5d * rho) * c_d) * ka_factor * front_area);

        // calculate slope + rolling resistance
        double gradient = ((double) currD.getGradient()) * 0.01d;
        forces[SLOPE] = (9.81d * m) * gradient;
        if (!isElevationCorrected)
            forces[SLOPE] = 0.0d;
        forces[ROLL] = (9.81d * m) * c_r;

        // calculate acceleration
        long t_curr = currD.getTimestamp() / 1000;
        long t_prev = prevD.getTimestamp() / 1000;
        double v_prev = kmphTomps(prevD.getSpeed());
        forces[ACCEL] = t_curr - t_prev == 0 ? 0.0d : ((bike_speed - v_prev) * m) / ((double) (t_curr - t_prev));
        return forces;
    }

    // must have 4 elements in drag_forces and v_curr must be in m / sec.
    public static double get_power(double[] forces, double v_curr, final double EFFICIENCY) {
        if (forces.length != 4) return 0.0;
        double f_total = forces[AIR] + forces[SLOPE] + forces[ROLL] + forces[ACCEL];
        f_total = (f_total > 0)? f_total: 0.0;  // coasting ?
        f_total = f_total * v_curr;
        return f_total / EFFICIENCY;
    }

    public static double get_power_test(double m, double c_r, double c_d, double front_area,
                                   DetailProvider.Detail currD, DetailProvider.Detail prevD) {
        double[] forces = get_bike_drag_forces(m, c_r, c_d, front_area, currD, prevD, true);
        double v_curr = kmphTomps(currD.getSpeed());
        return get_power(forces, v_curr, 0.95);
    }

    // get bicycle velocity in kmph. from power
    public static double get_bike_velocity(DetailProvider.Detail detail, double power, SharedPreferences sharedPreferences) {

        // get the bicycle parameters
        final double rider_wt = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_RIDER, "70.0"));
        final double bike_wt  = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_BIKE, "12.0"));
        final double front_area = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_FRONT, "0.5"));
        final double c_r = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_ROLLING, "0.015"));
        final double c_d = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_DRAG, "1.15"));
        final double mg = (rider_wt + bike_wt) * 9.81d;

        // calculate air density rho
        double ele = detail.getAltitude();
        double temp = detail.getTemp();
        double rh = detail.getHumidity();
        double rho = get_density(ele, temp, rh);

        // get the aerodynamic factor
        double wind_degree = bearingToDegrees((int) detail.getWindDeg());
        double wind_speed = mpsTokmph(detail.getWindSpeed());       // wind speed is saved in m/sec
        double bike_degree = bearingToDegrees(detail.getBearing());
        //double bike_speed = kmphTomps(detail.getSpeed());    // bike speed is saved in kmph.
        double K_A = 0.5d * rho * c_d * front_area;

        // calculate slope + rolling resistance
        double gradient = ((double) detail.getGradient()) * 0.01d;
        double other_resistance = mg * gradient + mg * c_r;
        return UtilsWind.get_velocity(K_A, bike_degree, wind_speed, wind_degree, power, other_resistance);

    }

    public static double[] filter_numbers(double[] values, double avg_value, int accuracy, int max_accuracy) {
        // when accuracy is high  use as-is
        // otherwise, use a sigmoid function to add a percentage of
        // the avg. speed, A higher % of avg. speed for low accuracy readings
        double factor = (accuracy < max_accuracy)? 1.0 / (1.0 + Math.exp(-(accuracy - (max_accuracy / 2.0)))): 0.0;
        for (int i = 0; i < values.length; i++)
            values[i] = factor * avg_value + (1.0 - factor) * values[i];
        return values;
    }

    // return the pressure for a given altitude and temperature using the hypsometric formula
    private static float get_pressure (double alt, double temp) {
        double kelvin = temp + 273.15;
        double lhs = 1.0 + (alt * 0.0065) / kelvin;
        double log_term = Math.log10(lhs) / (1.0 / 5.257);
        return (float) (1013.25 / (Math.pow(10, log_term)));
    }

    // get the density of air using teten's formula
    // alt is in meters, temp in cent. and relative humidity is a percentage
    public static float get_density(double alt, double temp, double rh) {
        double kelvin = temp + 273.15;
        double pressure = get_pressure(alt, temp);
        double saturated_pressure;
        if (temp >= 0)
            saturated_pressure = 0.61078 * Math.exp((17.270 * temp) / (temp + 237.3));
        else
            saturated_pressure = 0.61078 * Math.exp((21.875 * temp) / (temp + 265.5));
        return (float) ((0.0034848 / kelvin) * (pressure * 100 - 0.0037960 * rh * saturated_pressure));
    }

    private static int getOrbitRadius(int type) {
        final int GPS_ORBIT = 20000;      // average height from earth in kms.
        final int GLONASS_ORBIT = 19000;
        //final int GALILEO_ORBIT = 23000;
        //final int IRNSS_ORBIT = 36000;
        if (type == GnssStatus.CONSTELLATION_GPS) return GPS_ORBIT;
        if (type == GnssStatus.CONSTELLATION_GLONASS) return GLONASS_ORBIT;
        return 36000;

    }
}
