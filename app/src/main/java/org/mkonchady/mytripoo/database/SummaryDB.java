package org.mkonchady.mytripoo.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.LongSparseArray;

import org.json.JSONException;
import org.json.JSONObject;
import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.utils.RepairTrip;
import org.mkonchady.mytripoo.database.SummaryProvider.Summary;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;
import java.util.Collections;

/*
 A collection of utilities to manage the Summary tables
 */
public class SummaryDB {

    SQLiteDatabase db;

    // set the database handler
    public SummaryDB(SQLiteDatabase db) {
        this.db = db;
    }

    // get a particular summary
    public Summary getSummary(Context context, int trip_id) {
        ArrayList<Summary> summaries = getSummaries(context, "", "", trip_id);
        return summaries.get(0);
    }

    // get a list of summaries or a single summary
    public ArrayList<Summary> getSummaries(Context context, String whereClause, String sortOrder, int trip_id) {
        ArrayList<Summary> summaries = new ArrayList<>();
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        if (sortOrder == null || sortOrder.length() == 0)
            sortOrder = SummaryProvider.TRIP_ID + " desc";
        Cursor c = (trip_id == -1)? context.getContentResolver().query(trips, null, whereClause, null, sortOrder):
                                    context.getContentResolver().query(trips, null, "trip_id = " + trip_id, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                Summary summary = new Summary(
                        c.getInt(c.getColumnIndex(SummaryProvider.TRIP_ID)),
                        c.getString(c.getColumnIndex(SummaryProvider.NAME)),
                        c.getString(c.getColumnIndex(SummaryProvider.CATEGORY)),
                        c.getLong(c.getColumnIndex(SummaryProvider.START_TIME)),
                        c.getLong(c.getColumnIndex(SummaryProvider.END_TIME)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.DISTANCE)),
                        c.getInt(c.getColumnIndex(SummaryProvider.AVG_LAT)),
                        c.getInt(c.getColumnIndex(SummaryProvider.AVG_LON)),
                        c.getInt(c.getColumnIndex(SummaryProvider.MIN_ELEVATION)),
                        c.getInt(c.getColumnIndex(SummaryProvider.MAX_ELEVATION)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.MIN_SPEED)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.MAX_SPEED)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.AVG_SPEED)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.MIN_ACCURACY)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.MAX_ACCURACY)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.AVG_ACCURACY)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.MIN_TIME_READINGS)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.MAX_TIME_READINGS)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.AVG_TIME_READINGS)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.MIN_DIST_READINGS)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.MAX_DIST_READINGS)),
                        c.getFloat(c.getColumnIndex(SummaryProvider.AVG_DIST_READINGS)),
                        c.getString(c.getColumnIndex(SummaryProvider.STATUS)),
                        c.getString(c.getColumnIndex(SummaryProvider.EXTRAS))
                        );
                summaries.add(summary);
            } while (c.moveToNext());
            c.close();
        }
        return summaries;
    }

    // get a list of summary ids
    public ArrayList<Integer> getSummaryIDs(Context context) {
        ArrayList<Integer> summaryIDs = new ArrayList<>();
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        Cursor c = context.getContentResolver().query(trips, null, null, null, null);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                summaryIDs.add(c.getInt(c.getColumnIndex(SummaryProvider.TRIP_ID)));
            } while (c.moveToNext());
            c.close();
        }
        return summaryIDs;
    }

    // add a new summary
    public int addSummary(Context context, Summary summary) {
        Uri uri;
        ContentValues values = putSummaryValues(summary);
        uri = context.getContentResolver().insert(Uri.parse(SummaryProvider.SUMMARY_ROW), values);
        int id = -1;
        if (uri == null) return id;
        // get the ID of the last entered row
        switch (SummaryProvider.uriMatcher.match(uri)) {
            case SummaryProvider.SUMMARY:
                id = Integer.parseInt(uri.getPathSegments().get(1));
                break;
            default:
                break;
        }
        return id;
    }

    public ContentValues putSummaryValues(Summary summary) {
        ContentValues values = new ContentValues();
       // values.put(SummaryProvider.PREF_TRIP_ID, 0 );
        values.put(SummaryProvider.START_TIME, summary.getStart_time());
        values.put(SummaryProvider.NAME, summary.getName());
        values.put(SummaryProvider.CATEGORY, summary.getCategory());
        values.put(SummaryProvider.START_TIME, summary.getStart_time());
        values.put(SummaryProvider.END_TIME, summary.getEnd_time());
        values.put(SummaryProvider.DISTANCE, summary.getDistance());
        values.put(SummaryProvider.AVG_LAT, summary.getAvg_lat());
        values.put(SummaryProvider.AVG_LON, summary.getAvg_lon());
        values.put(SummaryProvider.MIN_ELEVATION, summary.getMin_elevation());
        values.put(SummaryProvider.MAX_ELEVATION, summary.getMax_elevation());
        values.put(SummaryProvider.MIN_SPEED, summary.getMin_speed());
        values.put(SummaryProvider.MAX_SPEED, summary.getMax_speed());
        values.put(SummaryProvider.AVG_SPEED, summary.getAvg_speed());
        values.put(SummaryProvider.MIN_ACCURACY, summary.getMin_accuracy());
        values.put(SummaryProvider.MAX_ACCURACY, summary.getMax_accuracy());
        values.put(SummaryProvider.AVG_ACCURACY, summary.getAvg_accuracy());
        values.put(SummaryProvider.MIN_TIME_READINGS, summary.getMin_time_readings());
        values.put(SummaryProvider.MAX_TIME_READINGS, summary.getMax_time_readings());
        values.put(SummaryProvider.AVG_TIME_READINGS, summary.getAvg_time_readings());
        values.put(SummaryProvider.MIN_DIST_READINGS, summary.getMin_dist_readings());
        values.put(SummaryProvider.MAX_DIST_READINGS, summary.getMax_dist_readings());
        values.put(SummaryProvider.AVG_DIST_READINGS, summary.getAvg_dist_readings());
        values.put(SummaryProvider.STATUS, summary.getStatus());
        values.put(SummaryProvider.EXTRAS, summary.getExtras());
        return values;
    }

    // get a list of trip ids in the timestamp range
    public ArrayList<Integer> getTripIds(Context context, long startTimestamp, long endTimestamp) {
        ArrayList<Integer> tripIds = new ArrayList<>();
        String whereClause = SummaryProvider.START_TIME + " >= " + startTimestamp + " and " +
                             SummaryProvider.END_TIME + " <= " + endTimestamp;
        ArrayList<Summary> summaries = getSummaries(context, whereClause, "", -1);
        for (Summary summary: summaries) {
            tripIds.add(summary.getTrip_id());
        }
        return tripIds;
    }

    // update the status of a summary
    public boolean setSummaryStatus(Context context, String status, int trip_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.STATUS + " = \"" + status +
                "\"  where " + SummaryProvider.TRIP_ID + " =  " + trip_id;

        return runSQL(execSQL);
    }

    // update the start timestamp of a summary
    //public boolean updateSummaryStartTimestamp(Context context, long timestamp, int trip_id) {
    //    String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
    //            SummaryProvider.START_TIME + " = " + timestamp +
    //            " where " + SummaryProvider.PREF_TRIP_ID + " =  " + trip_id;
    //    runSQL(execSQL);
    //    return true;
    //}

    // update the end timestamp of a summary
    public boolean setSummaryEndTimestamp(Context context, long timestamp, int trip_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.END_TIME + " = " + timestamp +
                " where " + SummaryProvider.TRIP_ID + " =  " + trip_id;
        return runSQL(execSQL);
    }


    // update the end timestamp of a summary
    public boolean setSummaryStartTimestamp(Context context, long timestamp, int trip_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.START_TIME + " = " + timestamp +
                " where " + SummaryProvider.TRIP_ID + " =  " + trip_id;
        return runSQL(execSQL);
    }

    // cumulative update the distance of a summary
    public boolean setSummaryCumulativeDistance(Context context, int distance, int trip_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.DISTANCE + " = " +
                   "(select distance from " + SummaryProvider.SUMMARY_TABLE +
                        " where " + SummaryProvider.TRIP_ID + " = " + trip_id + ")" +
                    " + " + distance +
                " where " + SummaryProvider.TRIP_ID + " =  " + trip_id;
        return runSQL(execSQL);
    }

    public boolean setSummaryExtrasEdited(Context context, int trip_id, boolean isEdited) {
        String extras = "";
        try {
            JSONObject jsonObject = new JSONObject(getExtras(context, trip_id));
            jsonObject.put("edited", isEdited);
            extras = jsonObject.toString();
        }catch (JSONException je) {
            Logger.e("JSON", "JSON formatting error in encoding Summary extras "  + je.getMessage());
        }
        setSummaryExtras(context, extras, trip_id);
        return true;
    }

    public boolean setSummaryCategoryEdited(Context context, int trip_id, boolean isEdited) {
        String extras = "";
        try {
            JSONObject jsonObject = new JSONObject(getExtras(context, trip_id));
            jsonObject.put(SummaryProvider.CATEGORY_EDITED, isEdited);
            extras = jsonObject.toString();
        }catch (JSONException je) {
            Logger.e("JSON", "JSON formatting error in encoding Summary category edited extras "  + je.getMessage());
        }
        setSummaryExtras(context, extras, trip_id);
        return true;
    }

    public boolean setSummaryLocationEdited(Context context, int trip_id, boolean isEdited) {
        String extras = "";
        try {
            JSONObject jsonObject = new JSONObject(getExtras(context, trip_id));
            jsonObject.put(SummaryProvider.LOCATION_EDITED, isEdited);
            extras = jsonObject.toString();
        }catch (JSONException je) {
            Logger.e("JSON", "JSON formatting error in encoding Summary location edited extras "  + je.getMessage());
        }
        setSummaryExtras(context, extras, trip_id);
        return true;
    }

    public boolean setSummaryElevationEdited(Context context, int trip_id, boolean isEdited) {
        String extras = "";
        try {
            JSONObject jsonObject = new JSONObject(getExtras(context, trip_id));
            jsonObject.put(SummaryProvider.ELEVATION_EDITED, isEdited);
            extras = jsonObject.toString();
        }catch (JSONException je) {
            Logger.e("JSON", "JSON formatting error in encoding Summary elevation edited extras "  + je.getMessage());
        }
        setSummaryExtras(context, extras, trip_id);
        return true;
    }


    // update the extras of a summary
    public boolean setSummaryExtras(Context context, String extras, int trip_id) {
        extras = UtilsMisc.escapeQuotes(extras);
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.EXTRAS + " = \"" + extras +
                "\" where " + SummaryProvider.TRIP_ID + " =  " + trip_id;
        return runSQL(execSQL);
    }

    // update the category of a summary
    public boolean setSummaryCategory(Context context, String category, int trip_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.CATEGORY + " = \"" + category +
                "\" where " + SummaryProvider.TRIP_ID + " =  " + trip_id;
        if (!runSQL(execSQL)) return false;
        setSummaryCategoryEdited(context, trip_id, true);
        return true;
    }

    // update the trip parameters of a summary
    public boolean setSummaryParameters(Context context, ContentValues values, int trip_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.START_TIME + " = " + values.getAsLong(SummaryProvider.START_TIME) + "," +
                SummaryProvider.NAME + " = \"" + values.getAsString(SummaryProvider.NAME) + "\"," +
                SummaryProvider.CATEGORY + " = \"" + values.getAsString(SummaryProvider.CATEGORY) + "\"," +
                SummaryProvider.END_TIME + " = " + values.getAsLong(SummaryProvider.END_TIME) + "," +
                SummaryProvider.DISTANCE + " = " + values.getAsFloat(SummaryProvider.DISTANCE) + "," +
                SummaryProvider.AVG_LAT + " = " + values.getAsInteger(SummaryProvider.AVG_LAT) + "," +
                SummaryProvider.AVG_LON + " = " + values.getAsInteger(SummaryProvider.AVG_LON) + "," +
                SummaryProvider.MIN_ELEVATION + " = " + values.getAsInteger(SummaryProvider.MIN_ELEVATION) + "," +
                SummaryProvider.MAX_ELEVATION + " = " + values.getAsInteger(SummaryProvider.MAX_ELEVATION) + "," +
                SummaryProvider.MIN_SPEED + " = " + values.getAsFloat(SummaryProvider.MIN_SPEED) + "," +
                SummaryProvider.MAX_SPEED + " = " + values.getAsFloat(SummaryProvider.MAX_SPEED) + "," +
                SummaryProvider.AVG_SPEED + " = " + values.getAsFloat(SummaryProvider.AVG_SPEED) + "," +
                SummaryProvider.MIN_ACCURACY + " = " + values.getAsFloat(SummaryProvider.MIN_ACCURACY) + "," +
                SummaryProvider.MAX_ACCURACY + " = " + values.getAsFloat(SummaryProvider.MAX_ACCURACY) + "," +
                SummaryProvider.AVG_ACCURACY + " = " + values.getAsFloat(SummaryProvider.AVG_ACCURACY) + "," +
                SummaryProvider.MIN_TIME_READINGS + " = " + values.getAsFloat(SummaryProvider.MIN_TIME_READINGS) + "," +
                SummaryProvider.MAX_TIME_READINGS + " = " + values.getAsFloat(SummaryProvider.MAX_TIME_READINGS) + "," +
                SummaryProvider.AVG_TIME_READINGS + " = " + values.getAsFloat(SummaryProvider.AVG_TIME_READINGS) + "," +
                SummaryProvider.MIN_DIST_READINGS + " = " + values.getAsFloat(SummaryProvider.MIN_DIST_READINGS) + "," +
                SummaryProvider.MAX_DIST_READINGS + " = " + values.getAsFloat(SummaryProvider.MAX_DIST_READINGS) + "," +
                SummaryProvider.AVG_DIST_READINGS + " = " + values.getAsFloat(SummaryProvider.AVG_DIST_READINGS) + "," +
                SummaryProvider.STATUS            + " = \"" + values.getAsString(SummaryProvider.STATUS)         + "\"," +
                SummaryProvider.EXTRAS            + " = \"" + UtilsMisc.escapeQuotes(values.getAsString(SummaryProvider.EXTRAS)) + "\"" +
                " where " + SummaryProvider.TRIP_ID + " =  " + trip_id;
        return runSQL(execSQL);
    }

    // get the distance of the summary
    public int getDistance(Context context , int trip_id) {
        int distance = -1;
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.TRIP_ID + " = " + trip_id;
        String sortOrder = "";
        Cursor c = context.getContentResolver().query(trips, null, whereClause,
                null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            distance = Math.round(c.getFloat(c.getColumnIndex(SummaryProvider.DISTANCE)));
            c.close();
        }
        return distance;
    }

    // get the start time of the summary
    public long getStartTime(Context context , int trip_id) {
        long startTime = 0;
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.TRIP_ID + " = " + trip_id;
        String sortOrder = "";
        Cursor c = context.getContentResolver().query(trips, null, whereClause,
                null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            startTime = c.getLong(c.getColumnIndex(SummaryProvider.START_TIME));
            c.close();
        }
        return startTime;
    }

    // get the end time of the summary
    public long getEndTime(Context context , int trip_id) {
        long endTime = 0;
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.TRIP_ID + " = " + trip_id;
        String sortOrder = "";
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            endTime = c.getLong(c.getColumnIndex(SummaryProvider.END_TIME));
            c.close();
        }
        return endTime;
    }


    // get the extras of the summary
    public String getExtras(Context context , int trip_id) {
        String extras = "";
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.TRIP_ID + " = " + trip_id;
        String sortOrder = "";
        Cursor c = context.getContentResolver().query(trips, null, whereClause,
                null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            extras = c.getString(c.getColumnIndex(SummaryProvider.EXTRAS));
            c.close();
        }
        return extras;
    }

    // get the distance of the segment of a trip
    public float getSegmentDistance(Context context, int trip_id, int segment) {
        if (segment == -1) return getSummary(context, trip_id).getDistance();
        float distance = 0.0f;
        try {
            JSONObject jsonObject = new JSONObject(getExtras(context, trip_id));
            distance = Float.parseFloat(jsonObject.getString("distance_" + segment));
        } catch (JSONException je) {
            Logger.d("JSON", "Could not parse segment distance from Summary extras" + je.getMessage());

        }
        return distance;
    }

    // get the time of the segment of a trip
    public long getSegmentTime(Context context, int trip_id, int segment) {
        if (segment == -1) return getSummary(context, trip_id).getEnd_time() - getSummary(context, trip_id).getStart_time();
        long time = 0L;
        try {
            JSONObject jsonObject = new JSONObject(getExtras(context, trip_id));
            time = Long.parseLong(jsonObject.getString("time_" + segment));
        } catch (JSONException je) {
            Logger.d("JSON", "Could not parse segment time from Summary extras" + je.getMessage());
        }
        return time;
    }

    // get the speed of the segment of a trip
    public float getSegmentSpeed(Context context, int trip_id, int segment) {
        if (segment == Constants.ALL_SEGMENTS || getSegmentCount(context, trip_id) == 1)
            return getSummary(context, trip_id).getAvg_speed();
        float speed = 0.0f;
        try {
            JSONObject jsonObject = new JSONObject(getExtras(context, trip_id));
            speed = Float.parseFloat(jsonObject.getString("speed_" + segment));
        } catch (JSONException je) {
            Logger.d("JSON", "Could not parse segment speed from Summary extras " + je.getMessage());
        }
        return speed;
    }

    // get the id of the most recent running summary
    public int getRunningSummaryID(Context context) {
        int id = -1;
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String sortOrder = SummaryProvider.TRIP_ID + " desc ";
        Cursor c = context.getContentResolver().query(trips, null, null, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                String status = c.getString(c.getColumnIndex(SummaryProvider.STATUS));
                if (status.equals(Constants.RUNNING))
                    id = c.getInt(c.getColumnIndex(SummaryProvider.TRIP_ID));
            } while (c.moveToNext());
            c.close();
        }
        return id;
    }

    // get closest summary based on the time stamp
    public int getClosestSummary(Context context, long timestamp) {
        int tripId = -1;
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause = "";
        String sortOrder = " abs(" + timestamp + " - " + SummaryProvider.START_TIME + ")";
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            tripId = c.getInt(c.getColumnIndex(SummaryProvider.TRIP_ID));
            c.close();
        }
        return tripId;
    }

    // has the trip been modified
    public boolean isEdited(Context context, int trip_id) {
        boolean edited = false;
        try {
            String extras = getExtras(context, trip_id);
            if (extras.length() > 0) {
                JSONObject jsonObject = new JSONObject(extras);
                edited = Boolean.valueOf(jsonObject.getString("edited"));
            }
        } catch (JSONException je) {
            Logger.d("JSON", "Could not parse edited from Summary extras " + je.getMessage());
        }
        return edited;
    }

    // is there a trip in progress
    public boolean isTripinProgress(Context context) {
        boolean progress = false;
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String sortOrder = SummaryProvider.TRIP_ID + " desc ";
        Cursor c = context.getContentResolver().query(trips, null, null, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                String status = c.getString(c.getColumnIndex(SummaryProvider.STATUS));
                if (status.equals(Constants.RUNNING))
                    progress = true;
            } while (c.moveToNext());
            c.close();
        }
        return progress;
    }

    // check if the trip was imported from an external source
    // extras field is blank for imported external files
    public boolean isImported(Context context, int trip_id) {
        return (UtilsJSON.isImported(getExtras(context, trip_id)) );
    }

    public boolean isCategoryEdited(Context context, int trip_id) {
        return (UtilsJSON.isCategoryEdited(getExtras(context, trip_id), SummaryProvider.CATEGORY_EDITED));
    }

    public boolean isLocationEdited(Context context, int trip_id) {
        return (UtilsJSON.isLocationEdited(getExtras(context, trip_id), SummaryProvider.LOCATION_EDITED));
    }

    public boolean isElevationEdited(Context context, int trip_id) {
        return (UtilsJSON.isElevationEdited(getExtras(context, trip_id), SummaryProvider.ELEVATION_EDITED));
    }

    // is trip finished
    public boolean isTripFinished(Context context, int trip_id) {
        boolean finished = false;
        Uri trips = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.TRIP_ID + " = " + trip_id;
        String sortOrder = "";
        Cursor c = context.getContentResolver().query(trips, null, whereClause,
                null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            String status = c.getString(c.getColumnIndex(SummaryProvider.STATUS));
            c.close();
            if (status.equals(Constants.FINISHED))
                finished = true;
        }
        return finished;
    }

    // get the number of detail records for the trip
    public int getDetailCount(Context context, int trip_id) {
        int readings = -1;
        Uri details = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause =  " " + DetailProvider.TRIP_ID + " = " + trip_id;
        String[] projection = {"count(*) as count"};
        Cursor c = context.getContentResolver().query(details, projection, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) {
            readings = c.getInt(0);
            c.close();
        }
        return readings;
    }

    // get the number of segments for the trip
    public int getSegmentCount(Context context, int trip_id) {
        int segments = Constants.ALL_SEGMENTS;
        Uri details = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause =  " " + DetailProvider.TRIP_ID + " = " + trip_id;
        String[] projection = {"MAX(" + DetailProvider.SEGMENT + ") as max_segment"};
        Cursor c = context.getContentResolver().query(details, projection, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) {
            segments = c.getInt(0);
            c.close();
        }
        return segments;
    }

    // delete a summary and associated details
    public int delSummary(Context context, int trip_id, boolean deleteDetails) {
        String URL = SummaryProvider.SUMMARY_ROW + "/" + trip_id;
        Uri summary = Uri.parse(URL);
        if (deleteDetails) {
            DetailDB detailDB = new DetailDB(DetailProvider.db);
            detailDB.deleteDetails(context, trip_id);
        }
        return (context.getContentResolver().delete(summary, null, null));
    }

    // combine multiple trips into a single trip
    public int combineSummaries(Context context, ArrayList<Integer> trip_ids) {
        // check if more than one trip has been selected
        if (trip_ids.size() < 1) return -1;
        //Collections.sort(trip_ids);

        // initialize the summary variables for the new combined trip
        int distance = 0;
        String status = "";
        String name = "";
        String category = "";
        String extras = "";
        float min_speed  = Constants.LARGE_FLOAT, max_speed = 0.0f;
        int min_elevation = Constants.LARGE_INT, max_elevation = 0;
        float min_accuracy  = Constants.LARGE_FLOAT, max_accuracy = 0.0f, cum_accuracy = 0.0f;
        float min_time_readings  = Constants.LARGE_FLOAT, max_time_readings = 0.0f, cum_time_readings = 0.0f;
        float min_dist_readings  = Constants.LARGE_FLOAT, max_dist_readings = 0.0f, cum_dist_readings = 0.0f;

        // collect information from the trips and build the summary
        ArrayList<Long> startTimes = new ArrayList<>();
        LongSparseArray<Integer> startTimeToTripId = new LongSparseArray<>();
        long duration = 0;
        for (Integer trip_id: trip_ids) {
            Summary summary = getSummary(context, trip_id);
            name += summary.getName() + " ";
            long startTime = summary.getStart_time();
            startTimes.add(startTime);   // save the start times for sorting
            // save a hash of the start time to trip id
            startTimeToTripId.put(startTime, trip_id);
            category = summary.getCategory();
            duration += (summary.getEnd_time() - startTime);

            distance += summary.getDistance();
            min_speed = (summary.getMin_speed() < min_speed)? summary.getMin_speed(): min_speed;
            max_speed = (summary.getMax_speed() > max_speed)? summary.getMax_speed(): max_speed;

            min_elevation = (summary.getMin_elevation() < min_elevation)? summary.getMin_elevation(): min_elevation;
            max_elevation = (summary.getMax_elevation() > max_elevation)? summary.getMax_elevation(): max_elevation;

            min_accuracy = (summary.getMin_accuracy() < min_accuracy)? summary.getMin_accuracy(): min_accuracy;
            max_accuracy = (summary.getMax_accuracy() > max_accuracy)? summary.getMax_accuracy(): max_accuracy;
            cum_accuracy += summary.getAvg_accuracy();

            min_time_readings = (summary.getMin_time_readings() < min_time_readings)? summary.getMin_time_readings(): min_time_readings;
            max_time_readings = (summary.getMax_time_readings() > max_time_readings)? summary.getMax_time_readings(): max_time_readings;
            cum_time_readings += summary.getAvg_time_readings();

            min_dist_readings = (summary.getMin_dist_readings() < min_dist_readings)? summary.getMin_dist_readings(): min_dist_readings;
            max_dist_readings = (summary.getMax_dist_readings() > max_dist_readings)? summary.getMax_dist_readings(): max_dist_readings;
            cum_dist_readings += summary.getAvg_dist_readings();
            status = summary.getStatus();
            extras = summary.getExtras();   // extras will be fixed in repair
        }

        // build the new summary data
        Collections.sort(startTimes);
        long startTime = startTimes.get(0);
        long local_startTime = 0;
        long endTime = startTime + duration;
        float avg_speed = 3.6f * distance / (duration / 1000.0f);   // speed in kmph
        float avg_accuracy = cum_accuracy / trip_ids.size();
        float avg_time_readings = cum_time_readings / trip_ids.size();
        float avg_dist_readings = cum_dist_readings / trip_ids.size();
        int avg_lat = 0; int avg_lon = 0;
        Summary newSummary = new Summary(-1, name, category, startTime,
                endTime,  distance,avg_lat, avg_lon, min_elevation, max_elevation, min_speed, max_speed, avg_speed,
                min_accuracy, max_accuracy, avg_accuracy,
                min_time_readings, max_time_readings, avg_time_readings,
                min_dist_readings, max_dist_readings, avg_dist_readings, status, extras);
        int newId = addSummary(context, newSummary);

        // update the details table with the new trip id
        int segment = 1;
        long firstDetailTime; long lastDetailTime = 0L; long timeCorrection = 0L;
        DetailDB detailDB = new DetailDB(DetailProvider.db);
        //for (Integer id: trip_ids) {
        for (long start: startTimes) {
            int id = startTimeToTripId.get(start);
            // get all the details for the trip id
            ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, id);
            firstDetailTime = details.get(0).getTimestamp();
            // the first detail time of the next trip minus the last detail of the previous trip
            // plus one second is the time correction for all details of the current trip
            if (lastDetailTime != 0L) timeCorrection = firstDetailTime - lastDetailTime + 1000L;

            // add each detail record with the new trip id and correct the detail time
            // such that all details are in a single time sequence
            for (DetailProvider.Detail detail: details) {
                detail.setTrip_id(newId);
                detail.setSegment(segment);
                long detailTime = detail.getTimestamp() - timeCorrection;
                detail.setTimestamp(detailTime);
                detailDB.addDetail(context, detail);
            }
            segment++;
            lastDetailTime = details.get(details.size()-1).getTimestamp();
        }

        // finally repair the trip to fix any remaining problems
        // Parms - null ProgressListener, trip id, not imported, calc. segments
        new RepairTrip(context, null, newId, false, true).run();

        return newId;
    }

    // run a sql statement
     boolean runSQL(String sql) {
        try {
            db.execSQL(sql);
        } catch (SQLException se) {
            return false;
        }
        return true;
    }

}