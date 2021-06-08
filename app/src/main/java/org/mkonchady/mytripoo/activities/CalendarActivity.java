package org.mkonchady.mytripoo.activities;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.database.SummaryDB;
import org.mkonchady.mytripoo.database.SummaryProvider;
import org.mkonchady.mytripoo.utils.UtilsDate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/*
  Build a calendar view and show the distances covered by week and by month
  for different categories
 */
public class CalendarActivity extends Activity  {

    // date variables
    int currentMonth, currentYear;
    GregorianCalendar calendar;
    SimpleDateFormat sdf;

    TableLayout tableLayout;
    public SummaryDB summaryTable = null;               // summary table
    int row_num = 0;
    public Context context;
    SparseArray<TextView> cells = new SparseArray<>(60);
    String category = Constants.CAT_ALL;
    int localLog;

    // Constants
    int UPPER_FORE_COLOR;
    int LOWER_FORE_COLOR;
    int BACK_COLOR;
    int HIGHLIGHT_BACK_COLOR;
    final int ROW_SIZE = 8;
    final int TEXT_SMALL = 1;
    final int TEXT_MEDIUM = 2;
    final String DASH = "-";
    final String UNDERSCORE = "_";
    final String BLANK = " ";
    final String TAG = "CalendarActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

        setContentView(R.layout.activity_calendar);
        summaryTable = new SummaryDB(SummaryProvider.db);

        UPPER_FORE_COLOR = ContextCompat.getColor(this, R.color.row_foreground);
        LOWER_FORE_COLOR = ContextCompat.getColor(this, R.color.DarkBlue);
        BACK_COLOR = ContextCompat.getColor(this, R.color.row_background);
        HIGHLIGHT_BACK_COLOR =  ContextCompat.getColor(this, R.color.row_highlight_background);

