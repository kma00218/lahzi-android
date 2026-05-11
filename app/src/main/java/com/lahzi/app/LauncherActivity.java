package com.lahzi.app;

import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen WebView launcher — no Chrome/Custom Tab dependency.
 *
 * Loads https://lahzi.ly directly in an in-app WebView, which is always
 * available on every Android device. Handles SSL errors, network errors,
 * and provides a retry button when the site cannot be reached.
 */
public class LauncherActivity extends AppCompatActivity {

    static final String TARGET_URL = "https://lahzi.ly";
    private static final int NAV_COLOR = 0xFF0B1220;

    private WebView mWebView;
    private ProgressBar mSpinner;
    private LinearLayout mErrorLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Navy status bar & nav bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(NAV_COLOR);
            getWindow().setNavigationBarColor(NAV_COLOR);
        }

        mWebView    = findViewById(R.id.webview);
        mSpinner    = findViewById(R.id.loading_spinner);
        mErrorLayout = findViewById(R.id.error_layout);

        Button retryBtn = findViewById(R.id.btn_retry);
        if (retryBtn != null) {
            retryBtn.setOnClickListener(v -> loadUrl());
        }

        setupWebView();
        loadUrl();
    }

    private void loadUrl() {
        showLoading(true);
        mErrorLayout.setVisibility(View.GONE);
        mWebView.setVisibility(View.VISIBLE);
        mWebView.loadUrl(TARGET_URL);
    }

    private void setupWebView() {
        WebSettings s = mWebView.getSettings();

        // JavaScript is required for the PWA
        s.setJavaScriptEnabled(true);
        // localStorage / sessionStorage support
        s.setDomStorageEnabled(true);
        // Disk-based HTML5 database (legacy, still used by some PWAs)
        s.setDatabaseEnabled(true);
        // Cache: use HTTP headers to decide (network-first, falls back to cache when offline)
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Viewport / responsive layout
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // Mixed content: needed if any sub-resources load over HTTP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        // Media auto-play (required for PWA audio features)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            s.setMediaPlaybackRequiresUserGesture(false);
        }
        // Security: disable file access (we only load from the network)
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        // Encoding
        s.setDefaultTextEncodingName("UTF-8");

        mWebView.setBackgroundColor(NAV_COLOR);

        mWebView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Return false = let WebView handle all navigations (including redirects).
                // Returning true here would block redirects and break the site.
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                showLoading(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                showLoading(false);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Proceed for our own domain; cancel for everything else.
                String url = error.getUrl();
                if (url != null && (url.contains("lahzi.ly") || url.contains("lahzi.online"))) {
                    handler.proceed();
                } else {
                    handler.cancel();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    showLoading(false);
                    mWebView.setVisibility(View.GONE);
                    mErrorLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        // WebChromeClient: needed for JavaScript console messages, geolocation, file pickers
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 80) showLoading(false);
            }
        });
    }

    private void showLoading(boolean show) {
        if (mSpinner != null) {
            mSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
        }
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
