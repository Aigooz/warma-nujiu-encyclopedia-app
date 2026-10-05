package com.aigooz.encyclopediaapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SettingsFragment extends Fragment implements DataRepository.Listener {
    private MaterialButtonToggleGroup siteToggle;
    private TextView versionText;
    private TextView dataTime;

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
                String releaseUrl = extractJson(builder.toString(), "html_url");
                String current = BuildConfig.VERSION_NAME;
                if (getActivity() == null || !isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (tagName == null || releaseUrl == null) {
                        Toast.makeText(requireContext(), "暂时无法获取更新", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (tagName.replace("v", "").compareTo(current.replace("v", "")) <= 0) {
                        new AlertDialog.Builder(requireContext()).setTitle("已是最新版本").setMessage("当前：" + current + "\n最新：" + tagName).setPositiveButton("好的", null).show();
                    } else {
                        new AlertDialog.Builder(requireContext()).setTitle("发现新版本").setMessage("最新：" + tagName + "\n是否前往下载？").setPositiveButton("前往下载", (dialog, which) -> {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))); } catch (Exception ignored) {}
                        }).setNegativeButton("取消", null).show();
                    }
                });
            } catch (Exception e) {
                if (getActivity() == null || !isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "检查更新失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
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
