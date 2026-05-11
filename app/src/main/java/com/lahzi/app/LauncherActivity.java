package com.lahzi.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.lang.ref.WeakReference;

/**
 * LauncherActivity — full-screen WebView for https://lahzi.ly
 *
 * Features:
 *  - Loads lahzi.ly directly (no Chrome/Custom Tab dependency)
 *  - External links (WhatsApp, Telegram, Facebook, Instagram…) routed to installed apps
 *  - navigator.share → Android native share sheet via JavascriptInterface
 *  - POST_NOTIFICATIONS permission requested once on first launch (Android 13+)
 *  - Loading spinner + network error screen with Arabic retry button
 */
public class LauncherActivity extends AppCompatActivity {

    static final  String TARGET_URL     = "https://lahzi.ly";
    private static final int    NAV_COLOR       = 0xFF0B1220;
    private static final int    REQ_NOTIF       = 1001;
    private static final String PREF_FILE       = "lahzi_prefs";
    private static final String PREF_NOTIF_ASKED = "notif_permission_asked";

    // Share text defaults
    private static final String SHARE_TITLE = "Lahzi | لحظي";
    private static final String SHARE_URL   = "https://lahzi.ly";

    private WebView      mWebView;
    private ProgressBar  mSpinner;
    private LinearLayout mErrorLayout;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Navy status + navigation bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(NAV_COLOR);
            getWindow().setNavigationBarColor(NAV_COLOR);
        }

        mWebView     = findViewById(R.id.webview);
        mSpinner     = findViewById(R.id.loading_spinner);
        mErrorLayout = findViewById(R.id.error_layout);

        Button retryBtn = findViewById(R.id.btn_retry);
        if (retryBtn != null) retryBtn.setOnClickListener(v -> loadUrl());

        setupWebView();
        loadUrl();

        // Request notification permission ~2 s after launch so the user sees
        // the app first and the dialog doesn't feel intrusive.
        new Handler(Looper.getMainLooper()).postDelayed(
                this::maybeRequestNotificationPermission, 2000);
    }

    @Override protected void onPause()   { super.onPause();   if (mWebView != null) mWebView.onPause();  }
    @Override protected void onResume()  { super.onResume();  if (mWebView != null) mWebView.onResume(); }
    @Override protected void onDestroy() {
        if (mWebView != null) { mWebView.stopLoading(); mWebView.destroy(); mWebView = null; }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) mWebView.goBack();
        else super.onBackPressed();
    }

    // -------------------------------------------------------------------------
    // Notification permission (Android 13+ POST_NOTIFICATIONS)
    // -------------------------------------------------------------------------

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_NOTIF_ASKED, false)) return;
        prefs.edit().putBoolean(PREF_NOTIF_ASKED, true).apply();
        requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQ_NOTIF);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Nothing extra needed — if denied the app continues normally.
        // If granted, the Android notification channel is ready for use.
    }

    // -------------------------------------------------------------------------
    // WebView setup
    // -------------------------------------------------------------------------

    private void loadUrl() {
        showLoading(true);
        if (mErrorLayout != null) mErrorLayout.setVisibility(View.GONE);
        if (mWebView    != null) mWebView.setVisibility(View.VISIBLE);
        if (mWebView    != null) mWebView.loadUrl(TARGET_URL);
    }

    private void setupWebView() {
        WebSettings s = mWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setDefaultTextEncodingName("UTF-8");
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            s.setMediaPlaybackRequiresUserGesture(false);
        }

        mWebView.setBackgroundColor(NAV_COLOR);

        // JavaScript interface — exposes AndroidBridge.share() to the page
        mWebView.addJavascriptInterface(new AndroidShareBridge(this), "AndroidBridge");

        mWebView.setWebViewClient(new WebViewClient() {

            // ------------------------------------------------------------------
            // A) External link routing
            // ------------------------------------------------------------------
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isInternalUrl(uri)) return false; // Stay in WebView
                openExternal(uri);
                return true; // Consumed
            }

            // ------------------------------------------------------------------
            // Loading / error events
            // ------------------------------------------------------------------
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                showLoading(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                showLoading(false);
                // B) Inject navigator.share bridge after every page load
                injectShareBridge(view);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
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
                    if (mErrorLayout != null) mErrorLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 85) showLoading(false);
            }
        });
    }

    // -------------------------------------------------------------------------
    // A) External-link helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true when the URI should stay inside the WebView (our own domain).
     */
    private boolean isInternalUrl(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false; // Non-http schemes are always external
        }
        String host = uri.getHost();
        if (host == null) return false;
        String h = host.toLowerCase();
        return h.equals("lahzi.ly") || h.endsWith(".lahzi.ly")
            || h.equals("lahzi.online") || h.endsWith(".lahzi.online");
    }

    /**
     * Opens a URI in the best available external handler.
     *
     * Priority:
     *  1. Try the exact URI (lets installed apps claim their deep-link schemes/hosts)
     *  2. If ActivityNotFoundException → try the plain https:// equivalent
     *  3. If still no handler → silently ignore (never crash)
     */
    private void openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e1) {
            // Installed app not found — try https fallback
            Uri fallback = toHttpsFallback(uri);
            if (fallback != null && !fallback.equals(uri)) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, fallback);
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                } catch (Exception e2) {
                    // Silently ignore — nothing we can do
                }
            }
        } catch (Exception e) {
            // Silently ignore all other errors
        }
    }

    /**
     * Best-effort conversion of an app-scheme URI to an https fallback.
     * e.g.  fb://profile/123  →  https://facebook.com/profile/123
     *        tg://resolve?...  →  https://t.me/...  (left for system browser)
     */
    private Uri toHttpsFallback(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return null;
        // Already http(s) — return as-is
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return uri;
        // Known scheme → https host mappings
        String host = uri.getHost();
        String path = uri.getPath() != null ? uri.getPath() : "";
        switch (scheme.toLowerCase()) {
            case "fb":
            case "fbmessenger": return Uri.parse("https://facebook.com" + path);
            case "tg":
            case "telegram":    return Uri.parse("https://t.me" + path);
            case "whatsapp":    return Uri.parse("https://wa.me" + path);
            case "instagram":   return Uri.parse("https://instagram.com" + path);
            default:
                if (host != null) return Uri.parse("https://" + host + path);
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // B) navigator.share bridge — injected into the page after each load
    // -------------------------------------------------------------------------

    /**
     * Injects a thin JS shim that maps navigator.share() calls to
     * AndroidBridge.share() (our @JavascriptInterface), which triggers the
     * Android native share sheet.  Already-functional native share is left
     * as a fallback so nothing breaks on capable devices/emulators.
     */
    private void injectShareBridge(WebView view) {
        // language=JavaScript
        String js =
            "(function() {" +
            "  if (window._lahziShareBridgeInstalled) return;" +
            "  window._lahziShareBridgeInstalled = true;" +
            "  var _native = (typeof navigator.share === 'function') ? navigator.share.bind(navigator) : null;" +
            "  navigator.share = function(data) {" +
            "    return new Promise(function(resolve, reject) {" +
            "      try {" +
            "        var title = (data && data.title) ? data.title : '" + SHARE_TITLE + "';" +
            "        var text  = (data && data.text)  ? data.text  : '';" +
            "        var url   = (data && data.url)   ? data.url   : window.location.href;" +
            "        AndroidBridge.share(title, text, url);" +
            "        resolve();" +
            "      } catch (e) {" +
            "        if (_native) { _native(data).then(resolve).catch(reject); }" +
            "        else { reject(e); }" +
            "      }" +
            "    });" +
            "  };" +
            "  navigator.canShare = function() { return true; };" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    // -------------------------------------------------------------------------
    // B) Android share-bridge JavascriptInterface (inner class)
    // -------------------------------------------------------------------------

    private static final class AndroidShareBridge {
        private final WeakReference<Activity> mRef;
        AndroidShareBridge(Activity activity) { mRef = new WeakReference<>(activity); }

        /**
         * Called from JavaScript: AndroidBridge.share(title, text, url)
         * Executes on a background thread — must switch to UI for startActivity.
         */
        @JavascriptInterface
        public void share(String title, String text, String url) {
            Activity activity = mRef.get();
            if (activity == null || activity.isFinishing()) return;

            String safeTitle = (title != null && !title.isEmpty()) ? title : SHARE_TITLE;
            String safeUrl   = (url   != null && !url.isEmpty())   ? url   : SHARE_URL;
            String shareBody = safeTitle + "\n" + safeUrl;

            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, safeTitle);
            intent.putExtra(Intent.EXTRA_TEXT, shareBody);

            activity.runOnUiThread(() -> {
                try {
                    activity.startActivity(Intent.createChooser(intent, "مشاركة عبر"));
                } catch (Exception e) {
                    // Silently ignore
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showLoading(boolean show) {
        if (mSpinner != null) mSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