        calendar = new GregorianCalendar(Locale.getDefault());
        Date currentTime = new Date();
        calendar.setTime(currentTime);
        sdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());

        currentMonth = calendar.get(Calendar.MONTH);
        currentYear = calendar.get(Calendar.YEAR);
        tableLayout = findViewById(R.id.calendartablelayout);
        setActionBar();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.calendar_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_category_all:
                category = Constants.CAT_ALL; break;
            case R.id.action_category_cycling:
                category = Constants.CAT_CYCLING; break;
            case R.id.action_category_jogging:
                category = Constants.CAT_JOGGING; break;
            case R.id.action_category_walking:
                category = Constants.CAT_WALKING; break;
            default:
                return super.onOptionsItemSelected(item);
        }
        buildTable();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildTable();
    }

    // build the table for the calendar
    private void buildTable() {

        // clean out the old table, except for the title view -- the first row
        //SparseArray<TextView> cells = new SparseArray<>(60);
        int count = tableLayout.getChildCount();
        for (int i = 1; i < count; i++) {
            View child = tableLayout.getChildAt(i);
            if (child instanceof TableRow) ((ViewGroup) child).removeAllViews();
        }

        // set the title month and year and set the calendar
        calendar.set(currentYear, currentMonth, 1);
        final TextView tableTitle= findViewById(R.id.calendarTitle);
        tableTitle.setText(getTitle(currentMonth, currentYear));

        // build the top weekday row
        int head_backColor = ContextCompat.getColor(this, R.color.header_background);
        int head_foreColor = ContextCompat.getColor(this, R.color.header_foreground);
        String[] cols = new String[] {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Total"};
        row_num = 0;
        addTableRow(cols, head_backColor, head_foreColor, row_num++, TEXT_MEDIUM, true);

        // build the rest of the table with underscores for the days and dashes for the distances
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int numOfDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        cols = getInitCols(UNDERSCORE);
        int colIndex = dayOfWeek - 1;

        // fill in the rest of the days
        for (int i = 1; i <= numOfDays; i++) {
            cols[colIndex++] = i + "";
            if ((colIndex % 7) == 0) {
                addTableRow(cols, BACK_COLOR, UPPER_FORE_COLOR, row_num++, TEXT_MEDIUM, true);
                cols = getInitCols(DASH);
                addTableRow(cols, HIGHLIGHT_BACK_COLOR, LOWER_FORE_COLOR, row_num++, TEXT_SMALL, true);
                cols = getInitCols(UNDERSCORE);
                colIndex = 0;
            }
        }
        // fill in the last row of the calendar
        if (colIndex > 0) {
            addTableRow(cols, BACK_COLOR, UPPER_FORE_COLOR, row_num++, TEXT_MEDIUM, true);
            cols = getInitCols(DASH);
            addTableRow(cols, HIGHLIGHT_BACK_COLOR, LOWER_FORE_COLOR, row_num++, TEXT_SMALL, true);
        }

        // initialize the cells from an earlier month which are not displayed
        while (row_num < 12) {
            cols = getInitCols(UNDERSCORE);
            addTableRow(cols, BACK_COLOR, UPPER_FORE_COLOR, row_num++, TEXT_MEDIUM, false);
            cols = getInitCols(DASH);
            addTableRow(cols, HIGHLIGHT_BACK_COLOR, LOWER_FORE_COLOR, row_num++, TEXT_SMALL, false);
        }

        // fill in the distances in a separate thread
        final Object[] params = {this};
        new FetchCells().execute(params);
    }


    // Load the list of summaries asynchronously and then build the table rows
    private class FetchCells extends AsyncTask<Object, Integer, String> {

        float[] rowDistances = new float[6];    // for the 6 weekly totals
        Calendar localCalendar;

        @Override
        protected String doInBackground(Object...params) {
            return "";
        }

        @Override
        protected void onPostExecute(String result) {

            // check for limits Jan 1, 1970 and Jan 1, 2038
            if (currentYear <  1970 || currentYear > 2037) return;

            localCalendar = GregorianCalendar.getInstance();
            localCalendar.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));

            // clear the row distance totals
            for (int i = 0; i < 6; i++) rowDistances[i] = 0.0f;

            //for (Integer key : cells.keySet()   ) {
            final String dist_format = "%4.0f";
            for (int key = 0; key < cells.size(); key++) {
                TextView tv = cells.get(key);
                if (!tv.getText().equals(DASH)) continue;

                // check if the upper cell has a number
                int upperKey = key - 8;
                TextView upper_tv = cells.get(upperKey);
                String upper_text = (String) upper_tv.getText();
                if (upper_text.equals(UNDERSCORE) || upper_text.equals(BLANK)) continue;

                // get the current year, month, and date and build the start / end timestamps
                int currentDate = Integer.parseInt(upper_text);
                setLocalCalendar(currentDate, currentMonth, currentYear);

                // must be within the timestamp range with 1 day on either side for timezones
                // start and end timestamps are in UTC
                long utc_start_date_time = localCalendar.getTimeInMillis();
                long utc_end_date_time = utc_start_date_time + Constants.MILLISECONDS_PER_DAY;
                String whereClause = SummaryProvider.START_TIME + " >= " + (utc_start_date_time - Constants.MILLISECONDS_PER_DAY)
                         + " and " + SummaryProvider.END_TIME   + " <= " + (utc_end_date_time + Constants.MILLISECONDS_PER_DAY);
                if (!category.equals(Constants.CAT_ALL))
                    whereClause += " and " + SummaryProvider.CATEGORY + " = \"" + category + "\"";

                upper_tv.setClickable(true);
                ArrayList<SummaryProvider.Summary> all_summaries = summaryTable.getSummaries(context, whereClause, null, -1);
                ArrayList<SummaryProvider.Summary> summaries = new ArrayList<>();
                for (SummaryProvider.Summary summary : all_summaries) {
                    // find the timezone correction based on lat/lon of trip to make UTC time for start/end trip times
                    long start_time = summary.getStart_time(); long end_time = summary.getEnd_time();
                    int lat = summary.getAvg_lat(); int lon = summary.getAvg_lon();
                    TimeZone tz = UtilsDate.getTZ(lat, lon);
                    long utc_start_trip_time = start_time + tz.getOffset(System.currentTimeMillis());
                    long utc_end_trip_time = end_time + tz.getOffset(System.currentTimeMillis());
                    long multi_date_correction = (UtilsDate.getDate(start_time, lat, lon).equals(UtilsDate.getDate(end_time, lat, lon)))?
                                       0: Constants.MILLISECONDS_PER_DAY;  // check for a multi day trip
                    if ( (utc_start_trip_time >= utc_start_date_time) && (utc_end_trip_time <= utc_end_date_time + multi_date_correction) )
                        summaries.add(summary);
                }

                // check for multi-date trips that start on the current date
              //  String dateKey = sdf.format(localCalendar.getTimeInMillis());
              //  if (multiDateTrips.containsKey(dateKey)) {
              //      SummaryProvider.Summary summary = multiDateTrips.get(dateKey);
              //      if (category.equals(Constants.CAT_ALL))
              //          summaries.add(summary);
              //      else if (summary.getCategory().equals(category))
              //              summaries.add(summary);
              //  }

                // update the appropriate cell number with sum of the distances
                int rowNumber = getRow((Integer) tv.getTag());
                if (summaries.size() > 0) {
                    float distance = 0.0f;
                    for (SummaryProvider.Summary summary: summaries)
                        distance += (summary.getDistance() / 1000.0f);
                    rowDistances[rowNumber] += distance;
                    tv.setText(String.format(Locale.getDefault(), dist_format, distance));  // set the text of the cell
                    tv.setClickable(true); tv.setOnClickListener(getClickListener(utc_start_date_time));
                    upper_tv.setOnClickListener(getClickListener(utc_start_date_time));
                }
            }

            // set the distance sub totals in the weekly table cells
            int rowNumber = 0;
            for (int tag = (ROW_SIZE * 3) - 1; tag <= (ROW_SIZE * 12) - 1; tag += (ROW_SIZE * 2) ) {
                TextView tv = cells.get(tag);
                if (tv != null) {
                    String cellText =  String.format(Locale.getDefault(), dist_format, rowDistances[rowNumber++]);
                    tv.setText(cellText);
                }
            }

            // calculate the total distance for the month
            String[] cols = getInitCols(BLANK);
            addTableRow(cols, BACK_COLOR, LOWER_FORE_COLOR, row_num++, TEXT_MEDIUM, true);
            float totalDistance = 0.0f;
            for (float distance: rowDistances)
                totalDistance += distance;
            cols = getInitCols(DASH);

            cols[ROW_SIZE - 3] = "Month";
            cols[ROW_SIZE - 2] = "Total";
            cols[ROW_SIZE - 1] = String.format(Locale.getDefault(), dist_format, totalDistance);

            // calculate the total distance for the year
            setLocalCalendar(1, 0, currentYear);
            long utc_start_date_time = localCalendar.getTimeInMillis();
            setLocalCalendar(1, currentMonth, currentYear);
            long utc_end_date_time = localCalendar.getTimeInMillis();
            int number_of_days = localCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            utc_end_date_time += number_of_days * Constants.MILLISECONDS_PER_DAY;
            String whereClause = SummaryProvider.START_TIME + " >= " + utc_start_date_time
                    + " and " + SummaryProvider.END_TIME   + " <= " + utc_end_date_time;
            if (!category.equals(Constants.CAT_ALL))
                whereClause += " and " + SummaryProvider.CATEGORY + " = \"" + category + "\"";
            ArrayList<SummaryProvider.Summary> all_summaries = summaryTable.getSummaries(context, whereClause, null, -1);
            float distance = 0.0f;
            for (SummaryProvider.Summary summary : all_summaries) {
                distance += (summary.getDistance() / 1000.0f);
            }
            cols[ROW_SIZE - 8] = "Year";
            cols[ROW_SIZE - 7] = "Total";
            cols[ROW_SIZE - 6] = String.format(Locale.getDefault(), dist_format, distance);

            addTableRow(cols, HIGHLIGHT_BACK_COLOR, LOWER_FORE_COLOR, row_num++, TEXT_SMALL, true);

        }

        private void setLocalCalendar(int currentDate, int currentMonth, int currentYear) {
            localCalendar.set(GregorianCalendar.DAY_OF_MONTH, currentDate);
            localCalendar.set(GregorianCalendar.MONTH,currentMonth);
            localCalendar.set(GregorianCalendar.YEAR, currentYear);
            localCalendar.set(GregorianCalendar.HOUR_OF_DAY, 0);
            localCalendar.set(GregorianCalendar.MINUTE, 0);
            localCalendar.set(GregorianCalendar.SECOND, 0);
            localCalendar.set(GregorianCalendar.MILLISECOND, 0);
        }
    }

    // create a new row using the passed column data
    private void addTableRow(String[] cols, int backColor, int foreColor, final int row_num, int fontSize, boolean display ) {
        final TableRow tr = (TableRow) getLayoutInflater().inflate(R.layout.table_calendar_row, tableLayout, false);

        //final TableRow tr = (TableRow) inflater.inflate(R.layout.table_calendar_row, tableLayout, false);
        tr.setBackgroundColor(backColor);

        // set the text size
        int textSize = (fontSize == TEXT_SMALL)?  android.R.style.TextAppearance_DeviceDefault_Small:
                (fontSize == TEXT_MEDIUM)? android.R.style.TextAppearance_DeviceDefault_Medium:
                        android.R.style.TextAppearance_DeviceDefault_Large;
        // create an unique tag number for the cell
        int tag_num = row_num * ROW_SIZE;

        final TextView cell1 = tr.findViewById(R.id.calendar_cell_1);
        setTextSize(this, cell1, textSize);
        cell1.setTag(tag_num);
        cell1.setText(cols[0]);  cell1.setTextColor(foreColor);
        cells.put(tag_num++, cell1);

        final TextView cell2 = tr.findViewById(R.id.calendar_cell_2);
        setTextSize(this, cell2, textSize);
        cell2.setText(cols[1]);  cell2.setTextColor(foreColor);
        cell2.setTag(tag_num);
        cells.put(tag_num++, cell2);

        final TextView cell3 = tr.findViewById(R.id.calendar_cell_3);
        setTextSize(this, cell3, textSize);
        cell3.setTag(tag_num);
        cell3.setText(cols[2]);cell3.setTextColor(foreColor);
        cells.put(tag_num++, cell3);

        final TextView cell4 = tr.findViewById(R.id.calendar_cell_4);
        setTextSize(this, cell4, textSize);
        cell4.setTag(tag_num);
        cell4.setText(cols[3]); cell4.setTextColor(foreColor);
        cells.put(tag_num++, cell4);

        final TextView cell5 = tr.findViewById(R.id.calendar_cell_5);
        setTextSize(this, cell5, textSize);
        cell5.setTag(tag_num);
        cell5.setText(cols[4]); cell5.setTextColor(foreColor);
        cells.put(tag_num++, cell5);

        final TextView cell6 = tr.findViewById(R.id.calendar_cell_6);
        setTextSize(this, cell6, textSize);
        cell6.setTag(tag_num);
        cell6.setText(cols[5]);cell6.setTextColor(foreColor);
        cells.put(tag_num++, cell6);

        final TextView cell7 = tr.findViewById(R.id.calendar_cell_7);
        setTextSize(this, cell7, textSize);
        cell7.setTag(tag_num);
        cell7.setText(cols[6]); cell7.setTextColor(foreColor);
        cells.put(tag_num++, cell7);

        final TextView cell8 = tr.findViewById(R.id.calendar_cell_8);
        setTextSize(this, cell8, textSize);
        cell8.setTag(tag_num);
        cell8.setText(cols[7]); cell8.setTextColor(foreColor);
        cells.put(tag_num, cell8);

        if (display) tableLayout.addView(tr);

    }


    @TargetApi(23)
    public void setTextSize(Context context, TextView tv, int resId) {
        if (Constants.preMarshmallow) tv.setTextAppearance(context, resId);
        else tv.setTextAppearance(resId);
    }

    private String getTitle(int month, int year) {
        String title;
        switch (month) {
            case 0: title = "January " + year; break;
            case 1: title = "February " + year; break;
            case 2: title = "March " + year; break;
            case 3: title = "April " + year; break;
            case 4: title = "May " + year; break;
            case 5: title = "June " + year; break;
            case 6: title = "July " + year; break;
            case 7: title = "August " + year; break;
            case 8: title = "September " + year; break;
            case 9: title = "October " + year; break;
            case 10: title = "November "  + year; break;
            default: title = "December "  + year;
        }
        return title;
    }

    private String[] getInitCols(String c) {
        return new String []{c, c, c, c, c, c, c, c};
    }

    public void upArrow(View v) {
        currentYear++;
        buildTable();

    }
    public void downArrow(View v) {
        currentYear--;
        buildTable();
    }

    public void rightArrow(View v) {
        currentMonth++;
        if (currentMonth >= 12) {
            currentMonth = 0;
            currentYear++;
        }
        buildTable();
    }

    public void leftArrow(View v) {
        currentMonth--;
        if (currentMonth < 0) {
            currentMonth = 11;
            currentYear--;
        }
        buildTable();
    }

    // get the row number in the table given the tag
    // the first row with the weekdays is excluded
    // tag 0 ..    6, 7     Title
    // tag 8 ..   14,15     Days of the month
    // tag 16 .. 22, 23     Distances
    //  . . .
    private int getRow(int cell) {
        int rowNum = 0;
        for (int i = 23; i <= 103; i += 16) {
            if (cell < i) return  rowNum;
            rowNum++;
        }
        return rowNum;
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
            actionBar.setTitle(getResources().getString(R.string.calendar));
        }
    }

    private View.OnClickListener getClickListener(final long timestamp) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {    // start the trip activity with the passed timestamp as start date
                Intent intent = new Intent(CalendarActivity.this, TripActivity.class);
                intent.putExtra(Constants.TRIP_FROM_DATE, timestamp);
                intent.putExtra(Constants.TRIP_CATEGORY, category);
                startActivity(intent);
            }
        };
    }
}
