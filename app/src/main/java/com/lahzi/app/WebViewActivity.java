package com.lahzi.app;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen WebView fallback.
 * Used when Chrome / Custom Tabs are not available (BlueStacks, restricted devices).
 * JavaScript, DOM storage, and cache are enabled for full PWA compatibility.
 */
public class WebViewActivity extends AppCompatActivity {

    static final String EXTRA_URL = "url";
    private static final int NAV_COLOR = 0xFF0B1220;

    private WebView mWebView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        // Navy status bar / nav bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(NAV_COLOR);
            getWindow().setNavigationBarColor(NAV_COLOR);
        }

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.isEmpty()) {
            url = LauncherActivity.TARGET_URL;
        }

        mWebView = findViewById(R.id.webview);
        WebSettings settings = mWebView.getSettings();

        // Enable JavaScript for the PWA
        settings.setJavaScriptEnabled(true);
        // Enable DOM storage (localStorage / sessionStorage)
        settings.setDomStorageEnabled(true);
        // Use network cache following HTTP headers
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Disable file access (security)
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        // Viewport support
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        // Encoding
        settings.setDefaultTextEncodingName("UTF-8");

        // Keep navigation within our domain
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                // Stay in-app for lahzi.ly pages; open external links in browser
                return !reqUrl.startsWith("https://lahzi.ly")
                        && !reqUrl.startsWith("http://lahzi.ly");
            }
        });

        mWebView.setBackgroundColor(Color.parseColor("#0B1220"));
        mWebView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWebView != null) mWebView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null) mWebView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
