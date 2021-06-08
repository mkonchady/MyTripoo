package org.mkonchady.mytripoo.utils;

import android.content.Context;

import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.ProgressListener;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;


// Use a Wahoo sensor file to update the details
 public class RepairWahoo {

    private final Context context;
    SummaryDB summaryDB;
    DetailDB detailDB;
    SummaryProvider.Summary summary = null;
    private ProgressListener progressListener = null;
    int localLog = 0;
    final String TAG = "RepairWahoo";    // number of readings before and after turn
    private final int trip_id;

    public RepairWahoo(Context context, ProgressListener progressListener, int trip_id) {
        this.context = context;
        this.progressListener = progressListener;
        summaryDB = new SummaryDB(SummaryProvider.db);
        detailDB = new DetailDB(DetailProvider.db);
        this.trip_id = trip_id;
    }

    /*
        Extract the speeds in kmph from the extras field in the Summary. The speeds are stored in
        10 second intervalis with the start time as the first long. A new lat and lon is calculated
        using the average speed. This new lat lon is updated with the speed and timestamp.
     */

    public String run() {

        Logger.d(TAG, "Start Fusing Wahoo File: " + trip_id, localLog);
        if (progressListener != null) progressListener.reportProgress(10);

        // extract the Wahoo speeds from the extras field in the summary
        summary = summaryDB.getSummary(context, trip_id);
        String wahooData = UtilsJSON.getWahooExtras(summary.getExtras());
        String[] data = wahooData.split(",");

        long current_time = Long.parseLong(data[0]);
        for (int i = 1; i < data.length; i++) {
            float current_speed = Float.parseFloat(data[i]);

            // find the closest detail to the current timestamp
            DetailProvider.Detail detail = detailDB.getClosestDetailByTime(context, trip_id, current_time);

            /*
            int delta_time = (int) (abs(detail.getTimestamp() - current_time) / 1000L); // in seconds
            float avg_speed = (float) UtilsMap.kmphTomps( (current_speed + detail.getSpeed()) / 2.0f); // in meters / second
            float delta_distance = avg_speed * delta_time; // in meters
            if (detail.getTimestamp() > current_time) delta_distance = -delta_distance; // place new detail before closest detail

                // one meter is about 10 microdegress
            double delta_lat = delta_distance * Math.sin(UtilsMap.bearingToDegrees(detail.getBearing())) * 10;
            double delta_lon = delta_distance * Math.cos(UtilsMap.bearingToDegrees(detail.getBearing())) * 10;
            */

            // clone the detail and update the lat, lon, and speed
            DetailProvider.Detail new_detail = detailDB.cloneDetail(detail);
            //new_detail.setLat(detail.getLat() + (int) Math.round(delta_lat));
            //new_detail.setLon(detail.getLon() + (int) Math.round(delta_lon));
            new_detail.setTimestamp(current_time);
            new_detail.setSpeed(current_speed);
            // delete the old detail and replace with the new detail
            synchronized (this) {
                detailDB.deleteDetail(trip_id, detail.getTimestamp());
                detailDB.addDetail(context, new_detail);
            }

            current_time += 10000; // 10 second intervals
            if (progressListener != null && i%10 == 0) {
                int progress = (int) Math.round(i * 100.0 / data.length);
                progressListener.reportProgress(progress);
            }
        }
        if (progressListener != null) progressListener.reportProgress(95);

        return "Finished fusing Wahoo data: " + trip_id;
    }
}