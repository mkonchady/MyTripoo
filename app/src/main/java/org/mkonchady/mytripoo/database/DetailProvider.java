package org.mkonchady.mytripoo.database;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
//import android.support.annotation.NonNull;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsJSON;

import java.util.HashMap;

/*------------------------------------------------------------
  Content provider for Details
  Latitude and longitude stored in microdegrees
 -------------------------------------------------------------*/
public class DetailProvider extends ContentProvider {

    // match with the authority in manifest
    public final static String PROVIDER_NAME = "org.mkonchady.mytripoo.database.DetailProvider";
    public final static String DATABASE_NAME = "detail.db";
    public final static int NUM_FIELDS = 15;
    public final static int DATABASE_VERSION = 1;
    public final static String TRACK_SEGMENT = "trkseg";
    public final static String TRACK_POINT = "trkpt";

    // detail table
    // latitude and longitude are in micro degrees
    public final static String DETAIL_TABLE = "detail";
    public final static String DETAIL_ROW = "content://" + PROVIDER_NAME + "/" + DETAIL_TABLE;
    static final String CREATE_DETAIL =
            " CREATE TABLE " + DETAIL_TABLE +
                    " (d_trip_id INTEGER NOT NULL, " +
                    " d_segment INTEGER NOT NULL, " +
                    " d_latitude INTEGER NOT NULL, " +      // 25.123456 * 1,000,000
                    " d_longitude INTEGER NOT NULL, " +     // 32.123456 * 1,000,000
                    " d_battery INTEGER NOT NULL, " +
                    " d_timestamp INTEGER NOT NULL, " +     // in milliseconds
                    " d_altitude REAL NOT NULL, " +         // in meters
                    " d_speed REAL NOT NULL, " +            // in kmph.
                    " d_bearing INTEGER NOT NULL, " +       // using meteorological north as 0 degrees
                    " d_accuracy INTEGER NOT NULL, " +
                    " d_gradient REAL NOT NULL, " +
                    " d_satinfo REAL NOT NULL," +           // signal strength
                    " d_satellites INTEGER NOT NULL," +     // number of satellites
                    " d_index TEXT," +                      // sequence number of detail for trip
                    " d_extras TEXT) ";                     // extras containing wind speed (m/s), wind direction, temp, humidity, and power
    static final int DETAILS = 3;
    static final int DETAIL = 4;

    // detail table columns
    public final static String TRIP_ID = "d_trip_id";
    public final static String SEGMENT = "d_segment";
    public final static String LAT = "d_latitude";
    public final static String LON = "d_longitude";
    public final static String BATTERY = "d_battery";
    public final static String TIMESTAMP = "d_timestamp";
    public final static String TIME = "time";
    public final static String ALTITUDE = "d_altitude";

    public final static String SPEED = "d_speed";
    public final static String BEARING = "d_bearing";
    public final static String ACCURACY = "d_accuracy";
    public final static String GRADIENT = "d_gradient";
    public final static String SATINFO = "d_satinfo";
    public final static String SATELLITES = "d_satellites";
    public final static String INDEX = "d_index";
    public final static String EXTRAS = "d_extras";

    // strava names
    public final static String STRAVA_ELEVATION = "ele";
    public final static String STRAVA_TIME = "time";
   // private static HashMap<String, String> DETAIL_PROJECTION_MAP;

