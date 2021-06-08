package org.mkonchady.mytripoo.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.BatteryManager;
import android.preference.PreferenceManager;

import org.mkonchady.mytripoo.Constants;

import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

// miscellaneous utilities
public final class UtilsMisc {

    // Date constants
    //final static private DecimalFormat floatFormat = new DecimalFormat("0.###");
    final static private DecimalFormat doubleFormat = new DecimalFormat("0.######");

    /**
     * no default constructor
     */
    private UtilsMisc() {
        throw new AssertionError();
    }

    //  calculate the percentage given the min. and max.
    public static double calcPercentage(float min, float max, float value) {
        double numerator = value - min;
        double denominator = max - min;
        if (denominator <= 0.0) return 0.0;
        return ((100.0 * numerator) / denominator);
    }

    // round down a number based on the number of digits
    // roundToNumber(13256789, 2) = 13256800
    // roundToNumber(13256449, 2) = 13256400
    public static int roundToNumber(int num, int digits, boolean ceil) {
        int factor = (int) Math.pow(10, digits);
        num /= factor;
        if (ceil) return (num + 1) * factor;
        return (num * factor);
    }

    // remove outliers
    // limit is n times standard deviation to detect outliers
    public static int[] removeOutliers(int[] raw_data, int num_std_deviations) {
        double[] data = new double[raw_data.length];
        for (int i = 0; i < raw_data.length; i++) data[i] = raw_data[i];
        double avg_data = getAverage(data);
        double LIMIT = avg_data + num_std_deviations * calcStandardDeviation(data);
        for (int i = 0; i < data.length; i++) {
            if (data[i] > LIMIT) data[i] = LIMIT;
            if (data[i] < -LIMIT) data[i] = -LIMIT;
        }
        for (int i = 0; i < raw_data.length; i++) raw_data[i] = (int) Math.round(data[i]);
        return (raw_data);
    }

    // limit is n times standard deviation to detect outliers
    public static float[] removeOutliers(float[] raw_data, int num_std_deviations) {
        double[] data = new double[raw_data.length];
        for (int i = 0; i < raw_data.length; i++) data[i] = raw_data[i];
        double avg_data = getAverage(data);
        double LIMIT = avg_data + num_std_deviations * calcStandardDeviation(data);
        for (int i = 0; i < data.length; i++) {
            if (data[i] > LIMIT) data[i] = LIMIT;
            if (data[i] < -LIMIT) data[i] = -LIMIT;
        }
        for (int i = 0; i < raw_data.length; i++)
            raw_data[i] = (float) data[i];
        return (raw_data);
    }

    // limit is n times standard deviation to detect outliers
    public static double[] removeOutliers(double[] raw_data, int num_std_deviations) {
        double[] data = new double[raw_data.length];
        for (int i = 0; i < raw_data.length; i++) data[i] = raw_data[i];
        double avg_data = getAverage(data);
        double LIMIT = avg_data + num_std_deviations * calcStandardDeviation(data);
        for (int i = 0; i < data.length; i++) {
            if (data[i] > LIMIT) data[i] = LIMIT;
            if (data[i] < -LIMIT) data[i] = -LIMIT;
        }
        for (int i = 0; i < raw_data.length; i++)
            raw_data[i] =  data[i];
        return (raw_data);
    }

    // normalize the float array to a percentage
    public static float[] normalize(float[] inValues) {
        if (inValues.length == 0) return inValues;

        // find the min. and max. in array
        float smallest = inValues[0];
        float largest = inValues[0];
        for (int i = 1; i < inValues.length; i++)
            if (inValues[i] > largest) largest = inValues[i];
            else if (inValues[i] < smallest) smallest = inValues[i];

        float range = largest - smallest;
        float[] outValues = new float[inValues.length];
        for (int i = 0; i < inValues.length; i++)
            outValues[i] = 100.0f * ((inValues[i] - smallest) / range);
        return outValues;
    }

