package com.aigooz.encyclopediaapp;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class VideoDetailActivity extends AppCompatActivity implements DataRepository.Listener {
    private Site site;
    private String bvid;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_detail);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        bvid = getIntent().getStringExtra("bvid");
        String siteName = getIntent().getStringExtra("site");
        site = siteName == null ? MainActivity.currentSite() : Site.valueOf(siteName);
        DataRepository.get().addListener(this);
        if (DataRepository.get().current(site) == null) DataRepository.get().load(getApplicationContext(), site);
        render();
    }

    private void render() {
        DataModels.Dataset dataset = DataRepository.get().current(site);
        DataModels.Video video = findVideo(dataset, bvid);
        if (video == null) {
            ((TextView) findViewById(R.id.title)).setText(DataRepository.get().error(site).isEmpty()
                    ? "正在读取视频…" : "视频暂时无法加载");
            return;
        }
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.getMenu().clear();
        android.view.MenuItem save = toolbar.getMenu().add("收藏");
        boolean saved = LibraryStore.favorites(this, site).contains(bvid);
        save.setTitle(saved ? "取消收藏" : "收藏");
        save.setIcon(saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        save.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        android.view.MenuItem share = toolbar.getMenu().add("分享").setIcon(R.drawable.ic_share);
        share.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item == save) {
                boolean added = LibraryStore.toggle(this, site, bvid);
                save.setTitle(added ? "取消收藏" : "收藏");
                save.setIcon(added ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
                android.widget.Toast.makeText(this, added ? "已加入收藏" : "已取消收藏", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, video.title + "\nhttps://www.bilibili.com/video/" + video.bvid);
                startActivity(Intent.createChooser(send, "分享视频"));
            }
            return true;
        });
        ShapeableImageView cover = findViewById(R.id.cover);
        TextView title = findViewById(R.id.title);
        TextView meta = findViewById(R.id.meta);
        TextView desc = findViewById(R.id.desc);
        TextView peakInfo = findViewById(R.id.peakInfo);
        LinearLayout statGrid = findViewById(R.id.statGrid);
        ChipGroup tagChips = findViewById(R.id.tagChips);
        LineChart peakChart = findViewById(R.id.peakChart);
        android.widget.Button openBili = findViewById(R.id.openBili);
        Glide.with(this).load(video.pic == null ? "" : video.pic.replace("http://", "https://")).centerCrop().placeholder(R.drawable.ic_video).into(cover);
        title.setText(video.title);
        meta.setText(video.date + " · " + video.account + (video.type.isEmpty() ? "" : " · " + video.type)
                + " · " + UiUtils.duration(video) + "\n" + video.bvid);
        desc.setText(video.desc == null || video.desc.isEmpty() ? (video.summary.isEmpty() ? "暂无视频简介" : video.summary) : video.desc);
        meta.setTextIsSelectable(true);
        desc.setTextIsSelectable(true);
        peakInfo.setText(buildPeakText(video));
        statGrid.removeAllViews(); tagChips.removeAllViews();
        addStatRow(statGrid, "播放", UiUtils.number(this, video.view), "点赞", UiUtils.number(this, video.like));
        addStatRow(statGrid, "弹幕", UiUtils.number(this, video.danmaku), "评论", UiUtils.number(this, video.reply));
        addStatRow(statGrid, "收藏", UiUtils.number(this, video.favorite), "投币", UiUtils.number(this, video.coin));
        addStatRow(statGrid, "分享", UiUtils.number(this, video.share), "互动率", UiUtils.percent(video.interactionRate()));
        addStatRow(statGrid, "字幕行数", UiUtils.number(this, video.sub_lines), "字幕字符", UiUtils.number(this, video.sub_chars));
        for (DataModels.Tag tag : video.tags) {
            Chip chip = new Chip(this);
            chip.setText(tag.name);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                Intent library = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                library.putExtra("search", tag.name); library.putExtra("site", site.name());
                startActivity(library); finish();
            });
            tagChips.addView(chip);
        }
        setupChart(peakChart, video);
        openBili.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bilibili.com/video/" + video.bvid)));
            } catch (android.content.ActivityNotFoundException ignored) {
                android.widget.Toast.makeText(this, "没有可用的浏览器", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private DataModels.Video findVideo(DataModels.Dataset dataset, String bvid) {
        if (dataset == null || dataset.data == null) return null;
        for (DataModels.Video video : dataset.data.videos) {
            if (video.bvid.equals(bvid)) return video;
        }
        return null;
    }

    private void setupChart(LineChart chart, DataModels.Video video) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        int textColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary);
        chart.getAxisLeft().setTextColor(textColor);
        XAxis axis = chart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM);
        axis.setDrawGridLines(false);
        axis.setTextColor(textColor);
        axis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override public String getFormattedValue(float value) { return UiUtils.timestamp(value); }
        });
        axis.setLabelCount(4, false);
        List<Entry> entries = new ArrayList<>();
        for (List<Double> point : video.dm_profile) {
            // The source exporter multiplies the second-based bucket position by 10.
            if (point.size() >= 2) entries.add(new Entry(point.get(0).floatValue() / 10f, point.get(1).floatValue()));
        }
        if (entries.isEmpty()) {
            chart.setNoDataText("暂无弹幕分布");
            chart.setNoDataTextColor(textColor);
            chart.clear();
            return;
        }
        LineDataSet set = new LineDataSet(entries, "弹幕分布");
        set.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary));
        set.setLineWidth(2.5f);
        set.setCircleColor(Color.parseColor("#FF6B8A"));
        set.setCircleRadius(2.5f);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        chart.setData(new LineData(set));
        chart.invalidate();
    }

    private void addStatRow(LinearLayout parent, String leftLabel, String leftValue, String rightLabel, String rightValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(statCard(parent, leftLabel, leftValue));
        row.addView(statCard(parent, rightLabel, rightValue));
    }

    private View statCard(LinearLayout parent, String label, String value) {
        View card = getLayoutInflater().inflate(R.layout.item_stat_card, parent, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int margin = (int) (getResources().getDisplayMetrics().density * 4);
        params.setMargins(margin, margin, margin, 0);
        card.setLayoutParams(params);
        TextView labelView = card.findViewById(R.id.label);
        TextView valueView = card.findViewById(R.id.value);
        labelView.setText(label);
        valueView.setText(value);
        return card;
    }

    private String buildPeakText(DataModels.Video video) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(3, video.peaks.size()); i++) {
            DataModels.Peak peak = video.peaks.get(i);
            builder.append(UiUtils.timestamp(peak.t)).append(" 附近 · ").append((int) peak.count).append(" 条\n");
            for (int j = 0; j < Math.min(2, peak.samples.size()); j++) {
                builder.append("　").append(peak.samples.get(j)).append("\n");
            }
        }
        return builder.length() == 0 ? "暂无弹幕峰值样本" : builder.toString().trim();
    }

    public static void open(Fragment fragment, String bvid) {
        Intent intent = new Intent(fragment.getContext(), VideoDetailActivity.class);
        intent.putExtra("bvid", bvid);
        intent.putExtra("site", MainActivity.currentSite().name());
        fragment.startActivity(intent);
    }

    @Override public void onDataChanged() { if (!isFinishing()) render(); }
    @Override protected void onDestroy() { DataRepository.get().removeListener(this); super.onDestroy(); }
}
