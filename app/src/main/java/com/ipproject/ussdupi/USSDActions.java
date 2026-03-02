package com.ipproject.ussdupi;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.content.ContextCompat;

public class USSDActions {
    Context context;
    public USSDActions(Context context) {
        this.context = context;
    }

    boolean sendUSSDCommand(String mmiCode) throws SecurityException {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                telephonyManager.sendUssdRequest(mmiCode, new TelephonyManager.UssdResponseCallback() {
                    @Override
                    public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
                        super.onReceiveUssdResponse(telephonyManager, request, response);

                        String ussdResponse = response.toString();
                        Log.d("USSD_SUCCESS", "Response received: " + ussdResponse);

                        if (ussdResponse.equals("Welcome to *99#")) {

                        }

                    }


                    @Override
                    public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
                        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode);

                        Log.e("USSD_ERROR", "USSD Request Failed. Code: " + failureCode);
                    }
                }, new Handler(Looper.getMainLooper()));
            } else {
                System.out.println("This feature uses Android API 26 or higher. Upgrade first!");
            }
        }
        return true;
    }
}