    public static int[] convertToIntegers(ArrayList<Long> longs) {
        int[] intArray = new int[longs.size()];
        for (int i = 0; i < intArray.length; i++) intArray[i] = longs.get(i).intValue();
        return intArray;
    }

    public static int[] convertIntegers(ArrayList<Integer> integers) {
        int[] intArray = new int[integers.size()];
        for (int i = 0; i < intArray.length; i++) intArray[i] = integers.get(i);
        return intArray;
    }

    public static float[] convertFloats(ArrayList<Float> floats) {
        float[] floatArray = new float[floats.size()];
        for (int i = 0; i < floatArray.length; i++) floatArray[i] = floats.get(i);
        return floatArray;
    }

    // format a floating point number to x decimal place
    public static String formatFloat(float num, int places) {
        return String.format("%." + places + "f", num);
    }

    // format a double number to 6 decimal places
    public static String formatDouble(double num, int places) {
        return doubleFormat.format(round(num, places));
    }

    public static double round(double number, int places) {
        double factor = Math.pow(10, places);
        number = Math.round(number * factor);
        number = number / factor;
        return number;
    }

    // return a string of specified length
    public static String fixedLengthString(String string, int length) {
        return String.format("%1$" + length + "s", string);
    }

    public static double[] smoothValues(double[] values, long[] timestamps, int MIN_TIME_PER_READING) {
        // need at least 7 entries for filter
        if (values.length < 8) return values;

        // define the kernels from broad to narrow
        double[] smooth_values = new double[values.length];
        double[] kernel0 = new double[]{0.006578, 0.0666886, 0.2660273, 0.3214122, 0.2660273, 0.0666886, 0.006578};
        double[] kernel1 = new double[]{0.005980, 0.060626, 0.241843, 0.383103, 0.241843, 0.060626, 0.005980};
        double[] kernel2 = new double[]{0.00299, 0.03013, 0.1209215, 0.691186, 0.1209215, 0.03013, 0.00299};
        double[] kernel3 = new double[]{0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f};
        double[] SMOOTH_KERNEL;
        for (int i = 0; i < values.length; i++) {
            // use a narrower smoothing kernel if the time between readings is large
            SMOOTH_KERNEL = (timestamps[i] < MIN_TIME_PER_READING * 8) ? kernel0 :
                    (timestamps[i] < MIN_TIME_PER_READING * 16) ? kernel1 :
                            (timestamps[i] < MIN_TIME_PER_READING * 32) ? kernel2 : kernel3;
            smooth_values[i] = 0.0f;
            for (int j = 0, k = -3; j < 7; j++, k++) // use a circular array
                smooth_values[i] += SMOOTH_KERNEL[j] * values[fix_index(i + k, values.length - 1)];
        }
        return smooth_values;
    }

    // return the minimum and maximum values of a double array
    public static double[] getMinMax(double[] doubles) {
        double max_value = -Constants.LARGE_FLOAT;
        double min_value = Constants.LARGE_FLOAT;
        for (double value : doubles) {
            if (value > max_value) max_value = value;
            if (value < min_value) min_value = value;
        }
        return new double[]{min_value, max_value};
    }

    // return the minimum and maximum values of a float array
    public static float[] getMinMax(float[] floats) {
        float max_value = -Constants.LARGE_FLOAT;
        float min_value = Constants.LARGE_FLOAT;
        for (float value : floats) {
            if (value > max_value) max_value = value;
            if (value < min_value) min_value = value;
        }
        return new float[]{min_value, max_value};
    }

    // return the minimum and maximum values of an int array
    public static int[] getMinMax(int[] ints) {
        int max_value = -Constants.LARGE_INT;
        int min_value = Constants.LARGE_INT;
        for (int value : ints) {
            if (value > max_value) max_value = value;
            if (value < min_value) min_value = value;
        }

        return new int[]{min_value, max_value};
    }

