package com.nova.pos;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CompoundBarcodeView;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private CompoundBarcodeView barcodeScanner;
    private TextInputEditText ipInput;
    private MaterialButton connectButton;
    private TextView lastConnectionText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("NovaPOS", MODE_PRIVATE);

        barcodeScanner = findViewById(R.id.barcode_scanner);
        ipInput = findViewById(R.id.ipInput);
        connectButton = findViewById(R.id.connectButton);
        lastConnectionText = findViewById(R.id.lastConnection);

        // آخر IP
        String lastIp = prefs.getString("last_ip", "");
        if (!lastIp.isEmpty()) {
            ipInput.setText(lastIp);
            lastConnectionText.setText("آخر اتصال: " + lastIp);
        }

        // صلاحية الكاميرا
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        } else {
            startScanner();
        }

        // زر الاتصال
        connectButton.setOnClickListener(v -> connectToServer());

        // آخر اتصال قابل للنقر
        lastConnectionText.setOnClickListener(v -> {
            String savedIp = prefs.getString("last_ip", "");
            if (!savedIp.isEmpty()) {
                ipInput.setText(savedIp);
                connectToServer();
            }
        });
    }

    private void startScanner() {
        barcodeScanner.setStatusText("");
        barcodeScanner.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() != null) {
                    String scannedText = result.getText();
                    barcodeScanner.pause();
                    
                    String ip = extractIpFromText(scannedText);
                    if (ip != null) {
                        ipInput.setText(ip);
                        new Handler().postDelayed(() -> connectToServer(), 500);
                    }
                }
            }

            @Override
            public void possibleResultPoints(java.util.List<com.google.zxing.ResultPoint> resultPoints) {
            }
        });
        barcodeScanner.resume();
    }

    private String extractIpFromText(String text) {
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return text.replace("http://", "").replace("https://", "").split("/")[0];
        }
        if (text.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*")) {
            return text.replaceAll("[^0-9.:]", "");
        }
        return text;
    }

    private void connectToServer() {
        String ip = ipInput.getText().toString().trim();
        
        if (ip.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال عنوان السيرفر", Toast.LENGTH_SHORT).show();
            return;
        }

        ip = ip.replace("http://", "").replace("https://", "");
        if (!ip.contains(":")) {
            ip = ip + ":3000";
        }

        final String finalIp = ip;
        prefs.edit().putString("last_ip", finalIp).apply();
        lastConnectionText.setText("آخر اتصال: " + finalIp);
        
        connectButton.setText("جاري الاتصال...");
        connectButton.setEnabled(false);

        // فتح مباشر بدون اختبار الاتصال
        new Handler().postDelayed(() -> {
            connectButton.setText("اتصال");
            connectButton.setEnabled(true);
            Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
            intent.putExtra("url", "http://" + finalIp + "/waiter");
            startActivity(intent);
        }, 300);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner();
            } else {
                Toast.makeText(this, "صلاحية الكاميرا مطلوبة", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeScanner != null) {
            barcodeScanner.resume();
        }
        String lastIp = prefs.getString("last_ip", "");
        if (!lastIp.isEmpty() && ipInput.getText().toString().isEmpty()) {
            ipInput.setText(lastIp);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeScanner != null) {
            barcodeScanner.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (barcodeScanner != null) {
            barcodeScanner.pause();
        }
    }
}
