package org.mkonchady.mytripoo.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
//import android.util.Log;
import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.ProgressListener;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.TimezoneMapper;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsFile;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

// class to import files in different formats
public class ImportFile {

    private final Context context;
    private DetailDB detailTable = null;
    private SummaryDB summaryTable = null;
    private String TRIP_FILE = "";
    private ProgressListener progressListener = null;
    private final long MIN_TIME_PER_READING;            // min. time between readings
    private final int MIN_DIST_PER_READING;             // min. distance between readings

    private final String TAG = "ImportFile";
    SharedPreferences sharedPreferences;
    int localLog = 0;

    public ImportFile(Context context, ProgressListener progressListener, String filename) {
        this.context = context;
        this.progressListener = progressListener;
        detailTable = new DetailDB(DetailProvider.db);
        summaryTable = new SummaryDB(SummaryProvider.db);
        TRIP_FILE = filename;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        MIN_TIME_PER_READING = UtilsMisc.getMinTimePerReading(sharedPreferences);
        MIN_DIST_PER_READING = UtilsMisc.getMinDistPerReading(sharedPreferences);
    }

    // import summaries in CSV format
    public int importCSV() {
        String msg;
        Logger.d(TAG, "Started importing CSV file ...");
        int trip_id = 0;
        try {
            int file_size = countLines();
            int num_lines = 0;
            int num_sub_lines = file_size / 4;
            String line;
            BufferedReader br = new BufferedReader(new FileReader(new File(TRIP_FILE)));
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",", SummaryProvider.NUM_FIELDS);
                if (fields.length == SummaryProvider.NUM_FIELDS) {
                    SummaryProvider.Summary summary = SummaryProvider.createSummary(fields);
                    trip_id = summaryTable.addSummary(context, summary);
                } else if ( (fields.length == DetailProvider.NUM_FIELDS) && (trip_id > 0) ) {
                    DetailProvider.Detail detail = DetailProvider.createDetail(trip_id, fields);
                    detailTable.addDetail(context, detail);
                }
                if (progressListener != null && ++num_lines % num_sub_lines == 0)
                    progressListener.reportProgress( Math.round(100.0f * (num_lines + 1.0f) / file_size) );
            }
            //br.close();
        } catch (IOException ie) {
            msg = "Could not read file: " + TRIP_FILE + " " + ie.getMessage();
            Logger.e(TAG, msg, localLog);
        } catch (IndexOutOfBoundsException ee) {
            msg = "No. of fields mismatch " + TRIP_FILE + " " + ee.getMessage();
            Logger.e(TAG, msg, localLog);
        }
        return trip_id;
    }

    public int importGPX() {

        final double DEFAULT_SPEED = 20.0;
        Logger.d(TAG, "Started importing GPX file ...");

        // create a new summary for the trip with default values and generate a trip id
        SummaryProvider.Summary summary = SummaryProvider.createSummary(0L);
        int trip_id = summaryTable.addSummary(context, summary);

        try {
            // open and count the number of lines in the file
            int file_size = countLines();
            int num_lines = 0;
            int num_sub_lines = (file_size >= 16)? file_size / 16: 1;
            final int INVALID_LAT = -400;
            final long MIN_TIME_SEPARATION_PER_LOCATION = 60000; // min. 1 minute separation between the same location

            // use the parser to read the file
            XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser parser = xmlFactoryObject.newPullParser();

            InputStream fos = (TRIP_FILE.equals(Constants.SAMPLE_GPX_FILE))?
                    context.getResources().openRawResource(R.raw.sample):
                    new FileInputStream(new File(TRIP_FILE));
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(fos, null);

            boolean isExternalFile = true;
            int event = parser.getEventType();
            String text = "";
            int segment = 1;
            HashMap<String, Long> seenLocations = new HashMap<>();
            ArrayList<String> waypoints = new ArrayList<>();

            // format for lat and lon values
            DecimalFormat df = new DecimalFormat("##.######");
            df.setRoundingMode(RoundingMode.FLOOR);
            int latValue = 0; int prev_latValue = INVALID_LAT;
            int lonValue = 0;  int prev_lonValue = 0;

            boolean firstTime = true;

            // default detail parameters
            long detail_time = System.currentTimeMillis();
            long prev_detail_time = detail_time;
            float altitude = 0;
            float speed = 0.0f;
            int bearing = 0;
            int accuracy = 0;
            float gradient = 0.0f;
            float satinfo = 0.0f;
            int satellites = 0;
            int battery = 100;
            String index = "";
            String d_extras = "";
            String timez = "";

            // parse the document
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();

                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals("trkpt")) { // save the track lat and lon in micro-degrees
                            String latString = parser.getAttributeValue(null, "lat");
                            double latDouble = Double.parseDouble(latString);
                            latValue = (int) Math.round(Double.valueOf(df.format(latDouble * Constants.MILLION)));
                            String lonString = parser.getAttributeValue(null, "lon");
                            double lonDouble = Double.parseDouble(lonString);
                            lonValue = (int) Math.round(Double.valueOf(df.format(lonDouble * Constants.MILLION)));
                            timez = TimezoneMapper.latLngToTimezoneString(latDouble, lonDouble);
                        }
                        if (name.equals("wpt")) {  // save the waypoint lat and lon in micro-degrees
                            String latString = parser.getAttributeValue(null, "lat");
                            double latDouble = Double.parseDouble(latString);
                            int waylatValue = (int) Math.round(Double.valueOf(df.format(latDouble * Constants.MILLION)));
                            String lonString = parser.getAttributeValue(null, "lon");
                            double lonDouble = Double.parseDouble(lonString);
                            int waylonValue = (int) Math.round(Double.valueOf(df.format(lonDouble * Constants.MILLION)));
                            waypoints.add(waylatValue + UtilsMap.DELIMITER + waylonValue);
                        }
                        break;

                    case XmlPullParser.TEXT:
                        text = parser.getText().trim();
                        break;

                    case XmlPullParser.END_TAG:
                        switch (name) {

                            // ------ Summary fields -------------
                            case SummaryProvider.NAME:
                                summary.setName(text);break;
                            //case SummaryProvider.PREF_TRIP_ID:
                            //    summary.setTrip_id(Integer.parseInt(text));break;
                            case SummaryProvider.CATEGORY:
                                summary.setCategory(text);break;
                            case SummaryProvider.START_TIME:
                                summary.setStart_time(Long.parseLong(text));break;
                            case SummaryProvider.END_TIME:
                                summary.setEnd_time(Long.parseLong(text));break;
                            case SummaryProvider.DISTANCE:
                                summary.setDistance(Float.parseFloat(text));break;
                            case SummaryProvider.AVG_LAT:
                                summary.setAvg_lat(Integer.parseInt(text));break;
                            case SummaryProvider.AVG_LON:
                                summary.setAvg_lon(Integer.parseInt(text));break;
                            case SummaryProvider.MAX_ELEVATION:
                                summary.setMax_elevation(Integer.parseInt(text));break;
                            case SummaryProvider.MIN_ELEVATION:
                                summary.setMin_elevation(Integer.parseInt(text));break;
                            case SummaryProvider.MIN_SPEED:
                                summary.setMin_speed(Float.parseFloat(text));break;
                            case SummaryProvider.MAX_SPEED:
                                summary.setMax_speed(Float.parseFloat(text));break;
                            case SummaryProvider.AVG_SPEED:
                                summary.setAvg_speed(Float.parseFloat(text));break;
                            case SummaryProvider.MIN_ACCURACY:
                                summary.setMin_accuracy(Float.parseFloat(text));break;
                            case SummaryProvider.MAX_ACCURACY:
                                summary.setMax_accuracy(Float.parseFloat(text));break;
                            case SummaryProvider.AVG_ACCURACY:
                                summary.setAvg_accuracy(Float.parseFloat(text));break;
                            case SummaryProvider.MAX_TIME_READINGS:
                                summary.setMax_time_readings(Float.parseFloat(text));break;
                            case SummaryProvider.MIN_TIME_READINGS:
                                summary.setMin_time_readings(Float.parseFloat(text));break;
                            case SummaryProvider.AVG_TIME_READINGS:
                                summary.setAvg_time_readings(Float.parseFloat(text));break;
                            case SummaryProvider.MAX_DIST_READINGS:
                                summary.setMax_dist_readings(Float.parseFloat(text));break;
                            case SummaryProvider.MIN_DIST_READINGS:
                                summary.setMin_dist_readings(Float.parseFloat(text));break;
                            case SummaryProvider.AVG_DIST_READINGS:
                                summary.setAvg_dist_readings(Float.parseFloat(text));break;
                            case SummaryProvider.STATUS:
                                summary.setStatus(text); break;
                            case SummaryProvider.EXTRAS:
                                summary.setExtras(text);
                                if (!UtilsJSON.isImported(text))
                                    isExternalFile = false;
                                break;

                            // ----------- Detail fields ---------------
                            case DetailProvider.TRACK_SEGMENT:          // ignore external track segments
                                if (!isExternalFile) segment++;break;
                            case DetailProvider.LAT:
                                latValue = Integer.parseInt(text); break;
                            case DetailProvider.LON:
                                lonValue = Integer.parseInt(text); break;
                            case DetailProvider.ALTITUDE:
                            case DetailProvider.STRAVA_ELEVATION:
                                altitude = Float.parseFloat(text);break;
                            case DetailProvider.TIMESTAMP:
                            case DetailProvider.STRAVA_TIME:
                                detail_time = (text.endsWith("Z"))? UtilsDate.getZuluTimeStamp(text): UtilsDate.getDetailTimeStamp(text); break;
                            case DetailProvider.SPEED:
                                speed = Float.parseFloat(text);break;
                            case DetailProvider.BATTERY:
                                battery = Integer.parseInt(text);break;
                            case DetailProvider.BEARING:
                                bearing = Integer.parseInt(text);break;
                            case DetailProvider.ACCURACY:
                                accuracy = Integer.parseInt(text);break;
                            case DetailProvider.GRADIENT:
                                gradient = Float.parseFloat(text);break;
                            case DetailProvider.SATINFO:
                                satinfo = Float.parseFloat(text);break;
                            case DetailProvider.SATELLITES:
                                satellites = Integer.parseInt(text);break;
                            case DetailProvider.INDEX:
                                index = text; break;
                            case DetailProvider.EXTRAS:
                                d_extras = text; break;
                            case DetailProvider.TRACK_POINT:


                                // find the time and distance gap between readings
                                double distanceGap = UtilsMap.getMeterDistance(prev_latValue, prev_lonValue, latValue, lonValue);
                                // for an external file use a fixed 20 kmph speed to calculate the next detail time,
                                // otherwise calculate the time between readings in microseconds
                                // for the first reading the timegap is zero
                                long timeGap = (isExternalFile && (prev_detail_time == detail_time) && !firstTime)?
                                                Math.round(distanceGap * 3600.0 / DEFAULT_SPEED): detail_time - prev_detail_time;

                                // add the detail record to the DB, if there is sufficient gap in time and space between readings
                                // or an external file is being read
                                if ( firstTime || isExternalFile || (timeGap > MIN_TIME_PER_READING && distanceGap > MIN_DIST_PER_READING) ) {
                                    // first remove dup locations that occur too soon ...
                                    String key = latValue + Constants.DELIMITER + lonValue;
                                    if (seenLocations.containsKey(key)) {
                                        long periodBetweenLocations = prev_detail_time - seenLocations.get(key);
                                        if (periodBetweenLocations < MIN_TIME_SEPARATION_PER_LOCATION) {
                                            Logger.d(TAG, "Duplicate locations -- Lat: " + latValue + " Lon: " + lonValue, localLog);
                                            break;
                                        }
                                    }
                                    seenLocations.put(key, prev_detail_time);
                                    // create a new detail
                                    detail_time = prev_detail_time + timeGap;
                                    DetailProvider.Detail detail = new DetailProvider.Detail(trip_id, segment,
                                            latValue, lonValue, battery, detail_time, altitude, speed, bearing,
                                            accuracy, gradient, satinfo, satellites, index, d_extras);
                                    detailTable.addDetail(context, detail);
                                    prev_latValue = latValue;
                                    prev_lonValue = lonValue;
                                    prev_detail_time = detail_time;
                                    firstTime = false;
                                } else {
                                    Logger.d(TAG, "Timegap: " + timeGap + " Distance gap: " + distanceGap, localLog);
                                }
                                break;
                        }
                        break;
                }
                event = parser.next();
                if (progressListener != null && (++num_lines % num_sub_lines == 0) && (num_lines < file_size) )
                    progressListener.reportProgress( Math.round(100.0f * (num_lines + 1.0f) / file_size) );
            }
            // add any waypoints
            if (waypoints.size() > 0) {
                Set<String> fixedWaypoints = new HashSet<>();
                for (String waypoint: waypoints) {
                    String[] parts = waypoint.split(UtilsMap.DELIMITER);
                    int[] location = detailTable.getClosestDetailByLocation(context, trip_id,
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Constants.ALL_SEGMENTS, 1);
                    fixedWaypoints.add(location[0] + UtilsMap.DELIMITER + location[1]);
                }
                String summary_extras = UtilsJSON.addWayPoints(fixedWaypoints, summary.getExtras());
                summary.setExtras(summary_extras);
            }

            summaryTable.setSummaryParameters(context, summaryTable.putSummaryValues(summary), trip_id);
            summaryTable.setSummaryStatus(context, Constants.FINISHED, trip_id);
            Logger.d(TAG, "Finished importing file with " + summaryTable.getDetailCount(context, trip_id) + " details", localLog);
            fos.close();
        } catch (XmlPullParserException xe) {
            Logger.e(TAG, "GPX Parse error: " + xe.getMessage(), localLog);
        } catch (IOException ie) {
            Logger.e(TAG, "GPX IO Error: " + ie.getMessage(), localLog);
        } catch (NumberFormatException ne) {
            Logger.e(TAG, "Number format error: " + ne.getMessage(), localLog);
        }
        return trip_id;
    }

    // restore a bunch of trips from a zip file
    public int[] importZIP() {
        ArrayList<String> files = UtilsFile.unzip(context, TRIP_FILE);
        Collections.sort(files);
        int[] tripIds = new int[files.size()];
        int tripId = 0;
        int i = 0;
        for (String file: files) {
            Logger.d(TAG, "Importing file: " + file);
            TRIP_FILE = UtilsFile.getFileName(context, file);
            String suffix = UtilsFile.getFileSuffix(file);
            switch (suffix) {
                case "csv": tripId = importCSV(); break;
                case "gpx": tripId = importGPX(); break;
            }
            tripIds[i++] = tripId;
        }
        return tripIds;
    }

    /*
      Import a runXML file from the Wahoo Fitness sensor app
      1. Find the trip id with the closest timestamp
      2. Build an array with the speeds in 10 second intervals
     */
    public int importXML() {
        Logger.d(TAG, "Started importing XML file ...");
        int tripId = 0;
        try {
            // open and count the number of lines in the file
            int file_size = countLines();
            int num_lines = 0;
            int num_sub_lines = (file_size >= 16)? file_size / 16: 1;

            // use the parser to read the file
            XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser parser = xmlFactoryObject.newPullParser();

            InputStream fos = new FileInputStream(new File(TRIP_FILE));
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(fos, null);

            long startTime = 0;
            boolean isSpeedTag = false;
            int event = parser.getEventType();
            String text = "";

            // parse the document
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();

                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals("extendedData")) { // save the track lat and lon in micro-degrees
                            String typeString = parser.getAttributeValue(null, "dataType");
                            if (typeString.equals("speed"))
                                isSpeedTag = true;
                        }
                        break;

                    case XmlPullParser.TEXT:
                        text = parser.getText().trim();
                        break;

                    case XmlPullParser.END_TAG:
                        switch (name) {

                            // ------ Summary fields -------------
                            case "startTime":
                                startTime = UtilsDate.getWahooTimeStamp(text);
                                tripId = summaryTable.getClosestSummary(context, startTime); // find trip with the closest start time
                                break;
                            case "extendedData":
                                if (isSpeedTag) {
                                    SummaryProvider.Summary summary = summaryTable.getSummary(context, tripId);
                                    String extras = UtilsJSON.buildWahooExtras(summary, startTime + "," + text);
                                    summaryTable.setSummaryExtras(context, extras, tripId);
                                    isSpeedTag = false;
                                }
                            default:
                                break;
                        }
                        break;
                }
                event = parser.next();
                if (progressListener != null && (++num_lines % num_sub_lines == 0) && (num_lines < file_size) )
                    progressListener.reportProgress( Math.round(100.0f * (num_lines + 1.0f) / file_size) );
            }
            fos.close();
        } catch (XmlPullParserException xe) {
            Logger.e(TAG, "GPX Parse error: " + xe.getMessage(), localLog);
        } catch (IOException ie) {
            Logger.e(TAG, "GPX IO Error: " + ie.getMessage(), localLog);
        }

        return tripId;
    }


    // import API Keys from a JSON File
    public int importKeys() {
        String msg;
        Logger.d(TAG, "Started importing API Keys file ...");
        int trip_id = 0;
        try {
            String line;
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(new File(TRIP_FILE)));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            String json_str = sb.toString();
            if (!UtilsJSON.isJSONValid(json_str)) return -1;
            String google_api_key = UtilsJSON.get_JSON_value(json_str, Constants.PREF_GOOGLE_API_KEY);
            String openweather_api_key = UtilsJSON.get_JSON_value(json_str, Constants.PREF_OPENWEATHER_API_KEY);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Constants.PREF_GOOGLE_API_KEY, google_api_key);
            editor.putString(Constants.PREF_OPENWEATHER_API_KEY, openweather_api_key);
            editor.apply();
            progressListener.reportProgress(100);
        } catch (IOException ie) {
            msg = "Could not read file: " + TRIP_FILE + " " + ie.getMessage();
            Logger.e(TAG, msg, localLog);
        } catch (IndexOutOfBoundsException ee) {
            msg = "No. of fields mismatch " + TRIP_FILE + " " + ee.getMessage();
            Logger.e(TAG, msg, localLog);
        }
        return 0;
    }

    private int countLines() throws  IOException {
        BufferedReader br = (TRIP_FILE.equals(Constants.SAMPLE_GPX_FILE))?
                new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.sample))):
                new BufferedReader(new FileReader(new File(TRIP_FILE)));
        int lines = 0;
        while (br.readLine() != null) lines++;
        br.close();
        return lines;
    }
}