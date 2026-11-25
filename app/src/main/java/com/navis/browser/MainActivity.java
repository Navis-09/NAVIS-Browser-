package com.navis.browser;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final String DUCKDUCKGO_HOME = "https://duckduckgo.com/";
    private static final String DUCKDUCKGO_SEARCH = "https://duckduckgo.com/?q=";
    private static final String GOOGLE_SEARCH = "https://www.google.com/search?q=";

    private WebView webView;
    private EditText omnibox;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEngineStatus;
    private boolean useDuckDuckGo = true;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        omnibox = findViewById(R.id.omnibox);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvEngineStatus = findViewById(R.id.tvEngineStatus);

        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnForward = findViewById(R.id.btnForward);
        ImageButton btnReload = findViewById(R.id.btnReload);
        ImageButton btnHome = findViewById(R.id.btnHome);
        ImageButton btnEngine = findViewById(R.id.btnEngine);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    view.loadUrl(uri.toString());
                    return true;
                }
                Toast.makeText(MainActivity.this, "Blocked external app launch: " + uri, Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                if (!omnibox.hasFocus()) {
                    omnibox.setText(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                if (!omnibox.hasFocus()) {
                    omnibox.setText(url);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(fileName);
                request.setDescription("Downloading via NAVIS Browser");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(MainActivity.this, "Downloading " + fileName, Toast.LENGTH_SHORT).show();
                }
            }
        });

        omnibox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigateToInput(omnibox.getText().toString());
                hideKeyboard();
                omnibox.clearFocus();
                return true;
            }
            return false;
        });

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });

        btnReload.setOnClickListener(v -> webView.reload());

        btnHome.setOnClickListener(v -> navigateToInput(DUCKDUCKGO_HOME));

        btnEngine.setOnClickListener(v -> {
            useDuckDuckGo = !useDuckDuckGo;
            tvEngineStatus.setText(useDuckDuckGo ? "NAVIS • DuckDuckGo" : "NAVIS • Google");
            Toast.makeText(this, "Search engine: " + (useDuckDuckGo ? "DuckDuckGo" : "Google"), Toast.LENGTH_SHORT).show();
        });

        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        webView.loadUrl(DUCKDUCKGO_HOME);
    }

    private void navigateToInput(String rawInput) {
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) return;

        String targetUrl;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            targetUrl = trimmed;
        } else if (!trimmed.contains(" ") && trimmed.contains(".")) {
            targetUrl = "https://" + trimmed;
        } else {
            String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
            targetUrl = (useDuckDuckGo ? DUCKDUCKGO_SEARCH : GOOGLE_SEARCH) + encoded;
        }
        webView.loadUrl(targetUrl);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
