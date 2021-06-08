package org.mkonchady.mytripoo.utils;

import android.telephony.SmsManager;

import org.mkonchady.mytripoo.Constants;
import org.mkonchady.mytripoo.Logger;
import java.util.ArrayList;
import java.util.Random;

public class SMSTracker {
    public static void sendSMSMessage(String phoneNumString, String message) {
        final String[] greetings = {
                "Hi, I am at ",
                "Hi there, I am cruising at ",
                "Helooo, I am struggling at ",
                "Yoo Hoo, I am still alive at ",
                "Hey, I was at "};
        final Random random = new Random();

        String sentMsg = UtilsDate.getDateTime(System.currentTimeMillis(), Constants.LARGE_INT, Constants.LARGE_INT) + "\n" +
                greetings[random.nextInt(greetings.length)] + message;
        //sentMsg += "\ud83d\ude01";
        //sentMsg += " \nLove";
        Logger.d("SMSTracker: ", "Sending message: " + sentMsg + " (" + sentMsg.length() + "chars)", 2);
        SmsManager smsManager = SmsManager.getDefault();
        String[] phoneNums = UtilsMisc.getPhoneNumbers(phoneNumString);
        for (String phoneNum: phoneNums) {
            smsManager.sendTextMessage(phoneNum, null, sentMsg, null, null);
            ArrayList<String> parts = smsManager.divideMessage(sentMsg);
            smsManager.sendMultipartTextMessage(phoneNum, null, parts, null, null);
        }
    }
}
    /*
    public static void sendSMS(Context context, String phoneNum, String message) {
        if (phoneNum.length() < 9) return;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.PREF_DEBUG_MODE, "0"));
        SmsManager smsMgr = SmsManager.getDefault();
        Intent sentIntent = new Intent(SENT_SMS_ACTION);
        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, 0);
        Intent deliveredIntent = new Intent(DELIVERED_SMS_ACTION);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, deliveredIntent, 0);

        // register the receivers
        context.registerReceiver(new SentReceiver(), new IntentFilter(SENT_SMS_ACTION));
        context.registerReceiver(new DeliverReceiver(), new IntentFilter(DELIVERED_SMS_ACTION));
        String sentMsg =  UtilsDate.getDateTime(System.currentTimeMillis()) + "\n" +
                greetings[random.nextInt(greetings.length)] + message;
        sentMsg += "\ud83d\ude01";
        sentMsg += " \nLove";

        smsMgr.sendTextMessage(phoneNum, null, sentMsg, sentPI, deliveredPI);
        Log.d(TAG, "Sent a message to " + phoneNum, localLog);
    }
}

    class SentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String result = "Unknown";
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    result = "Transmission successful";
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    result = "SMS generic failure actions";
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    result = "SMS radio off failure actions";
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    result = "SMS null PDU failure actions";
                    break;
            }
            Log.d("SentReceiver", result, localLog);
        }
    }

    class DeliverReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String result = "SMS delivered";
            Log.d("SentReceiver", result, localLog);
        }
    }
*/