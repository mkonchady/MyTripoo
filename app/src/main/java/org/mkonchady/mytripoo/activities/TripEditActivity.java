package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class TripEditActivity extends Activity implements OnMapReadyCallback {

    Context context;
    private int trip_id;
    DetailDB detailDB;
    ArrayList<DetailProvider.Detail> details = null;
    SummaryDB summaryDB;
    HashMap<Integer, String> edits = new HashMap<>();           // list of edits with ordered key and description value
    HashMap<Long, LatLng> markerLocations = new HashMap<>();    // keep track of marker locations (value) by timestamp (key)

    // marker snippet Hashmap keys
    final String OP = "op";
    final String END_LAT = "end_lat";
    final String END_LON = "end_lon";
    //final String START_LAT = "start_lat";
    //final String START_LON = "start_lon";
    final String TIMESTAMP = "timestamp";
    final String BEARING = "bearing";

    // operations
    final String ADD = "add";
    final String MOD = "mod";
    final String DEL = "del";

    LatLng firstLocation, lastLocation;
    int hashKey = 0;
    GoogleMap gMap;
    final int MAX_DRAG_DISTANCE = 1000;              // 1000 meters
    final double NEW_MARKER_SEPARATION = 0.00015;   // degree separation when creating a new marker
    final String NEW = "new";                       // title of new marker

    // GUI fields
    private TextView statusView;
    private Timer timer = null;
    private int currentSegment = 0;
    private int NUM_SEGMENTS;
    private int INC = 1;
    private int ZOOM;
    final String TAG = "TripEditActivity";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        context = this;
        trip_id = getIntent().getIntExtra(Constants.PREF_TRIP_ID, -1);
        MapsInitializer.initialize(getApplicationContext());

        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
        SummaryProvider.Summary summary = summaryDB.getSummary(context, trip_id);

        NUM_SEGMENTS = summaryDB.getSegmentCount(context, trip_id);
        if ((NUM_SEGMENTS > Constants.MAX_SEGMENTS) || (summaryDB.isImported(context, trip_id))) {
            NUM_SEGMENTS = 1;
            currentSegment = 1;
        } else {
            currentSegment = Constants.ALL_SEGMENTS;
        }
        ZOOM = calcZoom(summary.getDistance());

        // get the detail records for this trip
        details = detailDB.getDetails(context, trip_id);
        INC = 1;
        //int numDetails = details.size();
        //INC = (numDetails < 200)? 1 :        // adjust the increment to speed up rendering
        //      (numDetails < 400)? 1 :        // of long trips
        //      (numDetails < 600)? 1 : 1;

        setContentView(R.layout.activity_trip_map);
        setActionBar();
        statusView = this.findViewById(R.id.statusMap);
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_edit_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_map_pause:
                cancelTimer();
                break;
            case R.id.action_map_play:
                handleTimer(true);
                break;
            case R.id.action_map_reverse:
                handleTimer(false);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        gMap = map;
        centerMap(map);
        handleTimer(true);

        // handle the touch operation
        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                handleClick();
            }
        });

        // handle the drag operation
        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            String startSnippet;
            LatLng startLatLng;
            @Override
            public void onMarkerDragStart(Marker marker) {
                startSnippet = marker.getSnippet();
                startLatLng = marker.getPosition();
                cancelTimer();
                //Log.d(TAG, "Start : " + marker.getPosition());
            }
            @Override
            public void onMarkerDrag(Marker marker) {
            }
            @Override
            public void onMarkerDragEnd(Marker marker) {
                //Log.d(TAG, "End: " + marker.getPosition());
                String title = marker.getTitle();
                String status;
                String op;  // marker snippet used to stored timestamp, bearing, lat, lon, and op in hashmap
                HashMap<String,String> startsnippetHash = convertSnippetToHashMap(startSnippet);
                // find the distance between the start and end positions of the markers
                // limit the drag distance
                double distance = UtilsMap.getMeterDistance(startLatLng, marker.getPosition());
                if (distance > MAX_DRAG_DISTANCE) {
                    if (title != null && title.equals(NEW)) {
                        marker.remove();         // remove the added marker
                    } else {                     // restore the marker to the original position
                        marker.setPosition(markerLocations.get(Long.valueOf(startsnippetHash.get("timestamp"))));
                    }
                    status = "Exceeded drag limit of " + MAX_DRAG_DISTANCE + " meters";
                    statusView.setText(status);
                    return;
                }

                // add to the the list of operations
                if (title != null && title.equals(NEW)) {
                    op = ADD;
                    status = "Added new reading in dark red";
                } else {
                    op = MOD;
                    status = "Modified reading";
                }
                statusView.setText(status); // save the op details in the edits hashmap ordered by the integer key
                String value = convertHashMapToString(op, marker.getPosition().latitude, marker.getPosition().longitude,
                                Long.parseLong(startsnippetHash.get(TIMESTAMP)),  Integer.parseInt(startsnippetHash.get(BEARING)) );
                                //Double.parseDouble(startsnippetHash.get(END_LAT)), Double.parseDouble(startsnippetHash.get(END_LON))   );
                edits.put(hashKey++, value);
            }
        });

        // show an information window
        gMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                    return null;
            }

            @Override
            public View getInfoContents(final Marker marker) {
                cancelTimer();
                // place a red marker over the selected marker
                final HashMap<String, String> snippetHashmap = convertSnippetToHashMap(marker.getSnippet());
                final int bearing = Integer.parseInt(snippetHashmap.get(BEARING));
                MarkerOptions footprintMarker = new MarkerOptions().position(marker.getPosition())
                        .icon(BitmapDescriptorFactory.fromBitmap(UtilsMap.getArrowBitmap(Color.rgb(255,0,0))))
                        .rotation(bearing).anchor(0.5f, 0.5f);
                final Marker tempMarker = gMap.addMarker(footprintMarker);
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Edit Tracks");
                builder.setIcon(R.drawable.icon_e);
                builder.setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                tempMarker.remove();
                                dialog.cancel();
                            }
                        });
                builder.setNeutralButton("Delete",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                snippetHashmap.put(OP, DEL);
                                tempMarker.setSnippet(snippetHashmap.toString());
                                edits.put(hashKey++, snippetHashmap.toString());
                                marker.remove();
                                String status = "Deleted reading in red ";
                                statusView.setText(status);
                                dialog.cancel();
                            }
                        });
                builder.setPositiveButton("Add",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                tempMarker.remove();
                                MarkerOptions footprintMarker = new MarkerOptions().position(getClosePosition(marker))
                                        .icon(BitmapDescriptorFactory.fromBitmap(UtilsMap.getArrowBitmap(Color.rgb(51,0,0))))
                                        .anchor(0.1f, 0.1f).snippet(marker.getSnippet()).draggable(true).rotation(bearing);
                                final Marker addMarker = gMap.addMarker(footprintMarker);
                                addMarker.setTitle(NEW);
                                String status = "Adding reading in dark red ";
                                statusView.setText(status);
                                dialog.cancel();

                            }
                        });
                builder.show();
                return null;
            }

        });
    }

    private void handleClick() {
        // check if there are any operations to be completed
        cancelTimer();
        if (edits != null && edits.size() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Edit Tracks");
            builder.setIcon(R.drawable.icon_e);
            builder.setMessage("Process " + edits.size() + " modifications?");
            builder.setPositiveButton("Yes",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent();
                            intent.putExtra("trip_id", trip_id);    // for the caller TripActivity
                            setResult(Constants.EDIT_TRIP_RESULT_CODE, intent);
                            final Object[] param1 = {};
                            new RunTripMods().execute(param1);      // *---- run the modifications ----
                            dialog.cancel();
                            finish();
                        }
                    });
            builder.setNegativeButton("No",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            finish();
                        }
                    });
            builder.setNeutralButton("Cancel",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            builder.show();
        }
    }

    // if a map click was detected then either freeze the current map
    // or iterate through the segments
    private void handleTimer(final boolean positiveIncr) {
        if (gMap == null) return;
        if ((NUM_SEGMENTS == 1) || (NUM_SEGMENTS > Constants.MAX_SEGMENTS)) {
            final Object[] param1 = {gMap, trip_id, Constants.ALL_SEGMENTS};
            new DrawTracks().execute(param1);
        } else {
            cancelTimer();
            timer = new Timer();
            final long DELAY = 0;
            final long PERIOD = 5000;
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    incCurrentSegment(positiveIncr);
                    final Object[] param1 = {gMap, trip_id, currentSegment};
                    new DrawTracks().execute(param1);
                }
            }, DELAY, PERIOD);
        }
    }

    private void cancelTimer() {
        if (timer != null)
            timer.cancel();
    }

    // place the new marker in the neighborhood of the old marker
    private LatLng getClosePosition(Marker marker) {
        LatLng latLng = marker.getPosition();
        return (new LatLng(latLng.latitude + NEW_MARKER_SEPARATION, latLng.longitude + NEW_MARKER_SEPARATION));
    }

    private void incCurrentSegment(boolean positiveIncr) {
        currentSegment = (positiveIncr)? currentSegment+1: currentSegment-1;
        if (positiveIncr) {
            if (currentSegment == 0)
                currentSegment = 1;
            else if (currentSegment > NUM_SEGMENTS)
                currentSegment = Constants.ALL_SEGMENTS;
        } else {
            if (currentSegment == 0)
                currentSegment = Constants.ALL_SEGMENTS;
            else if (currentSegment < 0)
                currentSegment = NUM_SEGMENTS;
        }
    }

    // draw the tracks in the background
    private class DrawTracks extends AsyncTask<Object, Integer, String> {
        GoogleMap gMap;
        int trip_id;
        int segment;

        ArrayList<MarkerOptions> footprints = new ArrayList<>();

        @Override
        protected String doInBackground(Object... params) {
            gMap = (GoogleMap) params[0];
            trip_id = (int) params[1];
            segment = (int) params[2];

            // get the detail records for this trip, save the first location
            ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, trip_id, segment);
            firstLocation = new LatLng(details.get(0).getLat() / Constants.MILLION,
                                       details.get(0).getLon() / Constants.MILLION);

            // build an array of footprint markers at specific locations
            for (int j = 0; j < details.size(); j += INC) {
                DetailProvider.Detail detail = details.get(j);
                int bearing = detail.getBearing();
                double lat = detail.getLat() / Constants.MILLION;
                double lon = detail.getLon() / Constants.MILLION;
                LatLng location = new LatLng(lat, lon);
                markerLocations.put(detail.getTimestamp(), location);
                // build the snippet in a hashmap string
                String snippet = convertHashMapToString("", lat, lon, detail.getTimestamp(), detail.getBearing()); //, INVALID_LOC, INVALID_LOC);
                BitmapDescriptor footprint = (j == 0)?      // first footprint
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN):
                        ((j+INC) >= details.size())?        // last footprint
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED):
                        BitmapDescriptorFactory.fromBitmap(UtilsMap.getArrowBitmap(Color.rgb(0,0,255)));

                MarkerOptions footprintMarker = new MarkerOptions().position(location).icon(footprint)
                        .rotation(bearing).anchor(0.5f, 0.5f).snippet(snippet);

                footprintMarker.draggable(true);
                footprints.add(footprintMarker);

                if ((j + INC) >= details.size())    // save the last location
                    lastLocation = new LatLng(details.get(details.size() - 1).getLat() / Constants.MILLION,
                            details.get(details.size() - 1).getLon() / Constants.MILLION);
            }

            return "" + segment;
        }

        @Override
        protected void onPostExecute(String result) {
            gMap.clear();
            for (MarkerOptions footprint: footprints)
                gMap.addMarker(footprint);

            int segmentNumber = Integer.parseInt(result);
            if (segmentNumber != Constants.ALL_SEGMENTS) {
                String status = "Segment: " + segmentNumber;
                statusView.setText(status);
            } else {
                String status = "All segments ";
                statusView.setText(status);
            }
        }
    }

    //*--------------------------------------------------------------------
    //    Process the changes in the background
    //
    //    1. First process the add and mod operations in the order they
    //       were generated by the user
    //    2. Then process the delete operations
    //
    //*--------------------------------------------------------------------
    private class RunTripMods extends AsyncTask<Object, Integer, String> {

        @Override
        protected String doInBackground(Object... params) {
            // change the status to prevent a repair from running concurrently
            summaryDB.setSummaryStatus(context, Constants.RUNNING, trip_id);
            TreeMap<Integer, String> treeMap = new TreeMap<>(edits);

            // loop through the list of operations in ascending order of entry
            for(Map.Entry<Integer, String> entry : treeMap.entrySet()) {
                String value = entry.getValue();
                HashMap<String, String> snippetHashMap= convertSnippetToHashMap(value);
                String op = snippetHashMap.get(OP);
                // process the delete later, if there is a future modification of the same marker
                // i.e. the same marker modified twice or more, skip the current modification
                if (op.equals(DEL) || getEditIndex(snippetHashMap) > entry.getKey())
                    continue;
                //Log.d(TAG, "Key: " + entry.getKey() + " Value => " + value);

                // clone the old detail row, every detail record has an unique timestamp, duplicate timestamps are removed in repair trip.
                long timestamp = Long.parseLong(snippetHashMap.get(TIMESTAMP));
                DetailProvider.Detail detail_o = detailDB.cloneDetail(detailDB.getDetail(context, timestamp, trip_id));

                // 1. modify the lat / lon of the cloned detail with the new lat / lon
                DetailProvider.Detail detail_x = detailDB.cloneDetail(detailDB.getDetail(context, timestamp, trip_id));
                detail_x.setLat((int) Math.round(Double.parseDouble(snippetHashMap.get(END_LAT)) * Constants.MILLION));
                detail_x.setLon((int) Math.round(Double.parseDouble(snippetHashMap.get(END_LON)) * Constants.MILLION));

                // get the two detail readings a and b, after and before in index order
                DetailProvider.Detail[] details = detailDB.getDetailNeighbours(context, trip_id, detail_o.getIndex());
                DetailProvider.Detail detail_b = details[0];    // before
                DetailProvider.Detail detail_a = details[1];    // after

                // 2. modify the timestamp of the cloned detail
                if (op.equals(MOD) && detail_a != null && detail_b != null) {
                    long deltaTime = detail_a.getTimestamp() - detail_b.getTimestamp();
                    double distance_ab = UtilsMap.getMeterDistance(detail_a.getLat(), detail_a.getLon(),
                                                                   detail_b.getLat(), detail_b.getLon());
                    double distance_bx = UtilsMap.getMeterDistance(detail_b.getLat(), detail_b.getLon(),
                                                                   detail_x.getLat(), detail_x.getLon());
                    //  b ---------> x ------------> a
                    // set the new timestamp = before_timestamp + ( (bx / ab) * (after_timestamp - before_timestamp)
                    // verify that distance from b to x is less than distance from b to a
                    if (distance_bx >= distance_ab)
                        distance_bx = distance_ab - 5; // arbitrary correction so that time sequence of details is maintained in index sequence

                    detail_x.setTimestamp(Math.round(((distance_bx / distance_ab) * deltaTime)) + detail_b.getTimestamp());
                }
                // 3. Add a new detail with the last lat/lon and calculate the timestamp based on location
                // relative to neighbour (by distance) details
                if (op.equals(ADD) && detail_a != null && detail_b != null) {
                    double speed;
                    int case_num = getCaseNum(detail_a, detail_b, detail_x, detail_o);
                    long timestamp_x = detail_x.getTimestamp();
                    int distance_ax = (int) UtilsMap.getMeterDistance(detail_a.getLat(), detail_a.getLon(), detail_x.getLat(), detail_x.getLon());
                    int distance_bx = (int) UtilsMap.getMeterDistance(detail_b.getLat(), detail_b.getLon(), detail_x.getLat(), detail_x.getLon());
                    int distance_ox = (int) UtilsMap.getMeterDistance(detail_o.getLat(), detail_o.getLon(), detail_x.getLat(), detail_x.getLon());
                    // set timestamp_x based on the location of the new detail relative to details a and b
                    switch (case_num) {
                        case 0:     // detail x is before detail b
                            speed = UtilsMap.kmphTomps(detail_b.getSpeed()); // get speed in meters per second
                            timestamp_x = detail_b.getTimestamp() - Math.round(1000.0 * distance_bx / speed);
                            break;
                        case 1:     // detail x is between detail b and detail o
                            speed = UtilsMap.kmphTomps((detail_a.getSpeed() + detail_b.getSpeed()) / 2.0);
                            timestamp_x = detail_o.getTimestamp() - Math.round(1000.0 * distance_ox / speed);
                            break;
                        case 2:     // detail x is between detail o and detail a
                            speed = UtilsMap.kmphTomps((detail_a.getSpeed() + detail_b.getSpeed()) / 2.0);
                            timestamp_x = detail_o.getTimestamp() + Math.round(1000.0 * distance_ox / speed);
                            break;
                        case 3:    // detail x is after detail a and detail b
                            speed = UtilsMap.kmphTomps(detail_a.getSpeed());
                            timestamp_x = detail_a.getTimestamp() + Math.round(1000.0 * distance_ax / speed);
                            break;
                    }
                    detail_x.setTimestamp(timestamp_x);
                }

                // for a MOD operation, first delete the old detail.  Add a new detail
                synchronized (this) {
                    if (op.equals(MOD)) detailDB.deleteDetail(trip_id, Long.parseLong(snippetHashMap.get("timestamp")));
                    detailDB.addDetail(context, detail_x);
                }

            } // end of for

            // 4. process the deletes after the mods and adds
            for(Map.Entry<Integer, String> entry : treeMap.entrySet()) {
                String value = entry.getValue();
                HashMap<String, String> snippetHashMap = convertSnippetToHashMap(value);

                // for a delete, remove the detail reading from the DB, except the first and last markers
                if (snippetHashMap.get(OP).equals(DEL)) {
                    LatLng latLng = new LatLng(Double.parseDouble(snippetHashMap.get(END_LAT)), Double.parseDouble(snippetHashMap.get(END_LON)));
                    if (!isSameLocation(firstLocation, latLng) && !isSameLocation(lastLocation, latLng)) {
                        long timestamp = Long.parseLong(snippetHashMap.get(TIMESTAMP));
                        detailDB.deleteDetail(trip_id, timestamp);
                    }
                }
            }

            // flag this trip as a modified trip
            if (treeMap.size() > 0) {
                summaryDB.setSummaryExtrasEdited(context, trip_id, true);

                // restore the start and end times to the original time
                long startTime = summaryDB.getStartTime(context, trip_id);
                DetailProvider.Detail detail = detailDB.getFirstDetail(context, trip_id, Constants.ALL_SEGMENTS);
                detailDB.setDetailTimestamp(context, startTime, trip_id, Integer.parseInt(detail.getIndex()));

                long endTime = summaryDB.getEndTime(context, trip_id);
                detail = detailDB.getLastDetail(context, trip_id, Constants.ALL_SEGMENTS);
                detailDB.setDetailTimestamp(context, endTime, trip_id, Integer.parseInt(detail.getIndex()));
            }

            // signal end of modifications for the repair
            summaryDB.setSummaryStatus(context, Constants.FINISHED, trip_id);
            return "";
        }

        /*

        Detail B is before the clicked detail
        Detail A is after the clicked detail
        Detail O is the clicked detail
        Detail X is the new location of the clicked detail

        Case 0: X is before B and O, angle XBO is greater than 90
            X
             \
             B-----O-----A

        Case 1: X is between B and O , angles BOX and XOB are less than 90
             X
           /  \
          B----O----A

         Case 2: X is after B and O, angles XOA and XAO are less than 90
                   X
                 /   \
          B-----O-----A

         Case 3: X is after A, angles XAO is more than 90
                        X
                       /
          B---- O----A
         */
        private int getCaseNum(DetailProvider.Detail a, DetailProvider.Detail b, DetailProvider.Detail x, DetailProvider.Detail o) {
            // cannot add with start and end markers
            if (markerLocations.containsKey(o.getTimestamp())) {
                LatLng latLng = markerLocations.get(o.getTimestamp());
                if ( isSameLocation(firstLocation, latLng) || isSameLocation(lastLocation, latLng) )
                    return -1;
            }

            // check if a relatively straight route or turns
            if (isStraightRoute(a, b, x, o)) {
                if (UtilsMap.getAngle(x, b, o) >= 90) return 0;  // angle between xb and bo
                if (UtilsMap.getAngle(x, b, o) <= 90 && UtilsMap.getAngle(x, o, b) <= 90) return 1;
                if (UtilsMap.getAngle(x, o, a) <= 90 && UtilsMap.getAngle(x, a, o) <= 90) return 2;
                if (UtilsMap.getAngle(o, a, x) >= 90) return 3;  // angle between oa and ax
                return -1;
            }

            // may not work if x is moved far from a, b, o
            int dist_xb = getDistance(x, b);
            int dist_xa = getDistance(x, a);
            int dist_xo = getDistance(x, o);
            if ( (dist_xb < dist_xo) && (dist_xb < dist_xa) ) return 0;
            if ( (dist_xb < dist_xa) && (dist_xo < dist_xa) ) return 1;
            if ( (dist_xa < dist_xb) && (dist_xo < dist_xb) ) return 2;
            if ( (dist_xa < dist_xo) && (dist_xa < dist_xb) ) return 3;

            return -1;
        }


        private boolean isStraightRoute(DetailProvider.Detail a, DetailProvider.Detail b, DetailProvider.Detail x, DetailProvider.Detail o) {
            int bearing_diff = Math.abs(a.getBearing() - x.getBearing());
            bearing_diff += Math.abs(o.getBearing() - x.getBearing());
            bearing_diff += Math.abs(b.getBearing() - x.getBearing());
            return (bearing_diff < 90);
        }


        private boolean isSameLocation(LatLng latLng1, LatLng latLng2) {
            return (UtilsMap.getMeterDistance(latLng1, latLng2) < 1.0);
        }

        private int getDistance(DetailProvider.Detail a, DetailProvider.Detail b) {
            return (int) (UtilsMap.getMeterDistance(a.getLat(), a.getLon(), b.getLat(), b.getLon()));
        }

        @Override
        protected void onPostExecute(String result) {
        }
    }

    public void centerMap(GoogleMap map) {
        int[] bounds = detailDB.getBoundaries(context, trip_id);
        double north = bounds[0] / Constants.MILLION;
        double south = bounds[1] / Constants.MILLION;
        double east = bounds[2] / Constants.MILLION;
        double west = bounds[3] / Constants.MILLION;
        LatLng southWest = new LatLng(south, west);
        LatLng northEast = new LatLng(north, east);
        LatLngBounds latLngBounds = new LatLngBounds(southWest, northEast);
        CameraUpdate camera_center = CameraUpdateFactory.newLatLngZoom(latLngBounds.getCenter(), ZOOM);

        CameraUpdate camera_zoom = CameraUpdateFactory.zoomTo(ZOOM);
        map.moveCamera(camera_center);
        map.animateCamera(camera_zoom);
        final int MY_PERMISSIONS_FINE_LOCATION = 10;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_FINE_LOCATION);
        }
        map.setMyLocationEnabled(true);

    }

    // set the zoom lower for longer trip distances
    private int calcZoom(float distance) {
        int zoom;
        if      (distance < 10 * 1000.0f) zoom = 15;
        else if (distance < 25 * 1000.0f) zoom = 13;
        else if (distance < 50 * 1000.0f) zoom = 11;
        else if (distance < 100 * 1000.0f) zoom = 9;
        else zoom = 7;
        return zoom;
    }

    // Hashmap to string and string to hashmap utilities
    protected HashMap<String,String> convertSnippetToHashMap(String text){
        HashMap<String,String> map = new HashMap<>();
        Pattern p = Pattern.compile("[\\{\\}=, ]++");
        String[] split = p.split(text);
        for ( int i=1; i+2 <= split.length; i+=2 ){
            map.put( split[i], split[i+1] );
        }
        return map;
    }

    protected String convertHashMapToString(String op, double lat, double lon, long timestamp, int bearing) { //}, double markerLat, double markerLon) {
        HashMap<String, String> snippetHashmap = new HashMap<>();
        if (op.length() > 0)
            snippetHashmap.put(OP, op);
        snippetHashmap.put(END_LAT, lat + ""); snippetHashmap.put(END_LON, lon + "");
        snippetHashmap.put(TIMESTAMP, timestamp + "");
        snippetHashmap.put(BEARING, bearing + "");
        //if (markerLat != INVALID_LOC) {
        //    snippetHashmap.put(START_LAT, markerLat + "");
        //    snippetHashmap.put(START_LON, markerLon + "");
        //}
        return snippetHashmap.toString();
    }

    // scan the list of edits and find the highest value for the given
    // lat/lon/timestamp combination -- i.e. use the last modification
    // if multiple changes were made to a single reading
    protected int getEditIndex(HashMap<String, String> snippetHashMap) {
        int maxKey = -1;
        long timestamp = Long.parseLong(snippetHashMap.get(TIMESTAMP));
        for (Map.Entry<Integer, String> entry : edits.entrySet()) {
            HashMap<String, String> searchHashMap = convertSnippetToHashMap(entry.getValue());
            if (Long.parseLong(searchHashMap.get(TIMESTAMP)) == timestamp && entry.getKey() > maxKey)
                maxKey = entry.getKey();
        }
        return  maxKey;
    }


    @Override
    protected void onResume() {
        super.onResume();
        handleTimer(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelTimer();

    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelTimer();
    }

    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setLogo(R.drawable.icon);
        }
    }
}