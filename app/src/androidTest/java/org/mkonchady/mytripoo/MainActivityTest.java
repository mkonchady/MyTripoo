package org.mkonchady.mytripoo;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import org.junit.runner.RunWith;
import org.mkonchady.mytripoo.database.DetailProvider;
import org.mkonchady.mytripoo.utils.SMSTracker;
import org.mkonchady.mytripoo.utils.TimezoneMapper;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsFile;
import org.mkonchady.mytripoo.utils.UtilsJSON;
import org.mkonchady.mytripoo.utils.UtilsMap;
import org.mkonchady.mytripoo.utils.UtilsMisc;
import org.mkonchady.mytripoo.utils.UtilsWind;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Test
    public void testFunctions() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        checkDateFunctions();
        checkDistanceFunctions();
        checkPowerFunctions();
        checkOtherFunctions();
        checkJSONFunctions();
        //checkSMSFunction();
        checkWindFunctions();
        dumpSharedParameters(context);
    }

    public void checkDateFunctions() {
        boolean validDate = UtilsDate.isDate("Jul 21, 2016");
        assertEquals(true, validDate);
        validDate = UtilsDate.isDate("Jul 35, 2016");
        assertEquals(false, validDate);

        String dateTime = UtilsDate.getDateTimeSec(1455797152543L, Constants.LARGE_INT, Constants.LARGE_INT);
        assertEquals("Feb 18, 2016 17:35:52", dateTime);
        dateTime = UtilsDate.getDateTime(1455797152543L, Constants.LARGE_INT, Constants.LARGE_INT);
        assertEquals("Feb 18, 17:35", dateTime);
        dateTime = UtilsDate.getDate(1455797152543L, Constants.LARGE_INT, Constants.LARGE_INT);
        assertEquals("Feb 18, 2016", dateTime);
        dateTime = UtilsDate.getZuluDateTimeSec(1517726644000L);
        assertEquals("2018-02-04T06:44:04Z", dateTime);


        long detailTimestamp = UtilsDate.getDetailTimeStamp("2016-09-30T11:23:28.276+0530");
        assertEquals(1475214808276L, detailTimestamp);
        detailTimestamp = UtilsDate.getDetailTimeStamp("2010-02-11T03:52:45.000Z");
        assertEquals(1265860365000L, detailTimestamp);

        String duration = UtilsDate.getTimeDurationHHMMSS(3800 * 1000, false);
        assertEquals("01:03:20", duration);
        duration = UtilsDate.getTimeDurationHHMMSS(3800100, false);
        assertEquals("01:03:20", duration);
        duration = UtilsDate.getTimeDurationHHMMSS(3800900, false);
        assertEquals("01:03:21", duration);
        duration = UtilsDate.getTimeDurationHHMMSS(800 * 1000, false);
        assertEquals("13:20", duration);
        duration = UtilsDate.getTimeDurationHHMMSS(4 * 1000, false);
        assertEquals("00:04", duration);
    }

    public void checkDistanceFunctions() {
        // 110 meters in 1000 milliseconds to kmph
        float speed = (float) UtilsMap.calcKmphSpeed(110, 1000);
        assertEquals(396.0f, speed, 0.25f);

        speed = (float) UtilsMap.calcKmphSpeed(102.68, 8547);
        assertEquals(43.24886f, speed, 0.25f);

        // kms to miles
       // float miles = Constants.kmsToMiles(2.6f);
       // assertEquals(1.61557f, miles, 0.05f);

        // rounding up/down numbers
        boolean ceil = false;
        int rounded = UtilsMisc.roundToNumber(13256789, 2, ceil);
        assertEquals(13256700, rounded);
        ceil = true;
        rounded = UtilsMisc.roundToNumber(13256789, 2, ceil);
        assertEquals(13256800, rounded);
        ceil = false;
        rounded = UtilsMisc.roundToNumber(13256789, 1, ceil);
        assertEquals(13256780, rounded);
        ceil = true;
        rounded = UtilsMisc.roundToNumber(13256789, 1, ceil);
        assertEquals(13256790, rounded);

        rounded = UtilsMisc.roundToNumber(-13256789, 2, ceil);
        assertEquals(-13256600, rounded);
        ceil = true;
        rounded = UtilsMisc.roundToNumber(-13256789, 2, ceil);
        assertEquals(-13256600, rounded);
        ceil = false;
        rounded = UtilsMisc.roundToNumber(-13256789, 1, ceil);
        assertEquals(-13256780, rounded);
        ceil = true;
        rounded = UtilsMisc.roundToNumber(-13256789, 1, ceil);
        assertEquals(-13256770, rounded);

        // normalization
        float[] floats = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        float[] norms = UtilsMisc.normalize(floats);
        assertEquals("[0.0, 25.0, 50.0, 75.0, 100.0]", Arrays.toString(norms));

        // string rep. of float with 3 significant digits
        float number = 2.54678f;
        String formatNumber = UtilsMisc.formatFloat(number, 3);
        assertEquals("2.547", formatNumber);
        number = 2.54638f;
        formatNumber = UtilsMisc.formatFloat(number, 3);
        assertEquals("2.546", formatNumber);

        // check the standard deviation calculation
        double[] nums = {1.0f, 2.0f, 3.0f, 4.0f};
        double sd = UtilsMisc.calcStandardDeviation(nums);
        assertEquals(1.29f, sd, 0.005f * sd);

        double[] nums1 = {101.0f, 202.0f, 303.0f, 404.0f};
        sd = UtilsMisc.calcStandardDeviation(nums1);
        assertEquals(130.9f, sd, 0.005f * sd);

        // Use Haverside formula to calculate great circle distances
        // between Washington DC and Moscow, Russia
        int lon1 = -133508300; int lat1 = 37130500;
        int lon2 = 36615600; int lat2 = 55752200;
        float distance = (float) UtilsMap.getKmDistance(lat1, lon1, lat2, lon2);
        assertEquals(9323, distance, 0.05f * distance);

        distance = (float) UtilsMap.getKmDistance(38904800, -77035400, 55750000, 37583000);
        assertEquals(7820.0f, distance, 0.005f * distance);

        distance = (float) UtilsMap.getKmDistance(38904800, -77035400, 28632799, 77219498);
        assertEquals(12044.0f, distance, 0.005f * distance);

        distance = (float) UtilsMap.getKmDistance(38904800, -77035400, 40748798, -73984696);
        assertEquals(332.0f, distance, 0.005f * distance);

        distance = (float) UtilsMap.getKmDistance(38898556, -77037852, 38897147, -77043934);
        assertEquals(0.549f, distance, 0.005f * distance);

        distance = (float) UtilsMap.getKmDistance(13190621, 77645933, 13190714, 77646011);
        assertEquals(0.0133f, distance, 0.005f * distance);

        // test lat lon key generation for detecting a loop
        String[] keys  = UtilsMap.gen_keys(13022247, 77579847, 3);
        String[] keys1 = UtilsMap.gen_keys(13022437, 77579032, 3);
        String[] keys2 = {"13023000:77580000", "13023000:77579000", "13022000:77580000", "13022000:77579000"};
        assertEquals(dumpArray(keys), dumpArray(keys1));
        assertEquals(dumpArray(keys), dumpArray(keys2));

        keys  = UtilsMap.gen_keys(13022527, 77577460, 2);
        keys  = UtilsMap.gen_keys(13022525, 77577581, 2);
        distance = (float) UtilsMap.getKmDistance(13022527, 77577460, 13022525, 77577581);

        double lat = 13.023366; double lon = 77.577815;
        String timezone = TimezoneMapper.latLngToTimezoneString(lat, lon);
        assertEquals("Timezone1 mismatch", "Asia/Kolkata", timezone);
        lat = 38.9072; lon = -77.0369;
        timezone = TimezoneMapper.latLngToTimezoneString(lat, lon);
        assertEquals("Timezone2 mismatch", "America/New_York", timezone);

        double bearing = 15;
        assertEquals("Bearing Direction mismatch", "N", UtilsMap.getDirection(15));

        SparseIntArray colorMap = UtilsMap.buildColorRamp();
        int factor = colorMap.size();
        for (int pct = 0; pct < 101; pct+=5) {
            Double d = new Double( (pct * factor) / 100.0);      // convert to a value that will map to a color
            int index = d.intValue();
            if (index == factor) index--;   // keep the index within array bounds
            int color = colorMap.get(index);
            String hexColor = String.format("#%06X", (0xFFFFFF & color));
            //System.out.println(pct + ": " + hexColor );
        }
    }

    public void checkOtherFunctions() {

        // rotate point around center
        UtilsMisc.XYPoint center = new UtilsMisc.XYPoint(2,2);
        UtilsMisc.XYPoint point = new UtilsMisc.XYPoint(4, 3);
        UtilsMisc.XYPoint rot_point = UtilsMisc.rotate(point, center, 90);
        String expected = rot_point + "";
        assertTrue("Rot1 " + expected, stringCheck("X: 1.0 Y: 4.0", expected));

        center = new UtilsMisc.XYPoint(106,-20);
        point = new UtilsMisc.XYPoint(106, -10);
        rot_point = UtilsMisc.rotate(point, center, 90);
        expected = rot_point + "";
        assertTrue("Rot2 " + expected, stringCheck("X: 96.0 Y: -20.0", expected));

        point = new UtilsMisc.XYPoint(106, -10);
        rot_point = UtilsMisc.rotate(point, center, 180);
        expected = rot_point + "";
        assertTrue("Rot3 " + expected, stringCheck("X: 106.0 Y: -30.0", expected));

        point = new UtilsMisc.XYPoint(106, -10);
        rot_point = UtilsMisc.rotate(point, center, 270);
        expected = rot_point + "";
        assertTrue("Rot4 " + expected, stringCheck("X: 116.0 Y: -20.0", expected));

        point = new UtilsMisc.XYPoint(106, -10);
        rot_point = UtilsMisc.rotate(point, center, 360);
        expected = rot_point + "";
        assertTrue("Rot5 " + expected, stringCheck("X: 106.0 Y: -10.0", expected));

        // number of elements, max. size, min. size
        int[] partitions = UtilsMisc.getPartitions(20, 90, 10);
        assertTrue("Partition1", intArrayCheck(partitions, new int[] {20}));

        partitions = UtilsMisc.getPartitions(90, 90, 10);
        assertTrue("Partition2", intArrayCheck(partitions, new int[] {90}));

        partitions = UtilsMisc.getPartitions(100, 90, 10);
        assertTrue("Partition3", intArrayCheck(partitions, new int[] {100}));

        partitions = UtilsMisc.getPartitions(101, 90, 10);
        assertTrue("Partition4", intArrayCheck(partitions, new int[] {90, 11}));

        partitions = UtilsMisc.getPartitions(120, 90, 10);
        assertTrue("Partition5", intArrayCheck(partitions, new int[] {90,30}));

        partitions = UtilsMisc.getPartitions(180, 90, 10);
        assertTrue("Partition6", intArrayCheck(partitions, new int[] {90,90}));

        partitions = UtilsMisc.getPartitions(190, 90, 10);
        assertTrue("Partition7", intArrayCheck(partitions, new int[] {90,100}));

        partitions = UtilsMisc.getPartitions(191, 90, 10);
        assertTrue("Partition8", intArrayCheck(partitions, new int[] {90,90, 11}));

        String doubleValue = UtilsMisc.formatDouble(2.1234567, 6);
        assertEquals("Rounding", "2.123457", doubleValue);
        doubleValue = UtilsMisc.formatDouble(2.1234563, 6);
        assertEquals("Rounding", "2.123456", doubleValue);

        String floatValue = UtilsMisc.formatFloat(2.1234567f, 3);
        assertEquals("Rounding", "2.123", floatValue);
        floatValue = UtilsMisc.formatFloat(200.1236563f, 3);
        assertEquals("Rounding", "200.124", floatValue);

        floatValue = UtilsMisc.formatFloat(2.1234567f, 2);
        assertEquals("Rounding", "2.12", floatValue);
        floatValue = UtilsMisc.formatFloat(200.1266563f, 2);
        assertEquals("Rounding", "200.13", floatValue);

        // calculate the percentage
        double percentage = UtilsMisc.calcPercentage(0, 100, 50);
        assertEquals("Percentage Error", 50.0, percentage, 0.001);

        percentage = UtilsMisc.calcPercentage(0, 200, 50);
        assertEquals("Percentage Error", 25.0, percentage, 0.001);

        percentage = UtilsMisc.calcPercentage(100, 200, 150);
        assertEquals("Percentage Error", 50.0, percentage, 0.001);

        percentage = UtilsMisc.calcPercentage(-100, 100, 0);
        assertEquals("Percentage Error", 50.0, percentage, 0.001);

        percentage = UtilsMisc.calcPercentage(-200, 200, 50.0f);
        assertEquals("Percentage Error", 62.5, percentage, 0.001);

        // smooth function
        //float[] raw1 = {1.0f, 2.0f, 3.0f, 25.0f, 6.0f, 7.0f, 8.0f};
        //double[] smoothed = UtilsMisc.smoothValues(raw1);
        //assertTrue("Smooth failed", arrayCheck(smoothed, new float[]{1.0f, 2.0f, 3.0f, 4.5f, 6.0f, 7.0f, 8.0f}));

        //float[] raw2 = {1.0f, 2.0f, 3.0f, 25.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        //smoothed = UtilsMisc.smoothData(raw2, 1.5f);
        //assertTrue("Smooth failed", arrayCheck(smoothed, new float[]{1.0f, 2.0f, 3.0f, 4.5f, 6.0f}));

        // conversion from float[] to Number
        float[] raw_float = new float[] {10.0f, 20.0f, 10.0f, 30.0f};
        raw_float = UtilsMisc.removeOutliers(raw_float, 3);

        raw_float = new float[] {10.0f, 20.0f, 10.0f, 90.0f};
        raw_float = UtilsMisc.removeOutliers(raw_float, 3);

        // conversion from float[] to Number
        raw_float = new float[] {10.0f, 20.0f, -1.0f, 30.0f};
        Number[] raw_nums = UtilsMisc.toNumber(raw_float);
        assertTrue("Convert Numbers failed 1", numberCheck(raw_nums, new Number[]{10.0, 20.0, -1.0, 30.0}, 0.5f));

        // interpolation
        Number[] raw = new Number[] {10.0, 20.0, 0.0, -1.0, -1.0, 30.0, -1.0, -1.0, 20.0};
        Number[] interpolated = UtilsMisc.Interpolate(raw, true);
        assertTrue("Smooth Numbers failed 1", numberCheck(interpolated, new Number[]{10.0, 20.0, 22.5, 25.0, 27.5, 30.0, 26.66, 23.33, 20.0}, 0.5f));

        raw = new Number[] {10.0, 20.0, 30.0,20.0};
        interpolated = UtilsMisc.Interpolate(raw, true);
        assertTrue("Smooth Numbers failed 2", numberCheck(interpolated, new Number[]{10.0, 20.0, 30.0, 20.0}, 0.5f));

        raw = new Number[] {0.0, 99.0, 99.0,99.0};
        interpolated = UtilsMisc.Interpolate(raw, true);
        assertTrue("Smooth Numbers failed 2a", numberCheck(interpolated, new Number[]{99.0, 99.0, 99.0, 99.0}, 0.5f));

        Number[] numbers =  {1.0f, 0.0f, 5.0f};
        interpolated = UtilsMisc.Interpolate(numbers, true);
        assertTrue("Smooth Numbers failed 3", numberCheck(interpolated, new Number[]{1.0, 3.0, 5.0}, 0.1f));

        numbers = new Number[] {-10.0f, 20.0f, null,20.0f};
        interpolated = UtilsMisc.Interpolate(numbers, false);
        assertTrue("Smooth Numbers failed 2", numberCheck(interpolated, new Number[]{-10.0, 20.0, 20.0, 20.0}, 0.5f));

        numbers = new Number[] {null, 1.0f, 0.0f, 3.0f};
        interpolated = UtilsMisc.Interpolate(numbers, true);
        assertTrue("Smooth Numbers failed 3", numberCheck(interpolated, new Number[]{1.0f, 1.0, 2.0, 3.0}, 0.1f));

        numbers = new Number[] {null, 1.0f, 0.0f, 3.0f, null, null};
        interpolated = UtilsMisc.Interpolate(numbers, true);
        assertTrue("Smooth Numbers failed 4", numberCheck(interpolated, new Number[]{1.0f, 1.0, 2.0, 3.0, 3.0, 3.0}, 0.1f));

        numbers = new Number[] {null, 20.0f, 0.0f, -10.0f, 30.0f, 10.0f, 20.0f, null};
        interpolated = UtilsMisc.fix_numbers(numbers, true);
        assertTrue("Smooth Numbers failed 5", numberCheck(interpolated, new Number[]{20.0, 20.0, 0.0, -10.0, 30.0, 10.0f, 20.0f, 20.0f}, 0.1f));

        // interpolate with positive numbers alone
        numbers = new Number[] {null, 20.0f, 0.0f, -10.0f, -15.0f, 30.0f, null, 20.0f, null};
        interpolated = UtilsMisc.Interpolate(numbers, true);
        assertTrue("Smooth Numbers failed 6", numberCheck(interpolated, new Number[]{20.0, 20.0, 22.5, 25.0, 27.5, 30.0, 25.0, 20.0f, 20.0f}, 0.1f));

        // interpolate with non-zero numbers alone
        numbers = new Number[] {null, 20.0f, null, -10.0f, -15.0f, 30.0f, null, 20.0f, null};
        interpolated = UtilsMisc.Interpolate(numbers, false);
        assertTrue("Smooth Numbers failed 7", numberCheck(interpolated, new Number[]{20.0, 20.0, 5.0f, -10.0, -15.0, 30.0, 25.0, 20.0f, 20.0f}, 0.1f));

        Integer[] nums =  new Integer[]{1, 0, 5};
        interpolated = UtilsMisc.Interpolate(nums, true);
        assertTrue("Smooth Numbers failed 8", numberCheck(interpolated, new Number[]{1, 3, 5}, 0.1f));

        Number[] numsi = new Number[] {0, 20, 0, -10, -15, 30, 0, 20, 0};
        interpolated = UtilsMisc.Interpolate(numsi, true);
        assertTrue("Smooth Numbers failed 9", numberCheck(interpolated, new Number[]{20, 20, 22.5, 25.0, 27.5, 30, 25, 20, 20}, 0.1f));

        numsi = new Number[] {0, 20, 0, -10, -15, 30, 0, 20, 0};
        interpolated = UtilsMisc.Interpolate(numsi, false);
        assertTrue("Smooth Numbers failed 10", numberCheck(interpolated, new Number[]{20, 20, 5.0, -10.0, -15.0, 30, 25, 20, 20}, 0.1f));

        // linear interpolation
        ArrayList<Number> linear = UtilsMisc.LinearInterpolate(10, 20, 4);
        assertTrue("Linear interpolation 1 failed", numberCheck(linear.toArray(new Number[linear.size()]),
                                                               new Number[]{12, 14, 16, 18}, 0.5f));

        linear = UtilsMisc.LinearInterpolate(-10, -20, 4);
        assertTrue("Linear interpolation 2 failed", numberCheck(linear.toArray(new Number[linear.size()]),
                new Number[]{-12, -14, -16, -18}, 0.5f));

        linear = UtilsMisc.LinearInterpolate(-10, 10, 9);
        assertTrue("Linear interpolation 3 failed", numberCheck(linear.toArray(new Number[linear.size()]),
                new Number[]{-8, -6, -4, -2, 0, 2, 4, 6, 8}, 0.5f));

        assertTrue("Bezier interpolation failed", bezierTest());

        // file suffix extraction
        String suffix = UtilsFile.getFileSuffix("/opt/android/aa.txt");
        assertEquals("txt", suffix);
        suffix = UtilsFile.getFileSuffix("C:\\Progams Files\\code\\test.java");
        assertEquals("java", suffix);

        // calculate the bearing between 2 locations specified in micro-degrees
        int bearing = UtilsMap.getBearing(38904800, -77035400, 55750000, 37583000);
        assertEquals(33, bearing);
        bearing = UtilsMap.getBearing(38904800, -77035400, 28632799, 77219498);
        assertEquals(24, bearing);
        bearing = UtilsMap.getBearing(38904800, -77035400, 40748798, -73984596);
        assertEquals(51, bearing);
        bearing = UtilsMap.getBearing(39099912, -94581213, 38627089, -90200203);
        assertEquals(97, bearing);
        bearing = UtilsMap.getBearing(38904800, -77035400, 38912674, -77227706);
        assertEquals(273, bearing);
        bearing = UtilsMap.getBearing(38904800, -77035400, 38904800, -77035400);
        assertEquals(0, bearing);
        bearing = UtilsMap.getBearing(38904800, -77035400, 38904800, -77034400);
        assertEquals(90, bearing);
        bearing = UtilsMap.getBearing(38904800, -77035400, 36904800, -77035400);
        assertEquals(180, bearing);
        bearing = UtilsMap.getBearing(38904800, -77035400, 38904800, -77035600);
        assertEquals(270, bearing);
        bearing = UtilsMap.getBearing(38904800, -77035400, 39009313, -77037849);
        assertEquals(359, bearing);

        int angle = UtilsMap.getAngle(358, 359);
        assertEquals(1, angle);
        angle = UtilsMap.getAngle(350, 0);
        assertEquals(10, angle);
        angle = UtilsMap.getAngle(180, 0);
        assertEquals(180, angle);
        angle = UtilsMap.getAngle(270, 180);
        assertEquals(90, angle);
        angle = UtilsMap.getAngle(45, 135);
        assertEquals(90, angle);
        angle = UtilsMap.getAngle(45, 275);
        assertEquals(130, angle);
        angle = UtilsMap.getAngle(280, 45);
        assertEquals(125, angle);


        String xxx = "   ";
        assertEquals("String mismatch", xxx, UtilsMisc.fixedLengthString(" ", 3));

        String phoneNumString = "9876543210";
        String[] phoneNums = UtilsMisc.getPhoneNumbers(phoneNumString);
        assertEquals(phoneNumString, phoneNums[0]);

        phoneNumString = "9876543210, 123-456-7890, 5432109876";
        phoneNums = UtilsMisc.getPhoneNumbers(phoneNumString);
        assertEquals("9876543210", phoneNums[0]);
        assertEquals("123-456-7890", phoneNums[1]);
        assertEquals("5432109876", phoneNums[2]);

     //   UtilsMisc.genSounds(mainActvity, UtilsMap.getResid(90, 90));
     //   UtilsMisc.genSounds(mainActvity, UtilsMap.getResid(90, 0));
     //   UtilsMisc.genSounds(mainActvity, UtilsMap.getResid(90, 180));

/*
        int bearing0 = 0; int bearing1 = 90;
        int MAX_VOLUME = 100;
        MediaPlayer mediaPlayer = MediaPlayer.create(mainActvity, UtilsMap.getResid(bearing0, bearing1));

        int currVolume = 95;
        float logVal  = (float) (Math.log(MAX_VOLUME-currVolume)/Math.log(MAX_VOLUME));
        mediaPlayer.setVolume(1-logVal, 1-logVal);
        mediaPlayer.start();
        try {
            Thread.sleep(mediaPlayer.getDuration());
        } catch (InterruptedException ue){
        }
        */
    }

    public void checkPowerFunctions() {
        // density decreases with elevation

        float density = UtilsMap.get_density(0.0f, 25, 10);
        assertTrue(floatCheck(1.1842929f, density));
        density = UtilsMap.get_density(1000.0f, 25, 10);
        assertTrue(floatCheck(1.0573531f, density));
        density = UtilsMap.get_density(2000.0f, 25, 10);
        assertTrue(floatCheck(0.9462819f, density));
        density = UtilsMap.get_density(4000.0f, 25, 10);
        assertTrue(floatCheck(0.7630813f, density));

        // density increases with lower temps.
        density = UtilsMap.get_density(0.0f, 15, 10);
        assertTrue(floatCheck(1.2253935f, density));
        density = UtilsMap.get_density(0.0f, 5, 10);
        assertTrue(floatCheck(1.269449f, density));
        density = UtilsMap.get_density(0.0f, 0, 10);
        assertTrue(floatCheck(1.2926863f, density));
        density = UtilsMap.get_density(0.0f, -5, 10);
        assertTrue(floatCheck(1.3167902f, density));

        //
        int flipped_direction = UtilsMap.flip_direction(0);
        assertEquals(180, flipped_direction);
        flipped_direction = UtilsMap.flip_direction(90);
        assertEquals(270, flipped_direction);
        flipped_direction = UtilsMap.flip_direction(180);
        assertEquals(0, flipped_direction);
        flipped_direction = UtilsMap.flip_direction(270);
        assertEquals(90, flipped_direction);
        flipped_direction = UtilsMap.flip_direction(360);
        assertEquals(180, flipped_direction);

        int angle = Math.abs(UtilsMap.get_angle(3.0, 4.0, -3.0, -4.0));
        assertEquals(180, angle);

        // check calculation of head wind from bike and wind velocities
        double head_wind = UtilsWind.get_head_wind(8, UtilsMap.bearingToDegrees(360),
                6, UtilsMap.bearingToDegrees(360));
        assertTrue("Head wind1", doubleCheck(14.0, head_wind));
        head_wind = UtilsWind.get_head_wind(8, UtilsMap.bearingToDegrees(90),
                6, UtilsMap.bearingToDegrees(270));
        assertTrue("Head wind2",doubleCheck(-2.0, head_wind));
        head_wind = UtilsWind.get_head_wind(8, UtilsMap.bearingToDegrees(230),
                6, UtilsMap.bearingToDegrees(280));
        assertTrue("Head wind3",doubleCheck(11.14, head_wind));
        head_wind = UtilsWind.get_head_wind(8, UtilsMap.bearingToDegrees(80),
                6, UtilsMap.bearingToDegrees(260));
        assertTrue("Head wind4",doubleCheck(-2.0, head_wind));

        // check calculation of power from a bunch of parameters
        double m = 70 + 12;
        double c_r = 0.005;
        double c_d = 1.0;
        double front_area = 0.388;
        DetailProvider.Detail currD = DetailProvider.createEmptyDetail(); currD.setSpeed(18.0f);

        //*---- change magnitude of wind speed
        String extras = "{\"temp\":\"25.00\",\"humidity\":\"69.00\",\"wind_speed\":\"0.00\",\"wind_deg\":\"260.00\"}";
        currD.setExtras(extras);
        DetailProvider.Detail prevD = DetailProvider.createEmptyDetail();
        double power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 1 " + power,doubleCheck(51.4, power));

        extras = "{\"temp\":\"25.00\",\"humidity\":\"69.00\",\"wind_speed\":\"2.00\",\"wind_deg\":\"260.00\"}";
        currD.setExtras(extras);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 2 " + power, doubleCheck(46.00, power));

        extras = "{\"temp\":\"25.00\",\"humidity\":\"69.00\",\"wind_speed\":\"4.00\",\"wind_deg\":\"260.00\"}";
        currD.setExtras(extras);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 3 " + power, doubleCheck(39.31, power));

        //*---- change wind direction
        extras = "{\"temp\":\"25.00\",\"humidity\":\"69.00\",\"wind_speed\":\"8.00\",\"wind_deg\":\"270.00\"}";
        currD.setExtras(extras);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 4 " + power, doubleCheck(39.49, power));

        extras = "{\"temp\":\"25.00\",\"humidity\":\"69.00\",\"wind_speed\":\"8.00\",\"wind_deg\":\"330.00\"}";
        currD.setExtras(extras);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 5 " + power, doubleCheck(187.29, power));

        extras = "{\"temp\":\"25.00\",\"humidity\":\"69.00\",\"wind_speed\":\"8.00\",\"wind_deg\":\"30.00\"}";
        currD.setExtras(extras);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 6 " + power, doubleCheck(187.29, power));

        //*--- change gradient from 1% with zero wind
        extras = "{\"temp\":\"25.00\",\"humidity\":\"69.00\",\"wind_speed\":\"0.00\",\"wind_deg\":\"260.00\"}";
        currD.setExtras(extras);
        currD.setGradient(1.0f);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 7 " + power, doubleCheck(93.73, power));

        currD.setGradient(2.5f);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 8 " + power, doubleCheck(157.24, power));

        currD.setGradient(5.0f);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 9 " + power, doubleCheck(263.08, power));

        currD.setGradient(10.0f);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 10 " + power, doubleCheck(474.77, power));

        currD.setGradient(-0.5f);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 10 " + power, doubleCheck(30.23, power));

        //*--- change gradient from -0.5% with wind
        extras = "{\"temp\":\"25.00\",\"humidity\":\"69.00\",\"wind_speed\":\"2.00\",\"wind_deg\":\"260.00\"}";
        currD.setGradient(-0.5f);
        currD.setExtras(extras);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 11 " + power, doubleCheck(24.83, power));

        currD.setGradient(0.5f);
        power = UtilsMap.get_power_test(m, c_r, c_d, front_area, currD, prevD);
        assertTrue("Power 12 " + power, doubleCheck(67.17, power));
    }

    public void checkJSONFunctions() {
        // check the add waypoints function
        String extras = "{\"edited\":true,\"time_1\":520616}";
        ArrayList<String> wayPoints = new ArrayList<>();
        wayPoints.add("13.456789,77.04040500");
        wayPoints.add("13.456788,77.04040500");
        String out = UtilsJSON.addWayPoints(wayPoints, extras);
        assertEquals("{\"edited\":true,\"time_1\":520616,\"wpt_0\":\"13.456789,77.04040500\",\"wpt_1\":\"13.456788,77.04040500\"}", out);
        extras = "";
        wayPoints = new ArrayList<>();
        wayPoints.add("13.456789,77.04040500");
        wayPoints.add("13.456788,77.04040500");
        out = UtilsJSON.addWayPoints(wayPoints, extras);
        assertEquals("{\"wpt_0\":\"13.456789,77.04040500\",\"wpt_1\":\"13.456788,77.04040500\"}", out);

        // check the del waypoints function
        extras = "{\"edited\":true,\"time_1\":520616,\"wpt_0\":\"13.456789,77.04040500\",\"wpt_1\":\"13.456788,77.04040500\"}";
        out = UtilsJSON.delWayPoints(extras);
        assertEquals("{\"edited\":true,\"time_1\":520616}", out);
        extras = "{\"wpt_0\":\"13.456789,77.04040500\",\"wpt_1\":\"13.456788,77.04040500\"}";
        out = UtilsJSON.delWayPoints(extras);
        assertEquals("{}", out);
        extras = "";
        out = UtilsJSON.delWayPoints(extras);
        assertEquals("", out);

        // check the get waypoints function
        extras = "{\"edited\":true,\"time_1\":520616,\"wpt_0\":\"13.456789,77.04040500\",\"wpt_1\":\"13.456788,77.04040500\"}";
        wayPoints = UtilsJSON.getWayPoints(extras);
        assertEquals("13.456789,77.04040500!!!13.456788,77.04040500!!!", dumpArrayList(wayPoints));
        extras = "{\"wpt_0\":\"13.456789,77.04040500\",\"wpt_1\":\"13.456788,77.04040500\"}";
        wayPoints = UtilsJSON.getWayPoints(extras);
        assertEquals("13.456789,77.04040500!!!13.456788,77.04040500!!!", dumpArrayList(wayPoints));
        extras = "";
        wayPoints = UtilsJSON.getWayPoints(extras);
        assertEquals("", dumpArrayList(wayPoints));

        // generate waypoint keys
        final int FIFTY_METERS = 50;
        int latw = 13022321; int lonw = 77579891;
        String key_string = "";
        String[] keys = UtilsMap.gen_keys_distance(latw, lonw, FIFTY_METERS);
        for (String key: keys) key_string += key + "!";
        String expected = "13022639:77580217!13022639:77579565!13022003:77580217!13022003:77579565!";
        assertEquals(expected, key_string);

        String distance = "";
        for (String key: keys) {
            String[] parts = key.split(":");
            int lat0 = Integer.parseInt(parts[0]);
            int lon0 = Integer.parseInt(parts[1]);
            long m_distance = Math.round( UtilsMap.getMeterDistance(lat0, lon0, latw, lonw));
            distance += m_distance + "!";
        }
        assertEquals("50!50!50!50!", distance);

    }

    public void checkSMSFunction() {
        String smsNumber = "9880934801";
        String message = " Hello there";
        SMSTracker.sendSMSMessage(smsNumber, message);
    }

    public void checkWindFunctions() {

         // Density calculation
        double TEMP = 25; // centigrade
        double ELEVATION = 0; // meters
        double DENSITY = (1.293 - 0.00426 * TEMP) * Math.exp(-ELEVATION / 7000.0);

        // Set Bicycle and Rider Parameters
        double RIDER_WT = 70 * 9.8;
        double BICYCLE_WT = 10 * 9.8;
        double TOTAL_WT = RIDER_WT + BICYCLE_WT; // Newtons

        // Calculate grade resistance
        double GRADE_V = 0.00;
        double GRADE_RES = GRADE_V * TOTAL_WT;

        // Calculate rolling resistance
        // Clinchers: 0.005, Tubular: 0.004, MTB: 0.012
        double ROLL_V = 0.005;
        double ROLLING_RES = ROLL_V * TOTAL_WT;
        double OTHER_RES = GRADE_RES + ROLLING_RES;

        // calculate K_A: aerodynamic drag factor
        // C_D_Area Hoods: 0.388  Bartops: 0.445  Barends: 0.42  Drops: 0.3 Aerobar: 0.233
        double FRONTAL_AREA = 0.5;   // m^2
        double C_D = 1.15;
        double C_D_AREA = C_D * FRONTAL_AREA;
        C_D_AREA = 0.388;
        double K_A = 0.5 * C_D_AREA * DENSITY;

        // set wind parameters
        double V_WIND = 0; // wind velocity in kmph.
        double V_DEG = 0;  // direction (bearing of head wind)

        double TOLERANCE = 1.0;

        // Test 1
        double power = 200; // watts
        double velocity = UtilsWind.get_velocity(K_A, 0, V_WIND, V_DEG, power, OTHER_RES);
        double expected = 32;
        assertTrue(Math.abs(Math.ceil(velocity) - expected) <= TOLERANCE);

        // Test 2:
        power = 100;     // watts
        velocity = UtilsWind.get_velocity(K_A, 0, V_WIND, V_DEG, power, OTHER_RES);
        expected = 24;
        assertTrue(Math.abs(Math.ceil(velocity) - expected) <= TOLERANCE);

        // Test 3:
        V_WIND = 20; // kmph.
        V_DEG = 0;
        velocity = UtilsWind.get_velocity(K_A, 0, V_WIND, V_DEG, power, OTHER_RES);
        expected = 14;
        assertTrue(Math.abs(Math.ceil(velocity) - expected) <= TOLERANCE);

        // Test 4:
        V_WIND = 10; // kmph.
        V_DEG = 0;
        velocity = UtilsWind.get_velocity(K_A, 0, V_WIND, V_DEG, power, OTHER_RES);
        expected = 18;
        assertTrue(Math.abs(Math.ceil(velocity) - expected) <= TOLERANCE);

        // Test 5:
        ROLL_V = 0.012;
        ROLLING_RES = ROLL_V * TOTAL_WT;
        OTHER_RES = GRADE_RES + ROLLING_RES;
        velocity = UtilsWind.get_velocity(K_A, 0, V_WIND, V_DEG, power, OTHER_RES);
        expected = 16;
        assertTrue(Math.abs(Math.ceil(velocity) - expected) <= TOLERANCE);

        // Test 6:
        GRADE_V = 0.07;
        GRADE_RES = GRADE_V * TOTAL_WT;
        OTHER_RES = GRADE_RES + ROLLING_RES;
        velocity = UtilsWind.get_velocity(K_A, 0, V_WIND, V_DEG, power, OTHER_RES);
        expected = 5;
        assertTrue(Math.abs(Math.ceil(velocity) - expected) <= TOLERANCE);

        // Test 7:
        GRADE_V = 0.00;
        GRADE_RES = GRADE_V * TOTAL_WT;
        ROLL_V = 0.005;
        ROLLING_RES = ROLL_V * TOTAL_WT;
        OTHER_RES = GRADE_RES + ROLLING_RES;
        V_WIND = 30; V_DEG = 180;
        power = 200.0;
        for (V_DEG = 0; V_DEG < 360; V_DEG++) {
            velocity = UtilsWind.get_velocity(K_A, 0, V_WIND, V_DEG, power, OTHER_RES);
            //System.out.println("Deg: " + V_DEG + " Vel: " + velocity);
        }
        //expected = 5;
        //assertTrue(Math.abs(Math.ceil(velocity) - expected) <= TOLERANCE);

        // power tests
        // calculate air drag
        double V_BIKE = 10; // bicycle velocity in kmph.
        V_WIND = 0;      // wind velocity in kmph.
        V_DEG = 0;   // direction (bearing of head wind)
        double AIR_RES = UtilsWind.calc_air_res(K_A, V_BIKE, V_WIND, V_DEG, 0);
        TOLERANCE = 1.0;

        // Test 1
        expected = 17;
        GRADE_V = 0.00;
        GRADE_RES = GRADE_V * TOTAL_WT;

        ROLL_V = 0.005;
        ROLLING_RES = ROLL_V * TOTAL_WT;
        power = Math.ceil(UtilsWind.calc_power(GRADE_RES + ROLLING_RES + AIR_RES, V_BIKE));
        assertTrue (Math.abs(power - expected) <= TOLERANCE);

        // Test 2
        expected = 64;
        V_BIKE = 20;
        AIR_RES = UtilsWind.calc_air_res(K_A, V_BIKE, V_WIND, V_DEG, 0);
        power = Math.ceil(UtilsWind.calc_power(GRADE_RES + ROLLING_RES + AIR_RES, V_BIKE));
        assertTrue (Math.abs(power - expected) <= TOLERANCE);

        // Test 3
        expected = 15;
        V_BIKE = 10;
        C_D_AREA = 0.233;
        K_A = 0.5 * C_D_AREA * DENSITY;
        AIR_RES = UtilsWind.calc_air_res(K_A, V_BIKE, V_WIND, V_DEG, 0);
        power = Math.ceil(UtilsWind.calc_power(GRADE_RES + ROLLING_RES + AIR_RES, V_BIKE));
        assertTrue (Math.abs(power - expected) <= TOLERANCE);


        // Test 4
        expected = 48;
        V_BIKE = 20;
        AIR_RES = UtilsWind.calc_air_res(K_A, V_BIKE, V_WIND, V_DEG, 0);
        power = Math.ceil(UtilsWind.calc_power(GRADE_RES + ROLLING_RES + AIR_RES, V_BIKE));
        assertTrue (Math.abs(power - expected) <= TOLERANCE);


        // Test 5
        expected = 80;
        ROLL_V = 0.012;
        ROLLING_RES = ROLL_V * TOTAL_WT;
        power = Math.ceil(UtilsWind.calc_power(GRADE_RES + ROLLING_RES + AIR_RES, V_BIKE));
        assertTrue (Math.abs(power - expected) <= TOLERANCE);

        // Test 6
        expected = 99;
        V_BIKE = 24.0;
        ROLLING_RES = 0.005 * TOTAL_WT;
        GRADE_RES = 0.0;
        K_A = 0.5 * 0.388 * DENSITY;
        AIR_RES = UtilsWind.calc_air_res(K_A, V_BIKE, V_WIND, V_DEG, 0);
        power = Math.ceil(UtilsWind.calc_power(GRADE_RES + ROLLING_RES + AIR_RES, V_BIKE));
        assertTrue (Math.abs(power - expected) <= TOLERANCE);
    }


    public void dumpSharedParameters(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, String> prefMap = new HashMap<String, String>() {{
            put(Constants.PREF_FIRST_TRIP, "b");
            put(Constants.PREF_LONGITUDE, "f");
            put(Constants.PREF_LATITUDE, "f");
            put(Constants.PREF_LOCATION_INIT_TIME, "l");
            put(Constants.PREF_TRIP_ID, "i");
            put(Constants.PREF_TRIP_DURATION, "l");
            put(Constants.PREF_AVG_SPEED, "d");
            put(Constants.PREF_DISTANCE, "i");
            put(Constants.PREF_INS_SPEED, "d");
            put(Constants.PREF_SMS_TIME, "l");
            put(Constants.PREF_BATTERY, "d");
            put(Constants.PREF_BEEP_PERIOD, "s");
            put(Constants.PREF_LOG_FILE, "s");
            put(Constants.PREF_FOLLOW_TRIP, "i");
            put(Constants.PREF_FOLLOW_COUNTER, "i");
            put(Constants.PREF_TIME_INCR, "i");
            put(Constants.PREF_FIRST_LOCATION_TIME, "l");
            put(Constants.PREF_LAST_LOCATION_TIME, "l");
            put(Constants.PREF_DIRECTION_SCREEN_VISIBLE, "b");
            put(Constants.PREF_WALKING_SPEED, "s");
            put(Constants.PREF_DEBUG_MODE, "s");
            put(Constants.PREF_TRIP_TYPE, "s");
            put(Constants.PREF_PLOT_SPEED, "s");
            put(Constants.PREF_NUMBER_TRIP_ROWS, "s");
            put(Constants.PREF_NUMBER_TRIP_READINGS, "s");
            put(Constants.PREF_WAKEUP_GPS, "b");
            put(Constants.PREF_LOCATION_PROVIDER, "s");
            put(Constants.PREF_LOCATION_ACCURACY, "s");
            put(Constants.PREF_FILE_FORMAT, "s");
            put(Constants.PREF_POLL_INTERVAL, "s");
            put(Constants.PREF_GOOGLE_API_KEY, "s");
            put(Constants.PREF_OPENWEATHER_API_KEY, "s");
            put(Constants.PREF_AUTOSTOP, "s");
            put(Constants.PREF_FIRST_TIME, "b");
            put(Constants.PREF_SMS_NUMBER, "s");
            put(Constants.PREF_SMS_PERIOD, "s");
            put(Constants.PREF_SMS_SEND, "s");
            put(Constants.PREF_SMS_DISTANCE, "l");
            put(Constants.PREF_BIKE_RIDER, "s");
            put(Constants.PREF_BIKE_BIKE, "s");
            put(Constants.PREF_BIKE_FRONT, "s");
            put(Constants.PREF_BIKE_ROLLING, "s");
            put(Constants.PREF_BIKE_DRAG, "s");
            put(Constants.PREF_BIKE_COMMIT, "s");
            put(Constants.PREF_BIKE_COMMIT_DURATION, "l");
        }};

        TreeMap<String, String> sorted = new TreeMap<>();
        sorted.putAll(prefMap);
        for (Map.Entry<String,String> entry : sorted.entrySet()) {
            String t = entry.getValue();
            String v = "";
            //Log.d("Misc", "Key = " + entry.getKey() + ", Value = " + v);
            if (t.equals("b"))
                v = "" + sp.getBoolean(entry.getKey(), false);
            else if (t.equals("i"))
                v = "" + sp.getInt(entry.getKey(), 0);
            else if (t.equals("d") || t.equals("f"))
                v = "" + sp.getFloat(entry.getKey(), 0.0f);
            else if (t.equals("s"))
                v = "" + sp.getString(entry.getKey(), "0");
            else if (t.equals("l"))
                v = "" + sp.getLong(entry.getKey(), 0L);
            Logger.d("Misc", "Key = " + entry.getKey() + ", Value = " + v);
        }
    }
    private boolean intArrayCheck(int[] one, int[] two) {
        if (one.length != two.length) return false;
        for (int i = 0; i < one.length; i++) {
            if (one[i] != two[i]) return false;
        }
        return true;
    }

    private boolean numberCheck(Number[] one, Number[] two, float tolerance) {
        if (one.length != two.length) return false;
        for (int i = 0; i < one.length; i++) {
            if (Math.abs(one[i].floatValue() - two[i].floatValue()) > tolerance)
                return false;
        }
        return true;
    }

    private boolean stringCheck(String first, String second) {
        String a = first.toUpperCase();
        String b = second.toUpperCase();
        return a.equals(b);
    }

    private boolean bezierTest() {

        // Set control points
        UtilsMisc.XYPoint p1 = new UtilsMisc.XYPoint(100, 300, 0);
        UtilsMisc.XYPoint p2 = new UtilsMisc.XYPoint(150, 100, 0);
        UtilsMisc.XYPoint p3 = new UtilsMisc.XYPoint(200, 100, 0);
        UtilsMisc.XYPoint p4 = new UtilsMisc.XYPoint(250, 300, 0);
        ArrayList<UtilsMisc.XYPoint> points = UtilsMisc.BezierInterpolate(p1, p2, p3, p4);
        String expected = " 100 300 104 285 104 285 108 271 108 271 111 258 111 258 115 246 115" +
                          " 246 119 234 119 234 123 223 123 223 126 213 126 213 130 204 130 204" +
                          " 134 195 134 195 138 187 138 187 141 180 141 180 145 174 145 174 149" +
                          " 168 149 168 153 163 153 163 156 159 156 159 160 156 160 156 164 153" +
                          " 164 153 168 151 168 151 171 150 171 150 175 150 175 150 179 150 179" +
                          " 150 183 152 183 152 186 153 186 153 190 156 190 156 194 159 194 159" +
                          " 198 164 198 164 201 168 201 168 205 174 205 174 209 180 209 180 213" +
                          " 188 213 188 216 195 216 195 220 204 220 204 224 213 224 213 228 224" +
                          " 228 224 231 234 231 234 235 246 235 246 239 258 239 258 243 272 243" +
                          " 272 246 285 246 285 250 300";
        StringBuilder results = new StringBuilder();
        for (int i = 1; i < points.size(); i++) {
            int x1 = (int) Math.round(points.get(i-1).getX());
            int y1 = (int) Math.round(points.get(i-1).getY());
            int x2 = (int) Math.round(points.get(i).getX());
            int y2 = (int) Math.round(points.get(i).getY());
            String out = " " + x1 + " " +  y1 + " " + x2 + " " + y2;
            results.append(out);
        }
        return (expected.compareTo(results.toString()) == 0);
    }

    private String dumpArrayList(ArrayList<String> arrayList) {
        StringBuilder out = new StringBuilder();
        for (String key: arrayList)
            out.append(key + "!!!");
        return out.toString();
    }

    private String dumpArray(String[] arrayList) {
        StringBuilder out = new StringBuilder();
        for (String key: arrayList)
            out.append(key + "!!!");
        return out.toString();
    }

    private boolean doubleCheck(double d1, double d2) {
        return Math.abs(d1-d2) < 0.5;
    }

    private boolean floatCheck(float f1, float f2) {
        return Math.abs(f1-f2) < 0.5f;
    }
}