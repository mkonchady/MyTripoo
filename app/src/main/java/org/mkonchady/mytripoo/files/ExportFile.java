package org.mkonchady.mytripoo.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.ProgressListener;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsFile;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMap;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;

// class to dump files in different formats
public class ExportFile {

    private final Context context;
    private SummaryProvider.Summary[] summaries;
    private DetailDB detailsTable = null;
    private ProgressListener progressListener = null;
    private final String newline = Constants.NEWLINE;
    int localLog;
    private final SharedPreferences sharedPreferences;


    public ExportFile(Context context) {
        this.context = context;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
    }

    public ExportFile(Context context, ArrayList<SummaryProvider.Summary> summaryArrayList, ProgressListener progressListener) {
        this.context = context;
        summaries = new SummaryProvider.Summary[summaryArrayList.size()];
        this.progressListener = progressListener;
        summaryArrayList.toArray(summaries);
        detailsTable = new DetailDB(DetailProvider.db);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
    }

    // export summaries in CSV format
    public String exportCSV() {
        String msg = "";
        String TRIP_FILE;
        FileOutputStream fos1;
        ArrayList<String> outfiles = new ArrayList<>();
        int numSummaries = 0;
        try {
            for (SummaryProvider.Summary summary: summaries) {
                String dateTime = UtilsDate.getDateTimeSec(summary.getStart_time(), summary.getAvg_lat(), summary.getAvg_lon());
                dateTime = dateTime.replace(',','_').replace(' ', '_');
                TRIP_FILE = "trip_" + summary.getTrip_id() + "_" + dateTime + ".csv";
                fos1 = UtilsFile.openOutputFile(context, TRIP_FILE);
                String sb1 = summary.toString("csv") + newline;
                fos1.write(sb1.getBytes());
                ArrayList<DetailProvider.Detail> summaryDetails =
                        detailsTable.getDetails(context, summary.getTrip_id());
                for (DetailProvider.Detail detail: summaryDetails) {
                    String sb2 = detail.toString("csv") + newline;
                    fos1.write(sb2.getBytes());
                }
                fos1.flush(); fos1.close();
                UtilsFile.forceIndex(context, TRIP_FILE);
                outfiles.add(UtilsFile.getFileName(context, TRIP_FILE));
                //msg = "Finished export... " + TRIP_FILE;
                progressListener.reportProgress(++numSummaries);
            }

            if (outfiles.size() > 0)
                UtilsFile.zip(context, outfiles.toArray(new String[outfiles.size()]), "backup.zip");

        } catch (IOException ie) {
            msg = "Could not write CSV file " + ie.getMessage();
            Logger.e("TAG", msg, localLog);
        }
        return msg;
    }

