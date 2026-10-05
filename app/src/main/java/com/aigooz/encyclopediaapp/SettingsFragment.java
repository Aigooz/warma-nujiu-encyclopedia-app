package com.aigooz.encyclopediaapp;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsFragment extends Fragment implements DataRepository.Listener {
    private MaterialButtonToggleGroup siteToggle;
    private TextView versionText;
    private TextView dataTime;
    private TextView updateText;
    private ProgressBar updateProgress;
    private BroadcastReceiver downloadReceiver;
    private long downloadId = -1;
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
        Button update = view.findViewById(R.id.updateButton);
        Button web = view.findViewById(R.id.webButton);

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
        update.setOnClickListener(v -> checkForUpdate());
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
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (downloadId >= 0 && downloadId == intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)) {
                    installDownload();
                }
            }
        };
        ContextCompat.registerReceiver(requireContext(), downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    public void onPause() {
        if (downloadReceiver != null) {
            requireContext().unregisterReceiver(downloadReceiver);
            downloadReceiver = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
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
        DataModels.Dataset dataset = DataRepository.get().current();
        if (!isAdded() || dataset == null || dataset.site != MainActivity.currentSite() || dataset.data == null) {
            versionText.setText("数据版本：读取中…");
            dataTime.setText("正在同步最新数据");
            return;
        }
        versionText.setText("数据版本：" + dataset.data.docx_version);
        dataTime.setText("生成时间：" + dataset.data.insights_generated_at + "\n内置快照：" + (dataset.fromCache ? "已启用" : "未使用"));
    }

    private void checkForUpdate() {
        Toast.makeText(requireContext(), "正在检查更新…", Toast.LENGTH_SHORT).show();
        updateText.setText("正在检查更新…");
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/Aigooz/warma-nujiu-encyclopedia-app/releases/latest").openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Warma-Nujiu-App");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                StringBuilder builder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line);
                }
                String tagName = extractJson(builder.toString(), "tag_name");
                String downloadUrl = extractFirstApkUrl(builder.toString());
                String current = BuildConfig.VERSION_NAME;
                if (getActivity() == null || !isAdded()) return;
                mainHandler.post(() -> {
                    if (tagName == null || downloadUrl == null) {
                        updateText.setText("暂时无法获取更新，请稍后再试。");
                        Toast.makeText(requireContext(), "暂时无法获取更新", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (compareVersion(tagName, current) <= 0) {
                        updateText.setText("当前已是最新版本：" + current);
                        new AlertDialog.Builder(requireContext())
                                .setTitle("已是最新版本")
                                .setMessage("当前：" + current + "\n最新：" + tagName)
                                .setPositiveButton("好的", null)
                                .show();
                    } else {
                        updateText.setText("发现新版本：" + tagName + "\n点击“下载安装”即可在 APP 内更新。");
                        new AlertDialog.Builder(requireContext())
                                .setTitle("发现新版本")
                                .setMessage("当前：" + current + "\n最新：" + tagName + "\n\n下载完成后会自动唤起系统安装界面。")
                                .setPositiveButton("下载安装", (dialog, which) -> startDownload(tagName, downloadUrl))
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                if (getActivity() == null || !isAdded()) return;
                mainHandler.post(() -> {
                    updateText.setText("检查更新失败，请稍后再试。");
                    Toast.makeText(requireContext(), "检查更新失败，请稍后再试", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void startDownload(String tagName, String url) {
        try {
            Context context = requireContext().getApplicationContext();
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建下载目录");
            File file = new File(dir, "encyclopedia-" + tagName.replace("v", "") + ".apk");
            if (file.exists()) file.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("百科 APP " + tagName);
            request.setDescription("正在下载最新版本安装包");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setMimeType("application/vnd.android.package-archive");
            request.setDestinationUri(Uri.fromFile(file));
            request.addRequestHeader("User-Agent", "Warma-Nujiu-App");
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadId = manager.enqueue(request);
            updateProgress.setProgress(0);
            updateProgress.setVisibility(View.VISIBLE);
            updateText.setText("下载中…");
            pollDownloadProgress();
        } catch (Exception e) {
            updateText.setText("无法开始下载。");
            Toast.makeText(requireContext(), "无法开始下载：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pollDownloadProgress() {
        mainHandler.postDelayed(() -> {
            if (!isAdded() || downloadId < 0 || updateProgress.getVisibility() != View.VISIBLE) return;
            try {
                DownloadManager manager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor cursor = manager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    int downloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int total = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (status == DownloadManager.STATUS_RUNNING && total > 0) {
                        updateProgress.setProgress(Math.max(1, downloaded * 100 / total));
                    } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        updateProgress.setProgress(100);
                    }
                }
                if (cursor != null) cursor.close();
            } catch (Exception ignored) {}
            pollDownloadProgress();
        }, 400);
    }

    private void installDownload() {
        updateProgress.setVisibility(View.GONE);
        try {
            Context context = requireContext().getApplicationContext();
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor cursor = manager.query(query);
            int status = -1;
            String reason = "";
            if (cursor != null && cursor.moveToFirst()) {
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                reason = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                cursor.close();
            }
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                updateText.setText("下载未完成：" + reason + "\n请重新点击“检查 APP 更新”。");
                Toast.makeText(requireContext(), "下载未完成", Toast.LENGTH_SHORT).show();
                return;
            }
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates");
            File[] files = dir.listFiles((parent, name) -> name.endsWith(".apk"));
            if (files == null || files.length == 0) throw new IllegalStateException("安装包不存在");
            File latest = files[0];
            for (File file : files) {
                if (file.lastModified() > latest.lastModified()) latest = file;
            }
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".updater", latest);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(install);
            updateText.setText("下载完成，已唤起系统安装界面。");
        } catch (Exception e) {
            updateText.setText("无法启动安装，请允许“安装未知应用”权限后重试。");
            Toast.makeText(requireContext(), "无法启动安装，请检查“安装未知应用”权限", Toast.LENGTH_LONG).show();
        }
    }

    private int compareVersion(String left, String right) {
        String[] a = normalizeVersion(left).split("\\.");
        String[] b = normalizeVersion(right).split("\\.");
        int size = Math.max(a.length, b.length);
        for (int i = 0; i < size; i++) {
            int x = i < a.length ? parseNumber(a[i]) : 0;
            int y = i < b.length ? parseNumber(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private String normalizeVersion(String value) {
        return value == null ? "0" : value.trim().replaceFirst("(?i)^v", "");
    }

    private int parseNumber(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractFirstApkUrl(String body) {
        Pattern pattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk[^\"]*)\"");
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractJson(String body, String key) {
        int keyIndex = body.indexOf("\"" + key + "\"");
        if (keyIndex < 0) return null;
        int colon = body.indexOf(':', keyIndex);
        int quoteStart = body.indexOf('"', colon);
        int quoteEnd = body.indexOf('"', quoteStart + 1);
        if (quoteStart < 0 || quoteEnd < 0) return null;
        return body.substring(quoteStart + 1, quoteEnd);
    }
}
