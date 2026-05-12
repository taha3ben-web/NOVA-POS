package com.nova.pos;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
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
import com.journeyapps.barcodescanner.camera.CameraSettings;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private CompoundBarcodeView barcodeScanner;
    private TextInputEditText ipInput;
    private MaterialButton connectButton, switchCameraButton, flashButton;
    private TextView lastConnectionText;
    private SharedPreferences prefs;
    private boolean isFlashOn = false;
    private boolean isFrontCamera = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("NovaPOS", MODE_PRIVATE);

        barcodeScanner = findViewById(R.id.barcode_scanner);
        ipInput = findViewById(R.id.ipInput);
        connectButton = findViewById(R.id.connectButton);
        switchCameraButton = findViewById(R.id.switchCameraButton);
        flashButton = findViewById(R.id.flashButton);
        lastConnectionText = findViewById(R.id.lastConnection);

        String lastIp = prefs.getString("last_ip", "");
        if (!lastIp.isEmpty()) {
            ipInput.setText(lastIp);
            lastConnectionText.setText("آخر اتصال: " + lastIp);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        } else {
            startScanner();
        }

        connectButton.setOnClickListener(v -> connectToServer());

        lastConnectionText.setOnClickListener(v -> {
            String savedIp = prefs.getString("last_ip", "");
            if (!savedIp.isEmpty()) {
                ipInput.setText(savedIp);
                connectToServer();
            }
        });

        switchCameraButton.setOnClickListener(v -> {
            isFrontCamera = !isFrontCamera;
            barcodeScanner.pause();
            
            CameraSettings settings = barcodeScanner.getBarcodeView().getCameraSettings();
            if (isFrontCamera) {
                settings.setRequestedCameraId(1);
            } else {
                settings.setRequestedCameraId(0);
            }
            barcodeScanner.getBarcodeView().setCameraSettings(settings);
            
            barcodeScanner.resume();
        });

        flashButton.setOnClickListener(v -> {
            if (!isFrontCamera) {
                isFlashOn = !isFlashOn;
                if (isFlashOn) {
                    barcodeScanner.setTorchOn();
                } else {
                    barcodeScanner.setTorchOff();
                }
            } else {
                Toast.makeText(this, "الفلاش يعمل فقط مع الكاميرا الخلفية", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.settingsButton).setOnClickListener(v -> {
            Toast.makeText(this, "الإعدادات قريباً", Toast.LENGTH_SHORT).show();
        });
    }

    private void startScanner() {
        barcodeScanner.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() != null) {
                    String scannedText = result.getText();
                    barcodeScanner.pause();
                    
                    String ip = extractIpFromText(scannedText);
                    if (ip != null) {
                        ipInput.setText(ip);
                        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                        if (vibrator != null) {
                            vibrator.vibrate(100);
                        }
                        new Handler().postDelayed(() -> connectToServer(), 800);
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
        if (text.contains("nova.local")) {
            return text;
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

        new Thread(() -> {
            boolean reachable = testConnection(finalIp);
            runOnUiThread(() -> {
                connectButton.setText("اتصال بالسيرفر");
                connectButton.setEnabled(true);
                
                if (reachable) {
                    Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                    intent.putExtra("url", "http://" + finalIp + "/waiter");
                    startActivity(intent);
                } else {
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("تعذر الاتصال")
                        .setMessage("لم نتمكن من الاتصال بـ " + finalIp + "\n\nهل تريد المحاولة مباشرة؟")
                        .setPositiveButton("نعم", (dialog, which) -> {
                            Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                            intent.putExtra("url", "http://" + finalIp + "/waiter");
                            startActivity(intent);
                        })
                        .setNegativeButton("لا", null)
                        .show();
                }
            });
        }).start();
    }

    private boolean testConnection(String ip) {
        try {
            String host = ip.split(":")[0];
            int port = ip.contains(":") ? Integer.parseInt(ip.split(":")[1]) : 3000;
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner();
            } else {
                Toast.makeText(this, "صلاحية الكاميرا مطلوبة لمسح QR Code", Toast.LENGTH_LONG).show();
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
