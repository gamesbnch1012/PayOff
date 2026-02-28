package com.ipproject.ussdupi;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.Context;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
//import java.util.logging.Handler;
//import com.ipproject.ussdupi.USSDActions;


public class MainActivity extends AppCompatActivity {
    Button ussdSendButton, checkBalButton;
    EditText upiIDTextField;
    USSDActions ussd;
    String upiID = "", amount = "", upiPIN = "", upiIDReadFromQR = "";
    Button mainButton, bankButton;
    ImageButton settingsButton, historyButton;
    SharedPreferences curTransactionDetails, userSettings;
    boolean dialogBeingShown = false, dismissedDialog = false, accessibilityPermission = true, drawOverOtherAppsPermission = true, cameraPermission = true, callPermission = true, locationPermission = true;
    AlertDialog dialog, loadingDialog;
    private ProcessCameraProvider cameraProvider;
    private WindowManager windowManager;
    private View onTopView;
    ProgressBar progressBar;
    CountDownTimer checkForFinish, checkForQRScan, forceUPIID, paymentStartTimeout, signalCheck;
    TextView progressText, signalDebugText;
    Vibrator vibrator;
    Spinner spinner;
    TelephonyManager telephonyManager;
    boolean useOnlyLTE = false, showingStats = false;
    LinkedList<Boolean> lteHistory = new LinkedList<>();
    Intent intent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        beginWritingToLog();
        intent = getIntent();
        Uri data = intent.getData();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ussd = new USSDActions(this);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        lteHistory.clear();

