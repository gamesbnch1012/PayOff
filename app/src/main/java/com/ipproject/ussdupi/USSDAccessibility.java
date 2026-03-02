package com.ipproject.ussdupi;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.EditText;
import android.widget.ProgressBar;

import java.util.List;

public class USSDAccessibility extends AccessibilityService {

    private String lastReadText = "";
    private String nextStep = "";
    private View onTopView;
    private WindowManager windowManager;
    USSDActions ussd = new USSDActions(this);
    SharedPreferences curTransactionDetails, userSettings;
    String upiID = "";
    String amount = "";
    String remark = "";
    String payeeName = "";
    String upiPIN = "";
    boolean reallyPay = true, accessibilityEnabled = false;
    AccessibilityNodeInfo source;
    ProgressBar progressBar;
    CountDownTimer stepTimeout;


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Only process if the user actually clicked the button in MainActivity
        //if (!DataBridge.isServiceActive) return;

        // Use getWindows() to see through the overlay to the USSD below
        List<AccessibilityWindowInfo> windows = getWindows();
        StringBuilder entireText = new StringBuilder();

        readSharedPreferences();

        stepTimeout = new CountDownTimer(15000, 1000) {
            @Override
            public void onFinish() {
                pressButton("Cancel", source, 1);
                nextStep = "";
                curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-3").apply();
            }

            @Override
            public void onTick(long millisUntilFinished) {

            }
        };

        ValuePassHelper.sharedValue.observeForever(newValue -> {
            accessibilityEnabled = Boolean.parseBoolean(newValue);
        });

        if(!accessibilityEnabled){
            Log.d("Accessibility", "Accessibility event occurred, but ignored");
            return;
        }

