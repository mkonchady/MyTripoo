package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.database.DetailDB;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.files.ExportFile;
import org.mkonchady.mytripoo.files.FileDialog;
import org.mkonchady.mytripoo.external.GetAltitude;
import org.mkonchady.mytripoo.external.GetWeather;
import org.mkonchady.mytripoo.files.ImportFile;
import org.mkonchady.mytripoo.Logger;
import org.mkonchady.mytripoo.ProgressListener;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.utils.RepairTrip;
import org.mkonchady.mytripoo.utils.RepairWahoo;
import org.mkonchady.mytripoo.external.SnapToRoad;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.database.SummaryProvider.Summary;
import org.mkonchady.mytripoo.utils.TimezoneMapper;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsFile;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMap;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.mkonchady.mytripoo.utils.UtilsMap.kmphTomps;

public class TripActivity extends Activity {

    // Table fields
    SummaryDB summaryTable = null;      // summary table DB handler
    int NUM_ROWS = 40;                  // max. number of rows to display
    ArrayList<TableRow> rows = new ArrayList<>();
    LayoutInflater inflater = null;
    Context context = null;
    TableLayout tableLayout = null;
    ArrayList<Integer> selectedTrips = new ArrayList<>();
    String category;
    SharedPreferences sharedPreferences;
    int localLog = 0;
    boolean storagePermissionGranted = false;
    boolean copyRunning = false;
    boolean openWeatherRunning = false;

    // GUI fields
    private TextView statusView;
    private EditText startDate, endDate;
    private DatePickerDialog fromDatePickerDialog;
    private DatePickerDialog toDatePickerDialog;

    // colors for the table rows
    int rowBackColor;
    int rowSelectColor;
    int rowHighlightColor;
    String TAG = "TripActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip);
        context = this;
        summaryTable = new SummaryDB(SummaryProvider.db);
        tableLayout = findViewById(R.id.triptablelayout);
        inflater = getLayoutInflater();
        getDateViews();
        setDateDialogs();
        statusView = this.findViewById(R.id.statusTrip);

        rowBackColor = ContextCompat.getColor(context, R.color.row_background);
        rowSelectColor = ContextCompat.getColor(context, R.color.row_selected_background);
        rowHighlightColor = ContextCompat.getColor(context, R.color.row_highlight_background);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

        // ask for storage permission in Android 6.0+
        // check if storahe Permission has been granted
        storagePermissionGranted = Constants.preMarshmallow;
        if (Constants.postMarshmallow && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)
            storagePermissionGranted = true;

        if (!storagePermissionGranted) {
            Logger.d(TAG, "Getting Storage Permission", localLog);
            Intent intent = new Intent(getBaseContext(), PermissionActivity.class);
            intent.putExtra("permission", Manifest.permission.WRITE_EXTERNAL_STORAGE);
            startActivityForResult(intent, Constants.PERMISSION_CODE);
        }

        // check if a starting date  and category was passed in the intent
        Intent intent = getIntent();
        long fromDateTimestamp = intent.getLongExtra(Constants.TRIP_FROM_DATE, 0L);
        if (fromDateTimestamp != 0) {
            startDate.setText(UtilsDate.getDate(fromDateTimestamp, Constants.LARGE_INT, Constants.LARGE_INT));
            long endTimestamp = fromDateTimestamp + Constants.MILLISECONDS_PER_DAY;
            selectedTrips = summaryTable.getTripIds(context, fromDateTimestamp, endTimestamp);
        }
        category = intent.getStringExtra(Constants.TRIP_CATEGORY);
        if (category == null || category.length() == 0)
            category = Constants.CAT_ALL;

        // for the first time create a sample trip
        if (sharedPreferences.getBoolean(Constants.PREF_FIRST_TRIP, true)) {
            final Object[] params = {this};
            new ImportSampleTrip().execute(params);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(Constants.PREF_FIRST_TRIP, false);
            editor.apply();
        }

        NUM_ROWS = Integer.parseInt(sharedPreferences.getString(Constants.PREF_NUMBER_TRIP_ROWS, "40"));
        setActionBar();
        fetch_rows(false);  // sort by date
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // get the start date and end date views
    private void getDateViews() {
        startDate = findViewById(R.id.start_date);
        startDate.setInputType(InputType.TYPE_NULL);
        startDate.requestFocus();
        startDate.setKeyListener(null);
        endDate = findViewById(R.id.end_date);
        endDate.setInputType(InputType.TYPE_NULL);
        endDate.setKeyListener(null);
    }

