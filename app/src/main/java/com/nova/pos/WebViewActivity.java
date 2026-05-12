package com.nova.pos;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView errorText;
    private ImageButton backButton;
    private String currentUrl;
    private ValueCallback<Uri[]> fileUploadCallback;
    private static final int FILE_CHOOSER_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        // السماح بالدوران الحر
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);

        Toolbar toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        errorText = findViewById(R.id.errorText);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());

        // الحصول على الرابط
        currentUrl = getIntent().getStringExtra("url");
        if (currentUrl == null || currentUrl.isEmpty()) {
            // التعامل مع NFC أو الروابط الخارجية
            Intent intent = getIntent();
            if (intent != null && intent.getData() != null) {
                currentUrl = intent.getData().toString();
            }
            if (currentUrl == null || currentUrl.isEmpty()) {
                currentUrl = "http://100.111.42.87:3000/waiter";
            }
        }

        setupWebView();
        webView.loadUrl(currentUrl);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        String dbPath = this.getApplicationContext().getDir("database", MODE_PRIVATE).getPath();
        settings.setDatabasePath(dbPath);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                errorText.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                currentUrl = url;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                progressBar.setVisibility(View.GONE);
                if (request.isForMainFrame()) {
                    errorText.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "اختر صورة"), FILE_CHOOSER_REQUEST);
                return true;
            }
        });

        errorText.setOnClickListener(v -> {
            errorText.setVisibility(View.GONE);
            webView.loadUrl(currentUrl);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        results = new Uri[]{uri};
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // التعامل مع NFC أثناء تشغيل التطبيق
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url != null && !url.isEmpty()) {
                webView.loadUrl(url);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}
