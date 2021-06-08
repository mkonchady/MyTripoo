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

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsJSON;

import java.util.HashMap;

/*------------------------------------------------------------
  Content provider for Summary
 -------------------------------------------------------------*/
public class SummaryProvider extends ContentProvider {

    // match with the authority in manifest
    public final static String PROVIDER_NAME = "org.mkonchady.mytripoo.database.SummaryProvider";
    public final static String DATABASE_NAME = "summary.db";

    // summary table
    public final static String SUMMARY_TABLE = "summary";
    public final static String SUMMARY_ROW = "content://" + PROVIDER_NAME + "/" + SUMMARY_TABLE;
   // public final static String ONE_SUMMARY = "content://" + PROVIDER_NAME + "/" + SUMMARY_TABLE + "/#";
    static final String CREATE_SUMMARY =
            " CREATE TABLE " + SUMMARY_TABLE +
                    " (trip_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    " name TEXT NOT NULL, " +
                    " category TEXT NOT NULL, " +
                    " start_time INTEGER NOT NULL, " +
                    " end_time INTEGER NOT NULL, " +
                    " distance REAL NOT NULL, " +
                    " avg_lat INTEGER NOT NULL, " +
                    " avg_lon INTEGER NOT NULL, " +
                    " min_elevation INTEGER NOT NULL, " +
                    " max_elevation INTEGER NOT NULL, " +
                    " min_speed REAL NOT NULL," +
                    " max_speed REAL NOT NULL, " +
                    " avg_speed REAL NOT NULL, " +
                    " min_accuracy REAL NOT NULL," +
                    " max_accuracy REAL NOT NULL, " +
                    " avg_accuracy REAL NOT NULL, " +
                    " min_time_readings REAL NOT NULL," +
                    " max_time_readings REAL NOT NULL, " +
                    " avg_time_readings REAL NOT NULL, " +
                    " min_dist_readings REAL NOT NULL," +
                    " max_dist_readings REAL NOT NULL, " +
                    " avg_dist_readings REAL NOT NULL, " +
                    " status text NOT NULL, " +
                    " extras text)";
    static final int SUMMARIES = 1;
    static final int SUMMARY = 2;

    // summary table columns
    public final static String TRIP_ID = "trip_id";
    public final static String NAME = "name";
    public final static String CATEGORY = "category";
    public final static String START_TIME = "start_time";
    public final static String END_TIME = "end_time";
    public final static String DISTANCE = "distance";
    public final static String AVG_LAT = "avg_lat";
    public final static String AVG_LON = "avg_lon";
    public final static String MAX_ELEVATION = "max_elevation";
    public final static String MIN_ELEVATION = "min_elevation";
    public final static String MIN_SPEED = "min_speed";
    public final static String MAX_SPEED = "max_speed";
    public final static String AVG_SPEED = "avg_speed";
    public final static String MIN_ACCURACY = "min_accuracy";
    public final static String MAX_ACCURACY = "max_accuracy";
    public final static String AVG_ACCURACY = "avg_accuracy";
    public final static String MAX_TIME_READINGS = "max_time_readings";
    public final static String MIN_TIME_READINGS = "min_time_readings";
    public final static String AVG_TIME_READINGS = "avg_time_readings";
    public final static String MAX_DIST_READINGS = "max_dist_readings";
    public final static String MIN_DIST_READINGS = "min_dist_readings";
    public final static String AVG_DIST_READINGS = "avg_dist_readings";
    public final static String POLL_INTERVAL = "poll_interval";
    public final static String POLL_DISTANCE = "gps_accuracy";
    public final static String STATUS = "status";
    public final static String EXTRAS = "extras";
    public final static String MOV_AVG_SPEED = "mov_avg_speed";        // stored in extras
    public final static String CATEGORY_EDITED = "category_edited";    // category was manually corrected
    public final static String LOCATION_EDITED = "location_edited";    // location was corrected by an api
    public final static String ELEVATION_EDITED = "elevation_edited";    // elevation was corrected by an api
    public final static int NUM_FIELDS = 24;
    public final static int DATABASE_VERSION = 1;
    public final static String TAG = "SummaryProvider";

