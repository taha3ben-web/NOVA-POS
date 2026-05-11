package com.nova.pos;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CompoundBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String PREFS_NAME = "NovaPOS_Prefs";
    private static final String KEY_LAST_IP = "last_ip";

    private CompoundBarcodeView barcodeView;
    private EditText etIpAddress;
    private Button btnConnect;
    private Button btnSwitchCamera;
    private TextView tvLastConnection;
    private TextView tvScanStatus;

    private SharedPreferences sharedPreferences;
    private boolean isUsingFrontCamera = false;
    private boolean scannerActive = false;
    private boolean resultHandled = false;

    private final BarcodeCallback barcodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null || resultHandled) return;
            resultHandled = true;
            barcodeView.pause();

            String scannedText = result.getText().trim();
            String url = buildUrl(scannedText);

            saveLastIp(extractIp(url));
            openWebView(url);
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        loadLastIp();

        if (hasCameraPermission()) {
            startScanner();
        } else {
            requestCameraPermission();
        }
    }

    private void initViews() {
        barcodeView     = findViewById(R.id.barcode_scanner);
        etIpAddress     = findViewById(R.id.et_ip_address);
        btnConnect      = findViewById(R.id.btn_connect);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        tvLastConnection = findViewById(R.id.tv_last_connection);
        tvScanStatus    = findViewById(R.id.tv_scan_status);

        Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.setStatusText("");

        btnConnect.setOnClickListener(v -> onConnectClicked());

        btnSwitchCamera.setOnClickListener(v -> switchCamera());

        tvLastConnection.setOnClickListener(v -> {
            String lastIp = sharedPreferences.getString(KEY_LAST_IP, "");
            if (!TextUtils.isEmpty(lastIp)) {
                String url = buildUrl(lastIp);
                openWebView(url);
            }
        });
    }

    private void loadLastIp() {
        String lastIp = sharedPreferences.getString(KEY_LAST_IP, "");
        if (!TextUtils.isEmpty(lastIp)) {
            tvLastConnection.setVisibility(View.VISIBLE);
            tvLastConnection.setText(getString(R.string.last_connection) + " " + lastIp);
            etIpAddress.setText(lastIp);
        } else {
            tvLastConnection.setVisibility(View.GONE);
        }
    }

    private void saveLastIp(String ip) {
        sharedPreferences.edit().putString(KEY_LAST_IP, ip).apply();
    }

    private String extractIp(String url) {
        // Strip http:// or https://
        String ip = url;
        if (ip.startsWith("http://"))  ip = ip.substring(7);
        if (ip.startsWith("https://")) ip = ip.substring(8);
        // Strip trailing path
        int slash = ip.indexOf('/');
        if (slash != -1) ip = ip.substring(0, slash);
        return ip;
    }

    private String buildUrl(String input) {
        input = input.trim();

        // If it already looks like a full URL, just ensure /waiter path
        if (input.startsWith("http://") || input.startsWith("https://")) {
            if (!input.contains(":3000")) {
                // Insert port before any path
                int pathStart = input.indexOf('/', 8);
                if (pathStart != -1) {
                    input = input.substring(0, pathStart) + ":3000" + input.substring(pathStart);
                } else {
                    input = input + ":3000";
                }
            }
            return input;
        }

        // It's a bare IP or IP:port
        if (!input.contains(":")) {
            input = input + ":3000";
        }
        return "http://" + input;
    }

    private void onConnectClicked() {
        String ipText = etIpAddress.getText().toString().trim();
        if (TextUtils.isEmpty(ipText)) {
            Toast.makeText(this, R.string.enter_ip, Toast.LENGTH_SHORT).show();
            return;
        }

        String url = buildUrl(ipText);
        saveLastIp(extractIp(url));
        loadLastIp();
        openWebView(url);
    }

    private void openWebView(String url) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(WebViewActivity.EXTRA_URL, url);
        startActivity(intent);
        resultHandled = false; // reset for next time
    }

    private void switchCamera() {
        isUsingFrontCamera = !isUsingFrontCamera;
        barcodeView.pause();
        if (isUsingFrontCamera) {
            barcodeView.getCameraSettings().setRequestedCameraId(1);
        } else {
            barcodeView.getCameraSettings().setRequestedCameraId(0);
        }
        barcodeView.resume();
        btnSwitchCamera.setText(isUsingFrontCamera
                ? R.string.camera_back
                : R.string.camera_front);
    }

    private void startScanner() {
        scannerActive = true;
        resultHandled = false;
        barcodeView.decodeContinuous(barcodeCallback);
        barcodeView.resume();
        if (tvScanStatus != null) {
            tvScanStatus.setText(R.string.scan_qr_hint);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner();
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission() && scannerActive) {
            resultHandled = false;
            barcodeView.resume();
        }
        loadLastIp();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        barcodeView.pause();
    }
}
