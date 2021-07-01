package org.mkonchady.mytripoo.views;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Log;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.activities.TripCompareActivity;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMap;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;

public class MapsFragment2 extends Fragment {

    TripCompareActivity tripCompareActivity = null;
    TripCompareView tripCompareView = null;
    DetailDB detailDB = null;
    SummaryDB summaryDB = null;
    int[] bounds;
    int ZOOM;
    int greenColor;
    int trip_id = 0;
    private TreeMap<Long, TripCompareView.LocationDistance> mapTrip = new TreeMap<>(new TripCompareView.LongComparator());
    private boolean loadedTrip = false;
    private GoogleMap gMap = null;
    private long currentTime = 0;
    private final String TAG = "Maps1";

    private final OnMapReadyCallback callback = new OnMapReadyCallback() {
        @Override
        public void onMapReady(GoogleMap map) {
            UtilsMap.centerMap(map, bounds, ZOOM, tripCompareActivity);
            gMap = map;
            checkMapLoaded();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        tripCompareActivity = (TripCompareActivity) getActivity();
        greenColor = ContextCompat.getColor(tripCompareActivity, R.color.darkgreen);
        View v = inflater.inflate(R.layout.fragment_maps2, container, false);
        FrameLayout frameLayout = v.findViewById(R.id.map2);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                tripCompareActivity.getMap_width(), tripCompareActivity.getMap_height()));
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map2);
        if (mapFragment != null) {
            mapFragment.getMapAsync(callback);
            tripCompareView = tripCompareActivity.getTripCompareView();
            trip_id = tripCompareView.trip2;
            detailDB = tripCompareView.detailDB;
            bounds = detailDB.getBoundaries(tripCompareActivity, trip_id);
            summaryDB = tripCompareView.summaryDB;
            SummaryProvider.Summary summary = summaryDB.getSummary(tripCompareActivity, trip_id);
            ZOOM = UtilsMap.calcZoom(summary.getDistance());
            checkMapLoaded();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void createPath(long currentTime) {
        this.currentTime = currentTime;
        final Object[] param1 = {gMap, trip_id};
        new DrawTracks().execute(param1);
    }

    private void checkMapLoaded() {
        if (!loadedTrip && !tripCompareView.downIncomplete) {
            mapTrip = (TreeMap) tripCompareView.downTrip.clone();
            loadedTrip = true;
        }
    }

    private class DrawTracks extends AsyncTask<Object, Integer, String> {

        GoogleMap gMap;
        float cum_distance;
        int last_lat, last_lon;
        private ArrayList<PolylineOptions> polylineList = null;

        @Override
        protected String doInBackground(Object... params) {
            long MAX_DURATION = tripCompareView.getMAX_DURATION();
            int TIME_INCR = tripCompareView.getTIME_INCR();
            int MIN_TIME_INCR = tripCompareView.getMIN_TIME_INCR();

            gMap = (GoogleMap) params[0];
            polylineList = new ArrayList<>();
            long firstTime = 0;
            cum_distance = 0.0f;
            if (currentTime == 0) return "";
            double prev_lat = 0; double prev_lon = 0;
            checkMapLoaded();
            Set<Long> keySet =  mapTrip.keySet();
            PolylineOptions polylineOptions = new PolylineOptions();
            long lastTime = 0;
            for(Long timeKey: keySet) {
                TripCompareView.LocationDistance tm = mapTrip.get(timeKey);
                if (firstTime == 0) {
                    firstTime = timeKey;
                    prev_lat = tm.getLat() / Constants.MILLION;
                    prev_lon = tm.getLon() / Constants.MILLION;
                } else if (currentTime > 0 && timeKey % TIME_INCR == 0) {
                    cum_distance = tm.getDistance() / 1000.0f;
                    double curr_lat = tm.getLat() / Constants.MILLION;
                    double curr_lon = tm.getLon() / Constants.MILLION;
                    polylineOptions.add(new LatLng(prev_lat, prev_lon)).color(greenColor);
                    polylineOptions.add(new LatLng(curr_lat, curr_lon)).color(greenColor);
                    prev_lat = curr_lat;
                    prev_lon = curr_lon;
                }
                lastTime = timeKey;
                last_lat = tm.getLat();
                last_lon = tm.getLon();
                if ((timeKey - firstTime) >= (currentTime * 1000L) )
                    break;
            }
            tripCompareView.setDownDistance(cum_distance);

            // add the last reading if necessary
            if (currentTime + TIME_INCR >= MAX_DURATION / 1000) {
                TripCompareView.LocationDistance tm = mapTrip.get(lastTime);
                polylineOptions.add(new LatLng(tm.getLat() / Constants.MILLION, tm.getLon() / Constants.MILLION)).color(greenColor);
            }
            polylineList.add(polylineOptions);

            // lookahead and blank out any old tracks for 8x seconds
            polylineOptions = new PolylineOptions();
            long runTime = lastTime;
            long endTime = lastTime + 8 * MIN_TIME_INCR * 1000;
            while (runTime <= endTime) {
                runTime += 1000L;
                boolean containsKey =  mapTrip.containsKey(runTime);
                if (containsKey) {
                    TripCompareView.LocationDistance tm = mapTrip.get(runTime);
                    double curr_lat = tm.getLat() / Constants.MILLION;
                    double curr_lon = tm.getLon() / Constants.MILLION;
                    polylineOptions.add(new LatLng(prev_lat, prev_lon)).color(Color.WHITE);
                    polylineOptions.add(new LatLng(curr_lat, curr_lon)).color(Color.WHITE);
                    polylineList.add(polylineOptions);
                    prev_lat = curr_lat; prev_lon = curr_lon;
                    //last_lat = tm.getLat();
                    //last_lon = tm.getLon();
                }
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            if (gMap != null) {
                gMap.clear();
                for (PolylineOptions polylineOptions : polylineList)
                    gMap.addPolyline(polylineOptions);
                UtilsMap.centerMap(gMap, last_lat, last_lon, 15, tripCompareActivity);
                addFirstLastMarker();
            }
            // *-------------- redraw the two maps here ---------------*
            tripCompareView.postInvalidate();
        }


        private void addFirstLastMarker() {
            //gMap.addMarker(new MarkerOptions().position(tripCompareView.lastLocation1)); // add last marker
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(
                    BitmapDescriptorFactory.HUE_GREEN);   // add first marker
            gMap.addMarker(new MarkerOptions().icon(bitmapDescriptor).position(tripCompareView.firstLocation1));
        }
    }
}