        curTransactionDetails = getSharedPreferences("TRANSACTION_DATA", MODE_PRIVATE);
        userSettings = getSharedPreferences("USER_SETTINGS", MODE_PRIVATE);
        curTransactionDetails.edit().clear().apply();
        userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "1").apply();
        curTransactionDetails.edit().putString("AMOUNT", "")
                .putString("PAYEE_NAME", "")
                .putString("UPI_PIN", "")
                .putString("REFERENCE_ID", "")
                .putString("REMARK", "").apply();

        //checkForAllPermissions();
        startCamera();

        checkForQRScan = new CountDownTimer(1, 1) {
            @Override
            public void onFinish() {
                upiIDReadFromQR = "";
                String scannedUPIQR = curTransactionDetails.getString("UPI_ID", "0");
                if(!scannedUPIQR.equals("0") && !dismissedDialog){
                    ussdSendButton.setEnabled(true);
                    upiIDTextField.setText(scannedUPIQR);
                    upiIDReadFromQR = scannedUPIQR;
                    ussdSendButton.performClick();
                } else {
                    this.start();
                }
            }

            @Override
            public void onTick(long millisUntilFinished) {
            }
        }.start();

        forceUPIID = new CountDownTimer(500, 100) {
            @Override
            public void onFinish() {
                curTransactionDetails.edit().putString("UPI_ID", upiIDTextField.getText().toString()).apply();
                this.start();
            }

            @Override
            public void onTick(long millisUntilFinished) {

            }
        };

        signalCheck = new CountDownTimer(1500, 100) {
            @Override
            public void onFinish() {
                scanForSignals();
                this.start();
            }

            @Override
            public void onTick(long millisUntilFinished) {

            }
        }.start();

        //mainText = findViewById(R.id.main_text);
        ussdSendButton = findViewById((R.id.button));
        ussdSendButton.setEnabled(false);
        settingsButton = findViewById(R.id.settings_button);
        historyButton = findViewById(R.id.history_button);
        upiIDTextField = findViewById(R.id.upiIDField);
        checkBalButton = findViewById(R.id.bal_button);
        signalDebugText = findViewById(R.id.signal_stats_text);
        signalDebugText.setVisibility(View.GONE);
        //pinTextField = findViewById(R.id.pinField);
        //amountTextField = findViewById(R.id.amountField);

        //sendValuesToUSSDClass(upiID, upiPIN, amount);

        String lteOnly1 = userSettings.getString("LTE_ONLY", "false");
        String showStats1 = userSettings.getString("SHOW_STATS", "false");

        if(lteOnly1.equals("true")){
            useOnlyLTE = true;
        } else {
            useOnlyLTE = false;
        }
        if(showStats1.equals("true")){
            showingStats = true;
            signalDebugText.setVisibility(View.VISIBLE);
        } else {
            showingStats = false;
            signalDebugText.setVisibility(View.GONE);
        }

        ussdSendButton.setOnClickListener(v -> {
            if(!(upiIDTextField.getText().toString().isEmpty() || !upiIDTextField.getText().toString().contains("@"))|| (upiIDTextField.getText().toString().matches("\\d+") && upiIDTextField.getText().toString().length() == 10)){
            curTransactionDetails.edit().putString("UPI_ID", upiIDTextField.getText().toString()).apply();
            upiID = upiIDTextField.getText().toString();
            upiPIN = curTransactionDetails.getString("UPI_PIN", "");
            amount = curTransactionDetails.getString("AMOUNT", "");
            /*curTransactionDetails.edit().putString("UPI_ID", upiID)
                    .putString("AMOUNT", amount)
                    .putString("UPI_PIN", upiPIN)
                    .apply();*/
            if(!upiIDReadFromQR.isEmpty())
                upiID = upiIDReadFromQR;
            System.out.println("Saved the information as following: \nUPI ID: " + upiID + "\nAmount: " + amount + "\nUPI PIN: " + upiPIN);
            checkForQRScan.cancel();
            showNewDialog("AMOUNT", false);
            /*if(isAccessibilityServiceEnabled())
               ussd.sendUSSDCommand("*99#");*/
            } else {
                Toast.makeText(this, "Enter a valid UPI ID", Toast.LENGTH_SHORT).show();
            }
        });

        checkBalButton.setOnClickListener(v -> {
            showNewDialog("CHECK_BAL_PIN", true);
        });

        /*checkBalButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                readTransactionHistory();
                return true;
            }
        });*/

        settingsButton.setOnClickListener(v -> {
            if(dialogBeingShown){
                dialog.dismiss();
            }

            View dialogBox = getLayoutInflater().inflate(R.layout.settings, null);
            dialogBox.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setView(dialogBox);

            Switch lteOnlyToggle = dialogBox.findViewById(R.id.lteOnlyToggle);
            Switch showStatsToggle = dialogBox.findViewById(R.id.networkStatsToggle);
            Switch reallyPayToggle = dialogBox.findViewById(R.id.really_pay_toggle);
            Button reportButton = dialogBox.findViewById(R.id.report_bug_button);

            String lteOnly = userSettings.getString("LTE_ONLY", "false");
            String showStats = userSettings.getString("SHOW_STATS", "false");
            String reallyPay = userSettings.getString("REALLY_PAY", "true");

            if(lteOnly.equals("true")){
                lteOnlyToggle.setChecked(true);
            }
            if(showStats.equals("true")){
                showStatsToggle.setChecked(true);
            }
            if(reallyPay.equals("false")){
                reallyPayToggle.setChecked(false);
            }

            lteOnlyToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                    if(isChecked) {
                        userSettings.edit().putString("LTE_ONLY", "true").apply();
                        useOnlyLTE = true;
                    } else {
                        userSettings.edit().putString("LTE_ONLY", "false").apply();
                        useOnlyLTE = false;
                    }
                }
            });

            showStatsToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                    if(isChecked) {
                        userSettings.edit().putString("SHOW_STATS", "true").apply();
                        showingStats = true;
                        signalDebugText.setVisibility(View.VISIBLE);
                    } else {
                        userSettings.edit().putString("SHOW_STATS", "false").apply();
                        showingStats = false;
                        signalDebugText.setVisibility(View.GONE);
                    }
                }
            });

            reallyPayToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                    if(isChecked) {
                        userSettings.edit().putString("REALLY_PAY", "true").apply();
                    } else {
                        userSettings.edit().putString("REALLY_PAY", "false").apply();
                    }
                }
            });

            reportButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shareLog();
                    showToast("Please do tell what went wrong and click on send", Toast.LENGTH_LONG);
                }
            });

            reportButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    File logFile = new File(getExternalFilesDir(null), "debug_logcat.txt");
                    if(logFile.exists()){
                        logFile.delete();
                        showToast("Log file reset.", Toast.LENGTH_SHORT);
                        beginWritingToLog();
                    } else {
                        showToast("Log file is already empty.", Toast.LENGTH_SHORT);
                    }
                    return true;
                }
            });

            builder.show();
        });

        historyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readTransactionHistory();
            }
        });

        upiIDTextField.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String string = s.toString();
                if(string.contains("@") || (string.length() == 10 && string.matches("\\d+"))){
                    ussdSendButton.setEnabled(true);
                } else {
                    ussdSendButton.setEnabled(false);
                }

                if(string.length() == 10 && string.matches("\\d+")){
                    ussdSendButton.performClick();
                }
            }
        });

        //mainText.setText("text has to change now!");

        String ussdNumber = "tel:" + Uri.encode("*99#");
        //Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode("*99*1*3#")));
        //startActivity(intent);
        /*if(isAccessibilityServiceEnabled())
            ussd.sendUSSDCommand("*99#");*/
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if(data!=null && "upi".equals(data.getScheme())){
                    curTransactionDetails.edit().putString("UPI_ID", data.getQueryParameter("pa")).apply();
                    System.out.println("Detected UPI ID as: " + data.getQueryParameter("pa"));
                    System.out.println("Avaibale parameters in link: " + data.getQueryParameterNames() + "\nReceived URI: " + data.toString());

                    String payeeName = data.getQueryParameter("pn");
                    String amount = data.getQueryParameter("am");
                    String remark = data.getQueryParameter("tn");
                    if(data.getQueryParameter("tr")!=null){
                        showFinalDialog(false, "This payment isn't supported by PayOff. USSD doesn't support merchant payments yet.");
                    }
                    if(payeeName!=null) {
                        curTransactionDetails.edit().putString("PAYEE_NAME", data.getQueryParameter("pn")).apply();
                        System.out.println("Detected payee name as: " + payeeName);
                    }
                    if(amount!=null) {
                        curTransactionDetails.edit().putString("AMOUNT", data.getQueryParameter("am")).apply();
                        System.out.println("Detected amount as: " + amount);
                    }
                    if(remark!=null) {
                        curTransactionDetails.edit().putString("REMARK", data.getQueryParameter("tn")).apply();
                        System.out.println("Detected remark as: " + remark);
                    }
                }
            }
        }, 1000);
    }

    void checkForAllPermissions(){
        //call permission
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)!= PackageManager.PERMISSION_GRANTED) {
            callPermission = false;
        }

        //camera permission
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            cameraPermission = false;
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            locationPermission = false;
        }

        //accessibility permission
        if(!isAccessibilityServiceEnabled()){
            accessibilityPermission = false;
        }

        //draw over other apps permission
        if(!Settings.canDrawOverlays(this)){
            drawOverOtherAppsPermission = false;
        }

        if(!accessibilityPermission){
            requestAccessibilityPermission();
            accessibilityPermission = true;
        } else if(!drawOverOtherAppsPermission){
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 103);
            Toast.makeText(this, "Enable the setting for 'PayOff' to continue", Toast.LENGTH_LONG).show();
            drawOverOtherAppsPermission = true;
        } else if(!cameraPermission){
            showToast("Allow the camera permission", Toast.LENGTH_SHORT);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 102);
            cameraPermission = true;
        } else if(!callPermission){
            showToast("Allow the call permission", Toast.LENGTH_SHORT);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 101);
            callPermission = true;
        } else if(!locationPermission){
            showToast("Allow precise location permission", Toast.LENGTH_SHORT);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 103);
            locationPermission = true;
        }
    }

    private final BroadcastReceiver textReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra("message");
            if(msg.equals("TRANSACTION_FINISH")) {
                dialog.dismiss();
            }
        }
    };

    @Override
    public void onStart(){
        super.onStart();
        IntentFilter filter = new IntentFilter("com.ipproject.ussdupi.TEXT_DETECTED");
        registerReceiver(textReciever, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onStop(){
        super.onStop();
        unregisterReceiver(textReciever);
    }

    public void beginWritingToLog(){
        try {
            // 1. Define the file in your no-permission private storage
            File logFile = new File(getExternalFilesDir(null), "debug_logcat.txt");
            if(!logFile.exists())
                logFile.createNewFile();
            // 2. Clear the old logcat buffer so you don't get yesterday's logs
            Runtime.getRuntime().exec("logcat -c");

            // 3. Run the logcat command
            // -f: write to a file
            // -v time: include timestamps
            // *:D : Capture everything from Debug level and up
            String command = "logcat -f " + logFile.getAbsolutePath() + " RippleDrawable:S *:I -v time";

            Runtime.getRuntime().exec(command);

            System.out.println("Logcat is now being piped to: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shareLog(){
            File logFile = new File(getExternalFilesDir(null), "debug_logcat.txt");
            Uri fileUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    logFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.putExtra("jid",  "917330825652@s.whatsapp.net");
            intent.setPackage("com.whatsapp");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{
            startActivity(intent);
        } catch (ActivityNotFoundException e){
            System.out.println("Something went wrong while trying to share the debug file");
            e.printStackTrace();
        }
    }

    void writeToTransactionHistory(String upiID, String payeeName, String amount, boolean status, String refID){
        File transactionHistory = new File(getExternalFilesDir(null), "transaction_history.json");
        Gson gson = new Gson();
        ArrayList<Map<String, Object>> historyList;

        //transactionHistory.delete();

        if(transactionHistory.exists()){
            try(FileReader reader = new FileReader(transactionHistory)){
                Type type = new TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
                historyList = gson.fromJson(reader, type);
            } catch (IOException e){
                historyList = new ArrayList<>();
            }
        } else {
            historyList = new ArrayList<>();
        }

        Map<String, Object> currentTransaction = new HashMap<>();
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        currentTransaction.put("upi_id", upiID);
        currentTransaction.put("payee_name", payeeName);
        currentTransaction.put("amount", amount);
        currentTransaction.put("status", status);
        if(refID!=null)
            currentTransaction.put("ref_id", refID);
        else
            currentTransaction.put("ref_id", "---");
        currentTransaction.put("time", format.format(date));

        historyList.add(0, currentTransaction);

        try(FileWriter writer = new FileWriter(transactionHistory)){
            gson.toJson(historyList, writer);
            System.out.println("Wrote to transaction history JSON.");
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    void readTransactionHistory(){
        File transactionHistory = new File(getExternalFilesDir(null), "transaction_history.json");
        Gson gson = new Gson();

        if(dialogBeingShown){
            dialog.dismiss();
        }

        View dialogBox = getLayoutInflater().inflate(R.layout.transaction_history, null);
        dialogBox.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(dialogBox);

        TextView historyText = dialogBox.findViewById(R.id.history_text);

        try (FileReader reader = new FileReader(transactionHistory)) {
            // 1. Tell GSON we are reading a List of Maps
            Type type = new TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
            ArrayList<Map<String, Object>> history = gson.fromJson(reader, type);

            if (history != null) {
                StringBuilder stringBuilder = new StringBuilder();

                // 2. Loop through each record one-by-one
                for (Map<String, Object> record : history) {
                    String upiID = String.valueOf(record.get("upi_id"));
                    String payeeName = String.valueOf(record.get("payee_name"));
                    String status = String.valueOf(record.get("status"));
                    String amount = String.valueOf(record.get("amount"));
                    String time = String.valueOf(record.get("time"));
                    String refID = String.valueOf(record.get("ref_id"));

                    if(upiID.contains("@"))
                        stringBuilder.append("Payee UPI ID: ").append(upiID).append("\n");
                    else
                        stringBuilder.append("Payee Number: ").append(upiID).append("\n");
                    stringBuilder.append("Payee Name: ").append(payeeName).append("\n");
                    stringBuilder.append("Amount: ₹").append(amount).append("\n");
                    if(!time.isEmpty()){
                        stringBuilder.append("Time: ").append(time).append("\n");
                    } else {
                        stringBuilder.append("Time: ").append("---").append("\n");
                    }
                    if(!refID.isEmpty())
                        stringBuilder.append("Reference ID: ").append(refID).append("\n");
                    if(status.equals("true"))
                        stringBuilder.append("Status: ").append("Paid").append("\n");
                    else
                        stringBuilder.append("Status: ").append("Failed").append("\n");
                    stringBuilder.append("\n\n");
                }

                // 4. Update the TextView
                historyText.setText(stringBuilder.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        builder.show();
    }

    void scanForSignals(){
        if(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.requestCellInfoUpdate(getMainExecutor(), new TelephonyManager.CellInfoCallback() {
                    @Override
                    public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                        for(CellInfo info : cellInfo){
                            if (info instanceof CellInfoLte && info.isRegistered()) {
                                CellSignalStrengthLte lte = ((CellInfoLte) info).getCellSignalStrength();
                                int rsrp = lte.getRsrp();
                                int rsrq = lte.getRsrq();
                                int rssi = lte.getRssi();
                                int snr = lte.getRssnr();

                                if((rsrq>=-14 && rsrp>=-98) || useOnlyLTE){
                                    lteHistory.addFirst(true);
                                } else {
                                    lteHistory.addFirst(false);
                                }
                                if(lteHistory.size()>4){
                                    lteHistory.removeLast();
                                }
                                signalDebugText.setText("Signal readings: \nRSRP: " + rsrp + " \nRSRQ: " + rsrq + "\nUsing LTE/5G: " + checkIfLteReliable() + "\nLive LTE/5G usable: " + (rsrq>=-14 && rsrp>=-98) + "\nReally paying: " + userSettings.getString("REALLY_PAY", "true"));
                                //System.out.println("Signal readings: \nRSRP: " + rsrp + " \nRSRQ: " + rsrq + " \nSNR: " + snr + "\nRSSI: " + rssi + "\nUsing LTE/5G: " + reliableLTE);
                            }
                        }
                    }
                });
            }
        }
    }

    boolean checkIfLteReliable(){
        int i, maxSize = lteHistory.size();
        if(maxSize>4)
            maxSize = 4;
        Iterator<Boolean> it = lteHistory.iterator();
        for(i=0; (i<maxSize && it.hasNext()); i++){
            if(!it.next()){
                return false;
            }
        }
        return true;
    }

    void vibrateWhenClicked(){
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Modern way (Android 12+)
            VibratorManager vibratorManager = (VibratorManager) this.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            // Legacy way
            vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo enabledService : enabledServices) {
            ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
            // Check if the enabled service's package matches ours
            if (enabledServiceInfo.packageName.equals(getPackageName()) &&
                    enabledServiceInfo.name.equals(USSDAccessibility.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private void requestAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Find and enable 'PayOff' here", Toast.LENGTH_LONG).show();
        }
    }

    void showBankSelector(){
        hidePaymentProgress();
        View dialogBox = getLayoutInflater().inflate(R.layout.bank_pick, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(dialogBox);
        spinner = dialogBox.findViewById(R.id.spinner);
        bankButton = dialogBox.findViewById(R.id.settings_apply_button);
        bankButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedBankName = spinner.getSelectedItem().toString();
                System.out.println("Selected bank as: " + selectedBankName);
                userSettings.edit().putString("BANK", selectedBankName).apply();
                //showToast("Bank saved! Try the payment again.", Toast.LENGTH_LONG);
                //dialog.dismiss();
                //dialogBeingShown = false;
                showNewDialog("CARD_DIGITS", false);
            }
        });
        dialog = builder.create();
        dialog.show();
        dialogBeingShown = true;
    }

    private void showToast(String message, int duration){
        Toast.makeText(this, message, duration).show();
    }

    private void startCamera() {
        checkForAllPermissions();
        /*curTransactionDetails.edit().putString("AMOUNT", "")
                .putString("PAYEE_NAME", "")
                .putString("UPI_PIN", "")
                .putString("REMARK", "").apply();*/
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Preview configuration
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(((PreviewView) findViewById(R.id.viewFinder)).getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new QRScanner(this));

                // Select back camera as default
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind to lifecycle
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e("CAMERA_DEBUG", "Use case binding failed", e);
            }

        }, ContextCompat.getMainExecutor(this));
    }

    private void pauseCamera(){
        if(cameraProvider!=null)
            cameraProvider.unbindAll();
    }
    private void showNewDialog(String mode, boolean textBoxIsPassword){
        if(dialogBeingShown && !mode.equals("PAYING")){
            dialog.dismiss();
        }

        View dialogBox = getLayoutInflater().inflate(R.layout.prompt_sample, null);
        dialogBox.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(dialogBox);
        TextView secondSmallText = dialogBox.findViewById(R.id.text1);
        TextView secondBigText = dialogBox.findViewById(R.id.final_status_text);
        TextView firstSmallText = dialogBox.findViewById(R.id.status_text);
        TextView firstBigText = dialogBox.findViewById(R.id.final_bal_text);
        TextView hintText = dialogBox.findViewById(R.id.text4);
        EditText textBox = dialogBox.findViewById(R.id.main_pin);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mainButton = dialogBox.findViewById(R.id.settings_apply_button);

        if(textBoxIsPassword){
            textBox.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        } else {
            textBox.setInputType(InputType.TYPE_CLASS_NUMBER);
        }

        if(mode.equals("AMOUNT")){
            vibrateWhenClicked();
            if(!amount.isEmpty()){
                showNewDialog("PIN", true);
                return;
            }
            secondSmallText.setVisibility(View.GONE);
            secondBigText.setVisibility(View.GONE);
            hintText.setVisibility(View.GONE);
            mainButton.setVisibility((View.VISIBLE));
            firstBigText.setText("AMOUNT");
            firstSmallText.setText("Enter");
            textBox.setHint("Amount");
        } else if(mode.equals("PIN")){
            String displayAmountWithSymbol = "₹" + curTransactionDetails.getString("AMOUNT", "NULL");
            firstBigText.setText(displayAmountWithSymbol);
            firstSmallText.setText("Paying");
            if(curTransactionDetails.getString("PAYEE_NAME", "").isEmpty())
                secondBigText.setText(curTransactionDetails.getString("UPI_ID", "NULL"));
            else
                secondBigText.setText(curTransactionDetails.getString("PAYEE_NAME", "NULL"));
            secondBigText.setTextSize(22);
            secondSmallText.setText("to");
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText("PAY");
            textBox.setHint("PIN");
        } else if(mode.equals("CHECK_BAL_PIN")){
            secondSmallText.setVisibility(View.GONE);
            secondBigText.setVisibility(View.GONE);
            hintText.setVisibility(View.GONE);
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText("CHECK BALANCE");
            firstBigText.setText("PIN");
            firstSmallText.setText("Enter");
            textBox.setHint("PIN");
        } else if(mode.equals("PAYING")){
            curTransactionDetails.edit().putString("UPI_ID", upiIDReadFromQR).apply();
        } else if(mode.equals("CARD_DIGITS")){
            secondSmallText.setVisibility(View.GONE);
            secondBigText.setVisibility(View.GONE);
            hintText.setVisibility(View.VISIBLE);
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText("NEXT");
            firstBigText.setText("CARD DETAILS");
            firstSmallText.setText("Enter");
            hintText.setText("Enter the last 6 digits of your ATM card:");
            textBox.setHint("Last 6 digits of ATM card");
        } else if(mode.equals("CARD_EXPIRY")){
            secondSmallText.setVisibility(View.GONE);
            secondBigText.setVisibility(View.GONE);
            hintText.setVisibility(View.VISIBLE);
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText("DONE");
            firstBigText.setText("CARD DETAILS");
            firstSmallText.setText("Enter");
            hintText.setText("Enter the card's expiry date without the '/':");
            textBox.setHint("MMYY");
        }

        textBox.postDelayed(() -> {
            textBox.requestFocus();
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getInsetsController().show(WindowInsets.Type.ime());
            } else {*/
                imm.showSoftInput(textBox, InputMethodManager.SHOW_IMPLICIT);
            //}
        }, 150);

        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                System.out.println("Dismiss listener called.");
                //curTransactionDetails.edit().putString("UPI_ID", "0").apply();
                //upiIDTextField.setText("");
                //upiIDReadFromQR = "";
            }
        });

        dialog = builder.create();
        if(!mode.equals("PAYING")) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
            dialog.show();
            dialogBeingShown = true;
            if(dialog.getWindow()!=null){
                dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                dialog.getWindow().setWindowAnimations(0);
            }
        }
        textBox.setOnEditorActionListener((v, actionID, event) -> {
            if(actionID == EditorInfo.IME_ACTION_GO){
                mainButton.performClick();
                return true;
            }
            return false;
        });
        mainButton.setOnClickListener(v -> {
            if(mode.equals("AMOUNT")){
                String newAmount = textBox.getText().toString();
                if(Integer.parseInt(newAmount)>0) {
                    curTransactionDetails.edit().putString("AMOUNT", newAmount)
                            //.putString("UPI_ID", upiIDTextField.getText().toString())
                            .apply();
                    showNewDialog("PIN", true);
                } else {
                    Toast.makeText(this, "Enter a valid amount.", Toast.LENGTH_SHORT).show();
                }
            } else if(mode.equals("PIN") || mode.equals("CHECK_BAL_PIN")){
                System.out.println("-----------------PAYMENT INITIATED-------------------");
                String newPIN = textBox.getText().toString();
                secondBigText.setTextSize(40);
                if(newPIN.length()==4 || newPIN.length()==6) {
                    curTransactionDetails.edit().putString("UPI_PIN", newPIN).apply();
                    //ussd.sendUSSDCommand("*99#");
                    if(mode.equals("PIN")) {
                        if(checkIfLteReliable()) {
                            if(upiID.contains("@"))
                                makeCallToNumber("*99*1*3#");
                            else if(upiID.matches("\\d+"))
                                makeCallToNumber("*99*1*1#");
                        } else {
                            ussd.sendUSSDCommand("*99#");
                        }
                        showPaymentProgress("");
                    }else if(mode.equals("CHECK_BAL_PIN")) {
                        makeCallToNumber("*99*3#");
                        showPaymentProgress("Checking balance...");
                    }
                    showLoadingDialog("Checking balance...");
                    curTransactionDetails.edit().putString("TRANSACTION_FINISH", "0")
                            .putString("UPI_ID", upiIDReadFromQR)
                            .apply();
                    paymentStartTimeout = new CountDownTimer(20000, 1000) {
                        @Override
                        public void onFinish() {
                            curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-3").apply();
                        }

                        @Override
                        public void onTick(long millisUntilFinished) {

                        }
                    }.start();
                    showNewDialog("PAYING", false);
                } else {
                        Toast.makeText(this, "Enter a valid UPI PIN.", Toast.LENGTH_SHORT).show();
                }
            } else if(mode.equals("CARD_DIGITS")) {
                String lastSixDigits = textBox.getText().toString();
                if (lastSixDigits.length() != 6) {
                    showToast("Enter only the last 6 digits.", Toast.LENGTH_LONG);
                } else {
                    curTransactionDetails.edit().putString("CARD_SIX_DIGITS", lastSixDigits).apply();
                    showNewDialog("CARD_EXPIRY", false);
                }
            } else if(mode.equals("CARD_EXPIRY")){
                String expiryDate = textBox.getText().toString();
                if(expiryDate.length() != 4){
                    showToast("Enter only in MMYY format.", Toast.LENGTH_LONG);
                } else {
                    curTransactionDetails.edit().putString("CARD_EXPIRY", expiryDate).apply();
                    ussd.sendUSSDCommand("*99#");
                    showLoadingDialog("Registering app with UPI...");
                    showPaymentProgress("Registering app with UPI...");
                    curTransactionDetails.edit().putString("TIMER_INACTIVE", "1").apply();
                }
            }
        });

        textBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.length() > 6){
                    textBox.setText(textBox.getText().toString().substring(0,5));
                }
            }
        });
    }

    void makeCallToNumber(String number){
        String encodedNumber = Uri.encode(number);
        Uri uri = Uri.parse("tel:" + encodedNumber);

        Intent intent = new Intent(Intent.ACTION_CALL, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    private void showLoadingDialog(String customMessage){
        if(dialogBeingShown){
            dialog.dismiss();
            dialogBeingShown = false;
        }
        View loadingBox = getLayoutInflater().inflate(R.layout.loading, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(loadingBox);

        /*if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ){
            loadingDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }*/

        curTransactionDetails.edit().putString("UPI_ID", upiIDReadFromQR).apply();

        TextView loadingText = loadingBox.findViewById(R.id.loadingText);
        if(customMessage.isEmpty())
            loadingText.setText("Processing payment...");
        else
            loadingText.setText(customMessage);
        loadingDialog = builder.create();
        loadingDialog.setCancelable(false);
        loadingDialog.show();
        dialogBeingShown = true;
        if(checkForFinish!=null){
            checkForFinish.cancel();
        }
        checkForFinish = new CountDownTimer(5, 1){
            public void onTick(long millisUntilFinished){

            }
            public void onFinish(){
                //System.out.println("500ms checking interval complete.");
                String progressBarStatus = curTransactionDetails.getString("TRANSACTION_PROGRESS", "-1");
                int stopTimer = Integer.parseInt(curTransactionDetails.getString("TIMER_INACTIVE", "0"));
                if(!progressBarStatus.equals("-1")) {
                    if(progressBar.isIndeterminate()){
                        progressBar.setIndeterminate(false);
                    }
                    int curPer = Integer.parseInt(progressBarStatus);
                    progressBar.setProgress(curPer, true);
                } else {
                    if(!progressBar.isIndeterminate())
                        progressBar.setIndeterminate(true);
                }
                if(stopTimer == 1){
                    paymentStartTimeout.cancel();
                }
                String transaction_status = curTransactionDetails.getString("TRANSACTION_FINISH", "0");
                if(transaction_status.equals("1")) {
                    System.out.println("Transaction complete!");
                    upiIDReadFromQR = "";
                    hidePaymentProgress();
                    showFinalDialog(true, null);
                } else if(transaction_status.equals("2")){
                    System.out.println("Balance check complete!");
                    hidePaymentProgress();
                    showFinalDialog(true, "BAL_CHECK_COMPLETE");
                } else if(transaction_status.equals("3")){
                    System.out.println("USSD setup complete!");
                    hidePaymentProgress();
                    showFinalDialog(true, "USSD_SETUP_COMPLETE");
                }else if(transaction_status.equals("-1")) {
                    showFinalDialog(false, "You have exceeded your daily UPI transaction limit");
                    hidePaymentProgress();
                } else if(transaction_status.equals("-2")) {
                    showFinalDialog(false, "The UPI PIN entered is incorrect");
                    hidePaymentProgress();
                } else if(transaction_status.equals("-3")) {
                    showFinalDialog(false, "Payment has timed out. Check if the money has been debited and retry if it hasn't");
                    hidePaymentProgress();
                } else if(transaction_status.equals("-4")) {
                    showFinalDialog(false, "The amount entered is either invalid or too high");
                    hidePaymentProgress();
                } else if(transaction_status.equals("-5")) {
                    System.out.println("USSD isn't set up!");
                    hidePaymentProgress();
                    showBankSelector();
                    curTransactionDetails.edit().putString("TRANSACTION_FINISH", "").apply();
                    loadingDialog.dismiss();
                } else if(transaction_status.equals("-6")){
                    showFinalDialog(false, "UPI ID (or) QR Code is not valid");
                    hidePaymentProgress();
                } else if (transaction_status.equals("-99")){
                    showFinalDialog(false, "An unknown error occurred and the payment couldn't be processed");
                    hidePaymentProgress();
                } else {
                    this.start();
                }
            }
        }.start();
        forceUPIID.start();
        System.out.println("Loading started with UPI ID as: " + curTransactionDetails.getString("UPI_ID", "NULL") + "\nIn upiIDReadFromQR: " + upiIDReadFromQR);
    }

    private void showFinalDialog(boolean status, String message){
        System.out.println("-----------------PAYMENT FINISHED-------------------");
        forceUPIID.cancel();
        if(loadingDialog!=null)
            loadingDialog.setCancelable(true);
        View finalMessageBox = getLayoutInflater().inflate(R.layout.final_message, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(finalMessageBox);

        TextView smallText = finalMessageBox.findViewById(R.id.status_text);
        TextView secondSmallText = finalMessageBox.findViewById(R.id.second_small_text);
        TextView statusText = finalMessageBox.findViewById(R.id.final_status_text);
        TextView secondBigText = finalMessageBox.findViewById(R.id.second_big_text);
        ImageView statusIcon = finalMessageBox.findViewById(R.id.imageView);
        TextView statusInfo = finalMessageBox.findViewById(R.id.status_info);

        secondSmallText.setVisibility(View.GONE);
        secondBigText.setVisibility(View.GONE);

        //this.stopLockTask();

        if(!status){
            statusText.setText("FAILED");
            statusIcon.setImageResource(R.drawable.x_mark_256);
            amount = curTransactionDetails.getString("AMOUNT", "?");
            if(message!=null){
                statusInfo.setText(message);
            }
            if(message!=null) {
                if (!message.equals("BAL_CHECK_COMPLETE") && !message.equals("USSD_SETUP_COMPLETE"))
                    writeToTransactionHistory(curTransactionDetails.getString("UPI_ID", "NULL"), curTransactionDetails.getString("PAYEE_NAME", "NULL"), amount, false, null);
            } else {
                writeToTransactionHistory(curTransactionDetails.getString("UPI_ID", "NULL"), curTransactionDetails.getString("PAYEE_NAME", "NULL"), amount, false, null);
            }
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{100,0,0,100,0,0,75,0,0,75,0,0,50,0,0,50,0}, -1));
        } else {
            smallText.setText("Paid");
            amount = curTransactionDetails.getString("AMOUNT", "?");
            statusText.setText("₹" + amount);
            secondSmallText.setVisibility(View.VISIBLE);
            secondBigText.setVisibility(View.VISIBLE);
            String payeeName = curTransactionDetails.getString("PAYEE_NAME", "NULL");
            if(!payeeName.isEmpty())
                secondBigText.setText(curTransactionDetails.getString("PAYEE_NAME", "NULL"));
            else
                secondBigText.setText(curTransactionDetails.getString("UPI_ID", "NULL"));
            String refID = curTransactionDetails.getString("REFERENCE_ID", "");
            if(!refID.isEmpty())
                statusInfo.setText("Reference ID: " + refID);
            if(message==null){
                writeToTransactionHistory(curTransactionDetails.getString("UPI_ID", "NULL"), curTransactionDetails.getString("PAYEE_NAME", "NULL"), amount, true, refID);
            }
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.payment_success_sfx);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
            });
            if(message==null)
                mediaPlayer.start();
            if(message==null) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 0, 0, 50, 0, 0, 50, 0, 0, 50, 0, 0, 255, 0}, -1));
            } else if(message.contains("BAL_CHECK_COMPLETE")){
                vibrator.vibrate(VibrationEffect.createOneShot(60, 90));
            }
        }

        if(message!=null){
            if(message.equals("BAL_CHECK_COMPLETE")) {
                String balance = curTransactionDetails.getString("BALANCE", "?");
                smallText.setText("Balance:");
                statusText.setText("₹" + balance);
                statusInfo.setVisibility(View.GONE);
                secondSmallText.setVisibility(View.GONE);
                secondBigText.setVisibility(View.GONE);
            } else if(message.equals("USSD_SETUP_COMPLETE")){
                smallText.setText("UPI has been");
                statusText.setText("REGISTERED");
                statusInfo.setText("Try the payment again and it should work now");
                statusInfo.setVisibility(View.VISIBLE);
            }
        }

        if(loadingDialog!=null)
            loadingDialog.dismiss();
        dialog = builder.create();
        dialog.show();

        curTransactionDetails.edit().putString("AMOUNT", "")
                .putString("PAYEE_NAME", "")
                .putString("UPI_PIN", "")
                .putString("TIMER_INACTIVE", "0")
                .putString("REFERENCE_ID", "")
                .putString("REMARK", "").apply();
    }

    public void showPaymentProgress(String customMessage) {
        windowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        ContextThemeWrapper themeWrapper = new ContextThemeWrapper(getApplicationContext(), R.style.Theme_UssdUPI);

        // 1. Inflate your specific XML file
        onTopView = LayoutInflater.from(themeWrapper).inflate(R.layout.payment_in_progress, null);

        // 2. Set the Parameters to be "On Top"
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                // This is the magic layer that beats USSD messages
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // 3. Add the view to the screen
        if (Settings.canDrawOverlays(this)) {
            //this.startLockTask();
            fullScreenUI();
            curTransactionDetails.edit().putString("TIMER_INACTIVE", "0").apply();
            windowManager.addView(onTopView, params);
            progressBar = onTopView.findViewById(R.id.paymentProgress);
            progressText = onTopView.findViewById(R.id.textView);

            onTopView.animate()
                    .alpha(1f)
                    .setDuration(250)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();

            if(customMessage.isEmpty())
                progressText.setText("Payment in progress...");
            else
                progressText.setText(customMessage);
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
            onTopView.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
            unFullScreenUI();
            windowManager.removeView(onTopView);
            onTopView = null;
            paymentStartTimeout.cancel();
        }
    }

    public void fullScreenUI(){
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public void unFullScreenUI(){
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused){
            System.out.println("Main activity is being focused.");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkForAllPermissions();
                }
            }, 250);
            curTransactionDetails.edit().putString("UPI_ID", "0").apply();
            startCamera();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
            public void run(){
                    checkForQRScan.start();
                }
            }, 1000);
        } else {
            pauseCamera();
            checkForQRScan.cancel();
        }
    }

    OnBackPressedCallback callback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "0").apply();
            finishAffinity();
            System.exit(0);
        }
    };

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "0").apply();
        System.out.println("App left.");
    }

    @Override
    protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0)
            userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "1").apply();
        System.out.println("App brought to foreground.");
    }

    @Override
    protected void onResume(){
        super.onResume();
        //checkForAllPermissions();
    }

    @Override
    protected void onRestart(){
        super.onRestart();
        //checkForAllPermissions();
    }
}