    // get the min. and max. from a float array
    public static float getMin(float[] floats) {
        float[] minMax = getMinMax(floats);
        return minMax[0];
    }

    public static float getMax(float[] floats) {
        float[] minMax = getMinMax(floats);
        return minMax[1];
    }

    public static double getRange(double[] doubles) {
        double[] minMax = getMinMax(doubles);
        return (minMax[1] - minMax[0]);
    }

    public static float getRange(float[] floats) {
        float[] minMax = getMinMax(floats);
        return (minMax[1] - minMax[0]);
    }

    public static int getRange(int[] ints) {
        int[] minMax = getMinMax(ints);
        return (minMax[1] - minMax[0]);
    }

    // return the sum of values of a float array
    public static float getSum(float[] floats) {
        float sum = 0.0f;
        for (float value : floats) sum += value;
        return sum;
    }

    // return the sum of values of a float array
    public static float getSum(int[] ints) {
        int sum = 0;
        for (int value : ints) sum += value;
        return sum;
    }

    // calculate the additional range for the plot axes
    public static int calcRange(int val, float factor) {
        if (val <= 0) return 0;
        return (int) Math.ceil(factor * val);
    }

    // calculate the additional range for the plot axes
    public static int calcRange(float val, float factor) {
        if (val <= 0) return 0;
        return (int) Math.ceil(factor * val);
    }

    // return a list of string phone numbers
    public static String[] getPhoneNumbers(String phoneNumString) {
        String[] phoneNums = phoneNumString.split(",");
        for (int i = 0; i < phoneNums.length; i++) phoneNums[i] = phoneNums[i].trim();
        return phoneNums;
    }

    // return an index to implement a circular array
    private static int fix_index(int index, int limit) {
        if (limit < 3) return index;
        if (index == -3) return (limit - 3);
        if (index == -2) return (limit - 2);
        if (index == -1) return (limit - 1);
        if (index == (limit + 1)) return 0;
        if (index == (limit + 2)) return 1;
        if (index == (limit + 3)) return 2;
        return index;
    }

    // Use the walking speed to estimate the category based on avg. speed
    // Walking Speed   Walking Range Jogging Range Cycling Range Other Range
    //  4               0-6             7-14        15-27           28+
    //  5               0-7             8-16        17-31           32+
    //  6               0-8             9-18        19-35           36+
    //  7               0-9             10-20       21-39           40+
    public static String getCategory(float walking_speed, float avg_speed) {
        if (get_sigmoid_factor(avg_speed, walking_speed * 1) < 0.9) return Constants.CAT_WALKING;
        if (get_sigmoid_factor(avg_speed, walking_speed * 2) < 0.99) return Constants.CAT_JOGGING;
        if (get_sigmoid_factor(avg_speed, walking_speed * 4) < 0.999999999) return Constants.CAT_CYCLING;
        return Constants.CAT_OTHER;
    }

    public static String getCategory(float walking_speed, float avg_speed, float max_speed) {
        float CAT_AVG_SPEED = walking_speed; double CAT_MAX_SPEED = CAT_AVG_SPEED * 2.0; // max. speed cannot be more than 100% of cat. avg. speed
        if (max_speed < CAT_MAX_SPEED && get_sigmoid_factor(avg_speed, CAT_AVG_SPEED) < 0.9) return Constants.CAT_WALKING;
        CAT_AVG_SPEED = walking_speed * 2; CAT_MAX_SPEED = CAT_AVG_SPEED * 1.5;         // max. speed cannot be more than 50% of cat. avg. speed
        if (max_speed < CAT_MAX_SPEED && get_sigmoid_factor(avg_speed, CAT_AVG_SPEED) < 0.99) return Constants.CAT_JOGGING;
        CAT_AVG_SPEED = walking_speed * 4; CAT_MAX_SPEED = CAT_AVG_SPEED * 3.0;         // max. speed cannot be more than 300% of cat. avg. speed
        if (max_speed < CAT_MAX_SPEED && get_sigmoid_factor(avg_speed, CAT_AVG_SPEED) < 0.999999999) return Constants.CAT_CYCLING;
        return Constants.CAT_OTHER;
    }
    // use a sigmoid function to categorize
    private static double get_sigmoid_factor(double x, double y) {
        return 1.0 / (1.0 + Math.exp(-(x - y)));
    }

