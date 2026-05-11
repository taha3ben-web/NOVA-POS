package com.nova.pos;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout layoutError;
    private TextView tvErrorMessage;
    private Button btnRetry;
    private TextView tvPageTitle;
    private ImageButton btnBack;

    private String serverUrl;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        serverUrl = getIntent().getStringExtra(EXTRA_URL);
        if (serverUrl == null || serverUrl.isEmpty()) {
            finish();
            return;
        }

        // Append /waiter path if not already present
        if (!serverUrl.contains("/waiter")) {
            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl + "waiter";
            } else {
                serverUrl = serverUrl + "/waiter";
            }
        }

        initViews();
        setupWebView();
        loadUrl(serverUrl);
    }

    private void initViews() {
        webView       = findViewById(R.id.webview);
        progressBar   = findViewById(R.id.progress_bar);
        layoutError   = findViewById(R.id.layout_error);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        btnRetry      = findViewById(R.id.btn_retry);
        tvPageTitle   = findViewById(R.id.tv_page_title);
        btnBack       = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> goBack());

        btnRetry.setOnClickListener(v -> {
            layoutError.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            loadUrl(serverUrl);
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "ObsoleteSdkInt"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Storage
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Zoom
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Content
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Mixed content
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Cache
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // WebViewClient – open all links inside the app
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                layoutError.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showError();
                }
            }

            // Allow self-signed / local SSL
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           SslError error) {
                handler.proceed();
            }
        });

        // WebChromeClient for title updates and progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (tvPageTitle != null && title != null) {
                    tvPageTitle.setText(title.isEmpty() ? getString(R.string.app_name) : title);
                }
            }
        });
    }

    private void loadUrl(String url) {
        webView.loadUrl(url);
    }

    private void showError() {
        progressBar.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(getString(R.string.connection_error) + "\n" + serverUrl);
    }

    private void goBack() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            goBack();
        }
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
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
