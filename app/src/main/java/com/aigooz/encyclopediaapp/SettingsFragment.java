package com.aigooz.encyclopediaapp;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;

public class SettingsFragment extends Fragment implements DataRepository.Listener {
    private MaterialButtonToggleGroup siteToggle;
    private TextView versionText;
    private TextView dataTime;
    private TextView updateText;
    private ProgressBar updateProgress;
    private Button updateButton;
    private Button retryButton;
    private Button browserButton;
    private UpdateDownload download;
    private boolean checking;
    private boolean verifying;
    private boolean resumed;
    private final Runnable downloadPoll = this::pollDownloadProgress;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        siteToggle = view.findViewById(R.id.siteToggle);
        versionText = view.findViewById(R.id.versionText);
        dataTime = view.findViewById(R.id.dataTime);
        updateText = view.findViewById(R.id.updateText);
        updateProgress = view.findViewById(R.id.updateProgress);
        Button refresh = view.findViewById(R.id.refreshButton);
        updateButton = view.findViewById(R.id.updateButton);
        retryButton = view.findViewById(R.id.retryUpdateButton);
        browserButton = view.findViewById(R.id.browserUpdateButton);
        Button web = view.findViewById(R.id.webButton);
        MaterialButtonToggleGroup themeToggle = view.findViewById(R.id.themeToggle);
        int theme = requireContext().getSharedPreferences("encyclopedia", Context.MODE_PRIVATE)
                .getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        themeToggle.check(theme == AppCompatDelegate.MODE_NIGHT_NO ? R.id.themeLight
                : theme == AppCompatDelegate.MODE_NIGHT_YES ? R.id.themeDark : R.id.themeSystem);
        themeToggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            int mode = id == R.id.themeLight ? AppCompatDelegate.MODE_NIGHT_NO
                    : id == R.id.themeDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            requireContext().getSharedPreferences("encyclopedia", Context.MODE_PRIVATE).edit().putInt("theme", mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });
        ((TextView) view.findViewById(R.id.aboutText)).setText("Warma / 怒九百科 · " + BuildConfig.VERSION_NAME + "\n数据来源：Warma 百科、怒九百科");
        updateText.setText("当前 APP 版本：" + BuildConfig.VERSION_NAME);

        Site site = MainActivity.currentSite();
        if (site == Site.WARMA) siteToggle.check(R.id.siteWarma); else siteToggle.check(R.id.siteNujiu);
        siteToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            Site selected = checkedId == R.id.siteWarma ? Site.WARMA : Site.NUJIU;
            if (selected != MainActivity.currentSite() && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchSite(selected);
            }
        });

        refresh.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "正在刷新数据…", Toast.LENGTH_SHORT).show();
            DataRepository.get().refresh(requireContext().getApplicationContext(), MainActivity.currentSite());
        });
        updateButton.setOnClickListener(v -> {
            if (download.ready) installDownload(); else checkForUpdate();
        });
        retryButton.setOnClickListener(v -> chooseSource());
        browserButton.setOnClickListener(v -> openDownloadPage());
        web.setOnClickListener(v -> {
            String url = MainActivity.currentSite() == Site.WARMA
                    ? "https://aigooz.github.io/warma-encyclopedia/"
                    : "https://aigooz.github.io/nujiu-encyclopedia/";
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        });

        DataRepository.get().addListener(this);
        render();
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        restoreDownload();
        renderDownload();
        if (download.id >= 0 && !download.ready) pollDownloadProgress();
        if (download.ready && download.waitingForPermission && requireContext().getPackageManager().canRequestPackageInstalls()) {
            installDownload();
        }
    }

    @Override
    public void onPause() {
        resumed = false;
        mainHandler.removeCallbacks(downloadPoll);
        super.onPause();
    }

    @Override
    public void onDataChanged() {
        if (isAdded()) render();
    }

    @Override
    public void onDestroyView() {
        DataRepository.get().removeListener(this);
        super.onDestroyView();
    }

    private void render() {
        Site site = MainActivity.currentSite();
        siteToggle.check(site == Site.WARMA ? R.id.siteWarma : R.id.siteNujiu);
        boolean loading = DataRepository.get().isLoading(site);
        if (getView() != null) {
            Button refresh = getView().findViewById(R.id.refreshButton);
            refresh.setEnabled(!loading); refresh.setText(loading ? "正在同步…" : "刷新百科数据");
        }
        DataModels.Dataset dataset = DataRepository.get().current();
        if (!isAdded() || dataset == null || dataset.site != MainActivity.currentSite() || dataset.data == null) {
            versionText.setText("数据版本：读取中…");
            dataTime.setText(loading ? "正在同步最新数据" : "数据读取失败，请重试");
            return;
        }
        versionText.setText("数据版本：" + dataset.data.docx_version);
        String status = loading ? "同步中 · 当前数据仍可浏览" : !DataRepository.get().error(site).isEmpty()
                ? DataRepository.get().error(site) : dataset.fromCache ? "本地离线数据" : "已同步最新数据";
        dataTime.setText("生成时间：" + dataset.data.insights_generated_at + "\n" + status);
    }

    private void checkForUpdate() {
        if (checking || download.id >= 0) return;
        checking = true;
        updateButton.setEnabled(false);
        updateText.setText("正在检查更新…");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://api.github.com/repos/" + AppRelease.REPOSITORY + "/releases/latest").openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Warma-Nujiu-App");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP " + connection.getResponseCode());
                StringBuilder builder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line);
                }
                AppRelease release = AppRelease.parse(builder.toString());
                mainHandler.post(() -> {
                    checking = false;
                    if (getView() == null || !isAdded()) return;
                    updateButton.setEnabled(true);
                    if (AppRelease.compareVersion(release.tag, BuildConfig.VERSION_NAME) <= 0) {
                        updateText.setText("当前已是最新版本：" + BuildConfig.VERSION_NAME);
                    } else {
                        download = new UpdateDownload();
                        download.release = release;
                        saveDownload();
                        updateText.setText("发现新版本：" + release.tag);
                        retryButton.setVisibility(View.VISIBLE);
                        browserButton.setVisibility(View.VISIBLE);
                        if (resumed) chooseSource();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    checking = false;
                    if (getView() == null || !isAdded()) return;
                    updateButton.setEnabled(true);
                    browserButton.setVisibility(View.VISIBLE);
                    updateText.setText("检查更新失败：" + e.getMessage() + "\n可重试或在浏览器打开发布页。");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "check-app-update").start();
    }

    private void chooseSource() {
        if (download.release == null || verifying) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("下载 " + download.release.tag)
                .setMessage("官方线路不可达时会自动尝试备用线路。备用线路由第三方运营，安装前会校验文件及应用签名。")
                .setPositiveButton("自动下载", (dialog, which) -> startDownload(0))
                .setNeutralButton("选择线路", (dialog, which) -> new AlertDialog.Builder(requireContext())
                        .setTitle("下载线路")
                        .setItems(AppRelease.SOURCE_NAMES.toArray(new String[0]), (d, source) -> startDownload(source))
                        .setNegativeButton("取消", null).show())
                .setNegativeButton("取消", null).show();
    }

    private SharedPreferences updatePreferences() {
        return requireContext().getSharedPreferences("app-updater", Context.MODE_PRIVATE);
    }

    private void restoreDownload() {
        if (download != null) return;
        try {
            download = new Gson().fromJson(updatePreferences().getString("download", ""), UpdateDownload.class);
            if (download != null && (download.release == null || !download.release.validUrl()
                    || AppRelease.compareVersion(download.release.tag, BuildConfig.VERSION_NAME) <= 0
                    || download.source < 0 || download.source >= AppRelease.SOURCE_NAMES.size())) {
                if (download.id >= 0) downloadManager().remove(download.id);
                download = null;
            }
        } catch (Exception ignored) { download = null; }
        if (download == null) download = new UpdateDownload();
        saveDownload();
    }

    private void saveDownload() {
        updatePreferences().edit().putString("download", new Gson().toJson(download)).apply();
    }

    private DownloadManager downloadManager() {
        return (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
    }

    private File downloadFile() {
        File root = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (root == null) throw new IllegalStateException("下载目录不可用");
        File dir = new File(root, "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("无法创建下载目录");
        return new File(dir, download.release.fileName());
    }

    private void renderDownload() {
        boolean busy = checking || verifying || download.id >= 0 && !download.ready;
        updateButton.setEnabled(!busy);
        updateButton.setText(download.ready ? "安装已下载更新" : "检查 APP 更新");
        retryButton.setVisibility(download.release != null ? View.VISIBLE : View.GONE);
        retryButton.setEnabled(!verifying);
        retryButton.setText(busy ? "切换下载线路" : "选择线路 / 重新下载");
        browserButton.setVisibility(download.release != null ? View.VISIBLE : View.GONE);
        updateProgress.setVisibility(download.id >= 0 && !download.ready || verifying ? View.VISIBLE : View.GONE);
        if (download.ready) updateText.setText("下载完成：" + download.release.tag + "，点击安装。");
        else if (download.id >= 0) updateText.setText("正在恢复下载任务…");
    }

    private void startDownload(int source) {
        mainHandler.removeCallbacks(downloadPoll);
        if (download.id >= 0) downloadManager().remove(download.id);
        download.id = -1;
        download.source = source;
        download.ready = false;
        download.waitingForPermission = false;
        download.lastBytes = 0;
        download.lastProgressAt = System.currentTimeMillis();
        saveDownload();
        try {
            File file = downloadFile();
            if (file.exists() && !file.delete()) throw new IllegalStateException("无法清理旧安装包");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(download.release.sourceUrl(source)));
            request.setTitle("百科 APP " + download.release.tag);
            request.setDescription(AppRelease.SOURCE_NAMES.get(source));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationUri(Uri.fromFile(file));
            request.addRequestHeader("User-Agent", "Warma-Nujiu-App");
            download.id = downloadManager().enqueue(request);
            saveDownload();
            renderDownload();
            updateProgress.setIndeterminate(true);
            updateProgress.setProgress(0);
            updateText.setText("正在连接：" + AppRelease.SOURCE_NAMES.get(source));
            pollDownloadProgress();
        } catch (Exception e) {
            failDownload("无法开始下载：" + e.getMessage(), false);
        }
    }

    private void pollDownloadProgress() {
        mainHandler.removeCallbacks(downloadPoll);
        if (!resumed || !isAdded() || getView() == null || download.id < 0 || download.ready) return;
        int status;
        int reason;
        long bytes;
        long total;
        try (Cursor cursor = downloadManager().query(new DownloadManager.Query().setFilterById(download.id))) {
            if (cursor == null || !cursor.moveToFirst()) {
                failDownload("下载任务已被系统移除", false);
                return;
            }
            status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
        } catch (Exception e) {
            failDownload("无法读取系统下载任务：" + e.getMessage(), false);
            return;
        }
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            download.ready = true;
            saveDownload();
            installDownload();
            return;
        }
        if (status == DownloadManager.STATUS_FAILED) {
            boolean network = reason != DownloadManager.ERROR_INSUFFICIENT_SPACE && reason != DownloadManager.ERROR_DEVICE_NOT_FOUND
                    && reason != DownloadManager.ERROR_FILE_ERROR && reason != DownloadManager.ERROR_FILE_ALREADY_EXISTS;
            failDownload(downloadReason(reason), network);
            return;
        }
        long now = System.currentTimeMillis();
        download.track(bytes, now);
        saveDownload();
        boolean offline = status == DownloadManager.STATUS_PAUSED && reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK;
        if (!offline && download.stalled(now)) {
            failDownload("线路连接超时（30 秒无下载进展）", true);
            return;
        }
        if (offline) {
            download.lastProgressAt = now;
            saveDownload();
        }
        updateProgress.setIndeterminate(total <= 0);
        if (total > 0) updateProgress.setProgress((int) Math.min(100, bytes * 100 / total));
        updateText.setText((offline ? "等待网络连接" : status == DownloadManager.STATUS_PAUSED ? "等待系统重试" : "下载中")
                + " · " + AppRelease.SOURCE_NAMES.get(download.source)
                + String.format(java.util.Locale.CHINA, "\n%.2f / %.2f MB", bytes / 1048576.0, (total > 0 ? total : download.release.size) / 1048576.0));
        mainHandler.postDelayed(downloadPoll, 1000);
    }

    private String downloadReason(int reason) {
        if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) return "存储空间不足";
        if (reason == DownloadManager.ERROR_DEVICE_NOT_FOUND) return "下载存储不可用";
        if (reason == DownloadManager.ERROR_FILE_ERROR || reason == DownloadManager.ERROR_FILE_ALREADY_EXISTS) return "安装包文件写入失败";
        if (reason == DownloadManager.ERROR_TOO_MANY_REDIRECTS) return "下载线路重定向失败";
        if (reason == DownloadManager.ERROR_HTTP_DATA_ERROR) return "下载连接中断";
        if (reason >= 400 && reason < 600) return "下载服务器返回 HTTP " + reason;
        return "系统下载失败（" + reason + "）";
    }

    private void failDownload(String reason, boolean tryNext) {
        mainHandler.removeCallbacks(downloadPoll);
        if (download.id >= 0) downloadManager().remove(download.id);
        download.id = -1;
        download.ready = false;
        download.waitingForPermission = false;
        saveDownload();
        if (tryNext && download.hasNextSource()) {
            Toast.makeText(requireContext(), reason + "，切换备用线路", Toast.LENGTH_SHORT).show();
            startDownload(download.source + 1);
            return;
        }
        renderDownload();
        updateText.setText(reason + "\n可选择下载线路重试，或使用浏览器下载。");
    }

    private void installDownload() {
        if (!download.ready || verifying) return;
        final UpdateDownload task = download;
        final File file;
        try {
            file = downloadFile();
        } catch (Exception e) { failDownload(e.getMessage(), false); return; }
        Context context = requireContext().getApplicationContext();
        verifying = true;
        renderDownload();
        updateProgress.setVisibility(View.VISIBLE);
        updateProgress.setIndeterminate(true);
        updateText.setText("正在校验安装包…");
        new Thread(() -> {
            String error = null;
            try { ApkVerifier.verify(context, file, task.release); }
            catch (Exception e) { error = e.getMessage(); }
            String message = error;
            mainHandler.post(() -> {
                verifying = false;
                if (!isAdded() || getView() == null || task != download) return;
                if (message != null) { failDownload(message, true); return; }
                renderDownload();
                if (resumed) launchInstaller(file);
            });
        }, "verify-app-update").start();
    }

    private void launchInstaller(File file) {
        try {
            if (!requireContext().getPackageManager().canRequestPackageInstalls()) {
                download.waitingForPermission = true;
                saveDownload();
                updateText.setText("安装包已校验，请允许本应用安装未知应用，然后返回继续安装。");
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + requireContext().getPackageName())));
                return;
            }
            download.waitingForPermission = false;
            saveDownload();
            Uri apkUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".updater", file);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
            updateText.setText("下载完成，已唤起系统安装界面。");
        } catch (Exception e) {
            updateText.setText("无法启动安装：" + e.getMessage() + "\n可点击“安装已下载更新”重试。");
        }
    }

    private void openDownloadPage() {
        String[] options = {"GitHub 发布页", "备用线路 1 · ghfast.top", "备用线路 2 · gh-proxy.com"};
        if (download.release == null) { openBrowser(AppRelease.PAGE); return; }
        new AlertDialog.Builder(requireContext()).setTitle("浏览器下载")
                .setItems(options, (dialog, source) -> openBrowser(source == 0 ? AppRelease.PAGE : download.release.sourceUrl(source)))
                .setNegativeButton("取消", null).show();
    }

    private void openBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

}
