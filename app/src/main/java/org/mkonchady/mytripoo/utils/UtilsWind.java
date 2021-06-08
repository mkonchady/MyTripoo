package org.mkonchady.mytripoo.utils;

import static java.lang.Math.abs;

public final class UtilsWind {

    /**
     * no default constructor
     */
    private UtilsWind() {
        throw new AssertionError();
    }

    //
    // calculate head wind from bike velocity headed in 0 degree, wind velocity from wind_d degrees
    // wind_mag and wind_deg are the magnitude and direction of origin of the wind
    // bike_mag and bike_deg are the magnitude and direction of the bicycle travel

    // convert double to int
    private static int toInt(double d) {
        return (int) Math.round(d);
    }
    //convert clockwise bearing (0 as N ) to counterclockwise degrees (0 as E)
    private static int bearingToDegrees(int bearing) {
        return (450 - bearing) % 360;
    }

    //convert counterclockwise degrees (0 as E) to clockwise bearing (0 as N )
    private static int degreesToBearings(int degrees) {
        return(450-degrees) % 360;
    }

    // return the degree in the opposite direction
    private static int flip_direction(int angle) {
        return (((angle + 360) - 180) % 360);
    }

    // get  the angle between two vectors in degrees
    private static int get_angle(double v1_x, double v1_y, double v2_x, double v2_y) {
        double angle = Math.toDegrees(Math.atan2(v2_y, v2_x) - Math.atan2(v1_y, v1_x));
        return (int) Math.round(angle);
    }

    // get the dot product of two vectors
    private static double get_dot_product(double v1_x, double v1_y, double v2_x, double v2_y) {
        return v1_x * v2_x + v1_y * v2_y;
    }

    // get the magnitude of a vector
    private static double get_magnitude(double v_x, double v_y) {
        return Math.sqrt(v_x * v_x + v_y * v_y);
    }

    //*---------------------------------------------------------------------------------------------
    // Calculate the relative wind velocity given bike and wind velocities.
    // v_app = v_wind - v_bike
    //  Accept v_wind: wind mag, wind deg and v_bike: bike mag, and bike deg
    //  degrees must be in x,y coordinate system, i.e. North 0 bearing is 90 degrees
    //  1. v_wind is the velocity of wind originating at the bearing
    //  2. v_bike is the velocity of bike heading in the direction of the bearing.
    //
    // compute v_app x and v_app_y components and the direction
    // wind magnitude is from origin and therefore direction is flipped to represent a drag force
    // bike magnitude is in the direction of flow and therefore direction is flipped
    // positive direction is head wind and negative direction is tail wind
    // wind and bike mag in meters per second
    //*-----------------------------------------------------------------------------------------------
    public static double[] get_apparent(double wind_mag, double wind_deg, double bike_mag, double bike_deg) {
        double wind_x = wind_mag * Math.cos(Math.toRadians(flip_direction(toInt(wind_deg))));
        double bike_x = bike_mag * Math.cos(Math.toRadians(bike_deg));
        double minus_bike_x = bike_mag * Math.cos(Math.toRadians(flip_direction(toInt(bike_deg))));

        double wind_y = wind_mag * Math.sin(Math.toRadians(flip_direction(toInt(wind_deg))));
        double bike_y = bike_mag * Math.sin(Math.toRadians(bike_deg));
        double minus_bike_y = bike_mag * Math.sin(Math.toRadians(flip_direction(toInt(bike_deg))));

        double apparent_y = wind_y + minus_bike_y;
        double apparent_x = wind_x + minus_bike_x;

        double angle = abs(get_angle(bike_x, bike_y, apparent_x, apparent_y));
        int direction = 1;  // head wind
        if (angle < 90)
            direction = -1;  //tail wind
        else if(angle == 90)
            direction = 0;   //cross wind
        double[] result = {apparent_x,apparent_y, direction, angle};
        return result;
    }

