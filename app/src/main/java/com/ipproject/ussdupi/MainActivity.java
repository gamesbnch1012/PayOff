package com.ipproject.ussdupi;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.camera.core.Camera;
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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import android.content.Context;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//import java.util.logging.Handler;
//import com.ipproject.ussdupi.USSDActions;


public class MainActivity extends AppCompatActivity {
    Button checkBalButton;
    EditText upiIDTextField, textBox;
    USSDActions ussd;
    String upiID = "", amount = "", upiPIN = "", upiIDReadFromQR = "";
    Button mainButton, bankButton, forceStopButton;
    ImageButton settingsButton, historyButton, showQRButton, torchButton;
    MaterialButton ussdSendButton;
    SharedPreferences curTransactionDetails, userSettings, encryptedPreferences;
    PreviewView cameraView;
    boolean dialogBeingShown = false, paymentInProgress = false, dismissedDialog = false, accessibilityPermission = true, drawOverOtherAppsPermission = true, cameraPermission = true, callPermission = true, locationPermission = true, readPhoneStatePermission = true, contactsPermission = true;
    AlertDialog dialog, loadingDialog;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private WindowManager windowManager;
    private View onTopView;
    ProgressBar progressBar;
    CountDownTimer checkForFinish, checkForQRScan, forceUPIID, paymentStartTimeout, signalCheck;
    TextView progressText, signalDebugText, forceStopText;
    Vibrator vibrator;
    Spinner spinner;
    TelephonyManager telephonyManager;
    boolean useOnlyLTE = false, showingStats = false, torchOn = false, triggeredByContactsIntent = false, biometricPINenabled = false, dualSIM = false;
    LinkedList<Boolean> lteHistory = new LinkedList<>();
    Intent intent;
    String myUPIID, phNumURI;
    int chosenSIM = -1;
    ValuePassHelper valuePassHelper;
    private ActivityResultLauncher<Intent> contactPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData()!=null) {
                    handleContactResult(result.getData().getData());
                }
            }
    );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        beginWritingToLog();
        intent = getIntent();
        Uri data = intent.getData();
        setContentView(R.layout.activity_main);
        EdgeToEdge.enable(this);
        View mainView = findViewById(R.id.main);
        View statusBarBelow = findViewById(R.id.status_bar_below);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            ViewGroup.LayoutParams params = statusBarBelow.getLayoutParams();
            params.height = systemBars.top;
            statusBarBelow.setLayoutParams(params);
            return insets;
        });

        ussd = new USSDActions(this);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        lteHistory.clear();

        curTransactionDetails = getSharedPreferences("TRANSACTION_DATA", MODE_PRIVATE);
        userSettings = getSharedPreferences("USER_SETTINGS", MODE_PRIVATE);
        curTransactionDetails.edit().clear().apply();
        //userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "1").apply();
        curTransactionDetails.edit().putString("AMOUNT", "")
                .putString("PAYEE_NAME", "")
                .putString("UPI_PIN", "")
                .putString("REFERENCE_ID", "")
                .putString("REMARK", "").apply();
        myUPIID = userSettings.getString("USER_UPI_ID", "");

        //userSettings.edit().putString("CHOSEN_SIM", "-1").apply();

        if(userSettings.getString("CHOSEN_SIM", "-1").equals("0")){
            chosenSIM = 0;
        } else if(userSettings.getString("CHOSEN_SIM", "-1").equals("1")){
            chosenSIM = 1;
        }

        checkSIMs(false);

        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            encryptedPreferences = EncryptedSharedPreferences.create(
                    this,
                    "secure_shared_prefs", // Name of the file
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e){
            e.printStackTrace();
        }

        //checkForAllPermissions();
        startCamera();

        checkForQRScan = new CountDownTimer(1, 1) {
            @Override
            public void onFinish() {
                upiIDReadFromQR = "";
                String scannedUPIQR = curTransactionDetails.getString("UPI_ID", "");
                if(!scannedUPIQR.isEmpty() && !dismissedDialog && !paymentInProgress && !triggeredByContactsIntent){
                    ussdSendButton.setEnabled(true);
                    upiIDTextField.setText(scannedUPIQR);
                    upiIDReadFromQR = scannedUPIQR;
                    ussdSendButton.performClick();
                    System.out.print("MAIN button pressed from QR timer due to:");
                    if(!scannedUPIQR.isEmpty())
                        System.out.println(" scannedUPIQR != 0. Instead, it is: " + scannedUPIQR);
                    if(!dismissedDialog)
                        System.out.println(" dismissedDialog false");
                    if(!paymentInProgress)
                        System.out.println(" paymentInProgress false");
                    if(!triggeredByContactsIntent)
                        System.out.println(" triggeredByContactsIntent false");
                } else {
                    //Log.d("D", "QR timer reset!");
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
        settingsButton = findViewById(R.id.settings_button);
        historyButton = findViewById(R.id.history_button);
        showQRButton = findViewById(R.id.show_qr_button);
        torchButton = findViewById(R.id.torch_button);
        upiIDTextField = findViewById(R.id.upiIDField);
        checkBalButton = findViewById(R.id.bal_button);
        signalDebugText = findViewById(R.id.signal_stats_text);
        cameraView = findViewById(R.id.viewFinder);
        signalDebugText.setVisibility(View.GONE);
        //pinTextField = findViewById(R.id.pinField);
        //amountTextField = findViewById(R.id.amountField);

        //sendValuesToUSSDClass(upiID, upiPIN, amount);

        String lteOnly1 = userSettings.getString("LTE_ONLY", "true");
        String showStats1 = userSettings.getString("SHOW_STATS", "false");
        String biometricPINstatus = encryptedPreferences.getString("SAVED_PIN", "");

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

        if(biometricPINstatus.isBlank()){
            biometricPINenabled = false;
        } else {
            biometricPINenabled = true;
        }

        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData()!=null) {
                        handleContactResult(result.getData().getData());
                    }
                }
        );

        valuePassHelper = (ValuePassHelper) getApplicationContext();

        ussdSendButton.setOnClickListener(v -> {
            if(!curTransactionDetails.getString("UPI_ID", "").isEmpty()){
                upiIDTextField.setText(curTransactionDetails.getString("UPI_ID", ""));
            }
            if (!(upiIDTextField.getText().toString().isEmpty() || !upiIDTextField.getText().toString().contains("@")) || (upiIDTextField.getText().toString().matches("\\d+") && upiIDTextField.getText().toString().length() == 10)) {
                /*if((upiIDTextField.getText().toString().matches("\\d+") && upiIDTextField.getText().toString().length() == 10))
                    upiIDTextField.setText(upiIDTextField.getText().toString().concat("@upi"));*/
                curTransactionDetails.edit().putString("UPI_ID", upiIDTextField.getText().toString()).apply();
                upiID = upiIDTextField.getText().toString();
                upiPIN = curTransactionDetails.getString("UPI_PIN", "");
                amount = curTransactionDetails.getString("AMOUNT", "");
            /*curTransactionDetails.edit().putString("UPI_ID", upiID)
                    .putString("AMOUNT", amount)
                    .putString("UPI_PIN", upiPIN)
                    .apply();*/
                if (!upiIDReadFromQR.isEmpty())
                    upiID = upiIDReadFromQR;
                System.out.println("Saved the information as following: \nUPI ID: " + upiID + "\nAmount: " + amount + "\nUPI PIN: " + upiPIN);
                checkForQRScan.cancel();
                showNewDialog("AMOUNT", false);
            /*if(isAccessibilityServiceEnabled())
               ussd.sendUSSDCommand("*99#");*/
            } else if(upiIDTextField.getText().toString().isEmpty()){
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_DENIED){
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 105);
                } else {
                    contactPickerLauncher.launch(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI));
                }
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
                if(!myUPIID.isEmpty()) {
                    showMyQR();
                } else {
                    showNewDialog("USER_UPI_ENTER", false);
                }
                return true;
            }
        });*/

        settingsButton.setOnClickListener(v -> {
            if(dialogBeingShown){
                dialog.dismiss();
                dialogBeingShown = false;
            }

            final AlertDialog settingsMenu;

            View dialogBox = getLayoutInflater().inflate(R.layout.settings, null);
            dialogBox.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setView(dialogBox);
            settingsMenu = builder.create();

            Switch lteOnlyToggle = dialogBox.findViewById(R.id.lteOnlyToggle);
            Switch showStatsToggle = dialogBox.findViewById(R.id.networkStatsToggle);
            Switch reallyPayToggle = dialogBox.findViewById(R.id.really_pay_toggle);
            Button biometricButton = dialogBox.findViewById(R.id.setup_biometrics_button);
            Button changeSIMButton = dialogBox.findViewById(R.id.change_sim_button);
            Button reportButton = dialogBox.findViewById(R.id.report_bug_button);
            TextView versionText = dialogBox.findViewById(R.id.version_text);

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
            if(!isBiometricAvailable()){
                biometricButton.setEnabled(false);
            }
            if(biometricPINenabled){
                biometricButton.setText("Modify Biometric PIN");
            }

            if(dualSIM){
                changeSIMButton.setEnabled(true);
            } else {
                changeSIMButton.setEnabled(false);
            }

            try{
            PackageInfo pInfo = getApplicationContext().getPackageManager()
                    .getPackageInfo(getApplicationContext().getPackageName(), 0);
            versionText.setText("Version: " + pInfo.versionName);
            } catch (Exception e){
                e.printStackTrace();
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

            biometricButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settingsMenu.dismiss();
                    dialogBeingShown = false;
                    showNewDialog("BIOMETRIC_PIN_SETUP", true);
                }
            });

            changeSIMButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settingsMenu.dismiss();
                    dialogBeingShown = false;
                    checkSIMs(true);
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

            settingsMenu.show();
        });

        historyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readTransactionHistory();
            }
        });

        showQRButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!myUPIID.isEmpty()) {
                    showMyQR();
                } else {
                    showNewDialog("USER_UPI_ENTER", false);
                }
            }
        });

        torchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFlash();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if(torchOn)
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK));
                    else
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                }
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
                if(string.contains("@") || (string.length() == 10 && string.matches("\\d+")) || string.isEmpty()){
                    if(string.isEmpty())
                        ussdSendButton.setIcon(getDrawable(R.drawable.contacts_icon_512));
                    else
                        ussdSendButton.setIcon(getDrawable(R.drawable.right_arrow));
                    ussdSendButton.setEnabled(true);
                } else {
                    ussdSendButton.setIcon(getDrawable(R.drawable.right_arrow));
                    ussdSendButton.setEnabled(false);
                }

                /*if(string.length() == 10 && string.matches("\\d+") && !dialogBeingShown && !triggeredByContactsIntent){
                    upiIDTextField.setText(string.concat("@upi"));
                    ussdSendButton.performClick();
                    System.out.println("MAIN button pressed from text entered in field");
                } else if(triggeredByContactsIntent){
                    System.out.println("triggeredByContactsIntent is set to true, ignoring the change in text field...");
                } else if(dialogBeingShown){
                    System.out.println("dialogBeingShown is set to true, ignoring the change in text field...");
                }*/
            }
        });

        //mainText.setText("text has to change now!");

        String ussdNumber = "tel:" + Uri.encode("*99#");
        //Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode("*99*1*3#")));
        //startActivity(intent);
        /*if(isAccessibilityServiceEnabled())
            ussd.sendUSSDCommand("*99#");*/
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "false").apply();
                broadcastAccessibility(false);
                setEnabled(false);
                finishAffinity();
                overridePendingTransition(0, android.R.anim.slide_out_right);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

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
                        showFinalDialog(false, "This payment isn't supported by PayOff. USSD doesn't support merchant payments yet.", true);
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

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                executorService.execute(() -> {
                    try {
                        checkForUpdates();
                    } catch (Exception e){
                        System.out.println("Error occured when checking for updates");
                        e.printStackTrace();
                    } finally {
                        executorService.shutdown();
                    }
                });
            }
        }, 3000);
    }

    void broadcastAccessibility(boolean status){
        ValuePassHelper.sharedValue.postValue(String.valueOf(status));
        System.out.println("Set accessibility service enabled to: " + status);
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

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){
            readPhoneStatePermission = false;
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
            contactsPermission = false;
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
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                //scrolls to the app in the settings menu
                startActivityForResult(intent, 103);
            } catch (Exception e) {
                intent.setData(null);
                //simply opens the settings menu
                startActivityForResult(intent, 103);
            }
            Toast.makeText(this, "Enable the setting for 'PayOff' to continue", Toast.LENGTH_SHORT).show();
            drawOverOtherAppsPermission = true;
        } else if(!cameraPermission){
            showToast("Allow all these permissions", Toast.LENGTH_SHORT);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 102);
            cameraPermission = true;
        } else if(!callPermission){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 101);
            callPermission = true;
        } else if(!locationPermission){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 103);
            locationPermission = true;
        } else if(!readPhoneStatePermission){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 104);
            readPhoneStatePermission = true;
        } else if(!contactsPermission){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 105);
            contactsPermission = true;
        }
    }

    private boolean isBiometricAvailable(){
        BiometricManager biometricManager = BiometricManager.from(this);
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        if(biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS){
            return true;
        } else {
            return false;
        }
    }

    private void authenticateUser(){
        System.out.println("Authentication request received.");
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                showToast("Authentication failed. Enter your UPI PIN manually", Toast.LENGTH_SHORT);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                upiPIN = encryptedPreferences.getString("SAVED_PIN", "");
                textBox.setText(upiPIN);
                mainButton.performClick();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                showToast("Authentication failed. Enter your UPI PIN manually", Toast.LENGTH_SHORT);
            }
        };
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, callback);
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Authorization")
                .setSubtitle("Authorize this transaction using registered biometrics")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    void handleContactResult(Uri contactUri){
        String contactId = "";
        try (Cursor cursor = getContentResolver().query(contactUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
            }
        }

        // 2. Now query the Phone table specifically using that ID
        if (!contactId.isEmpty()) {
            String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
            String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
            String[] selectionArgs = {contactId};

            try (Cursor phoneCursor = getContentResolver().query(
                    contactUri,
                    projection,
                    null,
                    null,
                    null)) {

                if (phoneCursor != null && phoneCursor.moveToFirst()) {
                    int numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    System.out.println("Phone number got from intent: " + phoneCursor.getString(numberIndex).replaceAll("[^0-9+*#]", ""));
                    triggeredByContactsIntent = true;
                    String number = phoneCursor.getString(numberIndex).replaceAll("[^0-9+*#]", "");
                    if(number.startsWith("+91")){
                        number = number.substring(3);
                    }
                    upiIDTextField.setText(number);
                    if(number.length() == 10){
                        curTransactionDetails.edit().putString("UPI_ID", number).apply();
                        checkForQRScan.cancel();
                        upiIDReadFromQR = "";
                        ussdSendButton.setEnabled(true);
                        upiIDReadFromQR = number;
                        ussdSendButton.performClick();
                        System.out.println("MAIN button pressed from Contacts intent");
                    }
                }
            }
        }
    }

    void checkForUpdates() throws Exception{
        System.out.println("Checking for updates...");
        PackageInfo pInfo = getApplicationContext().getPackageManager()
                    .getPackageInfo(getApplicationContext().getPackageName(), 0);
        String currentVersion = pInfo.versionName;
        URL updateURL = new URL("https://api.github.com/repos/gamesbnch1012/payOff/releases/latest");
        HttpURLConnection connection = (HttpURLConnection) updateURL.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "payoff-application");
        if(connection.getResponseCode()==200){
            InputStream response = connection.getInputStream();
            Scanner s = new Scanner(response).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            JSONObject release = new JSONObject(result);
            String latestVersion = release.getString("tag_name");
            System.out.println("Current app version: " + currentVersion + "\nVersion got from GitHub: " + latestVersion);
            String finalURL = "";
            if(Integer.parseInt(latestVersion.substring(4)) > Integer.parseInt(currentVersion.substring(4))){
                System.out.println("Update detected. Opening the download page...");
                JSONArray assets = release.getJSONArray("assets");
                for(int i=0; i<assets.length(); i++){
                    JSONObject asset = assets.getJSONObject(i);
                    if(asset.getString("name").endsWith(".apk")){
                        finalURL = asset.getString("browser_download_url");
                        break;
                    }
                }
                String reallyFinalURL = finalURL;
                if(finalURL.isEmpty()){
                    System.out.println("Failed to check for updates.");
                    showToast("Failed to check for updates.", Toast.LENGTH_SHORT);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showDialog("Update Available", "New version " + latestVersion + " is available to download. The current version is " + currentVersion + ". Would you like to download it now?", "UPDATE_PROMPT=" + reallyFinalURL);
                    }
                });
            } else {
                System.out.println("Already on the latest/newer version.");
            }
        }
        connection.disconnect();
    }

    void checkSIMs(boolean forcePickSIM){
        System.out.println("Initiated SIM checking");
        int simCount = 1;
        //SubscriptionManager sm = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        SubscriptionManager sm = getSystemService(SubscriptionManager.class);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            System.out.println("Phone permission granted");
            List<SubscriptionInfo> activeSubscriptions = sm.getActiveSubscriptionInfoList();
            if(activeSubscriptions!=null){
                simCount = activeSubscriptions.size();
                System.out.println("SIMs found: " + simCount);
            } else {
                showToast("No SIMs found. This app needs at least one SIM", Toast.LENGTH_LONG);
                System.exit(0);
            }
            if(simCount>1){
                dualSIM = true;
                chosenSIM = Integer.parseInt(userSettings.getString("CHOSEN_SIM", "-1"));
                System.out.println("The SIM to be used is saved as: " + chosenSIM);
                if(chosenSIM==-1 || forcePickSIM){
                    System.out.println("SIM isn't picked yet. Prompting the user to select...");
                    if(dialogBeingShown){
                        dialog.dismiss();
                        dialogBeingShown = false;
                    }

                    View dialogBox = getLayoutInflater().inflate(R.layout.sim_selector, null);
                    dialogBox.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setView(dialogBox);
                    final AlertDialog simDialog = builder.create();

                    RadioButton[] simradio = {dialogBox.findViewById(R.id.sim_1_radio), dialogBox.findViewById(R.id.sim_2_radio)};
                    Button saveButton = dialogBox.findViewById(R.id.sim_selector_button);

                    for (SubscriptionInfo info : activeSubscriptions) {
                        int slotIndex = info.getSimSlotIndex();
                        simradio[slotIndex].setText("SIM " + slotIndex + ": " + info.getCarrierName().toString());
                    }

                    if(chosenSIM!=-1){
                        simradio[chosenSIM].setChecked(true);
                    } else {
                        simradio[0].setChecked(true);
                    }

                    saveButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if(simradio[0].isChecked()){
                                userSettings.edit().putString("CHOSEN_SIM", "0").apply();
                                //showToast("SIM 1 Saved", Toast.LENGTH_SHORT);
                                chosenSIM = 0;
                                simDialog.dismiss();
                            } else if(simradio[1].isChecked()){
                                userSettings.edit().putString("CHOSEN_SIM", "1").apply();
                                //showToast("SIM 2 Saved", Toast.LENGTH_SHORT);
                                chosenSIM = 1;
                                simDialog.dismiss();
                            } else {
                                showToast("Select one SIM", Toast.LENGTH_SHORT);
                            }
                        }
                    });
                    simDialog.show();
                } else {
                    if(userSettings.getString("CHOSEN_SIM", "-1").equals("0")){
                        chosenSIM = 0;
                    } else if(userSettings.getString("CHOSEN_SIM", "-1").equals("1")){
                        chosenSIM = 1;
                    }
                    System.out.println("Dual SIM detected. The sim being used: " + chosenSIM);
                }
            } else {
                dualSIM = false;
                chosenSIM = 0;
            }
        } else {
            System.out.println("Phone permission not granted");
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
            String[] command = {
                    "logcat",
                    "-f",
                    logFile.getAbsolutePath(),
                    "RippleDrawable:S", // First filter
                    "*:I",              // Second filter
                    "-v",
                    "time"
            };

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

        if(upiID.isBlank())
            return;

        Map<String, Object> currentTransaction = new HashMap<>();
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");

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
        if(historyList.size()>=20){
            Iterator<Map<String, Object>> iterator = historyList.iterator();
                for(int i=0; i<=historyList.size()-1; i++){
                    //System.out.print("i: " + i);
                    if(i>=20)
                        historyList.remove(i);
                    //System.out.print("\n");
                }
        }

        try(FileWriter writer = new FileWriter(transactionHistory)){
            gson.toJson(historyList, writer);
            System.out.println("Wrote to transaction history JSON.");
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    void readTransactionHistory(){
        boolean firstEntry = true;
        File transactionHistory = new File(getExternalFilesDir(null), "transaction_history.json");
        Gson gson = new Gson();

        if(dialogBeingShown){
            dialog.dismiss();
            dialogBeingShown = false;
        }

        View dialogBox = getLayoutInflater().inflate(R.layout.transaction_history, null);
        dialogBox.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(dialogBox);

        LinearLayout historyContainer = dialogBox.findViewById(R.id.history_container);
        historyContainer.removeAllViews();

        TextView historyText = dialogBox.findViewById(R.id.history_text);

        try (FileReader reader = new FileReader(transactionHistory)) {
            // 1. Tell GSON we are reading a List of Maps
            Type type = new TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
            ArrayList<Map<String, Object>> history = gson.fromJson(reader, type);

            if (history != null) {

                // 2. Loop through each record one-by-one
                for (Map<String, Object> record : history) {
                    StringBuilder stringBuilder = new StringBuilder();

                    String upiID = String.valueOf(record.get("upi_id"));
                    String payeeName = String.valueOf(record.get("payee_name"));
                    String status = String.valueOf(record.get("status"));
                    String amount = String.valueOf(record.get("amount"));
                    String time = String.valueOf(record.get("time"));
                    String refID = String.valueOf(record.get("ref_id"));

                    TextView transaction = new TextView(this);
                    transaction.setPadding(0, 5, 0, 5);

                    if(!firstEntry){
                        stringBuilder.append("\n");
                    } else {
                        firstEntry = false;
                    }

                    if(upiID.isEmpty())
                        continue;

                    if(upiID.contains("@")) {
                        String payeeUPIIdText = "Payee UPI ID: <font face='sans-serif-medium'>" + upiID + "</font>";
                        stringBuilder.append(Html.fromHtml(payeeUPIIdText, Html.FROM_HTML_MODE_LEGACY)).append("\n");
                    } else {
                        String payeeUPIIdText = "Payee Number: <b>" + upiID + "</b>";
                        stringBuilder.append(Html.fromHtml(payeeUPIIdText, Html.FROM_HTML_MODE_LEGACY)).append("\n");
                    }
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
                    //stringBuilder.append("\n\n");

                    transaction.setText(stringBuilder.toString());
                    transaction.setClickable(true);
                    transaction.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if(Boolean.parseBoolean(status))
                                showTransactionFromHistory(status, upiID, payeeName, amount, time, refID);
                            else
                                showDialog("", "This payment wasn't detected as completed. If in doubt, please check any SMS received for confirmation.", "");
                        }
                    });
                    historyContainer.addView(transaction);

                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)); // 2px tall
                    divider.setBackgroundColor(Color.LTGRAY);
                    divider.setPadding(0, 50, 0, 50);
                    historyContainer.addView(divider);
                }

                // 4. Update the TextView
                //historyText.setText(stringBuilder.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        builder.show();
    }

    void showMyQR(){
        MultiFormatWriter writer = new MultiFormatWriter();

        if(dialogBeingShown){
            dialog.dismiss();
            dialogBeingShown = true;
        }

        View dialogBox = getLayoutInflater().inflate(R.layout.user_qr_code, null);
        dialogBox.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(dialogBox);

        ImageView qrImg = dialogBox.findViewById(R.id.qr_code_image);
        TextView upiIDText = dialogBox.findViewById(R.id.upi_id_text);
        upiIDText.setText("UPI ID: " + myUPIID);

        try {
            BitMatrix matrix = writer.encode("upi://pay?pa=" + myUPIID, BarcodeFormat.QR_CODE, 512, 512);
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.createBitmap(matrix);
            qrImg.setImageBitmap(bitmap);
        } catch (Exception e){
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
            Toast.makeText(this, "Find and enable 'PayOff' here", Toast.LENGTH_SHORT).show();
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
                //showNewDialog("CARD_DIGITS", false);
                ussd.sendUSSDCommand("*99#");
                showLoadingDialog("Registering app with UPI...");
                showPaymentProgress("Registering app with UPI...");
                curTransactionDetails.edit().putString("TIMER_INACTIVE", "1").apply();
            }
        });
        dialog = builder.create();
        dialog.show();
        dialogBeingShown = true;
    }

    private void showToast(String message, int duration){
        Toast.makeText(this, message, duration).show();
    }

    private void showDialog(String title, String message, String type){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (!title.isBlank())
            builder.setTitle(title);
        builder.setMessage(message);
        if(type.isBlank()) {
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        } else if(type.contains("UPDATE_PROMPT")){
            builder.setPositiveButton("UPDATE", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String updateURL = type.substring(14);
                    Intent updateIntent = new Intent(Intent.ACTION_VIEW);
                    updateIntent.setData(Uri.parse(updateURL));
                    startActivity(updateIntent);
                }
            });
            builder.setNegativeButton("LATER", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
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
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e("CAMERA_DEBUG", "Use case binding failed", e);
            }

        }, ContextCompat.getMainExecutor(this));

    }

    private void toggleFlash(){
        if(camera!=null){
            camera.getCameraControl().enableTorch(!torchOn);
            torchOn = !torchOn;
        }
    }

    private void pauseCamera(){
        if(cameraProvider!=null)
            cameraProvider.unbindAll();
    }
    private void showNewDialog(String mode, boolean textBoxIsPassword){
        if(dialogBeingShown && !mode.equals("PAYING")){
            dialog.dismiss();
            dialogBeingShown = false;
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
        textBox = dialogBox.findViewById(R.id.main_pin);
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
            if(curTransactionDetails.getString("PAYEE_NAME", "").isEmpty()) {
                if(curTransactionDetails.getString("UPI_ID", "").contains("@upi"))
                    secondBigText.setText(curTransactionDetails.getString("UPI_ID", "NULL").substring(0, 10));
                else
                    secondBigText.setText(curTransactionDetails.getString("UPI_ID", "NULL"));
            } else
                secondBigText.setText(curTransactionDetails.getString("PAYEE_NAME", "NULL"));
            secondBigText.setTextSize(22);
            secondSmallText.setText("to");
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText("PAY");
            textBox.setHint("PIN");
            if(biometricPINenabled)
                authenticateUser();
        } else if(mode.equals("CHECK_BAL_PIN")){
            secondSmallText.setVisibility(View.GONE);
            secondBigText.setVisibility(View.GONE);
            hintText.setVisibility(View.GONE);
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText("CHECK BALANCE");
            firstBigText.setText("PIN");
            firstSmallText.setText("Enter");
            textBox.setHint("PIN");
            if(biometricPINenabled)
                authenticateUser();
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
        } else if(mode.equals("USER_UPI_ENTER")){
            secondSmallText.setVisibility(View.GONE);
            secondBigText.setVisibility(View.GONE);
            hintText.setVisibility(View.VISIBLE);
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText("DONE");
            firstBigText.setText("UPI ID");
            firstSmallText.setText("Enter your");
            hintText.setText("Enter your UPI ID to display your QR:");
            textBox.setHint("Your UPI ID");
            textBox.setInputType(InputType.TYPE_CLASS_TEXT);
        } else if(mode.equals("BIOMETRIC_PIN_SETUP")){
            secondSmallText.setVisibility(View.GONE);
            secondBigText.setVisibility(View.GONE);
            hintText.setVisibility(View.VISIBLE);
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText("SAVE PIN");
            firstBigText.setText("PIN");
            firstSmallText.setText("Enter");
            hintText.setText("Leave blank and save to remove Biometric PIN");
            textBox.setHint("PIN");
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

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                triggeredByContactsIntent = false;
                dialogBeingShown = false;
                upiIDTextField.setText("");
                System.out.println("User dismissed the dialog. Clearing current transaction info...");
                curTransactionDetails.edit().remove("UPI_ID")
                        .remove("AMOUNT")
                        .remove("UPI_PIN")
                        .remove("REMARK").apply();
            }
        });

        dialog = builder.create();
        if(!mode.equals("PAYING") && !paymentInProgress && !isDestroyed() && !isFinishing()) {
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
                if(chosenSIM==-1){
                    checkSIMs(true);
                    return;
                }
                System.out.println("-----------------PAYMENT INITIATED-------------------");
                userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "true").apply();
                broadcastAccessibility(true);
                System.out.println("Accessibility service started");
                String newPIN = textBox.getText().toString();
                secondBigText.setTextSize(40);
                if(newPIN.length()==4 || newPIN.length()==6) {
                    curTransactionDetails.edit().putString("UPI_PIN", newPIN).apply();
                    //ussd.sendUSSDCommand("*99#");
                    if(mode.equals("PIN")) {
                        if(checkIfLteReliable()) {
                            if(upiID.contains("@"))
                                makeCallToNumber("*99*1*3#");
                            else if(upiID.matches("\\d+") && upiID.length()==10){
                                makeCallToNumber("*99*1*1#");
                            }
                        } else {
                            ussd.sendUSSDCommand("*99#");
                        }
                        showPaymentProgress("");
                    }else if(mode.equals("CHECK_BAL_PIN")) {
                        makeCallToNumber("*99*3#");
                        showPaymentProgress("Checking balance...");
                    }
                    showLoadingDialog("Checking balance...");
                    if(!mode.equals("CHECK_BAL_PIN")) {
                        curTransactionDetails.edit().putString("TRANSACTION_FINISH", "0")
                                .putString("UPI_ID", upiIDReadFromQR)
                                .apply();
                    } else {
                        upiIDReadFromQR = "";
                        curTransactionDetails.edit().putString("TRANSACTION_FINISH", "0").apply();
                    }
                    paymentStartTimeout = new CountDownTimer(20000, 1000) {
                        @Override
                        public void onFinish() {
                            String onScreenText = curTransactionDetails.getString("CUR_SCREEN_TEXT", "");
                            if(!onScreenText.isBlank() && !onScreenText.contains("USSD code running")) {
                                System.out.println("Payment timed out. The screen text read at that moment: " + onScreenText);
                                curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-3").apply();
                            }
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
                    showToast("Enter only the last 6 digits.", Toast.LENGTH_SHORT);
                } else {
                    curTransactionDetails.edit().putString("CARD_SIX_DIGITS", lastSixDigits).apply();
                    showNewDialog("CARD_EXPIRY", false);
                }
            } else if(mode.equals("CARD_EXPIRY")){
                String expiryDate = textBox.getText().toString();
                if(expiryDate.length() != 4){
                    showToast("Enter only in MMYY format.", Toast.LENGTH_SHORT);
                } else {
                    curTransactionDetails.edit().putString("CARD_EXPIRY", expiryDate).apply();
                    ussd.sendUSSDCommand("*99#");
                    showLoadingDialog("Registering app with UPI...");
                    showPaymentProgress("Registering app with UPI...");
                    curTransactionDetails.edit().putString("TIMER_INACTIVE", "1").apply();
                }
            } else if(mode.equals("USER_UPI_ENTER")) {
                String userEnteredUPIID = textBox.getText().toString();
                if(userEnteredUPIID.contains("@")){
                    userSettings.edit().putString("USER_UPI_ID", userEnteredUPIID).apply();
                    myUPIID = userEnteredUPIID;
                    showMyQR();
                } else {
                    showToast("Enter a valid UPI ID", Toast.LENGTH_SHORT);
                }
            } else if(mode.equals("BIOMETRIC_PIN_SETUP")){
                String enteredPIN = textBox.getText().toString();
                if(enteredPIN.length()==4 || enteredPIN.length()==6) {
                    encryptedPreferences.edit().putString("SAVED_PIN", enteredPIN).apply();
                    dialog.dismiss();
                    biometricPINenabled = true;
                    showDialog("Biometric PIN is set-up", "Biometric PIN is now set up and will be asked the next time it is required.", "");
                } else if(enteredPIN.isBlank()){
                    if(!biometricPINenabled){
                        showToast("UPI PIN can only be 4 or 6 digits", Toast.LENGTH_SHORT);
                    } else {
                        encryptedPreferences.edit().putString("SAVED_PIN", "").apply();
                        dialog.dismiss();
                        biometricPINenabled = false;
                        showDialog("Biometric PIN removed", "Biometric PIN has been removed successfully.", "");
                    }
                } else {
                    showToast("UPI PIN can only be 4 or 6 digits", Toast.LENGTH_SHORT);
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
                if(s.length() > 6 && textBoxIsPassword){
                    textBox.setText(textBox.getText().toString().substring(0,5));
                }
            }
        });
    }

    void makeCallToNumber(String number){
        TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            List<PhoneAccountHandle> handles = telecomManager.getCallCapablePhoneAccounts();
            String encodedNumber = Uri.encode(number);
            PhoneAccountHandle selectedHandle = handles.get(chosenSIM);
            Uri uri = Uri.parse("tel:" + encodedNumber);

            Intent intent = new Intent(Intent.ACTION_CALL, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selectedHandle);
            startActivity(intent);
        } else {
            System.out.println("READ_PHONE_STATE not granted.");
        }
    }


    private void showLoadingDialog(String customMessage){
        if(dialogBeingShown){
            dialog.dismiss();
            dialogBeingShown = false;
        }
        View loadingBox = getLayoutInflater().inflate(R.layout.loading, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(loadingBox);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

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
        /*Window window = dialog.getWindow();
        if (window != null) {
            // 3. Force the dialog to take up the FULL screen area
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

            // 4. Remove the default dialog background (which has margins)
            window.setBackgroundDrawableResource(android.R.color.transparent);

            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            // 5. Tell the DIALOG window to ignore the notch/cutout
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(lp);
            }
            View decorView = window.getDecorView();
            decorView.setPadding(0, 0, 0, 0);

            // 6. Hide System Bars for this specific dialog window
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decorView);
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }*/
        dialogBeingShown = true;
        paymentInProgress = true;
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
                    showFinalDialog(true, null, false);
                } else if(transaction_status.equals("2")){
                    System.out.println("Balance check complete!");
                    hidePaymentProgress();
                    showFinalDialog(true, "BAL_CHECK_COMPLETE", false);
                } else if(transaction_status.equals("3")){
                    System.out.println("USSD setup complete!");
                    hidePaymentProgress();
                    showFinalDialog(true, "USSD_SETUP_COMPLETE", false);
                }else if(transaction_status.equals("-1")) {
                    showFinalDialog(false, "You have exceeded your daily UPI transaction limit.", false);
                    hidePaymentProgress();
                } else if(transaction_status.equals("-2")) {
                    showFinalDialog(false, "The UPI PIN entered is incorrect.", false);
                    hidePaymentProgress();
                } else if(transaction_status.equals("-3")) {
                    showFinalDialog(false, "Payment has timed out. Check if the money has been debited and retry if it hasn't.", true);
                    hidePaymentProgress();
                } else if(transaction_status.equals("-4")) {
                    showFinalDialog(false, "The amount entered is either invalid or too high.", false);
                    hidePaymentProgress();
                } else if(transaction_status.equals("-5")) {
                    System.out.println("USSD isn't set up!");
                    hidePaymentProgress();
                    showBankSelector();
                    curTransactionDetails.edit().putString("TRANSACTION_FINISH", "").apply();
                    loadingDialog.dismiss();
                } else if(transaction_status.equals("-6")){
                    showFinalDialog(false, "UPI ID (or) QR Code is not valid.", true);
                    hidePaymentProgress();
                } else if(transaction_status.equals("-7")){
                    showFinalDialog(false, "Your bank server is down at the moment.", false);
                    hidePaymentProgress();
                } else if(transaction_status.equals("-8")) {
                    showFinalDialog(false, "Transaction was force quit by user.", true);
                    hidePaymentProgress();
                } else if(transaction_status.equals("-9")){
                    showFinalDialog(false, "The QR code is not a UPI QR code. Try using another UPI app if this was a mistake.", false);
                    hidePaymentProgress();
                }else if (transaction_status.equals("-99")){
                    showFinalDialog(false, "An unknown error occurred and the payment couldn't be processed.", true);
                    hidePaymentProgress();
                } else {
                    this.start();
                }
            }
        }.start();
        forceUPIID.start();
        System.out.println("Loading started with UPI ID as: " + curTransactionDetails.getString("UPI_ID", "NULL") + "\nIn upiIDReadFromQR: " + upiIDReadFromQR);
    }

    private void showFinalDialog(boolean status, String message, boolean otherAppButtonVisible){
        System.out.println("-----------------PAYMENT FINISHED-------------------");
        phNumURI = "upi://pay?pa=";
        paymentInProgress = false;
        triggeredByContactsIntent = false;
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
        Button payWithOtherAppButton = finalMessageBox.findViewById(R.id.other_upi_button);

        secondSmallText.setVisibility(View.GONE);
        secondBigText.setVisibility(View.GONE);

        //this.stopLockTask();

        if(!status){
            statusText.setText("FAILED");
            statusIcon.setImageResource(R.drawable.x_mark_256);
            amount = curTransactionDetails.getString("AMOUNT", "?");
            if(message!=null){
                if(otherAppButtonVisible)
                    statusInfo.setText(message + "\n\nYou can try using another UPI app, which might need internet:");
                else
                    statusInfo.setText(message);
            }
            if(message!=null) {
                if (!message.equals("BAL_CHECK_COMPLETE") && !message.equals("USSD_SETUP_COMPLETE")) {
                    writeToTransactionHistory(curTransactionDetails.getString("UPI_ID", "NULL"), curTransactionDetails.getString("PAYEE_NAME", "NULL"), amount, false, null);
                    String upiID = curTransactionDetails.getString("UPI_ID", "");
                    String payeeName = curTransactionDetails.getString("PAYEE_NAME", "");
                    if(!upiID.isEmpty()){
                        if((upiID.matches("\\d+") && upiID.length() == 10) || upiID.contains("@")){
                            if(upiID.contains("@")){
                                phNumURI = phNumURI.concat(upiID);
                            } else {
                                phNumURI = phNumURI.concat(upiID + "@upi");
                            }
                            if(!payeeName.isEmpty()){
                                phNumURI = phNumURI.concat("&pn=" + payeeName);
                            }
                            if(!amount.equals("?")){
                                phNumURI = phNumURI.concat("&am=" + amount);
                            }
                        }
                    }
                }
            } else {
                writeToTransactionHistory(curTransactionDetails.getString("UPI_ID", "NULL"), curTransactionDetails.getString("PAYEE_NAME", "NULL"), amount, false, null);
            }
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{50,0,0,50,0,0,75,0,0,75,0,0,50,0,0,50,0}, -1));
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
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 255, 0, 0, 255, 0, 0, 50, 0, 0, 50, 0, 0, 255, 0}, -1));
            } else if(message.contains("BAL_CHECK_COMPLETE")){
                vibrator.vibrate(VibrationEffect.createOneShot(60, 90));
            }
        }

        if(otherAppButtonVisible){
            payWithOtherAppButton.setVisibility(View.VISIBLE);
        } else {
            payWithOtherAppButton.setVisibility(View.GONE);
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
                secondSmallText.setVisibility(View.GONE);
                statusInfo.setVisibility(View.VISIBLE);
            }
        }

        payWithOtherAppButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent phNumIntent = new Intent(Intent.ACTION_VIEW);
                phNumIntent.setData(Uri.parse(phNumURI));
                try{
                    startActivityForResult(phNumIntent, 123);
                } catch (ActivityNotFoundException e){
                    showToast("Couldn't open another app", Toast.LENGTH_SHORT);
                }
            }
        });

        if(loadingDialog!=null)
            loadingDialog.dismiss();
        dialog = builder.create();
        dialog.show();

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                triggeredByContactsIntent = false;
                dialogBeingShown = false;
                upiIDTextField.setText("");
                System.out.println("Final dialog dismissed. Clearing current transaction info...");
                curTransactionDetails.edit().remove("UPI_ID")
                        .remove("AMOUNT")
                        .remove("UPI_PIN")
                        .remove("REMARK").apply();
                phNumURI = "";
            }
        });

        userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "false").apply();
        broadcastAccessibility(false);
        System.out.println("Stopped accessibility service");


        curTransactionDetails.edit().putString("AMOUNT", "")
                .putString("PAYEE_NAME", "")
                .putString("UPI_PIN", "")
                .putString("TIMER_INACTIVE", "0")
                .putString("REFERENCE_ID", "")
                .putString("REMARK", "").apply();
    }

    private void showTransactionFromHistory(String status, String upiID, String payeeName, String amount, String time, String refID){
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
        Button payWithOtherAppButton = finalMessageBox.findViewById(R.id.other_upi_button);

        secondSmallText.setVisibility(View.GONE);
        secondBigText.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(60, 90));
        }

        if(status.equals("false")){
            statusText.setText("FAILED");
            statusIcon.setImageResource(R.drawable.x_mark_256);
            statusInfo.setText("\n\n" + time + "\n\nYou can try using another UPI app, which might need internet:");
            payWithOtherAppButton.setVisibility(View.VISIBLE);
        } else {
            smallText.setText("Paid");
            statusText.setText("₹" + amount);
            secondSmallText.setVisibility(View.VISIBLE);
            secondBigText.setVisibility(View.VISIBLE);
            payWithOtherAppButton.setVisibility(View.GONE);
            if(!payeeName.isEmpty())
                secondBigText.setText(payeeName);
            else
                secondBigText.setText(upiID);
            if(!refID.isEmpty())
                statusInfo.setText(time + "\n\nReference ID: " + refID);
        }

        if(loadingDialog!=null)
            loadingDialog.dismiss();
        dialog = builder.create();
        dialog.show();
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        ViewCompat.setOnApplyWindowInsetsListener(onTopView, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return insets;
        });

        // 3. Add the view to the screen
        if (Settings.canDrawOverlays(this)) {
            //this.startLockTask();
            valuePassHelper.setOnTopView(onTopView);
            fullScreenUI();
            curTransactionDetails.edit().putString("TIMER_INACTIVE", "0").apply();
            windowManager.addView(onTopView, params);
            progressBar = onTopView.findViewById(R.id.paymentProgress);
            progressText = onTopView.findViewById(R.id.textView);
            forceStopText = onTopView.findViewById(R.id.force_stop_text);
            forceStopButton = onTopView.findViewById(R.id.force_stop_button);
            forceStopButton.setVisibility(View.GONE);
            forceStopText.setVisibility(View.GONE);
            progressBar.setIndeterminate(true);
            forceStopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hidePaymentProgress();
                    curTransactionDetails.edit().putString("TRANSACTION_FINISH", "-8").apply();
                }
            });

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
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public void unFullScreenUI(){
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, windowInsets) -> {
            v.setPadding(0, 0, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });
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
            //curTransactionDetails.edit().putString("UPI_ID", "0").apply();
            upiIDTextField.clearFocus();
            //upiIDTextField.setText("");
            curTransactionDetails.edit().putString("PAYEE_NAME", "").apply();
            startCamera();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
            public void run(){
                    checkForQRScan.start();
                }
            }, 1000);
        } else {
            System.out.println("Main activity focus left.");
            pauseCamera();
            checkForQRScan.cancel();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        userSettings.edit().putString("ACCESSIBILITY_ACTIVE", "false").apply();
        //broadcastAccessibility(false);
        System.out.println("App left.");
    }

    @Override
    protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        System.out.println("App brought to foreground.");
    }
}