    // export summaries in GPX format
    public String exportGPX() {
        String msg = "";
        String TRIP_FILE;
        FileOutputStream fos1;
        ArrayList<String> outfiles = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("##.######");
        df.setRoundingMode(RoundingMode.FLOOR);
        int numSummaries = 0;
        try {
            for (SummaryProvider.Summary summary: summaries) {
                String dateTime = UtilsDate.getDateTimeSec(summary.getStart_time(), summary.getAvg_lat(), summary.getAvg_lon());
                dateTime = dateTime.replace(',','_').replace(' ', '_');
                TRIP_FILE = "trip_" + summary.getTrip_id() + "_" + dateTime + ".gpx";
                fos1 = UtilsFile.openOutputFile(context, TRIP_FILE);
                String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?> "      + newline +
                        "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"MyTripoo\" "  + newline +
                        "version=\"0.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "    + newline +
                        "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 "                     + newline +
                        "http://www.topografix.com/GPX/1/1/gpx.xsd\">" + newline;
                fos1.write(header.getBytes());
                String metadata = "<metadata>" + newline  + summary.toString("xml");
                fos1.write(metadata.getBytes());

                // get any waypoints
                ArrayList<String> waypoints = UtilsJSON.getWayPoints(summary.getExtras());
                StringBuilder wpt = new StringBuilder();
                for (String waypoint: waypoints) {
                    String[] parts = waypoint.split(UtilsMap.DELIMITER);
                    wpt.append("   <wpt lat=\"" + df.format(Integer.parseInt(parts[0]) / Constants.MILLION) +
                                "\" lon=\"" + df.format(Integer.parseInt(parts[1]) / Constants.MILLION) + "\"/>" + newline);
                }
                wpt.append("</metadata>" + newline);
                fos1.write(wpt.toString().getBytes());

                // dump the title
                String name = (summary.getName().length() == 0) ?
                    " <trk>" + newline + "  <name>Trip " + summary.getTrip_id() + "</name>" + newline:
                    " <trk>" + newline + "  <name>" + summary.getName().trim() +    "</name>" + newline;
                fos1.write(name.getBytes());
                StringBuilder segments = new StringBuilder();
                int old_segment = -1;
                ArrayList<DetailProvider.Detail> summaryDetails = detailsTable.getDetails(context, summary.getTrip_id());
                for (DetailProvider.Detail detail: summaryDetails) {
                   int new_segment = detail.getSegment();
                   if (old_segment != new_segment) {
                       if (old_segment != -1) segments.append( "   </trkseg>" + newline);
                       segments.append( "  <trkseg>" + newline);
                       old_segment = new_segment;
                   }

                   segments.append("   <trkpt lat=\"" + df.format(detail.getLat() / Constants.MILLION) +
                                      "\" lon=\"" + df.format(detail.getLon() / Constants.MILLION) + "\">" + newline +
                           detail.toString("xml") +
                     "   </trkpt>" + newline);
                }
                fos1.write(segments.toString().getBytes());
                String footer = "  </trkseg>" + newline + " </trk>" + newline;
                fos1.write(footer.getBytes());
                String endLine = newline + "</gpx>";
                fos1.write(endLine.getBytes());
                fos1.flush(); fos1.close();
                UtilsFile.forceIndex(context, TRIP_FILE);
                outfiles.add(UtilsFile.getFileName(context, TRIP_FILE));

                //msg = "Finished export... " + TRIP_FILE;
                progressListener.reportProgress(++numSummaries);
            }

            // make a zip file, if necessary
            if (outfiles.size() > 0)
                UtilsFile.zip(context, outfiles.toArray(new String[outfiles.size()]), "backup.zip");

        } catch (IOException ie) {
            msg = "Could not write GPX file " + ie.getMessage();
            Logger.e("TAG", msg, localLog);
        }
        return msg;
    }