        for (AccessibilityWindowInfo window : windows) {
            int type = window.getType();
            if (type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                    type != AccessibilityWindowInfo.TYPE_SYSTEM) {
                continue;
            }
            source = window.getRoot();
            if (source != null) {
                if(userSettings.getString("REALLY_PAY", "true").equals("false")){
                    reallyPay = false;
                } else {
                    reallyPay = true;
                }
                String pkg = source.getPackageName()!=null ? source.getPackageName().toString() : "";
                if(pkg.equals("com.android.phone") || pkg.equals("com.google.android.dialer") || pkg.equals("com.android.stk")) {
                    findAndReadText(source, entireText);
                    String result = entireText.toString().trim();
                    if (!result.isEmpty() && !result.equals(lastReadText)) {
                        lastReadText = result;
                        System.out.println("Accessibility detected content: " + result);
                        performNextStep(result, source);
                    }
                }
                source.recycle();
            }
        }
    }

    public void showPaymentProgress() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 1. Inflate your specific XML file
        onTopView = LayoutInflater.from(this).inflate(R.layout.payment_in_progress, null);

        // 2. Set the Parameters to be "On Top"
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                // This is the magic layer that beats USSD messages
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        // 3. Add the view to the screen
        if (Settings.canDrawOverlays(this)) {
            windowManager.addView(onTopView, params);
            progressBar = onTopView.findViewById(R.id.paymentProgress);
            // You can now find views inside your XML like this:
            // TextView title = overlayView.findViewById(R.id.overlay_title);
        } else {
            // Request permission if not granted
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    public void hidePaymentProgress() {
        if (windowManager != null && onTopView != null) {
            windowManager.removeView(onTopView);
            onTopView = null;
            stepTimeout.cancel();
        }
    }

    private void findAndReadText(AccessibilityNodeInfo node, StringBuilder builder) {
        if (node == null) return;

        if (node.getText() != null) {
            //String textReadNow = node.getText().toString();
            //Log.d("USSD_SERVICE", "Screen Text: " + node.getText().toString());
            builder.append(node.getText().toString());
            builder.append("\n");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            findAndReadText(node.getChild(i), builder);
        }
    }

    @Override
    public void onInterrupt() {
    }

    void readSharedPreferences(){
        userSettings = getSharedPreferences("USER_SETTINGS", MODE_PRIVATE);
        curTransactionDetails = getSharedPreferences("TRANSACTION_DATA", MODE_PRIVATE);
        upiID = curTransactionDetails.getString("UPI_ID", "");
        amount = curTransactionDetails.getString("AMOUNT", "");
        remark = curTransactionDetails.getString("REMARK", "");
        payeeName = curTransactionDetails.getString("PAYEE_NAME", "");
        upiPIN = curTransactionDetails.getString("UPI_PIN", "");
    }


    void performNextStep(String curScreenText, AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;

        if(curScreenText.contains("Welcome to BHIM *99#")){
            readSharedPreferences();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
            userSettings = getSharedPreferences("USER_SETTINGS", MODE_PRIVATE);
            if(userSettings.getString("BANK", "UNSET").equals("UNSET")) {
                pressButton("Cancel", rootNode, 1);
                nextStep = "";
                curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-5").apply();
                //hidePaymentProgress();
                stepTimeout.cancel();
            } else {
                enterTextInField(userSettings.getString("BANK", "0"), rootNode, 1);
                pressButton("Send", rootNode, 2);
                nextStep = "";
            }
        } else if(curScreenText.contains("3 is not a valid selection.") || curScreenText.contains("Could not find your bank")){
            readSharedPreferences();
            pressButton("Cancel", rootNode, 2);
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "0").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "100")
                                .putString("TRANSACTION_FINISH", "-5")
                                .putString("TIMER_INACTIVE", "1").apply();
        } else if(curScreenText.contains("[Last 6 digits of debit card] [Expiry Date MMYY]")){
            readSharedPreferences();
            String lastSixDigits = curTransactionDetails.getString("CARD_SIX_DIGITS", "0"), expiry = curTransactionDetails.getString("CARD_EXPIRY", "0");
            System.out.println("Card details read from memory. The last six digits: " + lastSixDigits + "\nExpiry: " + expiry);
            nextStep = "CHECK_IF_REGISTERED";
            enterTextInField(lastSixDigits + " " + expiry, rootNode, 1);
            pressButton("Send", rootNode, 2);
        }else if (curScreenText.contains("1. Send Money") && nextStep.contains("CHECK_IF_REGISTERED")) {
            readSharedPreferences();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "0").apply();
            System.out.println("Detected main menu. Reading values in text fields and proceeding...");
            enterTextInField("1", rootNode, 1);
            pressButton("Send", rootNode, 2);
            nextStep = "SEND_MENU";
            stepTimeout.start();
        }else if (curScreenText.contains("1. Send Money") && nextStep.isEmpty()) {
            readSharedPreferences();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "0")
                            .putString("TIMER_INACTIVE", "1").apply();
            System.out.println("Detected main menu. Reading values in text fields and proceeding...");
            enterTextInField("1", rootNode, 1);
            pressButton("Send", rootNode, 2);
            nextStep = "SEND_MENU";
            stepTimeout.start();
        } else if (curScreenText.contains("1. Send Money") && nextStep.equals("SEND_MENU")) {
            stepTimeout.cancel();
            nextStep = "";
        } else if(curScreenText.contains("3. UPI ID") && nextStep.equals("SEND_MENU")){
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "17").apply();
            System.out.println("Detected send menu.");
            enterTextInField("3", rootNode, 1);
            pressButton("Send", rootNode, 2);
            nextStep = "UPI_ID_ENTER";
            stepTimeout.start();
        }else if (curScreenText.contains("Enter UPI ID.") || curScreenText.contains("Enter Mobile No.") /*&& (nextStep.equals("UPI_ID_ENTER") || nextStep.isEmpty()*/){
            stepTimeout.cancel();
            readSharedPreferences();
            //progressBar.setProgress(34, true);
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "34").apply();
            System.out.println("Detected UPI ID prompt menu.");
            enterTextInField(upiID, rootNode, 1);
            pressButton("Send", rootNode, 2);
            nextStep = "AMOUNT_ENTER";
            stepTimeout.start();
        } else if (curScreenText.contains("Enter Amount in Rs.") && nextStep.equals("AMOUNT_ENTER")) {
            stepTimeout.cancel();
            readSharedPreferences();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "51").apply();
            //progressBar.setProgress(51, true);
            System.out.println("Detected amount menu.");
            String nameFromUSSD = curScreenText.substring(curScreenText.indexOf("Paying ") + 7, curScreenText.indexOf(",") - 1);
            curTransactionDetails.edit().putString("PAYEE_NAME", nameFromUSSD).apply();
            System.out.println("Name from the incoming message detected as: " + nameFromUSSD);
            enterTextInField(amount, rootNode, 1);
            pressButton("Send", rootNode, 2);
            nextStep = "REMARK_ENTER";
            stepTimeout.start();
        } else if (curScreenText.contains("Enter a remark (Enter 1 to skip)") && nextStep.equals("REMARK_ENTER")) {
            stepTimeout.cancel();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "68").apply();
            //progressBar.setProgress(68, true);
            System.out.println("Detected remark menu.");
            if(remark.isEmpty())
                enterTextInField("1", rootNode, 1);
            else
                enterTextInField(remark, rootNode, 1);
            pressButton("Send", rootNode, 2);
            nextStep = "PIN_ENTER";
            stepTimeout.start();
        } else if (curScreenText.contains("Enter UPI Pin to proceed") || curScreenText.contains("Enter 4  digit UPI PIN") || curScreenText.contains("Enter 6  digit UPI PIN")) {
            stepTimeout.cancel();
            readSharedPreferences();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "85").apply();
            //progressBar.setProgress(85, true);
            System.out.println("Detected UPI PIN menu. Found reallyPay as " + reallyPay);
            enterTextInField(upiPIN, rootNode, 1);
            if(curScreenText.contains("Enter UPI Pin to proceed")) {
                if(reallyPay) {
                    pressButton("Send", rootNode, 2);
                    nextStep = "PAYMENT_DONE";
                } else {
                    System.out.println("Not paying! Just faking it...");
                    pressButton("Cancel", rootNode, 2);
                    nextStep = "";
                    Intent intent = new Intent("com.ipproject.ussdupi.TEXT_DETECTED");
                    intent.putExtra("message", "TRANSACTION_FINISH");
                    sendBroadcast(intent);
                    curTransactionDetails.edit().putString("TRANSACTION_FINISH", "1").apply();
                    curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
                    //hidePaymentProgress();
                }
            } else {
                pressButton("Send", rootNode, 2);
                nextStep = "";
            }
            stepTimeout.start();
        } else if(curScreenText.contains("Your UPI PIN is set.  To check balance for your Account")){
            System.out.println("That's the last step for registration!");
            pressButton("Cancel", rootNode, 1);
            nextStep = "";
            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "3").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
        } else if (curScreenText.contains("1. Save contact")) {
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "100").apply();
            //progressBar.setProgress(100, true);
            String refID = curScreenText.substring(curScreenText.indexOf("RefId: ") + 7, curScreenText.indexOf("RefId: ") + 19);
            System.out.println("Transaction ID is detected as: " + refID);
            curTransactionDetails.edit().putString("REFERENCE_ID", refID).apply();
            pressButton("Cancel", rootNode, 1);
            nextStep = "";
            Intent intent = new Intent("com.ipproject.ussdupi.TEXT_DETECTED");
            intent.putExtra("message", "TRANSACTION_FINISH");
            sendBroadcast(intent);
            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "1").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
            //hidePaymentProgress();
        } else if (curScreenText.contains("Your account balance is")) {
            stepTimeout.cancel();
            pressButton("Cancel", rootNode, 1);
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "100").apply();
            //progressBar.setProgress(100, true);
            String bal = curScreenText.substring(curScreenText.indexOf("Your account balance is") + 27, curScreenText.indexOf("\n", curScreenText.indexOf("Your account balance is")));
            curTransactionDetails.edit().putString("BALANCE", bal).apply();
            pressButton("Cancel", rootNode, 5);
            nextStep = "";
            Intent intent = new Intent("com.ipproject.ussdupi.TEXT_DETECTED");
            intent.putExtra("message", "TRANSACTION_FINISH");
            sendBroadcast(intent);
            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "2").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
            //hidePaymentProgress();
        } else if (curScreenText.contains("Money was not debited. You have exceeded the daily number of transactions allowed by your bank.") || curScreenText.contains("Money was not debited. You have exceeded the number of PIN attempts for the day.")) {
            pressButton("Cancel", rootNode, 1);
            nextStep = "";
            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-1").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
            //hidePaymentProgress();
            stepTimeout.cancel();
        } else if(curScreenText.contains("Money was not debited. Please enter correct UPI PIN") || curScreenText.contains("The entered UPI PIN is incorrect or invalid")) {
            pressButton("Cancel", rootNode, 1);
            nextStep = "";
            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-2").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
            //hidePaymentProgress();
            stepTimeout.cancel();
        } else if(curScreenText.contains("is not a valid Amount")) {
            pressButton("Cancel", rootNode, 1);
            nextStep = "";
            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-4").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
            stepTimeout.cancel();
        } else if(curScreenText.contains("PSP IS NOT REGISTERED")){
            pressButton("Cancel", rootNode, 1);
            nextStep = "";
            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-6").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
            stepTimeout.cancel();
        } else if(!curScreenText.contains("USSD code running…") || curScreenText.contains("Connection problem or invalid MMI code")){
            pressButton("Cancel", rootNode, 1);
            nextStep = "";
            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-99").apply();
            curTransactionDetails.edit().putString("TRANSACTION_PROGRESS", "-1").apply();
            //hidePaymentProgress();
            stepTimeout.cancel();
        }
    }

    // Helper to find a node by class name anywhere in the tree
    private AccessibilityNodeInfo findNodeByClass(AccessibilityNodeInfo node, String className) {
        if (node == null) return null;
        if (className.equals(node.getClassName())) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNodeByClass(node.getChild(i), className);
            if (result != null) return result;
        }
        return null;
    }

    // Helper to find buttons by their label
    private AccessibilityNodeInfo findButtonByText(AccessibilityNodeInfo node, String... labels) {
        if (node == null) return null;
        if ("android.widget.Button".equals(node.getClassName()) && node.getText() != null) {
            for (String label : labels) {
                if (node.getText().toString().equalsIgnoreCase(label)) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findButtonByText(node.getChild(i), labels);
            if (result != null) return result;
        }
        return null;
    }

    private void enterTextInField(String text, AccessibilityNodeInfo rootNode, long delayTime) {
        AccessibilityNodeInfo inputField = findNodeByClass(rootNode, "android.widget.EditText");
        if (inputField != null) {
            System.out.println("Found the text field! Typing '" + text + "'...");
            Bundle arguments = new Bundle();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            }, delayTime);
        }
    }

    private void dontpressButton(String buttonText, AccessibilityNodeInfo rootNode, long delayTime) {
        if (rootNode == null) return;

        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(buttonText);

        for (AccessibilityNodeInfo node : nodes) {
            // Double-check that the node is actually clickable
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("USSD_ACTION", "Tapped on node with text: " + buttonText);
                return; // Exit after the first successful tap
            }

            // If the text itself isn't clickable (like a label inside a button),
            // try to click its parent
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable()) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("USSD_ACTION", "Tapped on parent of text: " + buttonText);
                return;
            }
        }
    }

    private void pressButton(String buttonText, AccessibilityNodeInfo rootNode, long delayTime) {
        AccessibilityNodeInfo selectedButton = findButtonByText(rootNode, buttonText, "Reply");
        if (selectedButton != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                System.out.println("Clicking " + buttonText + "...");
                selectedButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }, delayTime);
        }
    }
}