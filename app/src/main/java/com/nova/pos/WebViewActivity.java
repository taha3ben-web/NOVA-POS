package com.nova.pos;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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
    private Toolbar toolbar;
    private String currentUrl;
    private ValueCallback<Uri[]> fileUploadCallback;
    private static final int FILE_CHOOSER_REQUEST = 100;

    private boolean isFullscreen = false;
    private GestureDetector gestureDetector;

    // ─────────────────────────────────────────────────────────────
    // JS يُحقن بعد تحميل كل صفحة - يعيد تعريف APIs غير المدعومة
    // في WebView بحيث تستدعي الواجهة الأصلية للأندرويد
    // ─────────────────────────────────────────────────────────────
    private static final String INJECT_JS =
        "(function() {" +
        "  if(window.__novaAndroidPatched) return;" +
        "  window.__novaAndroidPatched = true;" +

        // ── 1. تعريف AndroidFullscreen interface كـ fallback واضح ──
        "  window.AndroidFullscreen = window.AndroidFullscreen || {};" +

        // ── 2. إعادة تعريف screen.orientation.lock ──
        // waiter.html تستدعي: screen.orientation.lock('landscape')
        "  try {" +
        "    if(!screen.orientation) screen.orientation = {};" +
        "    screen.orientation.lock = function(orientation) {" +
        "      if(window.AndroidFullscreen && window.AndroidFullscreen.setLandscape) {" +
        "        var isLand = orientation && orientation.indexOf('landscape') !== -1;" +
        "        window.AndroidFullscreen.setLandscape(isLand);" +
        "      }" +
        "      return Promise.resolve();" +
        "    };" +
        "    screen.orientation.unlock = function() {" +
        "      if(window.AndroidFullscreen && window.AndroidFullscreen.setLandscape) {" +
        "        window.AndroidFullscreen.setLandscape(false);" +
        "      }" +
        "    };" +
        "  } catch(e) {}" +

        // ── 3. إعادة تعريف requestFullscreen ──
        // waiter.html تستدعي: document.documentElement.requestFullscreen()
        "  try {" +
        "    var _origReq = Element.prototype.requestFullscreen;" +
        "    Element.prototype.requestFullscreen = function() {" +
        "      if(window.AndroidFullscreen && window.AndroidFullscreen.enterFullscreen) {" +
        "        window.AndroidFullscreen.enterFullscreen();" +
        "        return Promise.resolve();" +
        "      }" +
        "      if(_origReq) return _origReq.call(this);" +
        "      return Promise.resolve();" +
        "    };" +
        "    Element.prototype.webkitRequestFullscreen = Element.prototype.requestFullscreen;" +
        "  } catch(e) {}" +

        // ── 4. إعادة تعريف document.exitFullscreen ──
        "  try {" +
        "    var _origExit = document.exitFullscreen;" +
        "    document.exitFullscreen = function() {" +
        "      if(window.AndroidFullscreen && window.AndroidFullscreen.exitFullscreen) {" +
        "        window.AndroidFullscreen.exitFullscreen();" +
        "        return Promise.resolve();" +
        "      }" +
        "      if(_origExit) return _origExit.call(document);" +
        "      return Promise.resolve();" +
        "    };" +
        "    document.webkitExitFullscreen = document.exitFullscreen;" +
        "  } catch(e) {}" +

        "  console.log('[NOVA] Android WebView APIs patched successfully');" +
        "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);

        toolbar    = findViewById(R.id.toolbar);
        webView    = findViewById(R.id.webView);
        progressBar= findViewById(R.id.progressBar);
        errorText  = findViewById(R.id.errorText);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());

        // نقر مزدوج يفعّل/يلغي ملء الشاشة
        gestureDetector = new GestureDetector(this,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    toggleFullscreen();
                    return true;
                }
            });

        webView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });

        currentUrl = getIntent().getStringExtra("url");
        if (currentUrl == null || currentUrl.isEmpty()) {
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

    // ─────────────────────────────────────────────────────────────
    // Fullscreen
    // ─────────────────────────────────────────────────────────────

    private void toggleFullscreen() {
        setFullscreenMode(!isFullscreen);
    }

    private void setFullscreenMode(boolean enable) {
        isFullscreen = enable;
        View decorView = getWindow().getDecorView();

        if (enable) {
            toolbar.setVisibility(View.GONE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        } else {
            toolbar.setVisibility(View.VISIBLE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullscreen) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    // ─────────────────────────────────────────────────────────────
    // JavaScript Interface
    // ─────────────────────────────────────────────────────────────

    private class FullscreenInterface {

        @JavascriptInterface
        public void enterFullscreen() {
            runOnUiThread(() -> setFullscreenMode(true));
        }

        @JavascriptInterface
        public void exitFullscreen() {
            runOnUiThread(() -> setFullscreenMode(false));
        }

        @JavascriptInterface
        public void toggleFullscreen() {
            runOnUiThread(() -> WebViewActivity.this.toggleFullscreen());
        }

        @JavascriptInterface
        public boolean isFullscreen() {
            return WebViewActivity.this.isFullscreen;
        }

        @JavascriptInterface
        public void setLandscape(boolean landscape) {
            runOnUiThread(() -> {
                if (landscape) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
                }
            });
        }
    }

    // ─────────────────────────────────────────────────────────────
    // WebView Setup
    // ─────────────────────────────────────────────────────────────

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

        String dbPath = getApplicationContext().getDir("database", MODE_PRIVATE).getPath();
        settings.setDatabasePath(dbPath);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // تسجيل الـ interface
        webView.addJavascriptInterface(new FullscreenInterface(), "AndroidFullscreen");

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
                // حقن الكود الذي يعيد تعريف APIs غير المدعومة في WebView
                view.evaluateJavascript(INJECT_JS, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                progressBar.setVisibility(View.GONE);
                if (request.isForMainFrame()) {
                    errorText.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                                                    WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView wv,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null)
                    fileUploadCallback.onReceiveValue(null);
                fileUploadCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(
                    Intent.createChooser(intent, "اختر صورة"),
                    FILE_CHOOSER_REQUEST
                );
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                setFullscreenMode(true);
            }

            @Override
            public void onHideCustomView() {
                setFullscreenMode(false);
            }
        });

        errorText.setOnClickListener(v -> {
            errorText.setVisibility(View.GONE);
            webView.loadUrl(currentUrl);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileUploadCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            fileUploadCallback.onReceiveValue(results);
            fileUploadCallback = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (!url.isEmpty()) webView.loadUrl(url);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isFullscreen) { setFullscreenMode(false); return true; }
            if (webView.canGoBack()) { webView.goBack(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onPause()   { super.onPause();   webView.onPause();  }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (webView != null) webView.destroy();
    }
}
