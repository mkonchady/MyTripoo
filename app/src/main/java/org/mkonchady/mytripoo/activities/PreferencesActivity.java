package org.mkonchady.mytripoo.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import android.view.MenuItem;
import android.widget.Toast;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;

import java.util.List;

// Modified from Sample preferences activity
/*
  a. The pref_headers.xml file contains the list of fragments for preferences
  b. Each fragment is a static class below that adds preferences from a separate xml file
  c. The findPreference function finds preferences with the passed name (contained in Constants.java)
  d. This name must match the android:key in the separate xml files
  e. The name of the android:title must match a value in the strings.xml file
  f. The names of the arrays and values must match in the strings.xml file
 */
public class PreferencesActivity extends AppCompatPreferenceActivity {

    private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                preference.setSummary(index >= 0? listPreference.getEntries()[index] : null);
            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || SMSPreferenceFragment.class.getName().equals(fragmentName)
                || GPSPreferenceFragment.class.getName().equals(fragmentName)
                || BikePreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_WALKING_SPEED));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_DEBUG_MODE));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_TRIP_TYPE));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_PLOT_SPEED));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_NUMBER_TRIP_ROWS));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_NUMBER_TRIP_READINGS));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_BEEP_PERIOD));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_AUTOSTOP));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), PreferencesActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GPSPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_gps);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_LOCATION_PROVIDER));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_LOCATION_ACCURACY));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_POLL_INTERVAL));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_FILE_FORMAT));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_GOOGLE_API_KEY));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_OPENWEATHER_API_KEY));

        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), PreferencesActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SMSPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_sms);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_SMS_SEND));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_SMS_PERIOD));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_SMS_NUMBER));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), PreferencesActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class BikePreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_bike);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_BIKE_RIDER));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_BIKE_BIKE));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_BIKE_FRONT));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_BIKE_ROLLING));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_BIKE_DRAG));
            bindPreferenceSummaryToValue(findPreference(Constants.PREF_BIKE_COMMIT));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), PreferencesActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected  void onPause() {
        super.onPause();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String send_sms = sharedPreferences.getString(Constants.PREF_SMS_SEND, "no");
        if (send_sms.equals("yes") ) {
            boolean smsPermissionGranted = Constants.preMarshmallow;
            if (Constants.postMarshmallow && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED)
                smsPermissionGranted = true;

            if (!smsPermissionGranted) {
                //Log.d(TAG, "Getting GPS Permission", localLog);
                Intent intent = new Intent(getBaseContext(), PermissionActivity.class);
                intent.putExtra("permission", Manifest.permission.SEND_SMS);
                startActivityForResult(intent, Constants.PERMISSION_CODE);
            }
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //Log.d(TAG, "Returned from Permission Activity", localLog);
        if (requestCode == Constants.PERMISSION_CODE) {
            String granted = data.getStringExtra("granted");
            //Log.d(TAG, "GPS permission Granted: " + granted);
            if (!granted.equals("true")) {
                String result = "Cannot send SMS without Permission";
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            }
        }

    }
}
