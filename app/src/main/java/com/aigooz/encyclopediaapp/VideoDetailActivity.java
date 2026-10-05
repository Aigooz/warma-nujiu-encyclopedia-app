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

public class VideoDetailActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_detail);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        String bvid = getIntent().getStringExtra("bvid");
        DataModels.Dataset dataset = DataRepository.get().current();
        DataModels.Video video = findVideo(dataset, bvid);
        if (video == null) {
            finish();
            return;
        }
        ShapeableImageView cover = findViewById(R.id.cover);
        TextView title = findViewById(R.id.title);
        TextView meta = findViewById(R.id.meta);
        TextView desc = findViewById(R.id.desc);
        TextView peakInfo = findViewById(R.id.peakInfo);
        LinearLayout statGrid = findViewById(R.id.statGrid);
        ChipGroup tagChips = findViewById(R.id.tagChips);
        LineChart peakChart = findViewById(R.id.peakChart);
        MaterialButton openBili = findViewById(R.id.openBili);
        Glide.with(this).load(video.pic == null ? "" : video.pic.replace("http://", "https://")).centerCrop().placeholder(R.drawable.ic_video).into(cover);
        title.setText(video.title);
        meta.setText(video.date + " · " + video.account + (video.type.isEmpty() ? "" : " · " + video.type)
                + (video.duration.isEmpty() ? "" : " · " + video.duration) + "\n" + video.bvid);
        desc.setText(video.desc == null || video.desc.isEmpty() ? video.summary : video.desc);
        peakInfo.setText(buildPeakText(video));
        addStatRow(statGrid, "播放", UiUtils.number(this, video.view), "点赞", UiUtils.number(this, video.like));
        addStatRow(statGrid, "弹幕", UiUtils.number(this, video.danmaku), "评论", UiUtils.number(this, video.reply));
        addStatRow(statGrid, "收藏", UiUtils.number(this, video.favorite), "投币", UiUtils.number(this, video.coin));
        addStatRow(statGrid, "分享", UiUtils.number(this, video.share), "互动率", UiUtils.percent(video.interactionRate()));
        for (DataModels.Tag tag : video.tags) {
            Chip chip = new Chip(this);
            chip.setText(tag.name);
            chip.setCheckable(false);
            tagChips.addView(chip);
        }
        setupChart(peakChart, video);
        openBili.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bilibili.com/video/" + video.bvid)));
            } catch (Exception ignored) {}
        });
    }

    private DataModels.Video findVideo(DataModels.Dataset dataset, String bvid) {
        if (dataset == null || dataset.data == null) return null;
        for (DataModels.Video video : dataset.data.videos) {
            if (bvid.equals(video.bvid)) return video;
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
        chart.getAxisLeft().setTextColor(Color.parseColor("#656D7D"));
        XAxis axis = chart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM);
        axis.setDrawGridLines(false);
        axis.setTextColor(Color.parseColor("#656D7D"));
        List<Entry> entries = new ArrayList<>();
        for (List<Double> point : video.dm_profile) {
            if (point.size() >= 2) entries.add(new Entry(point.get(0).floatValue(), point.get(1).floatValue()));
        }
        if (entries.isEmpty()) {
            chart.setNoDataText("暂无弹幕分布");
            return;
        }
        LineDataSet set = new LineDataSet(entries, "弹幕分布");
        set.setColor(Color.parseColor("#7C5CFF"));
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
            builder.append("第 ").append((int) peak.t).append(" 秒附近 · ").append((int) peak.count).append(" 条\n");
            for (int j = 0; j < Math.min(2, peak.samples.size()); j++) {
                builder.append("　").append(peak.samples.get(j)).append("\n");
            }
        }
        return builder.length() == 0 ? "暂无弹幕峰值样本" : builder.toString().trim();
    }

    public static void open(Fragment fragment, String bvid) {
        Intent intent = new Intent(fragment.getContext(), VideoDetailActivity.class);
        intent.putExtra("bvid", bvid);
        fragment.startActivity(intent);
    }
}