    static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, DETAIL_TABLE + "/#", DETAIL);
        uriMatcher.addURI(PROVIDER_NAME, DETAIL_TABLE, DETAILS);
    }
    public static SQLiteDatabase db;

    // Database helper class
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_DETAIL);
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + DETAIL_TABLE);
            onCreate(db);
        }
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
        if (db == null) {
            db = dbHelper.getReadableDatabase();
            return db != null;
        }
        return true;
    }

    @Override
    public Uri insert(@NonNull  Uri uri, ContentValues values) {
        long row;
        Uri _uri = null;
        switch (uriMatcher.match(uri)) {
            case DETAILS:
                row = db.insert(DETAIL_TABLE, "", values);
                if (row >= 0) {
                    _uri = ContentUris.withAppendedId(Uri.parse(DETAIL_ROW), row);
                    if (getContext() != null)
                        getContext().getContentResolver().notifyChange(_uri, null);
                }
                break;
            default:
                break;
        }
        if (_uri != null)
            return _uri;
        throw new SQLException("Did not add row in Detail table " + uri);
    }

    @Override
    public Cursor query(@NonNull  Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        HashMap<String, String> DETAIL_PROJECTION_MAP = new HashMap<>();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (uriMatcher.match(uri)) {
            case DETAIL:
                qb.setTables(DETAIL_TABLE);
                qb.appendWhere( TRIP_ID + "=" + uri.getPathSegments().get(1));
                break;
            case DETAILS:
                qb.setTables(DETAIL_TABLE);
                qb.setProjectionMap(DETAIL_PROJECTION_MAP);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // run the query
        Cursor c = qb.query(db,	projection,	selection, selectionArgs, null, null, sortOrder);
        if (getContext() != null)
            c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int delete(@NonNull  Uri uri, String selection, String[] selectionArgs) {
        int count;
        String trip_id;
        switch (uriMatcher.match(uri)){
            case DETAIL:
                trip_id = uri.getPathSegments().get(1);
                count = db.delete(DETAIL_TABLE, TRIP_ID +  " = " + trip_id +
                        (!TextUtils.isEmpty(selection) ? " AND (" +
                                selection + ')' : ""), selectionArgs);
                break;
            case DETAILS:
                count = db.delete(DETAIL_TABLE, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (getContext() != null)
            getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(@NonNull  Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        int count;
        switch (uriMatcher.match(uri)){
            case DETAILS:
                count = db.update(DETAIL_TABLE, values,
                        selection, selectionArgs);
                break;
            case DETAIL:
                count = db.update(DETAIL_TABLE, values, TRIP_ID +
                        " = " + uri.getPathSegments().get(1) +
                        (!TextUtils.isEmpty(selection) ? " AND (" +
                                selection + ')' : ""), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri );
        }
        if (getContext() != null)
            getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(@NonNull  Uri uri) {
        switch (uriMatcher.match(uri)){
            case DETAILS:
                return "vnd.android.cursor.dir/vnd.example.details";
            case DETAIL:
                return "vnd.android.cursor.item/vnd.example.detail";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    public static Detail createDetail(int trip_id, String[] fields) {
        for (int i = 0; i < fields.length; i++)
            fields[i] = fields[i].trim();
        return (new DetailProvider.Detail(
                trip_id, Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Long.parseLong(fields[5]),
                Float.parseFloat(fields[6]), Float.parseFloat(fields[7]), Integer.parseInt(fields[8]),
                Integer.parseInt(fields[9]), Float.parseFloat(fields[10]), Float.parseFloat(fields[11]),
                Integer.parseInt(fields[12]), fields[13], fields[14]) );
    }


    public static Detail createEmptyDetail() {
        return (new DetailProvider.Detail(0, 0, 0, 0, 0, 0,
                0.0f, 0.0f, 0, 0, 0.0f, 0.0f,
                0, "", "") );
    }

    // Class for Detail
    public static class Detail {

        private int trip_id;
        private int segment;
        private int lat;
        private int lon;
        private int battery;
        private long timestamp;
        private float altitude;
        private float speed;
        private int bearing;
        private int accuracy;
        private float gradient;
        private float satinfo;
        private int satellites;
        private String index;
        private String extras;

        public Detail(int trip_id, int segment, int lat, int lon, int battery, long timestamp, float altitude,
               float speed, int bearing, int accuracy, float gradient, float satinfo, int satellites,
               String index, String extras) {
            this.trip_id = trip_id;
            this.segment = segment;
            this.lat = lat;
            this.lon = lon;
            this.battery = battery;
            this.timestamp = timestamp;
            this.altitude = altitude;
            this.speed = speed;
            this.bearing = bearing;
            this.accuracy = accuracy;
            this.gradient = gradient;
            this.satinfo = satinfo;
            this.satellites = satellites;
            this.index = index;
            this.extras = extras;
        }

        public String toString(String format) {
            if (format.equalsIgnoreCase("csv"))
                return trip_id + "," + segment + ", " + lat + ", " + lon + ", " + battery + ", " +
                        timestamp + ", " + altitude + ", " + speed + ", " + bearing + ", " +
                        accuracy + ", " + gradient + ", " + satinfo + ", " + satellites + ", " + index + ", " + extras;

            if (format.equalsIgnoreCase("xml")) {
                String newline = Constants.NEWLINE;
                return  (
                    ("     <" + DetailProvider.TRIP_ID + ">" + trip_id + "</" + DetailProvider.TRIP_ID + ">" + newline) +
                    ("     <" + DetailProvider.SEGMENT + ">" + segment + "</" + DetailProvider.SEGMENT + ">" + newline) +
                    ("     <" + DetailProvider.LAT + ">" + lat + "</" + DetailProvider.LAT + ">" + newline) +
                    ("     <" + DetailProvider.LON + ">" + lon + "</" + DetailProvider.LON + ">" + newline) +
                    ("     <" + DetailProvider.BATTERY + ">" + battery + "</" + DetailProvider.BATTERY + ">" + newline) +
                    ("     <" + DetailProvider.TIMESTAMP + ">" + UtilsDate.getDetailDateTimeSec(timestamp, lat, lon)  + "</" + DetailProvider.TIMESTAMP + ">" + newline) +
                    ("     <" + DetailProvider.ALTITUDE + ">" + altitude + "</" + DetailProvider.ALTITUDE + ">" + newline) +
                    ("     <" + DetailProvider.SPEED + ">" + speed + "</" + DetailProvider.SPEED + ">" + newline) +
                    ("     <" + DetailProvider.BEARING + ">" + bearing + "</" + DetailProvider.BEARING + ">" + newline) +
                    ("     <" + DetailProvider.ACCURACY + ">" + accuracy + "</" + DetailProvider.ACCURACY + ">" + newline) +
                    ("     <" + DetailProvider.GRADIENT + ">" + gradient + "</" + DetailProvider.GRADIENT + ">" + newline) +
                    ("     <" + DetailProvider.SATINFO + ">" + satinfo + "</" + DetailProvider.SATINFO + ">" + newline) +
                    ("     <" + DetailProvider.SATELLITES + ">" + satellites + "</" + DetailProvider.SATELLITES + ">" + newline) +
                    ("     <" + DetailProvider.INDEX + ">" + index + "</" + DetailProvider.INDEX + ">" + newline) +
                    ("     <" + DetailProvider.EXTRAS + ">" + extras + "</" + DetailProvider.EXTRAS + ">" + newline) );
            }

            if (format.equalsIgnoreCase("strava")) {
                String newline = Constants.NEWLINE;
                return  ( ("     <ele>" + altitude + "</ele>" + newline) +
                          ("     <time>" + UtilsDate.getZuluDateTimeSec(timestamp) + "</time>" + newline) );
            }

            return ("");
        }

        private double getExtrasParm(String parm) {
            String parmValue = "";
            try {
                if (extras.length() > 0) {
                    JSONObject jsonObject = new JSONObject(extras);
                    parmValue = jsonObject.getString(parm);
                }
            } catch(JSONException je){
                //Log.d("JSON", "Could not parse " + parm + " from Detail extras " + je.getMessage());
            }
            if (parmValue.length() > 0) {
                try {
                    return Double.parseDouble(parmValue);
                } catch (NumberFormatException ne) {
                  //  Log.d("JSON", "Could not get number from " + parmValue + " in Detail extras "
                  //          + ne.getMessage());
                }
            }
            return 0.0;
        }

        public int getAccuracy() {
            return accuracy;
        }
        public float getAltitude() { return  altitude; }
        public int getBattery() {
            return battery;
        }
        public int getBearing() {
            return bearing;
        }
        public float getGradient() {
            return gradient;
        }
        public String getIndex() { return  index; }
        public String getExtras() { return extras; }
        public int getLat() {
            return lat;
        }
        public int getLon() {
            return lon;
        }
        public int getSatellites() {
            return satellites;
        }
        public float getSatinfo() {
            return satinfo;
        }
        public int getSegment() { return segment; }
        public float getSpeed() {
            return speed;
        }
        public float getVelocity() {
            return speed;
        }
        public long getTimestamp() {
            return timestamp;
        }
        public int getTrip_id() {
            return trip_id;
        }
        public double getTemp() { return getExtrasParm(Constants.OPENW_TEMP); }
        public double getWindSpeed() { return getExtrasParm(Constants.OPENW_WIND_SPEED); }
        public double getWindDeg() { return getExtrasParm(Constants.OPENW_WIND_DEG); }
        public double getHumidity() { return getExtrasParm(Constants.OPENW_HUMIDITY); }
        public double getPower() { return getExtrasParm(Constants.BIKE_POWER); }

        public void setAccuracy(int accuracy) {
            this.accuracy = accuracy;
        }
        public void setAltitude(float altitude) {
            this.altitude = altitude;
        }
        public void setBattery(int battery) {
            this.battery = battery;
        }
        public void setBearing(int bearing) {
            this.bearing = bearing;
        }
        public void setGradient(float gradient) {
            this.gradient = gradient;
        }
        public void setIndex(String index) {
            this.index = index;
        }
        public void setExtras(String extras) {
            this.extras = extras;
        }
        public void setPower(String power)  {setExtras(UtilsJSON.put_JSON_string(power, Constants.BIKE_POWER, extras));}
        public void setLat(int lat) {
            this.lat = lat;
        }
        public void setLon(int lon) {
            this.lon = lon;
        }
        public void setSpeed(float speed) {
            this.speed = speed;
        }
        public void setSatinfo(float satinfo) {
            this.satinfo = satinfo;
        }
        public void setSatellites(int satelllites) {
            this.satellites = satelllites;
        }
        public void setSegment(int segment) {
            this.segment = segment;
        }
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
        public void setTrip_id(int trip_id) {
            this.trip_id = trip_id;
        }
    }

}