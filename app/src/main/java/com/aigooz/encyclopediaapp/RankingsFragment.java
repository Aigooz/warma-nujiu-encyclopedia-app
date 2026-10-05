package com.aigooz.encyclopediaapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RankingsFragment extends Fragment implements DataRepository.Listener {
    private ChipGroup metricChips;
    private HorizontalBarChart rankChart;
    private RecyclerView rankList;
    private LinearLayout insightContainer;
    private String metric = "view";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rankings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        metricChips = view.findViewById(R.id.metricChips);
        rankChart = view.findViewById(R.id.rankChart);
        rankList = view.findViewById(R.id.rankList);
        insightContainer = view.findViewById(R.id.insightContainer);
        rankList.setLayoutManager(new LinearLayoutManager(requireContext()));
        rankList.setNestedScrollingEnabled(false);
        setupChart();
        String[][] metrics = {{"view", "播放"}, {"like", "点赞"}, {"danmaku", "弹幕"}, {"coin", "投币"}, {"favorite", "收藏"}, {"reply", "评论"}, {"share", "分享"}};
        for (String[] item : metrics) {
            Chip chip = new Chip(requireContext());
            chip.setText(item[1]);
            chip.setCheckable(true);
            chip.setChecked(item[0].equals(metric));
            chip.setId(View.generateViewId());
            chip.setOnClickListener(v -> {
                metric = item[0];
                render();
            });
            metricChips.addView(chip);
        }
        DataRepository.get().addListener(this);
        render();
    }

    private void setupChart() {
        rankChart.getDescription().setEnabled(false);
        rankChart.getLegend().setEnabled(false);
        rankChart.setTouchEnabled(false);
        rankChart.setDrawGridBackground(false);
        rankChart.getAxisRight().setEnabled(false);
        rankChart.getAxisLeft().setDrawGridLines(false);
        rankChart.getAxisLeft().setAxisMinimum(0f);
        rankChart.getAxisLeft().setTextColor(Color.parseColor("#656D7D"));
        XAxis axis = rankChart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM);
        axis.setDrawGridLines(false);
        axis.setGranularity(1f);
        axis.setTextColor(Color.parseColor("#656D7D"));
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
        if (!isAdded() || dataset == null || dataset.site != MainActivity.currentSite() || dataset.data == null) return;
        List<DataModels.Video> sorted = new ArrayList<>(dataset.data.videos);
        sorted.sort((a, b) -> Double.compare(b.metric(metric), a.metric(metric)));
        List<DataModels.Video> top = sorted.subList(0, Math.min(10, sorted.size()));
        rankList.setAdapter(new RankingAdapter(requireContext(), new ArrayList<>(top), metric, video -> VideoDetailActivity.open(this, video.bvid)));
        renderChart(top);
        renderInsights(dataset);
    }

    private void renderChart(List<DataModels.Video> top) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = top.size() - 1; i >= 0; i--) {
            DataModels.Video video = top.get(i);
            String label = video.title.length() > 12 ? video.title.substring(0, 12) + "…" : video.title;
            entries.add(new BarEntry(labels.size(), (float) video.metric(metric)));
            labels.add(label);
        }
        BarDataSet set = new BarDataSet(entries, "榜值");
        set.setColor(Color.parseColor("#7C5CFF"));
        set.setDrawValues(false);
        rankChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        rankChart.getXAxis().setLabelCount(labels.size(), true);
        rankChart.setData(new BarData(set));
        rankChart.invalidate();
    }

    private void renderInsights(DataModels.Dataset dataset) {
        DataModels.RawData data = dataset.data;
        insightContainer.removeAllViews();
        Map<String, Integer> types = new LinkedHashMap<>();
        Map<String, Integer> accounts = new LinkedHashMap<>();
        Map<String, Integer> tags = new LinkedHashMap<>();
        Map<Integer, Integer> weekdays = new HashMap<>();
        double interaction = 0;
        double sub = 0;
        double gap = 0;
        int dated = 0;
        for (DataModels.Video video : data.videos) {
            String type = video.type == null || video.type.isEmpty() ? "未标注" : video.type;
            types.put(type, types.getOrDefault(type, 0) + 1);
            accounts.put(video.account, accounts.getOrDefault(video.account, 0) + 1);
            for (DataModels.Tag tag : video.tags) tags.put(tag.name, tags.getOrDefault(tag.name, 0) + 1);
            if (video.pubdate_iso != null && video.pubdate_iso.length() >= 10) {
                try {
                    LocalDate date = LocalDate.parse(video.pubdate_iso.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
                    weekdays.merge(date.getDayOfWeek().getValue(), 1, Integer::sum);
                } catch (Exception ignored) {}
            }
            if (video.date != null && video.date.length() >= 10) dated++;
            interaction += video.interactionRate();
            sub += video.sub_lines;
            gap += video.gap_days;
        }
        int count = data.videos.size();
        addInsight("平均互动率", String.format("%.2f%%", interaction / Math.max(1, count)));
        addInsight("字幕覆盖", (int) data.videos.stream().filter(v -> v.sub_lines > 0).count() + " / " + count + " 个视频");
        addInsight("平均更新间隔", String.format("%.1f 天", gap / Math.max(1, count)));
        addInsight("最常见类型", maxEntry(types));
        addInsight("账号分布", mapToText(accounts));
        addInsight("高频标签", maxEntry(tags));
        addInsight("最活跃星期", weekdayName(maxNumericKey(weekdays)));
        addInsight("最强弹幕梗", data.top_memes.isEmpty() ? "暂无" : data.top_memes.get(0).content + " · " + (int) data.top_memes.get(0).count + " 次");
    }

    private void addInsight(String title, String body) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_insight, insightContainer, false);
        TextView titleText = card.findViewById(R.id.title);
        TextView bodyText = card.findViewById(R.id.body);
        titleText.setText(title);
        bodyText.setText(body);
        insightContainer.addView(card);
    }

    private String maxEntry(Map<String, Integer> map) {
        Map.Entry<String, Integer> max = null;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (max == null || entry.getValue() > max.getValue()) max = entry;
        }
        return max == null ? "暂无" : max.getKey() + " · " + max.getValue() + " 次";
    }

    private String mapToText(Map<String, Integer> map) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (builder.length() > 0) builder.append("，");
            builder.append(entry.getKey()).append(" ").append(entry.getValue());
        }
        return builder.length() == 0 ? "暂无" : builder.toString();
    }

    private int maxNumericKey(Map<Integer, Integer> map) {
        int key = -1;
        int value = -1;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getValue() > value) {
                key = entry.getKey();
                value = entry.getValue();
            }
        }
        return key;
    }

    private String weekdayName(int day) {
        switch (day) {
            case 1: return "周一";
            case 2: return "周二";
            case 3: return "周三";
            case 4: return "周四";
            case 5: return "周五";
            case 6: return "周六";
            case 7: return "周日";
            default: return "暂无";
        }
    }
}
