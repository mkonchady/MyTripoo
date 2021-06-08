package org.mkonchady.mytripoo.activities;

import android.app.Activity;
import android.content.Intent;
import android.location.GnssStatus;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;

//import androidx.core.location.GnssStatusCompat;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.utils.UtilsMap;

// show a window with satellite details
public class SatelliteInfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.satellite_info);
        int i = GnssStatus.CONSTELLATION_BEIDOU;
        Intent intent = getIntent();
        String azimuth = intent.getFloatExtra(Constants.GPS_AZIMUTH, 0.0f) + "°";
        String elevation = intent.getFloatExtra(Constants.GPS_ELEVATION, 0.0f) + "°";
        String snr = intent.getFloatExtra(Constants.GPS_SNR, 0.0f) + "";
        String prn = getResources().getText(R.string.sat_prn) + " " + intent.getIntExtra(Constants.GPS_PRN, 0);
        String type = intent.getStringExtra(Constants.GPS_TYPE);
        String sat_type = getSatelliteType(type);

        // set the title
        final TextView satTitle = findViewById(R.id.satTextView00);
        satTitle.setText(prn);

        // set the signal
        final TextView satSignalTitle = findViewById(R.id.satTextView01a);
        satSignalTitle.setText(getResources().getText(R.string.sat_sig));
        final TextView satSignalValue = findViewById(R.id.satTextView01b);
        snr = snr + " (" + getSignalStrength(
                UtilsMap.setSatSignalStrength(intent.getFloatExtra(Constants.GPS_SNR, 0.0f))) + ")";
        satSignalValue.setText(snr);

        // set the country
        final TextView satCountryTitle = findViewById(R.id.satTextView02a);
        satCountryTitle.setText(getResources().getText(R.string.sat_type));
        final TextView satCountryValue = findViewById(R.id.satTextView02b);
        satCountryValue.setText(sat_type);

        // set the azimuth and elevation
        final TextView satAzimuthTitle = findViewById(R.id.satTextView03a);
        satAzimuthTitle.setText(getResources().getText(R.string.sat_azi));
        final TextView satAzimuthValue = findViewById(R.id.satTextView03b);
        satAzimuthValue.setText(azimuth);

        final TextView satElevationTitle = findViewById(R.id.satTextView04a);
        satElevationTitle.setText(getResources().getText(R.string.sat_ele));
        final TextView satElevationValue = findViewById(R.id.satTextView04b);
        satElevationValue.setText(elevation);

        final TextView satFlagTitle = findViewById(R.id.satTextView05a);
        satFlagTitle.setText(getResources().getText(R.string.sat_flag));
        final TextView satFlagValue = findViewById(R.id.satTextView05b);
        int itype = Integer.valueOf(type);
        satFlagValue.setText(UtilsMap.getSatelliteCountry(this, itype));
        //satFlagValue.setText(getResources().getText(R.string.unknown));
    }

    public String getSignalStrength(int signalStrength) {
        String strength = "Weak";
        switch (signalStrength) {
            case 1: strength = "Weak"; break;
            case 2: strength = "OK"; break;
            case 3: strength = "Medium"; break;
            case 4: strength = "Strong"; break;
        }
        return strength;
    }

    private String getSatelliteType(String type) {
        int itype = Integer.parseInt(type);
        switch (itype) {
            case GnssStatus.CONSTELLATION_BEIDOU: return "Beidou";
            case GnssStatus.CONSTELLATION_GALILEO: return "Galileo";
            case GnssStatus.CONSTELLATION_GLONASS: return "Glonass";
            case GnssStatus.CONSTELLATION_GPS: return "GPS";
            case GnssStatus.CONSTELLATION_IRNSS: return "IRNSS";
            case GnssStatus.CONSTELLATION_QZSS: return "QZSS";
            case GnssStatus.CONSTELLATION_SBAS: return "SBAS";
        }
        return "Unknown";
    }
}
