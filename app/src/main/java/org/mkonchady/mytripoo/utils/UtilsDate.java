package org.mkonchady.mytripoo.utils;

//import android.support.annotation.NonNull;
import androidx.annotation.NonNull;


import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.mkonchady.mytripoo.Constants;

// Date utilities
public final class UtilsDate {

    final private static Locale locale = Locale.getDefault();
    final private static String dateFormat = "MMM dd, yyyy";
    final private static String dateTimeFormat = "MMM dd, HH:mm";
    final private static String dateTimeSecFormat = "MMM dd, yyyy HH:mm:ss";
    final private static String timeFormat = "HH:mm:ss";
    final private static String shortTimeFormat = "mm:ss";
    final private static SimpleDateFormat sdf_date_time = new SimpleDateFormat(dateTimeFormat, locale);
    final private static SimpleDateFormat sdf_date_time_sec = new SimpleDateFormat(dateTimeSecFormat, locale);
    final private static SimpleDateFormat sdf_date      = new SimpleDateFormat(dateFormat, locale);
    final private static SimpleDateFormat sdf_time      = new SimpleDateFormat(timeFormat, locale);
    final private static SimpleDateFormat sdf_short_time = new SimpleDateFormat(shortTimeFormat, locale);
    final private static SimpleDateFormat detailDateFormat =    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", locale);
    final private static SimpleDateFormat wahooDateFormat =     new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", locale);
    final private static SimpleDateFormat zuluDateFormat =     new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", locale);
    final private static SimpleDateFormat detailDateFormatGMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", locale);

    /**
     * no default constructor
     */
    private UtilsDate() {
        throw new AssertionError();
    }

    public static boolean isDate(String dateToValidate) {
        if(dateToValidate == null || dateToValidate.isEmpty()) return false;
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
        sdf.setLenient(false);
        try {
            sdf.parse(dateToValidate);
        } catch (ParseException e) {
            return false;
        }

        return true;
    }

    public static Date parseDate(@NonNull String date) {
        try {
            //return new SimpleDateFormat(dateFormat, Locale.getDefault()).parse(date);
            SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
            return sdf.parse(date);
        } catch (ParseException e) {
            return null;
        }
    }


    public static long getDetailTimeStamp(String text) {
        try {
            Date date = detailDateFormat.parse(text);
            return date.getTime();
        } catch (ParseException pe1) {
            detailDateFormatGMT.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
            try {
                Date date = detailDateFormatGMT.parse(text);
                return date.getTime();
            } catch (ParseException pe2) {
                return System.currentTimeMillis();
            }
        }
    }


    public static long getZuluTimeStamp(String text) {
        try {
            Date date = zuluDateFormat.parse(text);
            return date.getTime();
        } catch (ParseException pe1) {
            return System.currentTimeMillis();

        }
    }

    public static long getWahooTimeStamp(String text) {
        try {
            Date date = wahooDateFormat.parse(text);
            return date.getTime();
        } catch (ParseException pe1) {
            return System.currentTimeMillis();
        }
    }


    public static String getZuluDateTimeSec(long millis) {
        zuluDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return (zuluDateFormat.format(millis) + "Z");

    }

    public static String getDetailDateTimeSec(long millis, int lat, int lon) {
        detailDateFormat.setTimeZone(getTZ(lat, lon));
        return (detailDateFormat.format(millis));
    }

    public static String getDateTimeSec(long millis, int lat, int lon) {
        sdf_date_time_sec.setTimeZone(getTZ(lat, lon));

        return (sdf_date_time_sec.format(millis));
    }

    public static String getDateTime(long millis, int lat, int lon) {
        sdf_date_time.setTimeZone(getTZ(lat, lon));
        return (sdf_date_time.format(millis));
    }

    public static String getDate(long millis, int lat, int lon) {
        sdf_date.setTimeZone(getTZ(lat, lon));
        return (sdf_date.format(millis));
    }

    public static TimeZone getTZ(int lat, int lon) {
        return (lat == Constants.LARGE_INT)? TimeZone.getDefault():
                TimeZone.getTimeZone(TimezoneMapper.latLngToTimezoneString(lat / Constants.MILLION, lon / Constants.MILLION));
    }

    // pure time duration must be in GMT timezone
    public static String getTimeDurationHHMMSS(long milliseconds, boolean local) {
        return getTimeDurationHHMMSS(milliseconds, local, Constants.LARGE_INT, Constants.LARGE_INT);
    }

    public static String getTimeDurationHHMMSS(long milliseconds, boolean local, int lat, int lon) {
        // round up the milliseconds to the nearest second
        long millis = 1000 * ((milliseconds + 500) / 1000);

        // use the shorter format for less than an hour
        if (millis < Constants.MILLISECONDS_PER_MINUTE * 60) {
            if (local) sdf_short_time.setTimeZone(getTZ(lat, lon));
            else       sdf_short_time.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
            return (sdf_short_time.format(millis));
        }

        if (local) sdf_time.setTimeZone(getTZ(lat, lon));
        else       sdf_time.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
        return (sdf_time.format(millis));
    }

    public static String getDecimalFormat(Number n) {
        NumberFormat format = DecimalFormat.getInstance();
        format.setRoundingMode(RoundingMode.FLOOR);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        format.setMinimumIntegerDigits(2);
        return format.format(n);
    }

    // convert milliseconds to hours
    public static int getTimeHours(long millis) {
        int minutes = getTimeMinutes(millis);
        return Math.round(minutes / 60);
    }

    // convert milliseconds to minutes
    public static int getTimeMinutes(long millis) {
        int seconds = getTimeSeconds(millis);
        return Math.round(seconds / 60);
    }

    // convert milliseconds to seconds
    public static int getTimeSeconds(long millis) {
        double x = millis / 1000.0;
        return (int) Math.round(x);
    }

}