    // return the standard deviation
    public static double calcStandardDeviation(double[] doubles) {

        // calculate the mean
        double sum = 0;
        for (double value : doubles)
            sum += value;
        double mean = (doubles.length > 0) ? sum / doubles.length : 0.0;

        // calculate the sum of squares of deviations
        sum = 0;
        for (double value : doubles)
            sum += (value - mean) * (value - mean);

        // calculate the sd
        int denominator = (doubles.length > 1) ? (doubles.length - 1) : 1;
        return (Math.sqrt(sum / denominator));
    }


    // return the average of a float list
    public static float getAverage(float[] nums) {
        if (nums.length == 0) return 0.0f;
        if (nums.length == 1) return nums[0];
        double sum = 0.0;
        for (float num : nums) sum += num;
        return (float) (sum / nums.length);
    }

    // return the average of a double list
    private static double getAverage(double[] nums) {
        if (nums.length == 0) return 0.0;
        if (nums.length == 1) return nums[0];
        double sum = 0.0;
        for (double num : nums) sum += num;
        return (sum / nums.length);
    }

    // round a long number -- unix timestamp to seconds
    //public static long roundLong(long i) {
    //    i = i + 500L; i = i / 1000L; i = i * 1000L;
    //    return i;
    //}

    // return the center of two points
    public static XYPoint center(XYPoint p1, XYPoint p2) {
        return new XYPoint((p1.getX() + p2.getX()) / 2.0,
                (p1.getY() + p2.getY()) / 2.0, p1.getColor());
    }

    // convert int to float array
    public static float[] toFloat(int[] iarr) {
        float[] farr = new float[iarr.length];
        for (int i = 0; i < iarr.length; ++i) {
            farr[i] = iarr[i];
        }
        return farr;
    }

    public static boolean isZero(Double x) {
        final double THRESHOLD = 0.00001;
        return Math.abs(x - 0.0) < THRESHOLD;
    }

    public static String getDecimalFormat(Number n, int maxFractionDigits, int minNumberDigits) {
        NumberFormat format = DecimalFormat.getInstance();
        format.setRoundingMode(RoundingMode.CEILING);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(maxFractionDigits);
        format.setMinimumIntegerDigits(minNumberDigits);
        return format.format(n);
    }

    public static String getDecimalFormat(Number n, int minNumberDigits) {
        NumberFormat format = DecimalFormat.getInstance();
        format.setRoundingMode(RoundingMode.CEILING);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        format.setMinimumIntegerDigits(minNumberDigits);
        return format.format(n).replaceAll(",", "");
    }


    // convert array to array list
    public static List<Number> toNumber(int[] x) {
        List<Number> numberList = new ArrayList<>();
        for (int i : x) numberList.add(i);
        return numberList;
    }

    public static Number[] toNumber(float[] x) {
        Number[] numberList = new Number[x.length];
        for (int i = 0; i < x.length; i++) numberList[i] = x[i];
        return numberList;
    }

    // return an array of partition sizes
    // getPartitions(190, 90, 10) returns [90. 100]
    public static int[] getPartitions(int numElements, int maxSize, int minSize) {
        if (numElements <= maxSize)
            return new int[]{numElements};

        int numPartitions = (int) Math.ceil(1.0 * numElements / maxSize);
        int remainder = numElements % maxSize;
        if (remainder != 0 && remainder <= minSize) numPartitions--;
        int[] partitions = new int[numPartitions];
        int start = 0;
        for (int i = 0; i < numPartitions; i++) {
            // if this is the last partition
            if (i == numPartitions - 1)
                partitions[i] = numElements - start;
            else
                partitions[i] = maxSize;
            start += maxSize;
        }

        return partitions;
    }