    static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, SUMMARY_TABLE + "/#", SUMMARY);
        uriMatcher.addURI(PROVIDER_NAME, SUMMARY_TABLE, SUMMARIES);
    }
    public static SQLiteDatabase db;

    // Database helper class
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_SUMMARY);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + SUMMARY_TABLE);
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

            case SUMMARIES:
                row = db.insert(SUMMARY_TABLE, "", values);
                if (row >= 0) {
                    _uri = ContentUris.withAppendedId(Uri.parse(SUMMARY_ROW), row);
                    if (getContext() != null)
                        getContext().getContentResolver().notifyChange(_uri, null);
                }
                break;
            default:
                break;
        }
        if (_uri != null)
            return _uri;
        throw new SQLException("Did not add row in Summary table " + uri);
    }

    @Override
    public Cursor query(@NonNull  Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        HashMap<String, String> SUMMARY_PROJECTION_MAP = new HashMap<>();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (uriMatcher.match(uri)) {
            case SUMMARY:
                qb.setTables(SUMMARY_TABLE);
                qb.appendWhere(TRIP_ID + "=" + uri.getPathSegments().get(1));
                break;
            case SUMMARIES:
                qb.setTables(SUMMARY_TABLE);
                qb.setProjectionMap(SUMMARY_PROJECTION_MAP);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // run the query
        Cursor c = qb.query(db,	projection,	selection, selectionArgs,
                null, null, sortOrder);
        if (getContext() != null)
            c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int delete(@NonNull  Uri uri, String selection, String[] selectionArgs) {
        int count;
        String trip_id;
        switch (uriMatcher.match(uri)){
            case SUMMARY:
                trip_id = uri.getPathSegments().get(1);
                count = db.delete(SUMMARY_TABLE, TRIP_ID +  " = " + trip_id +
                        (!TextUtils.isEmpty(selection) ? " AND (" +
                                selection + ')' : ""), selectionArgs);
                break;
            case SUMMARIES:
                  count = db.delete(SUMMARY_TABLE, selection, selectionArgs);
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
            case SUMMARIES:
                count = db.update(SUMMARY_TABLE, values,
                        selection, selectionArgs);
                break;
            case SUMMARY:
                count = db.update(SUMMARY_TABLE, values, TRIP_ID +
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
            case SUMMARIES:
                return "vnd.android.cursor.dir/vnd.example.summaries";
            case SUMMARY:
                return "vnd.android.cursor.item/vnd.example.summary";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    public static Summary createSummary() {
        return createSummary(Constants.LARGE_INT);
    }

    public static Summary createSummary(long max_trip_time) {
        return createSummary(max_trip_time, false);
    }

    // create a summary with default values
    public static Summary createSummary(long max_trip_time, boolean local) {
        long start_time = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(SummaryProvider.TRIP_ID, 0);
        values.put(SummaryProvider.NAME, "");
        values.put(SummaryProvider.CATEGORY, "");
        values.put(SummaryProvider.START_TIME, start_time);
        values.put(SummaryProvider.END_TIME, start_time + max_trip_time);
        values.put(SummaryProvider.DISTANCE, 0.0f);
        values.put(SummaryProvider.AVG_LAT, 0);
        values.put(SummaryProvider.AVG_LON, 0);
        values.put(SummaryProvider.MIN_ELEVATION, Constants.LARGE_INT);
        values.put(SummaryProvider.MAX_ELEVATION, 0);
        values.put(SummaryProvider.MIN_SPEED, Constants.LARGE_FLOAT);
        values.put(SummaryProvider.MAX_SPEED, 0.0f);
        values.put(SummaryProvider.AVG_SPEED, 0.0f);
        values.put(SummaryProvider.MIN_ACCURACY, Constants.LARGE_FLOAT);
        values.put(SummaryProvider.MAX_ACCURACY, 0.0f);
        values.put(SummaryProvider.AVG_ACCURACY, 0.0f);
        values.put(SummaryProvider.MIN_TIME_READINGS, Constants.LARGE_FLOAT);
        values.put(SummaryProvider.MAX_TIME_READINGS, 0.0f);
        values.put(SummaryProvider.AVG_TIME_READINGS, 0.0f);
        values.put(SummaryProvider.MIN_DIST_READINGS, Constants.LARGE_FLOAT);
        values.put(SummaryProvider.MAX_DIST_READINGS, 0.0f);
        values.put(SummaryProvider.AVG_DIST_READINGS, 0.0f);
        values.put(SummaryProvider.STATUS, Constants.RUNNING);
        String extras = (local)? UtilsJSON.buildInitialExtras(): "";       // imported files will have a blank extras
        values.put(SummaryProvider.EXTRAS, extras); // field
        return (new SummaryProvider.Summary(values));
    }

    // create a summary from an ordered list of values
    public static Summary createSummary(String[] fields) {
        for (int i = 0; i < fields.length; i++)
            fields[i] = fields[i].trim();
        return (new SummaryProvider.Summary(0, fields[1], fields[2],
                Long.parseLong(fields[3]), Long.parseLong(fields[4]), Float.parseFloat(fields[5]),
                Integer.parseInt(fields[6]), Integer.parseInt(fields[7]),
                Integer.parseInt(fields[8]), Integer.parseInt(fields[9]),
                Float.parseFloat(fields[10]), Float.parseFloat(fields[11]), Float.parseFloat(fields[12]),
                Float.parseFloat(fields[13]), Float.parseFloat(fields[14]), Float.parseFloat(fields[15]),
                Float.parseFloat(fields[16]),Float.parseFloat(fields[17]),Float.parseFloat(fields[18]),
                Float.parseFloat(fields[19]),Float.parseFloat(fields[20]),Float.parseFloat(fields[21]),
                fields[22], fields[23]));
    }

    // Class for Summary
    public static class Summary {

        private int trip_id;
        private String name;
        private String category;
        private long start_time;
        private long end_time;
        private float distance;
        private int avg_lat;
        private int avg_lon;
        private int min_elevation;
        private int max_elevation;
        private float min_speed;
        private float max_speed;
        private float avg_speed;
        private float min_accuracy;
        private float max_accuracy;
        private float avg_accuracy;
        private float min_time_readings;
        private float max_time_readings;
        private float avg_time_readings;
        private float min_dist_readings;
        private float max_dist_readings;
        private float avg_dist_readings;
        private String status;
        private String extras;

        Summary(int trip_id, String name, String category, long start_time, long end_time,
                float distance, int avg_lat, int avg_lon, int min_elevation, int max_elevation,
                float min_speed, float max_speed, float avg_speed,
                float min_accuracy, float max_accuracy, float avg_accuracy,
                float min_time_readings, float max_time_readings, float avg_time_readings,
                float min_dist_readings, float max_dist_readings, float avg_dist_readings,
                String status, String extras) {
            this.trip_id = trip_id;
            this.name = name.trim();
            this.category = category;
            this.start_time = start_time;
            this.end_time = end_time;
            this.distance = distance;
            this.avg_lat = avg_lat;
            this.avg_lon = avg_lon;
            this.min_elevation = min_elevation;
            this.max_elevation = max_elevation;
            this.min_speed = min_speed;
            this.max_speed = max_speed;
            this.avg_speed = avg_speed;
            this.min_accuracy = min_accuracy;
            this.max_accuracy = max_accuracy;
            this.avg_accuracy = avg_accuracy;
            this.min_time_readings = min_time_readings;
            this.max_time_readings = max_time_readings;
            this.avg_time_readings = avg_time_readings;
            this.min_dist_readings = min_dist_readings;
            this.max_dist_readings = max_dist_readings;
            this.avg_dist_readings = avg_dist_readings;
            this.status = status;
            this.extras = extras;
        }

        Summary (ContentValues contentValues) {
            this.trip_id = contentValues.getAsInteger(TRIP_ID);
            this.name = contentValues.getAsString(NAME);
            this.category = contentValues.getAsString(CATEGORY);
            this.start_time = contentValues.getAsLong(START_TIME);
            this.end_time = contentValues.getAsLong(END_TIME);
            this.distance = contentValues.getAsFloat(DISTANCE);
            this.avg_lat = contentValues.getAsInteger(AVG_LAT);
            this.avg_lon = contentValues.getAsInteger(AVG_LON);
            this.min_elevation = contentValues.getAsInteger(MIN_ELEVATION);
            this.max_elevation = contentValues.getAsInteger(MAX_ELEVATION);
            this.min_speed = contentValues.getAsFloat(MIN_SPEED);
            this.max_speed = contentValues.getAsFloat(MAX_SPEED);
            this.avg_speed = contentValues.getAsFloat(AVG_SPEED);
            this.min_accuracy = contentValues.getAsFloat(MIN_ACCURACY);
            this.max_accuracy = contentValues.getAsFloat(MAX_ACCURACY);
            this.avg_accuracy = contentValues.getAsFloat(AVG_ACCURACY);
            this.min_time_readings = contentValues.getAsFloat(MIN_TIME_READINGS);
            this.max_time_readings = contentValues.getAsFloat(MAX_TIME_READINGS);
            this.avg_time_readings = contentValues.getAsFloat(AVG_TIME_READINGS);
            this.min_dist_readings = contentValues.getAsFloat(MIN_DIST_READINGS);
            this.max_dist_readings = contentValues.getAsFloat(MAX_DIST_READINGS);
            this.avg_dist_readings = contentValues.getAsFloat(AVG_DIST_READINGS);
            this.status = contentValues.getAsString(STATUS);
            this.extras = contentValues.getAsString(EXTRAS);
        }

        public String toString(String format) {
            if (name.length() == 0) name = "Trip " + trip_id;
            String[] fields = {trip_id + "", name + "", category + "", start_time + "", end_time + "", distance + "",
                    avg_lat + "", avg_lon + "",  min_elevation + "", max_elevation + "",
                    min_speed + "", max_speed + "", avg_speed + "",
                    min_accuracy + "", max_accuracy + "", avg_accuracy + "",
                    min_time_readings + "", max_time_readings + "", avg_time_readings + "",
                    min_dist_readings + "", max_dist_readings + "", avg_dist_readings + "",
                    status, extras};
            if (format.equalsIgnoreCase("csv")) {
                StringBuilder result = new StringBuilder();
                for(int i = 0; i < fields.length; i++) {
                    result.append(fields[i]);
                    if (i < (fields.length - 1)) result.append(",");
                }
                return result.toString();
            }

            if (format.equalsIgnoreCase("strava")) {
                return "    <time>" + UtilsDate.getZuluDateTimeSec(start_time) + "</time>";
            }

            if (format.equalsIgnoreCase("xml")) {
                String newline = Constants.NEWLINE;
                String new_extras = UtilsJSON.delWayPoints(extras);
                return (
                       ("     <" + SummaryProvider.TRIP_ID + ">"  + trip_id + "</" + SummaryProvider.TRIP_ID + ">"  + newline) +
                       ("     <" + SummaryProvider.NAME + ">" + name.trim() + "</" + SummaryProvider.NAME + ">" + newline) +
                       ("     <" + SummaryProvider.CATEGORY + ">" + category + "</" + SummaryProvider.CATEGORY + ">" + newline) +
                       ("     <" + SummaryProvider.START_TIME + ">"  + start_time + "</" + SummaryProvider.START_TIME + ">" + newline) +
                       ("     <" + SummaryProvider.END_TIME + ">" + end_time + "</" + SummaryProvider.END_TIME + ">"  + newline) +
                       ("     <" + SummaryProvider.DISTANCE + ">" + distance + "</" + SummaryProvider.DISTANCE + ">"  + newline) +
                       ("     <" + SummaryProvider.AVG_LAT + ">"  + avg_lat + "</" + SummaryProvider.AVG_LAT + ">"  + newline) +
                       ("     <" + SummaryProvider.AVG_LON + ">"  + avg_lon + "</" + SummaryProvider.AVG_LON + ">"  + newline) +
                       ("     <" + SummaryProvider.MIN_ELEVATION + ">" + min_elevation + "</" + SummaryProvider.MIN_ELEVATION + ">" + newline) +
                       ("     <" + SummaryProvider.MAX_ELEVATION + ">" + max_elevation + "</" + SummaryProvider.MAX_ELEVATION + ">" + newline) +
                       ("     <" + SummaryProvider.MIN_SPEED + ">" + min_speed + "</" + SummaryProvider.MIN_SPEED + ">" + newline) +
                       ("     <" + SummaryProvider.MAX_SPEED + ">" + max_speed + "</" + SummaryProvider.MAX_SPEED + ">" + newline) +
                       ("     <" + SummaryProvider.AVG_SPEED + ">" + avg_speed + "</" + SummaryProvider.AVG_SPEED + ">" + newline) +
                       ("     <" + SummaryProvider.MIN_ACCURACY + ">" + min_accuracy + "</" + SummaryProvider.MIN_ACCURACY + ">" + newline) +
                       ("     <" + SummaryProvider.MAX_ACCURACY + ">" + max_accuracy + "</" + SummaryProvider.MAX_ACCURACY + ">" + newline) +
                       ("     <" + SummaryProvider.AVG_ACCURACY + ">" + avg_accuracy + "</" + SummaryProvider.AVG_ACCURACY + ">" + newline) +
                       ("     <" + SummaryProvider.MIN_TIME_READINGS + ">" + min_time_readings + "</" + SummaryProvider.MIN_TIME_READINGS + ">" + newline) +
                       ("     <" + SummaryProvider.MAX_TIME_READINGS + ">" + max_time_readings + "</" + SummaryProvider.MAX_TIME_READINGS + ">" + newline) +
                       ("     <" + SummaryProvider.AVG_TIME_READINGS + ">" + avg_time_readings + "</" + SummaryProvider.AVG_TIME_READINGS + ">" + newline) +
                       ("     <" + SummaryProvider.MIN_DIST_READINGS + ">" + min_dist_readings + "</" + SummaryProvider.MIN_DIST_READINGS + ">" + newline) +
                       ("     <" + SummaryProvider.MAX_DIST_READINGS + ">" + max_dist_readings + "</" + SummaryProvider.MAX_DIST_READINGS + ">" + newline) +
                       ("     <" + SummaryProvider.AVG_DIST_READINGS + ">" + avg_dist_readings + "</" + SummaryProvider.AVG_DIST_READINGS + ">" + newline) +
                       ("     <" + SummaryProvider.STATUS + ">" + status + "</" + SummaryProvider.STATUS + ">" + newline) +
                       ("     <" + SummaryProvider.EXTRAS + ">" + new_extras + "</" + SummaryProvider.EXTRAS + ">" + newline)
                );
            }
            return ("");
        }

        public int getTrip_id() {
            return trip_id;
        }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public long getStart_time() {
            return start_time;
        }
        public long getEnd_time() {
            return end_time;
        }
        public float getDistance() {
            return distance;
        }
        public int getAvg_lat() { return avg_lat; }
        public int getAvg_lon() { return avg_lon; }
        public int getMin_elevation() { return min_elevation; }
        public int getMax_elevation() { return max_elevation; }
        public float getMin_speed() {
            return min_speed;
        }
        public float getMax_speed() {
            return max_speed;
        }
        public float getAvg_speed() {
            return avg_speed;
        }
        public float getMin_accuracy() {
            return min_accuracy;
        }
        public float getMax_accuracy() {
            return max_accuracy;
        }
        public float getAvg_accuracy() {
            return avg_accuracy;
        }
        public float getMin_time_readings() {
            return min_time_readings;
        }
        public float getMax_time_readings() {
            return max_time_readings;
        }
        public float getAvg_time_readings() {
            return avg_time_readings;
        }
        public float getMin_dist_readings() {
            return min_dist_readings;
        }
        public float getMax_dist_readings() {
            return max_dist_readings;
        }
        public float getAvg_dist_readings() {
            return avg_dist_readings;
        }
        public String getStatus() {
            return status;
        }
        public String getExtras() {
            return extras;
        }
        public String getMovingAverage(String extras) { return UtilsJSON.get_JSON_value(extras, MOV_AVG_SPEED); }
        public String getPollInterval() { return UtilsJSON.get_JSON_value(extras, POLL_INTERVAL); }
        public String getPollDistancel() { return UtilsJSON.get_JSON_value(extras, POLL_DISTANCE); }

        public void setTrip_id(int trip_id) {
            this.trip_id = trip_id;
        }
        public void setName(String name) {
            this.name = name;
        }
        public void setCategory(String category) {
            this.category = category;
        }
        public void setDistance(float distance) {
            this.distance = distance;
        }
        public void setAvg_lat(int avg_lat) {
            this.avg_lat = avg_lat;
        }
        public void setAvg_lon(int avg_lon) {
            this.avg_lon = avg_lon;
        }
        public void setMin_elevation(int min_elevation) {
            this.min_elevation = min_elevation;
        }
        public void setMax_elevation(int max_elevation) {
            this.max_elevation = max_elevation;
        }
        public void setMin_speed(float min_speed) {
            this.min_speed = min_speed;
        }
        public void setMax_speed(float max_speed) {
            this.max_speed = max_speed;
        }
        public void setMin_accuracy(float min_accuracy) {
            this.min_accuracy = min_accuracy;
        }
        public void setMax_accuracy(float max_accuracy) {
            this.max_accuracy = max_accuracy;
        }
        public void setAvg_accuracy(float avg_accuracy) {
            this.avg_accuracy = avg_accuracy;
        }
        public void setMin_time_readings(float min_time_readings) {
            this.min_time_readings = min_time_readings;
        }
        public void setMax_time_readings(float max_time_readings) {
            this.max_time_readings = max_time_readings;
        }
        public void setAvg_time_readings(float avg_time_readings) {
            this.avg_time_readings = avg_time_readings;
        }
        public void setMin_dist_readings(float min_dist_readings) {
            this.min_dist_readings = min_dist_readings;
        }
        public void setMax_dist_readings(float max_dist_readings) {
            this.max_dist_readings = max_dist_readings;
        }
        public void setAvg_dist_readings(float avg_dist_readings) {
            this.avg_dist_readings = avg_dist_readings;
        }
        public void setStatus(String status) {
            this.status = status;
        }
        public void setExtras(String extras) {
            this.extras = extras;
        }
        public void setAvg_speed(float avg_speed) {
            this.avg_speed = avg_speed;
        }
        public void setStart_time(long start_time) {
            this.start_time = start_time;
        }
        public void setEnd_time(long end_time) {
            this.end_time = end_time;
        }
    }
}