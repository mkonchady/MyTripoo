package org.mkonchady.mytripoo.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.utils.UtilsMisc;

import java.util.ArrayList;

/*
 A collection of utilities to manage the Detail table
 */

public class DetailDB {

    SQLiteDatabase db;

    // set the database handler
    public DetailDB(SQLiteDatabase db) {
        this.db = db;
    }

    // get a list of details for the entire trip
    public ArrayList<DetailProvider.Detail> getDetails(Context context, int trip_id) {
        return  getDetails(context, trip_id, Constants.ALL_SEGMENTS);
    }

    // get a list of details for the trip segment sorted in ascending order of timestamp
    public ArrayList<DetailProvider.Detail> getDetails(Context context, int trip_id, int segment) {
        ArrayList<DetailProvider.Detail> details = new ArrayList<>();
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = null;
        if (trip_id > 0) {
            whereClause = DetailProvider.TRIP_ID + " = " + trip_id;
            if (segment > 0)
                whereClause += " and " + DetailProvider.SEGMENT + " = " + segment;
        }

        // get the details in ascending order of timestamp
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, DetailProvider.TIMESTAMP);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                DetailProvider.Detail detail = createDetailRecord(c);
                details.add(detail);
            } while (c.moveToNext());
        }
        if (c != null) c.close();
        return details;
    }

    public boolean addDetail(Context context, DetailProvider.Detail detail) {
        ContentValues values = getContentValues(detail);
        Uri uri = context.getContentResolver().insert(Uri.parse(DetailProvider.DETAIL_ROW), values);
        return (uri != null);
    }


    public DetailProvider.Detail cloneDetail(DetailProvider.Detail detail) {
        ContentValues values = getContentValues(detail);
        return (new DetailProvider.Detail(
                (int) (values.get(DetailProvider.TRIP_ID)),
                (int) (values.get(DetailProvider.SEGMENT)),
                (int) values.get(DetailProvider.LAT),
                (int) values.get(DetailProvider.LON),
                (int) values.get(DetailProvider.BATTERY),
                (long) values.get(DetailProvider.TIMESTAMP),
                (float) values.get(DetailProvider.ALTITUDE),
                (float) values.get(DetailProvider.SPEED),
                (int) values.get(DetailProvider.BEARING),
                (int) values.get(DetailProvider.ACCURACY),
                (float) values.get(DetailProvider.GRADIENT),
                (float) values.get(DetailProvider.SATINFO),
                (int) values.get(DetailProvider.SATELLITES),
                (String) values.get(DetailProvider.INDEX),
                (String) values.get(DetailProvider.EXTRAS)
        ));
    }

    // build the content values for the Detail
    private ContentValues getContentValues(DetailProvider.Detail detail) {
        ContentValues values = new ContentValues();
        values.put(DetailProvider.TRIP_ID, detail.getTrip_id());
        values.put(DetailProvider.SEGMENT, detail.getSegment());
        values.put(DetailProvider.LAT, detail.getLat());
        values.put(DetailProvider.LON, detail.getLon());
        values.put(DetailProvider.BATTERY, detail.getBattery());
        values.put(DetailProvider.TIMESTAMP, detail.getTimestamp());
        values.put(DetailProvider.ALTITUDE, detail.getAltitude());
        values.put(DetailProvider.SPEED, detail.getSpeed());
        values.put(DetailProvider.BEARING, detail.getBearing());
        values.put(DetailProvider.ACCURACY, detail.getAccuracy());
        values.put(DetailProvider.GRADIENT, detail.getGradient());
        values.put(DetailProvider.SATINFO, detail.getSatinfo());
        values.put(DetailProvider.SATELLITES, detail.getSatellites());
        values.put(DetailProvider.INDEX, detail.getIndex());
        values.put(DetailProvider.EXTRAS, detail.getExtras());
        return values;
    }

    // update the altitude of the detail row
    public boolean setDetailAltitude(Context context, float altitude, int trip_id, int lat, int lon) {
        String execSQL = "update " + DetailProvider.DETAIL_TABLE + " set " +
                DetailProvider.ALTITUDE + " = " + altitude +
                " where " + DetailProvider.TRIP_ID + " =  " + trip_id + " and " +
                            DetailProvider.LAT + " = " + lat + " and " +
                            DetailProvider.LON + " = " + lon;
        return runSQL(execSQL);
    }

    // update the speed of the detail row
    public boolean setDetailSpeed(Context context, float speed, int trip_id, int lat, int lon) {
        String execSQL = "update " + DetailProvider.DETAIL_TABLE + " set " +
                DetailProvider.SPEED + " = " + speed +
                " where " + DetailProvider.TRIP_ID + " =  " + trip_id + " and " +
                DetailProvider.LAT + " = " + lat + " and " +
                DetailProvider.LON + " = " + lon;
        return runSQL(execSQL);
    }


    // update the timestamp of the detail row
    public boolean setDetailTimestamp(Context context, long timestamp, int trip_id, int index) {
        String execSQL = "update " + DetailProvider.DETAIL_TABLE + " set " +
                DetailProvider.TIMESTAMP + " = " + timestamp +
                " where " + DetailProvider.TRIP_ID + " =  " + trip_id + " and " +
                DetailProvider.INDEX + " = \"" + index + "\"";
        return runSQL(execSQL);
    }

    // update the extras of the detail row
    public boolean setDetailExtras(Context context, String extras, int trip_id, long timestamp) {
        extras = UtilsMisc.escapeQuotes(extras);
        String execSQL = "update " + DetailProvider.DETAIL_TABLE + " set " +
                DetailProvider.EXTRAS + " = \"" + extras + "\"" +
                " where " + DetailProvider.TRIP_ID + " =  " + trip_id + " and " +
                DetailProvider.TIMESTAMP + " = " + timestamp;
        return runSQL(execSQL);
    }

    public int[] getAverageDetailLocation(Context context, int trip_id) {
        int[] location = new int[2];
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String[] projection = {"avg(" + DetailProvider.LAT + ") as avg_lat, avg(" + DetailProvider.LON + ") as avg_lon"};
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id + " ";
        Cursor c = context.getContentResolver().query(trips, projection, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) {
            location[0] =  c.getInt(0);
            location[1] =  c.getInt(1);
        }
        if (c!= null) c.close();
        return location;
    }

    // get the locations of the north, south, east, and west boundaries for the map
    public int[] getBoundaries(Context context, int trip_id) {
        int[] boundaries = new int[4];
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String[] projection = {"max(" + DetailProvider.LAT + ") as max_lat, min(" + DetailProvider.LAT + ") as min_lat," +
                               "max(" + DetailProvider.LON + ") as max_lon, min(" + DetailProvider.LON + ") as min_lon"};
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id + " ";
        Cursor c = context.getContentResolver().query(trips, projection, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) {
            boundaries[0] = c.getInt(0);
            boundaries[1] = c.getInt(1);
            boundaries[2] = c.getInt(2);
            boundaries[3] = c.getInt(3);
        }
        if (c!= null) c.close();
        return boundaries;
    }

    public ArrayList<DetailProvider.Detail> getClosestDetails(Context context, int trip_id, int lat, int lon,
                                                              int segmentNumber, int numDetails, boolean not_identical) {
        ArrayList<DetailProvider.Detail> closeDetails = new ArrayList<>();
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id;
        if (segmentNumber != Constants.ALL_SEGMENTS) whereClause += " and " + DetailProvider.SEGMENT + " = " + segmentNumber;
        if (not_identical)  whereClause += " and " + DetailProvider.LAT + " != " + lat +
                                           " and " + DetailProvider.LON + " != " + lon;
        String sortOrder = " abs(" + lat + " - " + DetailProvider.LAT + ") + " +
                           " abs(" + lon + " - " + DetailProvider.LON + ")";

        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, sortOrder);
        int numResults = 0;
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                DetailProvider.Detail detail = createDetailRecord(c);
                closeDetails.add(detail);
            } while (c.moveToNext() && (++numResults < numDetails));
        }
        if (c!= null) c.close();
        return closeDetails;
    }

    // get the immediate neighbour details
    public DetailProvider.Detail[] getDetailNeighbours(Context context, int trip_id, String detailIndex) {
       return getDetailNeighbours(context, trip_id, detailIndex, 1);
    }

    // get a detail INC before and after the given detail index
    public DetailProvider.Detail[] getDetailNeighbours(Context context, int trip_id, String detailIndex, int INC) {
        DetailProvider.Detail[] closeDetails = new DetailProvider.Detail[2];
        int index = Integer.valueOf(detailIndex);
        DetailProvider.Detail detail = getDetail(context, trip_id, index - INC);
        if (detail != null) closeDetails[0] = detail;
        detail = getDetail(context, trip_id, index + INC);
        if (detail != null) closeDetails[1] = detail;
        return closeDetails;
    }

    // get the closest lat and lon based on lat and lon
    public int[] getClosestDetailByLocation(Context context, int trip_id, int lat, int lon, int segmentNumber, int numDetails) {
        int[] location = new int[2 * numDetails];
        for (int i = 0; i < 2 * numDetails; i++) location[i] = -100000;
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id;
        if (segmentNumber != Constants.ALL_SEGMENTS)
            whereClause += " and " + DetailProvider.SEGMENT + " = " + segmentNumber;
        String sortOrder = " abs(" + lat + " - " + DetailProvider.LAT + ") + " +
                           " abs(" + lon + " - " + DetailProvider.LON + ")";

        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, sortOrder);
        int numResults = 0;
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                location[numResults * 2] = c.getInt(c.getColumnIndex(DetailProvider.LAT));
                location[numResults * 2 + 1] = c.getInt(c.getColumnIndex(DetailProvider.LON));
            } while (c.moveToNext() && (++numResults < numDetails));
        }
        if (c!= null) c.close();
        return location;
    }


    // get closest detail based on the time stamp
    public DetailProvider.Detail getClosestDetailByTime(Context context, int trip_id, long timestamp) {
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id;
        String sortOrder = " abs(" + timestamp + " - " + DetailProvider.TIMESTAMP + ")";
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            DetailProvider.Detail detail = createDetailRecord(c);
            c.close();
            return detail;
        }
        return null;
    }

    // get a detail record by lat and lon
    public DetailProvider.Detail getDetail(Context context, int trip_id, int lat, int lon, int segmentNumber) {
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id + " and " +
                DetailProvider.LAT + " = " + lat + " and " +
                DetailProvider.LON + " = " + lon;
        if (segmentNumber != Constants.ALL_SEGMENTS)
            whereClause += " and " + DetailProvider.SEGMENT + " = " + segmentNumber;

        DetailProvider.Detail detail = null;
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) detail = createDetailRecord(c);
        if (c!= null) c.close();
        return detail;
    }

    // get a detail record by index
    public DetailProvider.Detail getDetail(Context context, int trip_id, int detailIndex) {
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id + " and " +
                DetailProvider.INDEX + " = \"" + detailIndex + "\"";
        DetailProvider.Detail detail = null;
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) detail = createDetailRecord(c);
        if (c!= null) c.close();
        return detail;
    }

    // get a detail record by timestamp
    public DetailProvider.Detail getDetail(Context context, long timestamp, int trip_id) {
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TIMESTAMP + " = " + timestamp + " and " +
                             " " + DetailProvider.TRIP_ID   + " = " +  trip_id;
        DetailProvider.Detail detail = null;
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) detail = createDetailRecord(c);
        if (c!= null) c.close();
        return detail;
    }

    public DetailProvider.Detail getFirstDetail(Context context, int trip_id, int segmentNumber) {
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id;
        if (segmentNumber != Constants.ALL_SEGMENTS)
            whereClause += " and " + DetailProvider.SEGMENT + " = " + segmentNumber;
        String sortOrder = DetailProvider.TIMESTAMP + " asc ";
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, sortOrder);
        DetailProvider.Detail detail = null;
        if ((c != null) && (c.moveToFirst()))
            detail = createDetailRecord(c);
        if (c != null) c.close();
        return detail;
    }


    public DetailProvider.Detail getLastDetail(Context context, int trip_id, int segmentNumber) {
        Uri trips = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TRIP_ID + " = " + trip_id;
        if (segmentNumber != Constants.ALL_SEGMENTS)
            whereClause += " and " + DetailProvider.SEGMENT + " = " + segmentNumber;
        String sortOrder = DetailProvider.TIMESTAMP + " desc ";
        Cursor c = context.getContentResolver().query(trips, null, whereClause, null, sortOrder);
        DetailProvider.Detail detail = null;
        if ((c != null) && (c.moveToFirst()))
            detail = createDetailRecord(c);
        if (c != null) c.close();
        return detail;
    }

    // create the detail record from the cursor
    private DetailProvider.Detail createDetailRecord(Cursor c) {
        return (new DetailProvider.Detail(
                c.getInt(c.getColumnIndex(DetailProvider.TRIP_ID)),
                c.getInt(c.getColumnIndex(DetailProvider.SEGMENT)),
                c.getInt(c.getColumnIndex(DetailProvider.LAT)),
                c.getInt(c.getColumnIndex(DetailProvider.LON)),
                c.getInt(c.getColumnIndex(DetailProvider.BATTERY)),
                c.getLong(c.getColumnIndex(DetailProvider.TIMESTAMP)),
                c.getFloat(c.getColumnIndex(DetailProvider.ALTITUDE)),
                c.getFloat(c.getColumnIndex(DetailProvider.SPEED)),
                c.getInt(c.getColumnIndex(DetailProvider.BEARING)),
                c.getInt(c.getColumnIndex(DetailProvider.ACCURACY)),
                c.getFloat(c.getColumnIndex(DetailProvider.GRADIENT)),
                c.getFloat(c.getColumnIndex(DetailProvider.SATINFO)),
                c.getInt(c.getColumnIndex(DetailProvider.SATELLITES)),
                c.getString(c.getColumnIndex(DetailProvider.INDEX)),
                c.getString(c.getColumnIndex(DetailProvider.EXTRAS))
        ));
    }

    // delete a single detail row based on the timestamp
    public boolean deleteDetail(int trip_id, long timestamp) {
        String execSQL = "delete from " + DetailProvider.DETAIL_TABLE +
                " where " + DetailProvider.TIMESTAMP + " =  " + timestamp + " and " +
                DetailProvider.TRIP_ID + " = " + trip_id;
        return runSQL(execSQL);
    }

    // delete all details for a summary
    public int deleteDetails(Context context, int trip_id) {
        String URL = DetailProvider.DETAIL_ROW + "/" + trip_id;
        Uri detail = Uri.parse(URL);
        return (context.getContentResolver().delete(detail, null, null));
    }

    // run a sql statement
    public boolean runSQL(String sql) {
        try {
            db.execSQL(sql);
        } catch (SQLException se) {
            return false;
        }
        return true;
    }
}