    // fix the Number array
    // set the first set of zero entries to the first non-zero entry
    // set the last set of zero entries to the last non-zero entry
    // pass:   <null, 20.0, 0.0, -10.0, 30.0, null, 20.0, null>,
    // return: <20.0, 20.0, 0.0, -10.0, 30.0, 0.0, 20.0, 20.0>
    public static Number[] fix_numbers(Number[] nums, boolean positive) {

        // find the first valid non-null value
        float first_num = 0.0f;
        for (Number num : nums) {
            // must be positive
            if ((num != null) && positive && (num.floatValue() > 0.0f)) {
                first_num = num.floatValue();
                break;
            }
            //must be non-zero
            if ((num != null) && !positive && (Float.compare(num.floatValue(), 0.0f) != 0)) {
                first_num = num.floatValue();
                break;
            }
        }

        // set the first null numbers to the first valid non-null value
        int i = 0;
        while (i < nums.length) {
            if (nums[i] == null) nums[i++] = first_num;
            else i = nums.length;
        }

        // find the last valid non-null number
        float last_num = 0.0f;
        for (i = nums.length - 1; i >= 0; i--) {

            // must be positive
            if ((nums[i] != null) && positive && (nums[i].floatValue() > 0.0f)) {
                last_num = nums[i].floatValue();
                break;
            }
            //must be non-zero
            if ((nums[i] != null) && !positive && (Float.compare(nums[i].floatValue(), 0.0f) != 0)) {
                last_num = nums[i].floatValue();
                break;
            }
        }

        // set the last null values to the last non-null value
        i = nums.length - 1;
        while (i >= 0) {
            if (nums[i] == null) nums[i--] = last_num;
            else i = -1;
        }

        return nums;
    }

