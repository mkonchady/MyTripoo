package org.mkonchady.mytripoo.gps;

import org.mkonchady.mytripoo.utils.UtilsMap;

public class Satellite {
    private float azimuth = 0.0f;
    float elevation = 0.0f;
    private float snr = 0.0f;               // signal to noise ratio
    private int prn = 0;                    // pseudo random number
    boolean used = false;
    int type = 0;               // GPS, Glonass, IRNSS, Galileo, Baidou

    private boolean validSatellite = false;
    private int signalStrength = 0;         // signal strength index
    public double x = 0;                   // cartesian coordinates
    public double y = 0;
    public double z = 0;

    @Override
    public String toString() {
        return "PRN: " + prn + " AZM: " + azimuth + " ALT: " + elevation + " SNR: " + snr +
                " x: " + x  + " y: " + y + " z: " + z;
    }

    public Satellite (float azimuth, float elevation, float snr, int prn, boolean used) {
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.snr = snr;
        this.prn = prn;
        this.used = used;
        setType(0);
        setSignalStrength();
    }

    public Satellite (float azimuth, float elevation, float snr, int prn, boolean used, int type) {
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.snr = snr;
        this.prn = prn;
        this.used = used;
        setType(type);
        setSignalStrength();
    }

    public float getAzimuth() {
        return azimuth;
    }

    public void setAzimuth(float azimuth) {
        this.azimuth = azimuth;
    }

    public float getElevation() {
        return elevation;
    }

    public void setElevation(float elevation) {
        this.elevation = elevation;
    }

    public float getSnr() {
        return snr;
    }

    public void setSnr(float snr) {
        this.snr = snr;
    }

    public int getPrn() {
        return prn;
    }

    public void setPrn(int prn) {
        this.prn = prn;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isValidSatellite() {
        return validSatellite;
    }

    /* return boolean if the satellite data is valid */
    public void setValidSatellite() {
        validSatellite = true;
        if ( (azimuth < 0) || (azimuth > 360) ) validSatellite = false;
        else if ( (elevation < 0) || (elevation > 90) ) validSatellite = false;
        else if ( (prn < 0) || (prn > 1000) ) validSatellite = false;
        else if ( (snr < 0) || (snr > 100)) validSatellite = false;
        else if ( (Float.compare(azimuth, 0.0f) == 0) && (Float.compare(elevation, 0.0f) == 0) )
            validSatellite = false;
    }

    public void setSignalStrength() {
        signalStrength = UtilsMap.setSatSignalStrength(snr);
    }
    public int getSignalStrength() {
        return signalStrength;
    }

}
