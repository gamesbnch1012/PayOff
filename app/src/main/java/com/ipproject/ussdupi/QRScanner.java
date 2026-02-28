package com.ipproject.ussdupi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.widget.Toast;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.StringTokenizer;

public class QRScanner implements ImageAnalysis.Analyzer {
    private BarcodeScanner scanner = BarcodeScanning.getClient();
    private Context context;
    SharedPreferences curTransactionDetails;

    public QRScanner(Context context){
        this.context = context;
    }

    @Override
    public void analyze(ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (com.google.mlkit.vision.barcode.common.Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            if (rawValue != null) {
                                android.util.Log.d("QR_SCANNER", "QR Found: " + rawValue);
                                curTransactionDetails = context.getSharedPreferences("TRANSACTION_DATA", Context.MODE_PRIVATE);
                                if(rawValue.startsWith("upi://pay?pa=")) {
                                    StringTokenizer st = new StringTokenizer(rawValue, "&");
                                    curTransactionDetails.edit().putString("UPI_ID", st.nextToken().substring(13)).apply();
                                    while(st.hasMoreElements()){
                                        registerQRInfo(st.nextToken());
                                    }
                                } else {
                                    Toast.makeText(context, "Not a UPI QR.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close()); // MUST close the proxy
        } else {
            imageProxy.close();
        }
    }


    void registerQRInfo(String token){
        if(token.startsWith("pn=")){
            String finalPayeeName = token.substring(3).replaceAll("%20", " ");
            curTransactionDetails.edit().putString("PAYEE_NAME", finalPayeeName).apply();
            System.out.println("Detected payee name as: " + token.substring(3));
        } else if(token.startsWith("am=")){
            curTransactionDetails.edit().putString("AMOUNT", token.substring(3)).apply();
            System.out.println("Detected amount as: " + token.substring(3));
        } else if(token.startsWith("tn=")){
            curTransactionDetails.edit().putString("REMARK", token.substring(3)).apply();
            System.out.println("Detected remark as: " + token.substring(3));
        }
    }
}