    /*
     * return a linearly interpolated array
     * pass:   <10,0, 20.0, 0.0, -1.0, -1.0, 30.0, -1.0, -1.0, 20.0>, true
     * return: <10.0, 20.0, 22.5, 25.0, 27.5, 30.0, 26.66, 23.33, 20.0>
     *     positive flag to interpolate all non-positive numbers
     */
    public static Number[] Interpolate(Number[] numbers, boolean positive) {

        // Verify that there are sufficient elements
        if (numbers.length < 2) {
            numbers[0] = 0;
            return numbers;
        }

        // nullify numbers that are invalid
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] != null) {
                // all numbers must be positive or 0
                if (positive && numbers[i].doubleValue() <= 0.0) numbers[i] = null;
                // all numbers must be positive or negative (i.e. non-zero)
                if (!positive && Double.compare(numbers[i].doubleValue(), 0.0) == 0)
                    numbers[i] = null;
            }
        }

        // verify that the leading and trailing elements of the array contain valid elements
        numbers = fix_numbers(numbers, positive);

        // keep track of the ranges where elements are null
        ArrayList<Integer> ranges = new ArrayList<>();
        int step_cnt = 0;
        for (Number number : numbers) {
            if (number == null) {
                step_cnt++;
            } else {
                if (step_cnt != 0)
                    ranges.add(step_cnt);
                step_cnt = 0;
            }
        }

        int step_index = 0;
        int i = 0;
        ArrayList<Number> interpolated = new ArrayList<>();
        while (i < numbers.length) {
            // add the original elements if non-null
            if (numbers[i] != null) {
                interpolated.add(numbers[i++]);
            } else {  // otherwise, interpolate between the start and end elements
                int start = i - 1;
                int range = ranges.get(step_index++);
                int end = i + range;
                interpolated.addAll(LinearInterpolate(numbers[start].doubleValue(), numbers[end].doubleValue(), range));
                i += range;
            }
        }

        // convert to a Number array and return
        return interpolated.toArray(new Number[interpolated.size()]);
    }

    /* Linear interpolation using the start & end points and the range
        pass  : start 20.0, end 30.0, range 3 return: <22.5, 25.0, 27.5>
        pass  : start 30.0, end 20.0, range 2 return: <26.6666, 23.3333>
        */
    public static ArrayList<Number> LinearInterpolate(double start, double end, int range) {
        ArrayList<Number> result = new ArrayList<>();
        double diff = (end - start) / (range + 1);
        for (int i = 0; i < range; i++)
            result.add(start + diff * (i + 1));
        return result;
    }

    /*
        p1 : start point p2/p3: control points p4: end point
        B(t) = (1 - t)^3 * P0 + 3(1 - t)^2 * t * P1 + 3(1-t) * t^2 * P2 + t^3 * P3
     */
    public static ArrayList<XYPoint> BezierInterpolate(XYPoint p1, XYPoint p2, XYPoint p3, XYPoint p4) {
        double t;    //the time interval
        double k = 0.025f;    //time step value for drawing curve
        double x1 = p1.x;
        double y1 = p1.y;
        ArrayList<XYPoint> points = new ArrayList<>();
        points.add(new XYPoint(x1, y1, p1.getColor()));
        for (t = k; t <= 1 + k; t += k) {
            x1 = (p1.x + t * (-p1.x * 3 + t * (3 * p1.x - t * p1.x))) +     // ((1-t)^3 * P1
                    t * (3 * p2.x + t * (-6 * p2.x + p2.x * 3 * t)) +       // 3(1-t)^2 * P2 * t
                    t * t * (p3.x * 3 - p3.x * 3 * t) +                     // 3(1-t) * P3 * t^2
                    t * t * t * (p4.x);                                     // t^3 * P4
            y1 = (p1.y + t * (-p1.y * 3 + t * (3 * p1.y - t * p1.y))) +
                    t * (3 * p2.y + t * (-6 * p2.y + p2.y * 3 * t)) +
                    t * t * (p3.y * 3 - p3.y * 3 * t) +
                    t * t * t * (p4.y);
            points.add(new XYPoint(x1, y1, p1.getColor()));
        }
        return points;
    }

    // rotate a point counter-clockwise around a center by an angle
    public static XYPoint rotate(XYPoint point, final XYPoint center, final double angle) {

        double sint = Math.sin(Math.toRadians(angle));
        double cost = Math.cos(Math.toRadians(angle));

        //point.y = point.y + 90;
        //center.y = center.y + 90;

        point.x = point.x - center.x; // translate point to origin:
        point.y = point.y - center.y;

        double xnew = point.x * cost - point.y * sint; // rotate point
        double ynew = point.x * sint + point.y * cost;

        point.x = (float) (xnew + center.x); // translate point back:
        point.y = (float) (ynew + center.y);
        //point.y = point.y - 90;
        return new XYPoint(point.x, point.y);
    }

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // use a double quote
    public static String escapeQuotes(String in) {
        final char quote = '\"';
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            if (in.charAt(i) == quote) {
                stringBuilder.append("\"\"");
            } else {
                stringBuilder.append(in.charAt(i));
            }
        }
        return stringBuilder.toString();
    }

    public static void initializeSharedParams(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(Constants.PREF_LATITUDE, 0.0f);
        editor.putFloat(Constants.PREF_LONGITUDE, 0.0f);
        editor.putLong(Constants.PREF_LOCATION_INIT_TIME, 0L);
        editor.putInt(Constants.PREF_TRIP_ID, 0);
        editor.putLong(Constants.PREF_TRIP_DURATION, Constants.MILLISECONDS_PER_DAY);
        editor.putInt(Constants.PREF_DISTANCE, 0);
        editor.putFloat(Constants.PREF_AVG_SPEED, 0.0f);
        editor.putString(Constants.PREF_LOG_FILE, "");
        editor.putLong(Constants.PREF_LAST_LOCATION_TIME, 0L);
        editor.putBoolean(Constants.LOCATION_SERVICE_RUNNING, false);
        editor.putInt(Constants.PREF_FOLLOW_TRIP, -1);
        editor.putInt(Constants.PREF_FOLLOW_COUNTER, 0);
        editor.putBoolean(Constants.PREF_DIRECTION_SCREEN_VISIBLE, false);
        editor.putInt(Constants.GPS_SATELLITES, 0);
        editor.putInt(Constants.GPS_USED_SATELLITES, 0);
        editor.putFloat(Constants.GPS_SNR, 0.0f);
        editor.apply();
    }

    public static int getBatteryLevel(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return Math.round(100.0f * (level / (float) scale));
        }
        return 100;
    }

    // get the minimum time period between readings based on preferences
    public static int getMinTimePerReading(SharedPreferences sharedPreferences) {
        String pollInterval = sharedPreferences.getString(Constants.PREF_POLL_INTERVAL, "low");
        return getReadingTime(pollInterval);
    }

    public static int getReadingTime(String pollInterval) {
        switch (pollInterval) {
            case "really_low":
                return 1000;
            case "low":
                return (1000 * 5);
            case "medium":
                return (1000 * 10);
            default:
                return (1000 * 20);
        }
    }
    // get the smallest displacement in meters before location update is called
    public static int getMinDistPerReading(SharedPreferences sharedPreferences) {
        String location_separation = sharedPreferences.getString(Constants.PREF_LOCATION_ACCURACY, "medium");
        return getReadingDist(location_separation);
    }

    public static int getReadingDist(String location_separation) {
        switch (location_separation) {
            case "high":
                return 20;
            case "medium":
                return 10;
            default:
                return 5;
        }

    }

    // full key must be entered in the shared preferences
    public static String getGoogleAPIKey(SharedPreferences sharedPreferences) {
        return sharedPreferences.getString(Constants.PREF_GOOGLE_API_KEY,
                "AIzaSyB6RBvMqDCTTrVKdDqLumwNtTi2Z6M");
    }

    // full key must be entered in the shared preferences
    public static String getOpenWeatherAPIKey(SharedPreferences sharedPreferences) {
        return sharedPreferences.getString(Constants.PREF_OPENWEATHER_API_KEY,
                "c5d14b4cafe6af9c3e1606b744f7");
    }

    public static int getMaxReadings(SharedPreferences sharedPreferences) {
        String readings_trip = sharedPreferences.getString(Constants.PREF_NUMBER_TRIP_READINGS, "2000");
        return Integer.parseInt(readings_trip);
    }



    public static void genSounds(Context context, int resid) {
        AudioManager amanager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = amanager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        amanager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
        MediaPlayer mediaPlayer= new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM); // this is important.
        Uri myUri = Uri.parse("android.resource://org.mkonchady.gpstracker/" +  resid);
        mediaPlayer.setLooping(false);
        try {
            mediaPlayer.setDataSource(context, myUri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            Thread.sleep(mediaPlayer.getDuration());
        } catch (IOException ue) {
        } catch (InterruptedException ie) {
        }
    }

    public final static class XYPoint {

        private double x;
        private double y;
        private int color;

        public XYPoint(double x, double y) {
            this(x,y,0);
        }

        public XYPoint(double x, double y, int color) {
            this.x = x; this.y = y; this.color = color;
        }

        public double getX() {
            return x;
        }
        public double getY() {
            return y;
        }
        public int getColor() {
            return color;
        }
        public void setX(double x) {
            this.x = x;
        }
        public void setY(double y) {
            this.y = y;
        }
        public void setXY(double x, double y) {
            this.x = x; this.y = y;
        }
        public void setColor(int color) {
            this.color = color;
        }
        public String toString() {
            return ("X: " + x + " Y: " + y);
        }
    }
}
