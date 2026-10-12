package com.dez.desktop.apps;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.dez.desktop.DesktopActivity;
import com.dez.desktop.R;
import com.dez.desktop.Ui;
import com.dez.desktop.wm.DesktopWindow;
import com.dez.desktop.wm.WindowContent;

/** Windowed web browser that requests desktop sites by default, like browsers in DeX. */
final class BrowserApp extends WindowApp {
    static final String ARG_URL = "url";
    private static final String HOME = "https://www.google.com/";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    BrowserApp() {
        super(BuiltInApps.BROWSER, R.string.app_browser, R.drawable.ic_app_browser);
    }

    @Override
    public View createView(DesktopActivity host, Bundle args) {
        String url = args == null ? null : args.getString(ARG_URL);
        return new BrowserView(host, url == null ? HOME : url, host.getPrefs().isDesktopUserAgent());
    }

    @SuppressLint("ViewConstructor")
    static final class BrowserView extends LinearLayout implements WindowContent {
        private final WebView web;
        private final EditText address;
        private final ProgressBar progress;

        @SuppressLint("SetJavaScriptEnabled")
        BrowserView(Context c, String startUrl, boolean desktopMode) {
            super(c);
            setOrientation(VERTICAL);
            setBackgroundColor(Ui.WINDOW_BG);

            LinearLayout bar = new LinearLayout(c);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setBackgroundColor(Ui.SURFACE);
            bar.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
            bar.addView(Ui.iconButton(c, R.drawable.ic_back, 36, 8, c.getString(R.string.tb_back),
                    v -> { if (getWeb().canGoBack()) getWeb().goBack(); }));
            bar.addView(Ui.iconButton(c, R.drawable.ic_forward, 36, 8, null,
                    v -> { if (getWeb().canGoForward()) getWeb().goForward(); }));
            bar.addView(Ui.iconButton(c, R.drawable.ic_refresh, 36, 8,
                    c.getString(R.string.files_refresh), v -> getWeb().reload()));
            address = new EditText(c);
            address.setSingleLine(true);
            address.setTextSize(13);
            address.setTextColor(Ui.TEXT);
            address.setHintTextColor(Ui.TEXT_DIM);
            address.setHint(R.string.browser_url_hint);
            address.setSelectAllOnFocus(true);
            address.setImeOptions(EditorInfo.IME_ACTION_GO);
            address.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI);
            address.setBackground(Ui.rounded(Ui.WINDOW_BG, Ui.dp(c, 16)));
            address.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
            address.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    navigate(address.getText().toString());
                    return true;
                }
                return false;
            });
            LayoutParams alp = new LayoutParams(0, Ui.dp(c, 34), 1f);
            alp.leftMargin = Ui.dp(c, 4);
            bar.addView(address, alp);
            addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setVisibility(GONE);
            addView(progress, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 3)));

            web = new WebView(c);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setSupportMultipleWindows(false);
            if (desktopMode) s.setUserAgentString(DESKTOP_UA);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    String scheme = uri.getScheme();
                    if ("http".equals(scheme) || "https".equals(scheme)) return false;
                    try {
                        getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (ActivityNotFoundException ignored) {
                    }
                    return true;
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    if (!address.hasFocus()) address.setText(url);
                }
            });
            web.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int p) {
                    progress.setProgress(p);
                    progress.setVisibility(p < 100 ? VISIBLE : GONE);
                }

                @Override
                public void onReceivedTitle(WebView view, String title) {
                    DesktopWindow w = DesktopWindow.of(BrowserView.this);
                    if (w != null && title != null) w.setTitle(title);
                }
            });
            addView(web, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
            navigate(startUrl);
        }

        private WebView getWeb() {
            return web;
        }

        private void navigate(String input) {
            String text = input.trim();
            if (text.isEmpty()) return;
            String url;
            if (text.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                url = text;
            } else if (!text.contains(" ") && Patterns.WEB_URL.matcher(text).matches()) {
                url = "https://" + text;
            } else {
                url = "https://www.google.com/search?q=" + Uri.encode(text);
            }
            address.setText(url);
            address.clearFocus();
            InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
            imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
            web.loadUrl(url);
            web.requestFocus();
        }

        @Override
        public boolean onBackPressed() {
            if (web.canGoBack()) {
                web.goBack();
                return true;
            }
            return false;
        }

        @Override
        public void onWindowClosed() {
            web.stopLoading();
            web.destroy();
        }
    }
}