    // the head wind is the apparent wind projected on the bike vector in m/s
    public static double get_head_wind(double wind_mag, double wind_deg, double bike_mag, double bike_deg) {
        double[] apparent = get_apparent(wind_mag, wind_deg, bike_mag, bike_deg);
        double apparent_x = apparent[0];
        double apparent_y = apparent[1];
        double direction = apparent[2];

        double bike_x = bike_mag * Math.cos(Math.toRadians(bike_deg));
        double bike_y = bike_mag * Math.sin(Math.toRadians(bike_deg));

        double dot_product = get_dot_product(apparent_x, apparent_y, bike_x, bike_y);
        double mag = get_magnitude(bike_x, bike_y);
        double apparent_proj = abs(dot_product / mag);
        return apparent_proj * direction;
    }

    // return the head wind squared projected on the bike vector Wind factor (W_a) m^2 / s^2
    // wind and bike mag in meters per second
    public static double get_head_wind2(double wind_mag, double wind_deg, double bike_mag, double bike_deg) {
        double[] apparent = get_apparent(wind_mag, wind_deg, bike_mag, bike_deg);
        double apparent_x = apparent[0];
        double apparent_y = apparent[1];
        double direction = apparent[2];

        double bike_x = bike_mag * Math.cos(Math.toRadians(bike_deg));
        double bike_y = bike_mag * Math.sin(Math.toRadians(bike_deg));
        double dot_product = get_dot_product(apparent_x, apparent_y, bike_x, bike_y);
        double mag = get_magnitude(bike_x, bike_y);
        double apparent_proj = abs(dot_product / mag);
        double angle = abs(get_angle(bike_x, bike_y, apparent_x, apparent_y));
        // square of projection and the angle
        double apparent_proj2 = abs(apparent_proj * apparent_proj * Math.cos(Math.toRadians(angle)));
        return apparent_proj2 * direction;
    }

    // use Newton's method to calculate the speed in kmph.
    // wind speed in given in kmph.
    public static double get_velocity(double K_A, double bike_degree, double wind_speed, double wind_degree, double power, double OTHER_RES) {
        double EFFICIENCY = 0.95;
        double bike_speed = 1000;
        int MAX_ITERATIONS = 100;
        double TOLERANCE = 0.05;
        if (wind_speed > 28) TOLERANCE = 0.02 * wind_speed;
        wind_speed = UtilsMap.kmphTomps(wind_speed);
        for (int i =0; i < MAX_ITERATIONS; i++) {
            double W_A = get_head_wind2(wind_speed, wind_degree, bike_speed, bike_degree);
            double head_mag = get_head_wind(wind_speed, wind_degree, bike_speed, bike_degree);
            if (W_A < 0)
                K_A = -K_A;
            double f = bike_speed * (K_A * W_A + OTHER_RES) - EFFICIENCY * power;
            double fprime = K_A * (3.0 * bike_speed + wind_speed) * head_mag + OTHER_RES;
            //fprime = 3 * K_A * bike_speed * bike_speed + K_A * head_mag * head_mag + 4 * K_A * bike_speed * head_mag + OTHER_RES;
            double new_bike_speed = bike_speed - (f / fprime);
            if (abs(new_bike_speed - bike_speed) < TOLERANCE)
                return UtilsMap.mpsTokmph(new_bike_speed);
            bike_speed = new_bike_speed;
        }
        return 0.0;
    }

    // calculate the air resistance
    public static double calc_air_res(double K_A, double v_bike, double v_wind, double wind_d, double bike_d) {
        v_bike = UtilsMap.kmphTomps(v_bike); // convert to m/sec
        v_wind = UtilsMap.kmphTomps(v_wind);
        return K_A * UtilsWind.get_head_wind2(v_wind, (int) Math.round(wind_d),v_bike, bike_d);
    }

    public static double calc_power(double total_res, double v_bike) {
        double EFFICIENCY = 0.95;
        return (UtilsMap.kmphTomps(v_bike) * total_res) / EFFICIENCY;
    }
}

