package com.lahzi.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * LauncherActivity — full-screen WebView for https://lahzi.ly
 *
 * Fixes in v1.4.0:
 *  A) Share: injects bridge in onPageStarted (early) + onPageFinished (re-apply).
 *     Chooser guarded by queryIntentActivities; falls back to opening URL in
 *     browser if no share apps are installed (BlueStacks case). FLAG_ACTIVITY_NEW_TASK
 *     added to both inner intent and chooser.
 *  B) Notifications: uses checkSelfPermission directly — no SharedPreferences guard
 *     that persists across re-installs.
 */
public class LauncherActivity extends AppCompatActivity {

    static final  String TARGET_URL   = "https://lahzi.ly";
    private static final int    NAV_COLOR     = 0xFF0B1220;
    private static final int    REQ_NOTIF     = 1001;
    private static final String SHARE_TITLE   = "Lahzi | لحظي";
    private static final String SHARE_URL     = "https://lahzi.ly";

    private WebView      mWebView;
    private ProgressBar  mSpinner;
    private LinearLayout mErrorLayout;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        // Ask for notification permission 2 s after launch (non-intrusive).
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

    // ─── B) Notification permission ───────────────────────────────────────────
    //
    // Checks the ACTUAL permission state on every launch instead of relying on a
    // SharedPreferences flag that persists across re-installs. Android's own
    // permission system tracks "Don't ask again" internally, so if the user
    // denied with "Don't ask again" requestPermissions is silently ignored.

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQ_NOTIF);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Granted → notification channel is ready.
        // Denied  → app continues normally.
    }

    // ─── WebView setup ────────────────────────────────────────────────────────

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
        mWebView.addJavascriptInterface(new AndroidShareBridge(this), "AndroidBridge");

        mWebView.setWebViewClient(new WebViewClient() {

            // ── External link routing ─────────────────────────────────────────
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isInternalUrl(uri)) return false;
                openExternal(uri);
                return true;
            }

            // ── Page lifecycle ────────────────────────────────────────────────
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                showLoading(true);
                // A) Inject share bridge EARLY — before page JS runs — so
                //    navigator.share is already our version when the page evaluates it.
                injectShareBridge(view, true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                showLoading(false);
                // Re-apply after page load in case page JS overwrote navigator.share.
                injectShareBridge(view, false);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Always cancel — never bypass certificate validation.
                // Google Play policy prohibits handler.proceed() in onReceivedSslError.
                handler.cancel();
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

    // ─── External link helpers ────────────────────────────────────────────────

    private boolean isInternalUrl(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return false;
        String host = uri.getHost();
        if (host == null) return false;
        String h = host.toLowerCase();
        return h.equals("lahzi.ly") || h.endsWith(".lahzi.ly")
            || h.equals("lahzi.online") || h.endsWith(".lahzi.online");
    }

    private void openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e1) {
            Uri fallback = toHttpsFallback(uri);
            if (fallback != null && !fallback.equals(uri)) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, fallback);
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private Uri toHttpsFallback(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return null;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return uri;
        String path = uri.getPath() != null ? uri.getPath() : "";
        switch (scheme.toLowerCase()) {
            case "fb":
            case "fbmessenger": return Uri.parse("https://facebook.com" + path);
            case "tg":
            case "telegram":    return Uri.parse("https://t.me" + path);
            case "whatsapp":    return Uri.parse("https://wa.me" + path);
            case "instagram":   return Uri.parse("https://instagram.com" + path);
            default:
                String host = uri.getHost();
                return host != null ? Uri.parse("https://" + host + path) : null;
        }
    }

    // ─── A) navigator.share bridge ────────────────────────────────────────────

    /**
     * Injects the Android share bridge into the WebView.
     *
     * @param forceReinstall  true = always overwrite navigator.share (used in
     *                        onPageStarted where the page may not have run yet).
     *                        false = only install if our flag isn't set.
     */
    private void injectShareBridge(WebView view, boolean forceReinstall) {
        // language=JavaScript
        String js =
            "(function() {" +
            // In onPageFinished we skip if already installed; in onPageStarted we
            // always install (forceReinstall=true flag injected below).
            "  if (" + (forceReinstall ? "false" : "window._lahziShareBridgeInstalled") + ") return;" +
            "  window._lahziShareBridgeInstalled = true;" +
            "  var _native = (typeof navigator !== 'undefined' && typeof navigator.share === 'function')" +
            "    ? navigator.share.bind(navigator) : null;" +
            "  navigator.share = function(data) {" +
            "    return new Promise(function(resolve, reject) {" +
            "      try {" +
            "        var title = (data && data.title) ? String(data.title) : '" + SHARE_TITLE + "';" +
            "        var text  = (data && data.text)  ? String(data.text)  : '';" +
            "        var url   = (data && data.url)   ? String(data.url)   : window.location.href;" +
            "        AndroidBridge.share(title, text, url);" +
            "        resolve();" +
            "      } catch(e) {" +
            "        if (_native) { _native(data).then(resolve).catch(reject); }" +
            "        else { reject(e); }" +
            "      }" +
            "    });" +
            "  };" +
            "  navigator.canShare = function() { return true; };" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    // ─── A) Share JavascriptInterface ─────────────────────────────────────────

    private static final class AndroidShareBridge {
        private final WeakReference<Activity> mRef;
        AndroidShareBridge(Activity a) { mRef = new WeakReference<>(a); }

        /**
         * Called from JS on a background thread.
         * Creates an ACTION_SEND intent and shows the system share sheet.
         *
         * If no apps are installed that handle text/plain shares (happens on a
         * fresh BlueStacks install), falls back to opening the URL in a browser.
         */
        @JavascriptInterface
        public void share(String title, String text, String url) {
            Activity activity = mRef.get();
            if (activity == null || activity.isFinishing()) return;

            final String safeTitle = (title != null && !title.isEmpty()) ? title : SHARE_TITLE;
            final String safeUrl   = (url   != null && !url.isEmpty())   ? url   : SHARE_URL;
            final String shareBody = safeTitle + "\n" + safeUrl;

            final Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_TITLE, safeTitle);
            sendIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
            // FLAG_ACTIVITY_NEW_TASK required when starting from a non-UI thread context.
            sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.runOnUiThread(() -> {
                try {
                    // Guard: check if any app can actually handle this intent.
                    // On BlueStacks with no messaging apps installed, the list is
                    // empty and the chooser would show "No app can perform this action".
                    List<ResolveInfo> resolvers = activity.getPackageManager()
                            .queryIntentActivities(sendIntent, PackageManager.MATCH_DEFAULT_ONLY);

                    if (!resolvers.isEmpty()) {
                        // Normal path — show system share sheet.
                        Intent chooser = Intent.createChooser(sendIntent, "مشاركة عبر");
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(chooser);
                    } else {
                        // Fallback: open URL in browser and show a toast.
                        Toast.makeText(activity,
                                "افتح الرابط: " + safeUrl,
                                Toast.LENGTH_LONG).show();
                        try {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse(safeUrl));
                            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(browserIntent);
                        } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) { }
            });
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        if (mSpinner != null) mSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
