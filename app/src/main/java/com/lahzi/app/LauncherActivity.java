package com.lahzi.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;

/**
 * Crash-proof launcher activity.
 *
 * Launch order:
 *   1. Try Chrome Custom Tab (best UX, looks almost native)
 *   2. If unavailable / crashes → launch full-screen WebViewActivity (always works)
 *
 * This replaces androidbrowserhelper entirely to eliminate all unpredictable
 * initialization paths that cause crashes on BlueStacks and devices without Chrome.
 */
public class LauncherActivity extends AppCompatActivity {

    static final String TARGET_URL = "https://lahzi.ly";
    private static final int NAV_COLOR = 0xFF0B1220;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply navy status bar / nav bar colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(NAV_COLOR);
            getWindow().setNavigationBarColor(NAV_COLOR);
        }

        // Determine URL (supports deep-link launches via intent-filter)
        String url = resolveUrl();

        // Try Custom Tab → fall back to WebView on ANY exception
        try {
            launchCustomTab(url);
        } catch (ActivityNotFoundException | SecurityException e) {
            launchWebView(url);
        } catch (Exception e) {
            launchWebView(url);
        }
    }

    private String resolveUrl() {
        try {
            Intent intent = getIntent();
            if (intent != null
                    && Intent.ACTION_VIEW.equals(intent.getAction())
                    && intent.getData() != null) {
                String incoming = intent.getData().toString();
                if (incoming.startsWith("https://lahzi.ly")) {
                    return incoming;
                }
            }
        } catch (Exception ignored) {
        }
        return TARGET_URL;
    }

    private void launchCustomTab(String url) {
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                        new CustomTabColorSchemeParams.Builder()
                                .setToolbarColor(NAV_COLOR)
                                .setNavigationBarColor(NAV_COLOR)
                                .setNavigationBarDividerColor(NAV_COLOR)
                                .build())
                .setUrlBarHidingEnabled(true)
                .setShowTitle(false)
                .build();
        customTabsIntent.launchUrl(this, Uri.parse(url));
        // LauncherActivity finishes; Chrome handles the session
        finish();
    }

    private void launchWebView(String url) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(WebViewActivity.EXTRA_URL, url);
        startActivity(intent);
        finish();
    }
}
