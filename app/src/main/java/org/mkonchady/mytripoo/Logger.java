package org.mkonchady.mytripoo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Logger {
    //static Context context = null;
    private static File file = null;
    private static String filename = "";


    public Logger(Context context)  {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));

        // return if debug mode is "no messages" or Android Log
        if (localLog != Integer.parseInt(Constants.DEBUG_LOCAL_FILE)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Constants.PREF_LOG_FILE, "");
            editor.apply();
            return;
        }

        // check if a log file was created earlier
        String logFile = sharedPreferences.getString(Constants.PREF_LOG_FILE, "");
        if (logFile.length() != 0)
            return;

        // ensure that the logs directory is available
        if (!externalStorageAvailable()) return;
        File logDirectory = new File(context.getExternalFilesDir(null), "logs");
        if (!logDirectory.exists())
            if (!logDirectory.mkdirs())
                return;

        // get a list of existing log files in the directory and clean up old files
        long DELETE_INTERVAL = Constants.MILLISECONDS_PER_DAY * 5; // for old log files
        for (File f :logDirectory.listFiles()) {
            if ( (f.isFile()) && ( (System.currentTimeMillis()-f.lastModified()) > DELETE_INTERVAL) )
                if (!f.delete())
                    android.util.Log.e("Log", "Could not delete old log files");
        }

        Date date = new Date() ;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        filename = "logs/" + dateFormat.format(date) + ".log";
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.PREF_LOG_FILE, filename);
        editor.apply();

        try {
            file = new File(context.getExternalFilesDir(null), filename);
            if (!file.exists())
                if (!file.createNewFile())
                    throw new IOException();
            FileOutputStream fos = new FileOutputStream(file, false);
            String logLine = UtilsDate.getDateTimeSec(System.currentTimeMillis(), Constants.LARGE_INT, Constants.LARGE_INT) + ": Debug: Log: " +
                    "------ Start of Log --------" + Constants.NEWLINE;
            fos.write(logLine.getBytes());
            fos.close();
        } catch (IOException ie) {
            android.util.Log.e("Log", "Open IO Exception: " + ie.getMessage());
        }
    }

    // Android Log functions
    public static void d(String TAG, String msg) {
        android.util.Log.d(TAG, msg);
    }

    public static void e(String TAG, String msg) {
        android.util.Log.e(TAG, msg);
    }

    public static void i(String TAG, String msg) {
        android.util.Log.e(TAG, msg);
    }

    // Local functions
    public static void d(String TAG, String msg, int local)  {
        if (local == 0) return;
        if (local == 1) append(TAG, msg, "Debug");
        else d(TAG, msg);
    }

    public static void e(String TAG, String msg, int local) {
        if (local == 0) return;
        if (local == 1) append(TAG, msg, "Error");
        else e(TAG, msg);
    }

    public static void i(String TAG, String msg, int local) {
        if (local == 0) return;
        if (local == 1) append(TAG, msg, "Info");
        else i(TAG, msg);
    }

    private static void append(String TAG, String msg, String type)  {
        if (!externalStorageAvailable())
            return;
        try {
            if (file == null) return;
            FileOutputStream fos = new FileOutputStream(file, true);
            String logLine = UtilsDate.getDateTimeSec(System.currentTimeMillis(), Constants.LARGE_INT, Constants.LARGE_INT) +
                    ": " + type + ": " + TAG + ": " + msg + Constants.NEWLINE;
            fos.write(logLine.getBytes());
            fos.close();
        } catch (IOException ie) {
            android.util.Log.e("Log", "Local IO Exception: " + ie.getMessage());
        }
    }

    private static boolean externalStorageAvailable() {
        return (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) );
    }

    public static void forceIndex(Context context) {
        if ( (context != null) && (filename.length() > 0) )
           UtilsFile.forceIndex(context, filename);
    }
}