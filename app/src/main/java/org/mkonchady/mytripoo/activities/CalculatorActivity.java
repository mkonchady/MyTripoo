package org.mkonchady.mytripoo.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;

import android.text.TextWatcher;
import android.view.LayoutInflater;

import android.widget.EditText;
import android.widget.TextView;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;
import org.mkonchady.mytripoo.utils.UtilsWind;

public class CalculatorActivity extends Activity {

    LayoutInflater inflater = null;
    EditText speedText, powerText, gradientText, wind_speedText, wind_degreeText,
            tempText, elevationText, bike_degreeText;
    TextView statusText;
    int localLog;
    double rider_wt, bike_wt, front_area, c_r, c_d, m;
    boolean speed_auto_set = false;
    boolean power_auto_set = false;

    final int MAX_POWER = 100000;
    final int MAX_SPEED = 100000;
    final int MIN_GRADIENT = -50;
    final int MAX_GRADIENT = 50;
    final int MAX_WIND = 100;
    final int MAX_DEGREE = 360;
    final int MAX_TEMP = 100;
    final int MIN_TEMP = -100;
    final int MAX_ELEVATION = 20000;
    final int ZERO_VAL = 0;

    Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inflater = getLayoutInflater();
        context = this;

        // get the bike fields from the preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        rider_wt = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_RIDER, "70.0"));
        bike_wt = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_BIKE, "12.0"));
        front_area = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_FRONT, "0.5"));
        c_r = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_ROLLING, "0.005"));
        c_d = Double.parseDouble(sharedPreferences.getString(Constants.PREF_BIKE_DRAG, "1.15"));
        m = (rider_wt + bike_wt);

        setContentView(R.layout.activity_calculator);
        statusText = this.findViewById(R.id.calc_textView9);

        speedText = this.findViewById(R.id.calc_editText1);
        speedText.addTextChangedListener(new TextValidator(speedText) {
            @Override
            public void validate(TextView textView, String text) {
                if (isValid(text, ZERO_VAL, MAX_SPEED)) {
                    if (!speed_auto_set) {
                        power_auto_set = true;
                        String power = UtilsMisc.formatDouble(calcPower(),1);
                        powerText.setText(power);
                    }
                    speed_auto_set = false;
                    statusText.setText("");
                } else
                    statusText.setText("Speed must be between " + ZERO_VAL + " and " + MAX_SPEED);
            }
        });

        powerText = this.findViewById(R.id.calc_editText2);
        powerText.addTextChangedListener(new TextValidator(powerText) {
            @Override
            public void validate(TextView textView, String text) {
                if (isValid(text, ZERO_VAL, MAX_POWER)) {
                    if (!power_auto_set) {
                        speed_auto_set = true;
                        String speed = UtilsMisc.formatDouble(calcSpeed(),1);
                        speedText.setText(speed);
                    }
                    power_auto_set = false;
                    statusText.setText("");
                } else
                    statusText.setText("Power must be between " + ZERO_VAL + " and " + MAX_POWER);
            }
        });

        gradientText = this.findViewById(R.id.calc_editText3);
        gradientText.addTextChangedListener(new TextValidator(gradientText) {
            @Override
            public void validate(TextView textView, String text) {
                if (isValid(text, MIN_GRADIENT,  MAX_GRADIENT)) {
                    speed_auto_set = true;
                    String speed = UtilsMisc.formatDouble(calcSpeed(),1);
                    speedText.setText(speed);
                    statusText.setText("");
                } else
                    statusText.setText("Gradient must be between " + MIN_GRADIENT + " and " + MAX_GRADIENT);
            }
        });

        wind_speedText = this.findViewById(R.id.calc_editText4);
        wind_speedText.addTextChangedListener(new TextValidator(wind_speedText) {
            @Override
            public void validate(TextView textView, String text) {
                if (isValid(text, ZERO_VAL, MAX_WIND)) {
                    speed_auto_set = true;
                    String speed = UtilsMisc.formatDouble(calcSpeed(),1);
                    speedText.setText(speed);
                    statusText.setText("");
                } else
                    statusText.setText("Wind speed must be between " + ZERO_VAL + " and " + MAX_WIND);
            }
        });
        wind_degreeText = this.findViewById(R.id.calc_editText5);
        wind_degreeText.addTextChangedListener(new TextValidator(wind_degreeText) {
            @Override
            public void validate(TextView textView, String text) {
                if (isValid(text, ZERO_VAL, MAX_DEGREE)) {
                    speed_auto_set = true;
                    String speed = UtilsMisc.formatDouble(calcSpeed(),1);
                    speedText.setText(speed);
                    statusText.setText("");
                } else
                    statusText.setText("Wind degree must be between " + ZERO_VAL + " and " + MAX_DEGREE);
            }
        });
        tempText = this.findViewById(R.id.calc_editText6);
        tempText.addTextChangedListener(new TextValidator(tempText) {
            @Override
            public void validate(TextView textView, String text) {
                if (isValid(text,  MIN_TEMP,  MAX_TEMP)) {
                    speed_auto_set = true;
                    String speed = UtilsMisc.formatDouble(calcSpeed(),1);
                    speedText.setText(speed);
                    statusText.setText("");
                } else
                    statusText.setText("Temperature must be between " + MIN_TEMP + " and " + MAX_TEMP);
            }
        });
        elevationText = this.findViewById(R.id.calc_editText7);
        elevationText.addTextChangedListener(new TextValidator(elevationText) {
            @Override
            public void validate(TextView textView, String text) {
                if (isValid(text, ZERO_VAL, MAX_ELEVATION)) {
                    speed_auto_set = true;
                    String speed = UtilsMisc.formatDouble(calcSpeed(),1);
                    speedText.setText(speed);
                    statusText.setText("");
                } else
                    statusText.setText("Elevation should be between " + ZERO_VAL + " and " + MAX_ELEVATION);
            }
        });
        bike_degreeText = this.findViewById(R.id.calc_editText8);
        bike_degreeText.addTextChangedListener(new TextValidator(bike_degreeText) {
            @Override
            public void validate(TextView textView, String text) {
                if (isValid(text, ZERO_VAL, MAX_DEGREE)) {
                    speed_auto_set = true;
                    String speed = UtilsMisc.formatDouble(calcSpeed(),1);
                    speedText.setText(speed);
                    statusText.setText("");
                } else
                    statusText.setText("Bike degree should be between " + ZERO_VAL + " and " + MAX_DEGREE);
            }
        });

        setActionBar();
    }

    // calculate the power for the given fields
    public double calcPower() {
        double EFFICIENCY = 0.95;
        double speed = Double.parseDouble(speedText.getText().toString());
        double gradient = Double.parseDouble(gradientText.getText().toString());
        double wind_speed = Double.parseDouble(wind_speedText.getText().toString());
        double wind_degree = Double.parseDouble(wind_degreeText.getText().toString());
        double TEMP = Double.parseDouble(tempText.getText().toString());
        double ELEVATION = Double.parseDouble(elevationText.getText().toString());
        double bike_degree = Double.parseDouble(bike_degreeText.getText().toString());

        double TOTAL_WT = (rider_wt + bike_wt) * 9.8; // Newtons
        double GRADE_RES = (gradient / 100.0) * TOTAL_WT; // calculate grade resistance
        double ROLLING_RES = c_r * TOTAL_WT;  // calculate rolling resistance

        // calculate K_A: aerodynamic drag factor
        double DENSITY = (1.293 - 0.00426 * TEMP) * Math.exp(-ELEVATION / 7000.0);
        double C_D_AREA = c_d * front_area;
        double K_A = 0.5 * C_D_AREA * DENSITY;

        double AIR_RES = UtilsWind.calc_air_res(K_A, speed, wind_speed, wind_degree, bike_degree); // calculate air drag
        double TOTAL_RES = AIR_RES + GRADE_RES + ROLLING_RES;
        double power = UtilsWind.calc_power(TOTAL_RES, speed);
        return (power < 0)? 0: power;
    }

    // calculate the bike velocity in kmph. given power, A, head wind (m / sec), and sum of other resistance
    private double calcSpeed() {
        double power = Double.parseDouble(powerText.getText().toString());
        double gradient = Double.parseDouble(gradientText.getText().toString());
        double wind_speed = Double.parseDouble(wind_speedText.getText().toString());
        double wind_degree = Double.parseDouble(wind_degreeText.getText().toString());
        double TEMP = Double.parseDouble(tempText.getText().toString());
        double ELEVATION = Double.parseDouble(elevationText.getText().toString());
        double bike_degree = Double.parseDouble(bike_degreeText.getText().toString());

        // Density calculation
        double DENSITY = (1.293 - 0.00426 * TEMP) * Math.exp(-ELEVATION / 7000.0);
        double TOTAL_WT = (rider_wt + bike_wt) * 9.8; // Newtons

        // Calculate grade resistance
        double GRADE_RES = (gradient / 100.0) * TOTAL_WT;

        // calculate rolling resistance
        double ROLLING_RES = c_r * TOTAL_WT;

        // calculate K_A: aerodynamic drag factor
        double C_D_AREA = c_d * front_area;
        double K_A = 0.5 * C_D_AREA * DENSITY;
        //K_A = 0.388;

        double OTHER_RES = GRADE_RES + ROLLING_RES;

        double speed = UtilsWind.get_velocity(K_A, bike_degree, wind_speed, wind_degree, power, OTHER_RES);
        return (speed < 0)? 0: speed;
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
            actionBar.setTitle(getResources().getString(R.string.action_calculator));
        }
    }

    // check if a number is within range
    public static boolean isValid(String strNum, double start, double end) {
        try {
            double d = Double.parseDouble(strNum);
            if (d < start) return false;
            if (d > end) return false;
        } catch (NumberFormatException | NullPointerException nfe) {
            return false;
        }
        return true;
    }

    public abstract static class TextValidator implements TextWatcher {
        private final TextView textView;

        public TextValidator(TextView textView) {
            this.textView = textView;
        }

        public abstract void validate(TextView textView, String text);

        @Override
        final public void afterTextChanged(Editable s) {
            String text = textView.getText().toString();
            validate(textView, text);
        }

        @Override
        final public void
        beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        final public void
        onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}