    // export summaries in Strava GPX format
    public String exportStrava() {
        String msg = "";
        String TRIP_FILE;
        FileOutputStream fos1;
        ArrayList<String> outfiles = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("##.######");
        df.setRoundingMode(RoundingMode.FLOOR);
        int numSummaries = 0;
        try {
            for (SummaryProvider.Summary summary: summaries) {
                String dateTime = UtilsDate.getDateTimeSec(summary.getStart_time(), summary.getAvg_lat(), summary.getAvg_lon());
                dateTime = dateTime.replace(',','_').replace(' ', '_');
                TRIP_FILE = "trip_" + summary.getTrip_id() + "_" + dateTime + ".gpx";
                fos1 = UtilsFile.openOutputFile(context, TRIP_FILE);
                String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?> "      + newline +
                        "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"MyTripoo\" "  + newline +
                        "version=\"0.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "    + newline +
                        "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 "                     + newline +
                        "http://www.topografix.com/GPX/1/1/gpx.xsd\">" + newline;
                fos1.write(header.getBytes());
                String metadata = "<metadata>" + newline  + summary.toString("strava") + newline +
                                  "</metadata>" + newline;
                fos1.write(metadata.getBytes());

                // dump the title
                String name = (summary.getName().length() == 0) ?
                        " <trk>" + newline + "  <name>Trip " + summary.getTrip_id() + "</name>" + newline:
                        " <trk>" + newline + "  <name>" + summary.getName().trim() +    "</name>" + newline;
                fos1.write(name.getBytes());
                StringBuilder segments = new StringBuilder();
                int old_segment = -1;
                ArrayList<DetailProvider.Detail> summaryDetails = detailsTable.getDetails(context, summary.getTrip_id());
                for (DetailProvider.Detail detail: summaryDetails) {
                    int new_segment = detail.getSegment();
                    if (old_segment != new_segment) {
                        if (old_segment != -1) segments.append( "   </trkseg>" + newline);
                        segments.append( "  <trkseg>" + newline);
                        old_segment = new_segment;
                    }

                    segments.append("   <trkpt lat=\"" + df.format(detail.getLat() / Constants.MILLION) +
                            "\" lon=\"" + df.format(detail.getLon() / Constants.MILLION) + "\">" + newline +
                            detail.toString("strava") +
                            "   </trkpt>" + newline);
                }
                fos1.write(segments.toString().getBytes());
                String footer = "  </trkseg>" + newline + " </trk>" + newline;
                fos1.write(footer.getBytes());
                String endLine = newline + "</gpx>";
                fos1.write(endLine.getBytes());
                fos1.flush(); fos1.close();
                UtilsFile.forceIndex(context, TRIP_FILE);
                outfiles.add(UtilsFile.getFileName(context, TRIP_FILE));

                //msg = "Finished export... " + TRIP_FILE;
                progressListener.reportProgress(++numSummaries);
            }

            // make a zip file, if necessary
            if (outfiles.size() > 0)
                UtilsFile.zip(context, outfiles.toArray(new String[outfiles.size()]), "backup.zip");

        } catch (IOException ie) {
            msg = "Could not write GPX file " + ie.getMessage();
            Logger.e("TAG", msg, localLog);
        }
        return msg;
    }


    public String exportXML() {
        String msg = "";
        String TRIP_FILE;
        FileOutputStream fos1;
        int numSummaries = 0;
        try {
            for (SummaryProvider.Summary summary: summaries) {
                String dateTime = UtilsDate.getDateTimeSec(summary.getStart_time(), summary.getAvg_lat(), summary.getAvg_lon());
                dateTime = dateTime.replace(',','_').replace(' ', '_');
                TRIP_FILE = "trip_" + summary.getTrip_id() + "_" + dateTime + ".xml";
                fos1 = UtilsFile.openOutputFile(context, TRIP_FILE);

                String sb1 = summary.toString("xml") + newline;
                fos1.write(sb1.getBytes());
                ArrayList<DetailProvider.Detail> summaryDetails =
                        detailsTable.getDetails(context, summary.getTrip_id());
                for (DetailProvider.Detail detail: summaryDetails) {
                    String sb2 = detail.toString("xml") + newline;
                    fos1.write(sb2.getBytes());
                }
                fos1.flush(); fos1.close();
                UtilsFile.forceIndex(context, TRIP_FILE);
                progressListener.reportProgress(++numSummaries);
            }

        } catch (IOException ie) {
            msg = "Could not write XML file " + ie.getMessage();
            Logger.e("TAG", msg, localLog);
        }
        return msg;
    }

    public String exportKeys() {
        String msg;
        String FILENAME = "api_keys.json";
        try {
            FileOutputStream fos1 = UtilsFile.openOutputFile(context, FILENAME);
            fos1.write(("{" + newline).getBytes());
            String gkey = "\"google_api_key\": \"" + sharedPreferences.getString(Constants.PREF_GOOGLE_API_KEY, "0") + "\"," + newline ;
            fos1.write(gkey.getBytes());
            String okey = "\"openweather_api_key\": \"" + sharedPreferences.getString(Constants.PREF_OPENWEATHER_API_KEY, "0") + "\"" + newline;
            fos1.write((okey.getBytes()));
            fos1.write(("}" + newline).getBytes());
            fos1.close();
            msg = "Finished writing API keys";
            UtilsFile.forceIndex(context, FILENAME);

        } catch (IOException ie) {
            msg = "Could not write API keys file " + ie.getMessage();
            Logger.e("TAG", msg, localLog);
        }
        return msg;
    }
}