    // build the date dialogs
    private void setDateDialogs() {
        Calendar calendar = Calendar.getInstance();
        fromDatePickerDialog = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    public void onDateSet(DatePicker view, int year, int month, int day) {
                        Calendar newDate = Calendar.getInstance();
                        newDate.set(year, month, day);
                        startDate.setText(UtilsDate.getDate(newDate.getTimeInMillis(), Constants.LARGE_INT, Constants.LARGE_INT));
                        fetch_rows(false);
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        startDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fromDatePickerDialog.show();
            }
        });

        toDatePickerDialog = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    public void onDateSet(DatePicker view, int year, int month, int day) {
                        Calendar newDate = Calendar.getInstance();
                        newDate.set(year, month, day);
                        endDate.setText(UtilsDate.getDate(newDate.getTimeInMillis(), Constants.LARGE_INT, Constants.LARGE_INT));
                        fetch_rows(false);
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        endDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toDatePickerDialog.show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_analyse:
                analyze_trip(); break;
            case R.id.action_combine:
                combine_rows(); break;
            case R.id.action_compare:
                compare_rows(); break;
            case R.id.action_copy:
                copy_trip(); break;
            case R.id.action_delete:
                delete_rows(); break;
            case R.id.action_edit:
                edit_trip(); break;
            case R.id.action_export:
                export_rows(); break;
            case R.id.action_get_temp:
                set_openweather_rows(); break;
            case R.id.action_fix_gps:
                snap_rows(); break;
            case R.id.action_fix_alt:
                set_altitude_rows(); break;
            case R.id.action_follow:
                follow_trip(); break;
            case R.id.action_import:
                import_rows(); break;
            case R.id.action_import_keys:
                import_keys(); break;
            case R.id.action_export_keys:
                export_keys(); break;
            case R.id.action_maps:
                map_trip(); break;
            case R.id.action_playback:
                playback_trip(); break;
            case R.id.action_plots:
                plot_trip(); break;
            case R.id.action_repair:
                repair_rows(false, false); break;
            case R.id.action_split:
                split_trip(); break;
            //case R.id.action_simulate:
            //    simulate_trip(); break;
            case R.id.action_waypoints:
                waypoint_trip(); break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void analyze_trip() {
        if (!trip_number_error(1, 1)) {
            int trip_id = getSelectedIds().get(0);
            Intent intent = new Intent(TripActivity.this, TripAnalysisActivity.class);
            intent.putExtra(Constants.PREF_TRIP_ID, trip_id);
            startActivity(intent);
        }
    }

    private void combine_rows() {
        if (trip_number_error(2, 10)) return;
        final Object[] params = {this};
        new CombineRows().execute(params);
    }

    private void compare_rows() {
        if (!trip_number_error(2, 2)) {
            int trip_id1 = getSelectedIds().get(0);
            int trip_id2 = getSelectedIds().get(1);
            Intent intent = new Intent(TripActivity.this, TripCompareActivity.class);
            intent.putExtra(Constants.PREF_TRIP_ID + "_1", trip_id1);
            intent.putExtra(Constants.PREF_TRIP_ID + "_2", trip_id2);
            startActivity(intent);
        }
    }

    // delete the selected rows or the rows in the date range
    private void delete_rows() {
        ArrayList<Integer> ids = getSelectedIds();
        String plural = (ids.size() > 1) ? "s" : "";
        final Object[] params = {this};
        new AlertDialog.Builder(this)
                .setTitle("Delete selected trips")
                .setMessage("Are you sure you want to delete " + ids.size() +
                        " trip" + plural + "?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        new DeleteRows().execute(params);  // delete rows
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // export one or more trips, a zip file maybe created for many exported trips
    private void export_rows() {
        ArrayList<Integer> ids = getSelectedIds();
        int size = ids.size();
        if (size > 1) {
            final Object[] params = {this};
            new AlertDialog.Builder(this)
                    .setTitle("Export selected trips")
                    .setMessage("Are you sure you want to export " + size + " trips" + "?")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            new ExportRows().execute(params);  // export rows
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // do nothing
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        } else {
            final Object[] params = {this};
            new ExportRows().execute(params);
        }

    }

    // the next trip that will be started will follow this trip
    private void follow_trip() {
        if (trip_number_error(1, 1)) return;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        int trip_id = getSelectedIds().get(0);
        editor.putInt(Constants.PREF_FOLLOW_TRIP, trip_id);
        editor.apply();
        String status = "Following trip: " + trip_id;
        statusView.setText(status);
    }

    // import trip from a file
    private void import_rows() {
        final Object[] params = {this};
        new ImportRows().execute(params);
    }

    // import API keys from a file
    private void import_keys() {
        final Object[] params = {this};
        new ImportKeys().execute(params);
    }

    // export API keys to a file
    private void export_keys() {
        final Object[] params = {this};
        new ExportKeys().execute(params);
    }

    // simulate a trip
    private void simulate_trip() {
        if (trip_number_error(1, 1)) return;
        int trip_id = getSelectedIds().get(0);            // get the id of trip to be copied
        final Object[] params = {this, trip_id};
        String msg = "Started simulation...";
        statusView.setText(msg);
        new SimulateTrip().execute(params);
    }

    // clone a trip
    private void copy_trip() {
        if (trip_number_error(1, 1)) return;
        int trip_id = getSelectedIds().get(0);
        final Object[] params = {this, trip_id};
        String msg = "Started copy";
        statusView.setText(msg);
        new CopyTrip().execute(params);
    }

    // show Google Maps of the trip
    private void map_trip() {
        if (!trip_number_error(1, 1)) {
            int trip_id = getSelectedIds().get(0);
            Intent intent = new Intent(TripActivity.this, TripMapActivity.class);
            intent.putExtra(Constants.PREF_TRIP_ID, trip_id);
            startActivity(intent);
        }
    }

    // build a Google map to show waypoints
    private void waypoint_trip() {
        if (!trip_number_error(1, 1)) {
            int trip_id = getSelectedIds().get(0);
            Intent intent = new Intent(TripActivity.this, TripWaypointActivity.class);
            intent.putExtra(Constants.PREF_TRIP_ID, trip_id);
            startActivity(intent);
        }
    }

    // playback a trip on a map
    private void playback_trip() {
        if (!trip_number_error(1, 1)) {
            int trip_id = getSelectedIds().get(0);
            Intent intent = new Intent(TripActivity.this, TripPlaybackActivity.class);
            intent.putExtra(Constants.PREF_TRIP_ID, trip_id);
            startActivity(intent);
        }
    }

    // show plots of the trip stats
    private void plot_trip() {
        if (!trip_number_error(1, 1)) {
            int trip_id = getSelectedIds().get(0);
            Intent intent = new Intent(TripActivity.this, TripPlotActivity.class);
            intent.putExtra(Constants.PREF_TRIP_ID, trip_id);
            startActivity(intent);
        }
    }

    // repair a trip detail records
    private void repair_rows(boolean imported, boolean selected_trips) {
        ArrayList<Integer> ids = getSelectedIds();
        int size = ids.size();
        if (size > 1) {
            final Object[] params = {this, imported, selected_trips};
            new AlertDialog.Builder(this)
                    .setTitle("Repair selected trips")
                    .setMessage("Are you sure you want to repair " + size + " trips" + "?")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            new RepairRows().execute(params);  // delete rows
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // do nothing
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        } else {
            final Object[] params = {this, imported, selected_trips};
            new RepairRows().execute(params);
        }
    }

    // Use Google Snap to Road to correct GPS coordinates
    private void snap_rows() {
        if (trip_number_error(1, 1)) return;
        final Object[] params = {this};
        new SnapRows().execute(params);
    }

    // Use Google Altitude to correct elevation readings
    private void set_altitude_rows() {
        if (trip_number_error(1, 1)) return;
        final Object[] params = {this};
        new AltitudeRows().execute(params);
    }

    // Use Openweather to add temperature, wind, and humidity to detail extras
    private void set_openweather_rows() {
        if (trip_number_error(1, 1)) return;
        final Object[] params = {this};
        new WeatherRows().execute(params);
    }

    // break one trip into multiple sub-trips
    private void split_trip() {
        if (!trip_number_error(1, 1)) {
            int trip_id = getSelectedIds().get(0);
            Intent intent = new Intent(TripActivity.this, TripSplitActivity.class);
            intent.putExtra(Constants.PREF_TRIP_ID, trip_id);
            startActivityForResult(intent, Constants.SPLIT_TRIP_RESULT_CODE);
            selectTrips(new ArrayList<>(Collections.singletonList(trip_id)));
        }
    }

    // fix the readings of a trip
    private void edit_trip() {
        if (!trip_number_error(1, 1)) {
            int trip_id = getSelectedIds().get(0);
            Intent intent = new Intent(TripActivity.this, TripEditActivity.class);
            intent.putExtra(Constants.PREF_TRIP_ID, trip_id);
            startActivityForResult(intent, Constants.EDIT_TRIP_RESULT_CODE);
            selectTrips(new ArrayList<>(Collections.singletonList(trip_id)));
        }
    }

    // show a message after returning from TripSplit, TripEdit, or Permission request
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //if(resultCode == RESULT_OK && requestCode == Constants.SPLIT_TRIP_RESULT_CODE)  {
        if (requestCode == Constants.SPLIT_TRIP_RESULT_CODE) {
            String status = "Finished split trips.";
            statusView.setText(status);
            if (data != null && data.hasExtra("tripIds"))
                selectedTrips = data.getIntegerArrayListExtra("tripIds");
            fetch_rows(true);
        }
        //if(resultCode == RESULT_OK && requestCode == Constants.EDIT_TRIP_RESULT_CODE)  {
        if (requestCode == Constants.EDIT_TRIP_RESULT_CODE && data != null) {
            int trip_id = data.getIntExtra("trip_id", -1);
            if (trip_id != -1) {
                String status = "Repairing trip";
                statusView.setText(status);
                repair_rows(false, false);
            }
        }

        if (requestCode == Constants.PERMISSION_CODE) {
            String granted = data.getStringExtra("granted");
            Logger.d(TAG, "Storage permission Granted: " + granted);
            if (granted.equals("true")) {
                storagePermissionGranted = true;
            } else {
                if (Constants.preAndroid10) {
                    String result = "Cannot run without storage permission";
                    Toast.makeText(context, result, Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }

    // fetch the table rows from the DB in the background
    private void fetch_rows(boolean sortByID) {
        new FetchRows().execute(buildParams(sortByID));
    }

    // verify that the number of trips selected is correct
    private boolean trip_number_error(int min, int max) {
        int trip_size = getHighlightedRows(true).size();
        if ( (min <= trip_size) && (trip_size <= max))
            return false;
        String dup = (min > 1)? "s": "";
        if (min > trip_size) {
            String status = "Please select at least " + min + " trip" + dup;
            statusView.setText(status);

        } else if (trip_size > max) {
            String status = (max > min)? "Please select between " + min + " and " + max + " trips":
                                         "Please select " + min + " trip" + dup;
            statusView.setText(status);
        }
        return true;
    }

    // build the date parameters for the select
    private Object[] buildParams(boolean sortByID) {
        // extract the dates
        String startText = startDate.getText().toString();
        String endText = endDate.getText().toString();
        // construct the SQL clauses
        String whereClause = setWhereClause(startText, endText);
        String sortOrder = setSortOrder(startText, endText, sortByID);
        return new Object[] {this, whereClause, sortOrder};
    }

    // Load the list of summaries asynchronously and then build the table rows
    private static class FetchRows extends AsyncTask <Object, String, String> {
        ArrayList<Summary> summaries = null;
        private WeakReference<TripActivity> activityReference;
        String whereClause, sortOrder;
        long startTimestamp, endTimestamp;

        @Override
        protected String doInBackground(Object...params) {
            if (params.length < 3) return "";
            //context = (Context) params[0];
            activityReference = new WeakReference<>((TripActivity) params[0]);
            TripActivity tripActivity = activityReference.get();
            whereClause = (String) params[1];
            sortOrder = (String) params[2];
            String startText = tripActivity.startDate.getText().toString();
            startTimestamp = (startText.length() > 0)? tripActivity.getTimestamp(startText): 0;
            String endText = tripActivity.endDate.getText().toString();
            endTimestamp = (endText.length() > 0)? tripActivity.getTimestamp(endText): Long.MAX_VALUE;
            ArrayList<SummaryProvider.Summary> all_summaries = tripActivity.summaryTable.getSummaries(
                    tripActivity.context, whereClause, sortOrder, -1);
            summaries = new ArrayList<>();

            for (SummaryProvider.Summary summary : all_summaries) {
                TimeZone tz = TimeZone.getTimeZone(
                        TimezoneMapper.latLngToTimezoneString(summary.getAvg_lat() / Constants.MILLION, summary.getAvg_lon() / Constants.MILLION));
                long correction = tz.getOffset(System.currentTimeMillis());
                long local_startTime = summary.getStart_time() + correction;
                long local_endTime = summary.getEnd_time() + correction;
                if ( (local_startTime >= startTimestamp) && (local_endTime <= endTimestamp) ){
                    summaries.add(summary);
                }
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            TripActivity tripActivity = activityReference.get();
            for (TableRow row : tripActivity.rows) tripActivity.tableLayout.removeView(row);
            tripActivity.rows = new ArrayList<>();
            tripActivity.buildTableRows(summaries);
            if (summaries.size() == 0) {
                String status = "Create a trip by pressing the Start Button in the main window.";
                tripActivity.statusView.setText(status);
            }
            tripActivity.selectTrips(tripActivity.selectedTrips);
        }
    }

    /* dump the summary and details files
      To locate the output file on Linux:
        cd ~/.android/avd/<avd name>.avd/sdcard.img
        sudo mount sdcard.img -o loop /mnt/sdcard
        cd /mnt/sdcard/Android/data/org.mkonchady.mytripoo/files
    */
    private class ExportRows extends AsyncTask <Object, Integer, String> implements ProgressListener {
        int numSummaries = 0;
        @Override
        protected String doInBackground(Object...params) {
            String msg = "";
            if (!isExternalStorageWritable()) {
                msg = "Could not write to external storage";
                Logger.e(TAG, msg, localLog);
                return msg;
            }
            // get the list of summaries,  // get all summaries if none were selected
            ArrayList<Summary> summaries = getSelectedSummaries();
            if (summaries.size() == 0)
                summaries = summaryTable.getSummaries(context, "", "", -1);
            numSummaries = summaries.size();
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            String exportFormat = SP.getString(Constants.PREF_FILE_FORMAT, "gpx");
            ExportFile exportFile = new ExportFile(context, summaries, this);
            switch (exportFormat) {
                case "csv": msg = exportFile.exportCSV(); break;
                case "xml": msg = exportFile.exportXML(); break;
                case "gpx": msg = exportFile.exportGPX(); break;
                case "strava": msg = exportFile.exportStrava(); break;
            }
            return msg;
        }

        @Override
        protected void onPostExecute(String result) {
            String status = "Finished export ...";
            statusView.setText(status);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            int files = progress[0];
            String status = "Completed " + files + " of " + numSummaries;
            statusView.setText(status);
        }

        @Override
        protected void onPreExecute() {
            String status = "Started export ...";
            statusView.setText(status);
        }
    }

    // dump the API keys to a file
    private class ExportKeys extends AsyncTask <Object, Integer, String>  {
        @Override
        protected String doInBackground(Object...params) {
            String msg = "";
            if (!isExternalStorageWritable()) {
                msg = "Could not write to external storage";
                Logger.e(TAG, msg, localLog);
                return msg;
            }
            ExportFile exportFile = new ExportFile(context);
            msg = exportFile.exportKeys();
            return msg;
        }

        @Override
        protected void onPostExecute(String result) {
            String status = "Finished export API keys ...";
            statusView.setText(status);
        }

        @Override
        protected void onPreExecute() {
            String status = "Started export API keys ...";
            statusView.setText(status);
        }
    }

    // Import a trip from a file
    private class ImportRows extends AsyncTask <Object, Integer, String> implements  ProgressListener {
        String filename = "";
        FileDialog fileDialog = null;
        int trip_id = 0;
        int[] tripIds = null;
        boolean isWahooFile = false;

        @Override
        protected String doInBackground(Object...params) {
            while (fileDialog.isShowing()) {                  // wait for file selection
                try { Thread.sleep(1000); }
                catch (InterruptedException ie) {
                    Logger.e(TAG, "Interrupted sleep " + ie.getMessage(), localLog);
                }
            }
            if (filename.length() == 0) return "";      // no file was selected
            publishProgress(5);
            String suffix = UtilsFile.getFileSuffix(filename);
            ImportFile importFile = new ImportFile(context, this, filename);
            switch (suffix) {
                case "csv": trip_id = importFile.importCSV(); break;
                case "gpx": trip_id = importFile.importGPX(); break;
                case "zip": tripIds = importFile.importZIP(); break;
                case "runXml": trip_id = importFile.importXML(); isWahooFile = true; break;
                //case "xml": trip_id = importFile.importXML(); break;
            }
            return "Started importing file";
        }

        @Override
        protected void onPreExecute() {
            File mPath = new File(Environment.getExternalStorageDirectory() + "//DIR//");
            String[] suffixes = {"zip", "csv", "gpx", "runXml"};
            fileDialog = new FileDialog((Activity) context, mPath, suffixes);
            fileDialog.addFileListener(new FileDialog.FileSelectedListener() {
                public void fileSelected(File file) {
                  filename = file.toString();
                  fileDialog.setShowing(false);
                }
            });
            fileDialog.showDialog();
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Importing file " + progress[0] + "%";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            //Log.d(TAG, result);
            if (result.length() == 0) return;
            statusView.setText(result);
            if (isWahooFile) {
                final Object[] params = {trip_id};
                new RepairWahooFile().execute(params);
            }
            if (tripIds == null) {
                final Object[] params = {trip_id};
                new RepairImportedFile().execute(params);
            } else {
                for (int tripId: tripIds) {
                    final Object[] params = {tripId};
                    new RepairImportedFile().execute(params);
                }
            }
            fetch_rows(true);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

    }

    // Import API Keys from a file
    private class ImportKeys extends AsyncTask <Object, Integer, String> implements  ProgressListener {
        String filename = "";
        FileDialog fileDialog = null;

        @Override
        protected String doInBackground(Object...params) {
            while (fileDialog.isShowing()) {                  // wait for file selection
                try { Thread.sleep(1000); }
                catch (InterruptedException ie) {
                    Logger.e(TAG, "Interrupted sleep " + ie.getMessage(), localLog);
                }
            }
            if (filename.length() == 0) return "";      // no file was selected
            publishProgress(5);
            ImportFile importFile = new ImportFile(context, this, filename);
            int i = importFile.importKeys();
            if (i == 0)
                return "Finished importing keys";
            else
                return "Did not import keys";
        }

        @Override
        protected void onPreExecute() {
            File mPath = new File(Environment.getExternalStorageDirectory() + "//DIR//");
            String[] suffixes = {"json"};
            fileDialog = new FileDialog((Activity) context, mPath, suffixes);
            fileDialog.addFileListener(new FileDialog.FileSelectedListener() {
                public void fileSelected(File file) {
                    filename = file.toString();
                    fileDialog.setShowing(false);
                }
            });
            fileDialog.showDialog();
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Importing file " + progress[0] + "%";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            statusView.setText(result);
            fetch_rows(true);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

    }

    // Import the sample trip from a file
    private class ImportSampleTrip extends AsyncTask <Object, Integer, String> implements  ProgressListener {

        int trip_id;
        @Override
        protected String doInBackground(Object...params) {
            publishProgress(5);
            trip_id = new ImportFile(context, this, Constants.SAMPLE_GPX_FILE).importGPX();
            return "Finished importing sample file";
        }

        @Override
        protected void onPreExecute() {
            String status = "Started importing sample file...";
            statusView.setText(status);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Importing sample file " + progress[0] + "%";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.length() == 0) return;
            statusView.setText(result);
            selectedTrips = new ArrayList<>(Collections.singletonList(trip_id));
            fetch_rows(true);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }
    }


    // Repair an imported file
    private class RepairWahooFile extends AsyncTask <Object, Integer, String> implements  ProgressListener {

        int trip_id = 0;
        @Override
        protected String doInBackground(Object...params) {
            trip_id = (int) params[0];
            publishProgress(5);
            new RepairWahoo(context, this, trip_id).run();
            return "Finished fusing file";
        }

        @Override
        protected void onPreExecute() {
            String status = "Started fusing wahoo file...";
            statusView.setText(status);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Fusing wahoo file " + progress[0] + "%";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.length() > 0) {
                statusView.setText(result);
                selectTrips(new ArrayList<>(Collections.singletonList(trip_id)));
            }
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }
    }

    // Repair an imported file
    private class RepairImportedFile extends AsyncTask <Object, Integer, String> implements  ProgressListener {

        int trip_id = 0;
        @Override
        protected String doInBackground(Object...params) {
            trip_id = (int) params[0];
            publishProgress(5);
            // Parms - ProgressListener, trip id, imported, calc. segments
            new RepairTrip(context, this, trip_id, true, true).run();
            return "Finished repairing file";
        }

        @Override
        protected void onPreExecute() {
            String status = "Started repairing file...";
            statusView.setText(status);

        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Repairing file " + progress[0] + "%";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.length() > 0) {
                statusView.setText(result);
                selectTrips(new ArrayList<>(Collections.singletonList(trip_id)));
            }
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

    }

    // Delete the selected rows
    private class DeleteRows extends AsyncTask <Object, Integer, String>  implements  ProgressListener {
        @Override
        protected String doInBackground(Object...params) {
            // get the list of ids and delete the summary and associated details
            String msg = "";
            int deletedTrips = 0;
            int num_trips_to_delete = getSelectedIds().size();
            for (Integer id: getSelectedIds()) {
                deletedTrips += summaryTable.delSummary(context, id, true); // also delete details
                if (deletedTrips > 0) {
                    String plural = (deletedTrips > 1) ? "s" : "";
                    msg = deletedTrips + " trip" + plural + " deleted";
                    int percent = (int) (100.0 * (deletedTrips * 1.0 / num_trips_to_delete));
                    publishProgress(percent);
                }
                //deletedTrips = 0;
            }
            return msg;
        }

        @Override
        protected void onPostExecute(String result) {
            selectedTrips = new ArrayList<>();
            fetch_rows(false);
            statusView.setText(result);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Completed delete trips " + progress[0] + "%";
            statusView.setText(status);
        }

    }

    // Copy the trip
    private class CopyTrip extends AsyncTask <Object, Integer, String> {
        int new_trip_id, old_trip_id;
        @Override
        protected String doInBackground(Object...params) {
            // create a new summary from the old summary
            Context context = (Context) params[0];
            old_trip_id = (int) params[1];
            Summary old_summary = summaryTable.getSummary(context, old_trip_id);
            new_trip_id = summaryTable.addSummary(context, old_summary);

            // create the detail records for the new summary
            DetailDB detailTable = new DetailDB(DetailProvider.db);
            ArrayList<DetailProvider.Detail> details = detailTable.getDetails(context, old_trip_id);
            for (DetailProvider.Detail detail: details) {
                detail.setTrip_id(new_trip_id);
                detailTable.addDetail(context, detail);
            }
            return "Ended copy";
        }

        @Override
        protected void onPreExecute() {
            copyRunning = true;
        }

        @Override
        protected void onPostExecute(String result) {
            //selectedTrips = new ArrayList<>(Arrays.asList(old_trip_id, new_trip_id));
            selectedTrips = new ArrayList<>(Arrays.asList(new_trip_id));
            fetch_rows(true);
            statusView.setText(result);
            copyRunning = false;
        }
    }

    // Simulate the trip using the power from an existing trip
    private class SimulateTrip extends AsyncTask <Object, Integer, String> implements ProgressListener {
        int old_trip_id;
        int new_trip_id;
        @Override
        protected String doInBackground(Object...params) {

            // copy the selected trip
            Context context = (Context) params[0];
            old_trip_id = (int) params[1];
            Summary old_summary = summaryTable.getSummary(context, old_trip_id); // create a new summary from the old summary
            String category = old_summary.getCategory();
            new_trip_id = summaryTable.addSummary(context, old_summary);
            publishProgress(1);

            // create the detail records for the new summary with the new trip id
            DetailDB detailTable = new DetailDB(DetailProvider.db);
            ArrayList<DetailProvider.Detail> details = detailTable.getDetails(context, old_trip_id);
            for (DetailProvider.Detail detail: details) {
                detail.setTrip_id(new_trip_id);
                detailTable.addDetail(context, detail);
            }
            publishProgress(2);

            // get the weather for the new detail records
            /*
            GetWeather getWeather = new GetWeather(context, this, 25.0f, 50.0f);
            String msg = getWeather.updateDetails(new_trip_id);
            if (msg.equals(UtilsJSON.NO_RESPONSE))
                    return "Connection to OpenWeather unsuccessful: Check the Internet connection or API key";
            */
            // update the new details records with the new timings
            final double TOLERANCE = 1.0;
            boolean first = true;
            DetailProvider.Detail prev_detail = null;
            DetailProvider.Detail curr_detail = null;
            long prev_timestamp = 0L;  long current_timestamp = 0L; long delta_time = 0L;
            long first_timestamp = 0L; long last_timestamp = 0L;
            float progressBase = 75.0f;
            float progressFraction = 25.0f;
            int numDetails = details.size();
            for (int i = 0; i < numDetails; i++) {
                if (first) {
                    current_timestamp = System.currentTimeMillis();
                    if (!detailTable.setDetailTimestamp(context, current_timestamp, new_trip_id, 1))
                        Logger.d(TAG, "Could not set first detail timestamp due to sql error", localLog); // set the timestamp of the first detail
                    prev_timestamp = current_timestamp;
                    first_timestamp = current_timestamp;
                    prev_detail = detailTable.getDetail(context, new_trip_id, 1);
                    first = false;
                } else {
                    curr_detail = detailTable.getDetail(context, new_trip_id, i+1);
                    double distance = UtilsMap.getMeterDistance(curr_detail.getLat(), curr_detail.getLon(), prev_detail.getLat(), prev_detail.getLon());
                    double power = curr_detail.getPower();
                    if (category.equals(Constants.CAT_CYCLING)) {
                        double velocity = (power > 10) ? UtilsMap.get_bike_velocity(curr_detail, power, sharedPreferences): curr_detail.getSpeed();
                        velocity = kmphTomps(velocity);
                        delta_time = Math.round( (distance / velocity) * 1000);
                    } else {
                        delta_time = curr_detail.getTimestamp() - prev_detail.getTimestamp();
                    }
                    current_timestamp = prev_timestamp + delta_time;

                    if (!detailTable.setDetailTimestamp(context, current_timestamp, new_trip_id, i+1))
                        Logger.d(TAG, "Could not set " + curr_detail.getIndex() + " detail timestamp due to sql error", localLog);
                    prev_timestamp = current_timestamp;
                    last_timestamp = current_timestamp;
                    prev_detail = curr_detail;
                }

                float completedFrac = (i + 1.0f) / numDetails;
                publishProgress(Math.round(progressBase + progressFraction * completedFrac));
            }
            summaryTable.setSummaryStartTimestamp(context, first_timestamp, new_trip_id);
            summaryTable.setSummaryEndTimestamp(context, last_timestamp, new_trip_id);

            details = detailTable.getDetails(context, new_trip_id);

            return "Ended simulate";

        }

        @Override
        protected void onPreExecute() {
            String status = "Started simulation ...";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            selectedTrips.remove(Integer.valueOf(old_trip_id));
            selectedTrips.add(Integer.valueOf(new_trip_id));
            //selectTrips(selectedTrips);
            repair_rows(true, true);          // finally repair the new trip
            fetch_rows(true);
            statusView.setText(result);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Completed Simulation " + progress[0] + "%";
            statusView.setText(status);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

    }

    // Combine the selected rows (trips)
    private class CombineRows extends AsyncTask <Object, Integer, String> {
        ArrayList<Integer> trip_ids;
        @Override
        protected String doInBackground(Object...params) {
            Context context = (Context) params[0];
            trip_ids = getHighlightedRows(true);
            String msg = "";
            int newId = summaryTable.combineSummaries(context, trip_ids);
            if (newId > 1) msg = trip_ids.size() + " trips" + " combined";
            trip_ids.add(newId);
            return msg;
        }

        @Override
        protected void onPreExecute() {
            String status = "Started combining trips...";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            selectedTrips = trip_ids;
            fetch_rows(true);
            statusView.setText(result);
        }
    }

    // Repair the summary and details
    private class RepairRows extends AsyncTask <Object, Integer, String> implements ProgressListener {

        ArrayList<Integer> trips;
        boolean imported = false;
        boolean selected = false;
        int current_trip;
        @Override
        protected String doInBackground(Object...params) {

            Context context = (Context) params[0];
            if (params.length > 1) {
                imported = (boolean) params[1];
            }
            if (params.length > 2) {
                selected = (boolean) params[2];
            }

            // set the array of trips to repair
            if (selected) {
                trips = selectedTrips;
            } else {
                // get a list of all trip ids or selected trip id
                trips = (getSelectedIds().size() > 0) ?
                        getSelectedIds() : summaryTable.getSummaryIDs(context);
            }
            int progress = 0; int i = 0;
            for (Integer trip: trips) {
                while (!summaryTable.isTripFinished(context, trip) && i++ < Constants.TWO_MINUTES_SECONDS) {
                    sleep(1000L);
                }
                // Parms - null ProgressListener, trip id, imported flag, calc. segments
                current_trip = trip;
                new RepairTrip(context, this, trip, imported, true).run();
                progress++;
                publishProgress(Math.round(100.0f * progress / trips.size()), trip);
            }
            return "Finished repair";
        }

        @Override
        protected void onPostExecute(String result) {
            statusView.setText(result);
            selectTrips(trips);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Completed Trip " + current_trip + " repair " + progress[0] + "%";
            fetch_rows(true);
            statusView.setText(status);
        }

        @Override
        protected void onPreExecute() {
            String status = "Started repair ...";
            statusView.setText(status);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }
    }

    // Snap trip locations to the road locations using the Google API
    private class SnapRows extends AsyncTask <Object, Integer, String> implements ProgressListener {

        @Override
        protected String doInBackground(Object...params) {

            SnapToRoad snapToRoad = new SnapToRoad(context, this);
            for (Integer id: getSelectedIds()) {
                String msg = snapToRoad.updateDetails(id);
                if (msg.equals(UtilsJSON.NO_RESPONSE))
                    return "Connection to Google unsuccessful: Check the Internet connection or API Key";
            }
            return "Finished snap to road";
        }

        @Override
        protected void onPreExecute() {
            String status = "Started Google requests ...";
            statusView.setText(status);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            int done = progress[0]  / 2;
            String status = "Completed " + done + "% of Google requests:";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("Finished snap to road"))
                repair_rows(false, false);
            else statusView.setText(result);

        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }
    }

    // Set the altitude of locations using the Google API
    private class AltitudeRows extends AsyncTask <Object, Integer, String> implements ProgressListener {

        @Override
        protected String doInBackground(Object...params) {

            GetAltitude getAltitude = new GetAltitude(context, this);
            for (Integer id: getSelectedIds()) {
                String msg = getAltitude.updateDetails(id);
                if (msg.equals(UtilsJSON.NO_RESPONSE))
                    return "Connection to Google unsuccessful: Check the Internet connection or API key";
            }
            return "Finished get altitude";
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            //int done = 50 + progress[0]  / 2;
            String status = "Completed " + progress[0] + " % of Google requests:";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("Finished get altitude")) repair_rows(false, false);
            else statusView.setText(result);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }
    }

    // Set the weather data for locations using openweather
    private class WeatherRows extends AsyncTask <Object, Integer, String> implements ProgressListener {

        @Override
        protected String doInBackground(Object...params) {

            GetWeather getWeather = new GetWeather(context, this, 0.0f, 100.0f);
            for (Integer id: getSelectedIds()) {
                String msg = getWeather.updateDetails(id);
                if (msg.equals(UtilsJSON.NO_RESPONSE))
                    return "Connection to OpenWeather unsuccessful: Check the Internet connection or API key";
            }
            return "Finished weather update";
        }

        @Override
        protected void onPreExecute() {
            openWeatherRunning = true;
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Completed " + progress[0] + " % of OpenWeather requests:";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("Finished weather update")) repair_rows(false, false);
            else statusView.setText(result);
            openWeatherRunning = false;
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }
    }

    // return an array list of Summaries objects
    private ArrayList<Summary> getSelectedSummaries() {
        return getSelectedRows(false);
    }

    // return an array list of Summary ids
    private ArrayList<Integer> getSelectedIds() {
        return getSelectedRows(true);
    }

    /* Extract the ids of the selected rows
        1. First check if any rows were highlighted
        2. If not, use the date range to extract the corresponding rows.
        3. Return either a list of summaries or ids
    */
    @SuppressWarnings("unchecked")
    private <T> ArrayList<T> getSelectedRows(boolean getIDs) {
        // first get the highlighted rows
        ArrayList<T> selectedRows = getHighlightedRows(getIDs);
        if (!selectedRows.isEmpty())
            return selectedRows;

        // if none, get the list of summaries using the start and end dates
        final Object[] params = buildParams(false);
        ArrayList<Summary> summaries = summaryTable.getSummaries((Context) params[0], (String) params[1], (String) params[2], -1);
            for (Summary summary : summaries) {
                // skip a summary that is running
                if (summary.getStatus().equals(Constants.RUNNING)) continue;
                if (getIDs)
                    //selectedRows.add((T) new Integer(summary.getTrip_id()));
                    selectedRows.add((T) Integer.valueOf(summary.getTrip_id()));
                else
                    selectedRows.add((T) summary);
            }
        return selectedRows;
    }

    // get the ids or summaries of rows that have been selected
    @SuppressWarnings("unchecked")
    private <T> ArrayList<T> getHighlightedRows(boolean getIDs) {
        ArrayList<T> selectedRows = new ArrayList<>();
        // first check if any rows have been clicked on
        for (TableRow row: rows) {
            Drawable background = row.getBackground();
            if (background instanceof ColorDrawable) {
                int backColor = ((ColorDrawable) background).getColor();
                if (backColor == rowHighlightColor) continue;
                if (backColor == rowSelectColor) {
                    TextView tv = (TextView) row.getChildAt(0);  // get the trip id
                    Integer id = Integer.valueOf(tv.getText().toString());
                    //Log.d(TAG, "Selected " + id, localLog);
                    if (getIDs)
                        selectedRows.add((T) id);
                    else
                        selectedRows.add((T) summaryTable.getSummaries(this, "", "", id).get(0));
                }
            }
        }
        return selectedRows;
    }

    // build the list of table rows
    private void buildTableRows(ArrayList<Summary> summaries) {
        int row_count = 0;
        for (Summary summary : summaries) {
            addTableRow(getSummaryColumns(summary));
            if (++row_count >= NUM_ROWS) break; // limit the number of rows
        }
    }

    // build the string values of summary data
    private String[] getSummaryColumns(Summary summary) {
        String trip_id = String.format(Locale.getDefault(), "%03d", summary.getTrip_id());
        long start = summary.getStart_time();
        long end = summary.getEnd_time();
        if (summary.getStatus().equals(Constants.RUNNING))
            end = System.currentTimeMillis();

        String dateString = UtilsDate.getDateTime(start, summary.getAvg_lat(), summary.getAvg_lon());
        String duration = UtilsDate.getTimeDurationHHMMSS(end - start, false);
        float fDistance = (summary.getDistance() / 1000.0f);
        //float fSpeed = (float) UtilsMap.calcKmphSpeed(summary.getDistance(), end - start);
        float fSpeed = summary.getAvg_speed();
        String speedString = String.format(Locale.getDefault(), "%5.2f", fSpeed);
        String category = abbreviateCategory(summary.getCategory());
        String distanceString = String.format(Locale.getDefault(), "%5.2f", fDistance);
        String status = summary.getStatus();
        return new String[] {trip_id, dateString, category, duration, distanceString, speedString, status};
    }

    // create a new row using the passed column data
    private void addTableRow(String[] cols){
        final TableRow tr = (TableRow) inflater.inflate(R.layout.table_trip_row, tableLayout, false);
        tr.setClickable(true);
        final TextView summaryID = tr.findViewById(R.id.summaryID);
        final TextView summaryDate = tr.findViewById(R.id.summaryDate);
        final TextView summaryCategory = tr.findViewById(R.id.summaryCategory);
        final TextView summaryDuration = tr.findViewById(R.id.summaryDuration);
        final TextView summaryDistance = tr.findViewById(R.id.summaryDistance);
        final TextView summarySpeed = tr.findViewById(R.id.summarySpeed);

        // set the background color to indicate if the row was selected
        tr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Drawable background = summaryID.getBackground();
                //int backColor = getResources().getColor(R.color.row_background);
                int backColor = ContextCompat.getColor(context, R.color.row_background);
                if ( (background instanceof ColorDrawable) ) {
                    int currentBackColor = ((ColorDrawable) background).getColor();
                    if (currentBackColor == rowSelectColor)
                        backColor = rowBackColor;
                    else if (currentBackColor == rowHighlightColor)
                        backColor = rowHighlightColor;
                    else
                        backColor = rowSelectColor;
                }
                summaryID.setBackgroundColor(backColor);
                summaryDate.setBackgroundColor(backColor);
                summaryCategory.setBackgroundColor(backColor);
                summaryDuration.setBackgroundColor(backColor);
                summaryDistance.setBackgroundColor(backColor);
                summarySpeed.setBackgroundColor(backColor);
                tr.setBackgroundColor(backColor);
            }
        });

        // check the status of the row and set the back color
        int backColor = (cols[6].equals(Constants.RUNNING)) ? rowHighlightColor: rowBackColor;

        // set the background color and text of the table row
        summaryID.setText(cols[0]);         summaryID.setBackgroundColor(backColor);
        summaryDate.setText(cols[1]);       summaryDate.setBackgroundColor(backColor);
        summaryCategory.setText(cols[2]);   summaryCategory.setBackgroundColor(backColor);
        summaryDuration.setText(cols[3]);   summaryDuration.setBackgroundColor(backColor);
        summaryDistance.setText(cols[4]);   summaryDistance.setBackgroundColor(backColor);
        summarySpeed.setText(cols[5]);      summarySpeed.setBackgroundColor(backColor);
        summaryCategory.setTag(cols[0]);    // set the tag of the view
        rows.add(tr);                       // save the collection of rows
        tableLayout.addView(tr);
    }

    // select particular rows
    private void selectTrips(ArrayList<Integer> trips) {
        if (trips == null || trips.size() == 0) return;
        for (TableRow row: rows) {
            TextView tv = (TextView) row.getChildAt(0);  // get the trip id
            Integer id = Integer.valueOf(tv.getText().toString());
            //Log.d(TAG, " Selected row " + id, localLog);
            final TextView summaryID = row.findViewById(R.id.summaryID);
            final TextView summaryDate = row.findViewById(R.id.summaryDate);
            final TextView summaryCategory = row.findViewById(R.id.summaryCategory);
            final TextView summaryDuration = row.findViewById(R.id.summaryDuration);
            final TextView summaryDistance = row.findViewById(R.id.summaryDistance);
            final TextView summarySpeed = row.findViewById(R.id.summarySpeed);

            int backColor = (trips.contains(id)) ? rowSelectColor: rowBackColor;
            String[] cols = getSummaryColumns(summaryTable.getSummary(context, id));

            // set the background color and text of the table row
            summaryID.setText(cols[0]);         summaryID.setBackgroundColor(backColor);
            summaryDate.setText(cols[1]);       summaryDate.setBackgroundColor(backColor);
            summaryCategory.setText(cols[2]);   summaryCategory.setBackgroundColor(backColor);
            summaryDuration.setText(cols[3]);   summaryDuration.setBackgroundColor(backColor);
            summaryDistance.setText(cols[4]);   summaryDistance.setBackgroundColor(backColor);
            summarySpeed.setText(cols[5]);      summarySpeed.setBackgroundColor(backColor);
            row.setBackgroundColor(backColor);

        }
    }

    // return the where clause, handling all cases of startLocationFind and end dates
    // extend the start and end timestamp by a day to cover all possible timezones
    private String setWhereClause(String startText, String endText) {

        ArrayList<String> clauses = new ArrayList<>();

        // check if a category was passed from Calendar activity
        if (!category.equals(Constants.CAT_ALL))
            clauses.add(SummaryProvider.CATEGORY + " = \"" + category + "\" ");

        // Case 1: Both startLocationFind and end are non-blank
        if (!startText.isEmpty() && !endText.isEmpty() ) {
            if (UtilsDate.isDate(startText)) {
                //long startTimestamp = Constants.parseDate(startText).getTime();
                long startTimestamp = getTimestamp(startText) - Constants.MILLISECONDS_PER_DAY;
                long endTimestamp = (UtilsDate.isDate(endText)) ?
                        getTimestamp(endText) + Constants.MILLISECONDS_PER_DAY * 2 :
                        Long.MAX_VALUE;
                if (endTimestamp > startTimestamp) {
                    clauses.add(SummaryProvider.START_TIME + " >= " + startTimestamp);
                    clauses.add(SummaryProvider.END_TIME + " <= " + endTimestamp);
                } else
                    clauses.add(" 1 == 0 ");
            }
            return join(" and ", clauses);
        }

        // Case 2: Start is non-blank
        if (!startText.isEmpty()) {
            if (UtilsDate.isDate(startText)) {
                //long timeStamp = Constants.parseDate(startText).getTime();
                long startTimestamp = getTimestamp(startText) - Constants.MILLISECONDS_PER_DAY;
                clauses.add(SummaryProvider.START_TIME + " >= " + startTimestamp);
            }
            return  join(" and ", clauses);
        }

        // Case 3: End is non-blank
        if (!endText.isEmpty()) {
            if (UtilsDate.isDate(endText)) {
                long endTimestamp = getTimestamp(endText) + Constants.MILLISECONDS_PER_DAY * 2;
                clauses.add(SummaryProvider.END_TIME + " <= " + endTimestamp);
            }
            return  join(" and ", clauses);
        }

        // Case 4: All blank
        return join(" and ", clauses);
    }

    private String join(String operator, ArrayList<String> clauses) {
        StringBuilder whereClause = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            if (i+1 == clauses.size()) whereClause.append(clauses.get(i));
            else {
                whereClause.append(clauses.get(i));
                whereClause.append(operator);
            }

        }
        return whereClause.toString();
    }

    // return the sort order, handling all cases of startLocationFind and end dates
    private String setSortOrder(String startText, String endText, boolean sortByID) {
        String sortClause = "";

        if (sortByID) return SummaryProvider.TRIP_ID + " desc";

        // Case 1: Both startLocationFind and end are non-blank
        if ( !startText.isEmpty() && !endText.isEmpty() ) {
            if (UtilsDate.isDate(startText) && UtilsDate.isDate(endText)) {
                sortClause = SummaryProvider.START_TIME + " asc";
            }
            return sortClause;
        }

        // Case 2: Start is non-blank
        if (!startText.isEmpty()) {
            if (UtilsDate.isDate(startText)) {
                sortClause = SummaryProvider.START_TIME + " asc";
            }
            return sortClause;
        }

        // Case 3: End is non-blank
        if (!endText.isEmpty()) {
            if (UtilsDate.isDate(endText)) {
                sortClause = SummaryProvider.END_TIME + " desc";
            }
            return sortClause;
        }

        // Case 4: All blank
        //return (sortByID)? SummaryProvider.PREF_TRIP_ID + " desc": SummaryProvider.START_TIME + " desc";
        return SummaryProvider.START_TIME + " desc";
    }

    /* Checks if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state));
    }

    // given a string timestamp, return the equivalent long value
    private long getTimestamp(String dateText) {
        Date date = UtilsDate.parseDate(dateText);
        if (date != null) return date.getTime();
        return 0L;
    }

    // sort trips by id
    public void idOrder(View v) {
        fetch_rows(true);
    }

    // sort trips by date
    public void dateOrder(View v) {
        //selectedTrips = null;
        fetch_rows(false);
    }

    // update the category of the trip
    public void fixCategory(final View v) {
        if (((ColorDrawable)v.getBackground()).getColor() == rowHighlightColor) {
            v.setBackgroundColor(rowBackColor);
            return;     // return if a highlighted category
        }
        final int trip_id = Integer.parseInt((String) v.getTag());
        final Summary summary = summaryTable.getSummary(context, trip_id);
        final String category = summary.getCategory();
        final String[] categories = getOtherCategories(category);
        v.setBackgroundColor(rowHighlightColor);

        AlertDialog.Builder categoryDialog = new AlertDialog.Builder(context);
        categoryDialog.setTitle("Edit Category");
        categoryDialog.setIcon(R.drawable.icon_c);
        categoryDialog.setNegativeButton(categories[0],
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        summaryTable.setSummaryCategory(context, categories[0], trip_id);
                        dialog.cancel();
                        new RepairTrip(context, null, trip_id, false, true).run();
                        selectedTrips = new ArrayList<>(Collections.singletonList(trip_id));
                        fetch_rows(false);
                        v.setBackgroundColor(rowBackColor);
                    }
                });
        categoryDialog.setNeutralButton(categories[1],
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        summaryTable.setSummaryCategory(context, categories[1], trip_id);
                        dialog.cancel();
                        new RepairTrip(context, null, trip_id, false, true).run();
                        selectedTrips = new ArrayList<>(Collections.singletonList(trip_id));
                        fetch_rows(false);
                        v.setBackgroundColor(rowBackColor);
                    }
                });
        categoryDialog.setPositiveButton(categories[2],
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        summaryTable.setSummaryCategory(context, categories[2], trip_id);
                        dialog.cancel();
                        new RepairTrip(context, null, trip_id, false, true).run();
                        selectedTrips = new ArrayList<>(Collections.singletonList(trip_id));
                        fetch_rows(false);
                        v.setBackgroundColor(rowBackColor);
                    }
                });
        categoryDialog.show();
    }

    // build an array of strings for the other categories
    private String[] getOtherCategories(String category) {
        switch (category) {
            case Constants.CAT_WALKING: return new String[]{Constants.CAT_CYCLING, Constants.CAT_JOGGING, Constants.CAT_OTHER};
            case Constants.CAT_JOGGING: return new String[]{Constants.CAT_WALKING, Constants.CAT_CYCLING, Constants.CAT_OTHER};
            case Constants.CAT_CYCLING: return new String[]{Constants.CAT_WALKING, Constants.CAT_JOGGING, Constants.CAT_OTHER};
            default: return new String[] {Constants.CAT_CYCLING, Constants.CAT_WALKING, Constants.CAT_JOGGING};
        }
    }

    private String abbreviateCategory(String category) {
        switch (category) {
            case "Walking": return "Walk";
            case "Jogging": return "Jog";
            case "Cycling": return "Cycle";
            default: return "Other";
        }
    }

    // display the action bar
    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setLogo(R.drawable.icon);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setTitle(getResources().getString(R.string.trip_list));
        }
    }

    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        statusView.setText("");
    }

    private void sleep(long period) {
        try {
            Thread.sleep(period);
        } catch (InterruptedException ie) {
            //Log.d(TAG, "Could not sleep for " + period + " " + ie.getMessage(), localLog);
        }
    }


}