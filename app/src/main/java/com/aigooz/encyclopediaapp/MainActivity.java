package com.aigooz.encyclopediaapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final String WARMA_URL = "https://aigooz.github.io/warma-encyclopedia/";
    private static final String NUJIU_URL = "https://aigooz.github.io/nujiu-encyclopedia/";
    private static final String RELEASE_API =
            "https://api.github.com/repos/Aigooz/warma-nujiu-encyclopedia-app/releases/latest";

    private WebView warmaWebView;
    private WebView nujiuWebView;
    private SwipeRefreshLayout warmaRefresh;
    private SwipeRefreshLayout nujiuRefresh;
    private ProgressBar progressBar;
    private MaterialToolbar toolbar;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        warmaWebView = findViewById(R.id.webViewWarma);
        nujiuWebView = findViewById(R.id.webViewNujiu);
        warmaRefresh = findViewById(R.id.refreshWarma);
        nujiuRefresh = findViewById(R.id.refreshNujiu);
        progressBar = findViewById(R.id.progressBar);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        setupWebView(warmaWebView, warmaRefresh);
        setupWebView(nujiuWebView, nujiuRefresh);
        warmaWebView.loadUrl(WARMA_URL);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_warma) {
                showWarma();
                return true;
            } else if (id == R.id.nav_nujiu) {
                showNujiu();
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            showWarma();
        } else {
            warmaWebView.restoreState(savedInstanceState);
            nujiuWebView.restoreState(savedInstanceState);
            showWarma();
        }
    }

    private void showWarma() {
        warmaRefresh.setVisibility(View.VISIBLE);
        nujiuRefresh.setVisibility(View.GONE);
        toolbar.setTitle("Warma 百科");
        toolbar.setSubtitle("全景可视化");
        if (warmaWebView.getUrl() == null || warmaWebView.getUrl().isEmpty()) {
            warmaWebView.loadUrl(WARMA_URL);
        }
    }

    private void showNujiu() {
        warmaRefresh.setVisibility(View.GONE);
        nujiuRefresh.setVisibility(View.VISIBLE);
        toolbar.setTitle("怒九百科");
        toolbar.setSubtitle("全景可视化");
        if (nujiuWebView.getUrl() == null || nujiuWebView.getUrl().isEmpty()) {
            nujiuWebView.loadUrl(NUJIU_URL);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(WebView webView, SwipeRefreshLayout refreshLayout) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.setBackgroundColor(0xFF0B1020);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost();
                if (host.endsWith("github.io")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                if (newProgress >= 100) {
                    warmaRefresh.setRefreshing(false);
                    nujiuRefresh.setRefreshing(false);
                }
            }
        });

        refreshLayout.setOnRefreshListener(webView::reload);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "检查更新").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            checkForUpdate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkForUpdate() {
        Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL(RELEASE_API);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Warma-Nujiu-App");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);

                StringBuilder builder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                }

                String body = builder.toString();
                String tagName = extractJson(body, "tag_name");
                String releaseUrl = extractJson(body, "html_url");
                String current = BuildConfig.VERSION_NAME;

                runOnUiThread(() -> {
                    if (tagName == null || releaseUrl == null) {
                        Toast.makeText(this, "暂时无法获取更新", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (tagName.replace("v", "").compareTo(current.replace("v", "")) <= 0) {
                        new AlertDialog.Builder(this)
                                .setTitle("已是最新版本")
                                .setMessage("当前版本：" + current + "\n最新版本：" + tagName)
                                .setPositiveButton("好的", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("发现新版本")
                                .setMessage("最新版本：" + tagName + "\n是否前往下载？")
                                .setPositiveButton("前往下载", (dialog, which) -> {
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)));
                                    } catch (Exception ignored) {
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "检查更新失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String extractJson(String body, String key) {
        int keyIndex = body.indexOf("\"" + key + "\"");
        if (keyIndex < 0) {
            return null;
        }
        int colon = body.indexOf(':', keyIndex);
        int quoteStart = body.indexOf('"', colon);
        int quoteEnd = body.indexOf('"', quoteStart + 1);
        if (quoteStart < 0 || quoteEnd < 0) {
            return null;
        }
        return body.substring(quoteStart + 1, quoteEnd);
    }

    @Override
    public void onBackPressed() {
        WebView active = warmaRefresh.getVisibility() == View.VISIBLE ? warmaWebView : nujiuWebView;
        if (active != null && active.canGoBack()) {
            active.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (warmaWebView != null) {
            warmaWebView.saveState(outState);
        }
        if (nujiuWebView != null) {
            nujiuWebView.saveState(outState);
        }
    }
}
