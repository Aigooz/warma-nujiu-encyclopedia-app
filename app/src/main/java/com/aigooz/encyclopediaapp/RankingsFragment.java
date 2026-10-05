package com.aigooz.encyclopediaapp;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

public class RankingsFragment extends Fragment implements DataRepository.Listener {
    private ChipGroup metricChips;
    private com.github.mikephil.charting.charts.HorizontalBarChart rankChart;
    private RecyclerView rankList;
    private LinearLayout insightContainer;
    private CombinedChart annualChart;
    private BarChart clockChart;
    private BarChart weekdayChart;
    private PieChart honorChart;
    private ScatterChart clusterChart;
    private ChipGroup tagCloud;
    private LinearLayout tagAffinityContainer;
    private Spinner similarSource;
    private LinearLayout similarList;
    private View sectionRanking;
    private View sectionRhythm;
    private View sectionTags;
    private View sectionDeep;
    private String metric = "view";
    private String section = "ranking";
    private String selectedSimilar = "";

    private static final int[] CHART_COLORS = {
            R.color.primary, R.color.mint, R.color.pink, R.color.orange, R.color.blue,
            R.color.primary_dark, R.color.purple
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rankings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        rankList.setLayoutManager(new LinearLayoutManager(requireContext()));
        rankList.setNestedScrollingEnabled(false);
        setupRankChart();
        setupCharts();
        setupSimilarSpinner();
        setupMetricChips();
        setupSections();
        DataRepository.get().addListener(this);
        render();
    }

    private void bindViews(View view) {
        metricChips = view.findViewById(R.id.metricChips);
        rankChart = view.findViewById(R.id.rankChart);
        rankList = view.findViewById(R.id.rankList);
        insightContainer = view.findViewById(R.id.insightContainer);
        annualChart = view.findViewById(R.id.annualChart);
        clockChart = view.findViewById(R.id.clockChart);
        weekdayChart = view.findViewById(R.id.weekdayChart);
        honorChart = view.findViewById(R.id.honorChart);
        clusterChart = view.findViewById(R.id.clusterChart);
        tagCloud = view.findViewById(R.id.tagCloud);
        tagAffinityContainer = view.findViewById(R.id.tagAffinityContainer);
        similarSource = view.findViewById(R.id.similarSource);
        similarList = view.findViewById(R.id.similarList);
        sectionRanking = view.findViewById(R.id.sectionRanking);
        sectionRhythm = view.findViewById(R.id.sectionRhythm);
        sectionTags = view.findViewById(R.id.sectionTags);
        sectionDeep = view.findViewById(R.id.sectionDeep);
    }

    private void setupSections() {
        MaterialButtonToggleGroup toggle = requireView().findViewById(R.id.sectionChips);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.sectionRankButton) section = "ranking";
            else if (checkedId == R.id.sectionRhythmButton) section = "rhythm";
            else if (checkedId == R.id.sectionTagButton) section = "tags";
            else if (checkedId == R.id.sectionDeepButton) section = "deep";
            showSection();
        });
        toggle.check(R.id.sectionRankButton);
        showSection();
    }

    private void showSection() {
        sectionRanking.setVisibility("ranking".equals(section) ? View.VISIBLE : View.GONE);
        sectionRhythm.setVisibility("rhythm".equals(section) ? View.VISIBLE : View.GONE);
        sectionTags.setVisibility("tags".equals(section) ? View.VISIBLE : View.GONE);
        sectionDeep.setVisibility("deep".equals(section) ? View.VISIBLE : View.GONE);
    }

    private void setupMetricChips() {
        String[][] metrics = {{"view", "播放"}, {"like", "点赞"}, {"danmaku", "弹幕"},
                {"coin", "投币"}, {"favorite", "收藏"}, {"reply", "评论"}, {"share", "分享"}};
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
    }

    private void setupRankChart() {
        rankChart.getDescription().setEnabled(false);
        rankChart.getLegend().setEnabled(false);
        rankChart.setTouchEnabled(false);
        rankChart.setDrawGridBackground(false);
        rankChart.getAxisRight().setEnabled(false);
        rankChart.getAxisLeft().setDrawGridLines(false);
        rankChart.getAxisLeft().setAxisMinimum(0f);
        rankChart.getAxisLeft().setTextColor(axisColor());
        rankChart.getAxisLeft().setTextSize(10f);
        rankChart.setExtraBottomOffset(8f);
        XAxis axis = rankChart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM);
        axis.setDrawGridLines(false);
        axis.setGranularity(1f);
        axis.setTextColor(axisColor());
        axis.setTextSize(10f);
    }

    private void setupCharts() {
        setupBarChart(clockChart);
        setupBarChart(weekdayChart);
        setupAnnualChart();
        setupHonorChart();
        setupClusterChart();
    }

    private void setupAnnualChart() {
        annualChart.getDescription().setEnabled(false);
        annualChart.setDrawGridBackground(false);
        annualChart.setDrawBarShadow(false);
        annualChart.setHighlightFullBarEnabled(false);
        annualChart.setPinchZoom(false);
        annualChart.getAxisRight().setEnabled(false);
        annualChart.getAxisLeft().setAxisMinimum(0f);
        annualChart.getAxisLeft().setDrawGridLines(false);
        annualChart.getAxisLeft().setTextColor(axisColor());
        annualChart.getAxisLeft().setTextSize(10f);
        annualChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        annualChart.getXAxis().setDrawGridLines(false);
        annualChart.getXAxis().setGranularity(1f);
        annualChart.getXAxis().setTextColor(axisColor());
        annualChart.getXAxis().setTextSize(10f);
        annualChart.getLegend().setEnabled(true);
        annualChart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        annualChart.getLegend().setTextColor(axisColor());
        annualChart.getLegend().setTextSize(11f);
        annualChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE
        });
    }

    private void setupHonorChart() {
        honorChart.getDescription().setEnabled(false);
        honorChart.setDrawHoleEnabled(true);
        honorChart.setHoleColor(Color.TRANSPARENT);
        honorChart.setCenterTextColor(axisColor());
        honorChart.getLegend().setEnabled(true);
        honorChart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        honorChart.getLegend().setTextColor(axisColor());
        honorChart.getLegend().setTextSize(11f);
    }

    private void setupClusterChart() {
        clusterChart.getDescription().setEnabled(false);
        clusterChart.setDrawGridBackground(false);
        clusterChart.setPinchZoom(true);
        clusterChart.setScaleEnabled(true);
        clusterChart.getAxisRight().setEnabled(false);
        clusterChart.getAxisLeft().setDrawGridLines(true);
        clusterChart.getAxisLeft().setGridColor(Color.parseColor("#1F656D7D"));
        clusterChart.getAxisLeft().setTextColor(axisColor());
        clusterChart.getAxisLeft().setTextSize(10f);
        clusterChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        clusterChart.getXAxis().setDrawGridLines(true);
        clusterChart.getXAxis().setGridColor(Color.parseColor("#1F656D7D"));
        clusterChart.getXAxis().setTextColor(axisColor());
        clusterChart.getXAxis().setTextSize(10f);
        clusterChart.getLegend().setEnabled(true);
        clusterChart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        clusterChart.getLegend().setTextColor(axisColor());
        clusterChart.getLegend().setTextSize(11f);
    }

    private void setupBarChart(BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().setTextColor(axisColor());
        chart.getAxisLeft().setTextSize(10f);
        XAxis axis = chart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM);
        axis.setDrawGridLines(false);
        axis.setGranularity(1f);
        axis.setTextColor(axisColor());
        axis.setTextSize(10f);
    }

    private int axisColor() {
        return ContextCompat.getColor(requireContext(), R.color.text_secondary);
    }

    private void setupSimilarSpinner() {
        similarSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                DataModels.Dataset dataset = DataRepository.get().current();
                if (dataset == null || dataset.data == null || position < 0
                        || position >= dataset.data.videos.size()) return;
                DataModels.Video selected = sortByDate(dataset.data.videos).get(position);
                selectedSimilar = selected.bvid;
                renderSimilarItems(dataset, selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
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
        if (!isAdded() || dataset == null || dataset.site != MainActivity.currentSite()
                || dataset.data == null) return;
        renderRanking(dataset);
        renderInsights(dataset);
        renderAnnual(dataset);
        renderClock(dataset);
        renderWeekday(dataset);
        renderHonors(dataset);
        renderTags(dataset);
        renderClusters(dataset);
        renderSimilar(dataset);
    }

    private void renderRanking(DataModels.Dataset dataset) {
        List<DataModels.Video> sorted = new ArrayList<>(dataset.data.videos);
        sorted.sort((a, b) -> Double.compare(b.metric(metric), a.metric(metric)));
        List<DataModels.Video> top = sorted.subList(0, Math.min(10, sorted.size()));
        rankList.setAdapter(new RankingAdapter(requireContext(), new ArrayList<>(top), metric,
                video -> VideoDetailActivity.open(this, video.bvid)));

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
        double gap = 0;
        for (DataModels.Video video : data.videos) {
            String type = video.type == null || video.type.isEmpty() ? "未标注" : video.type;
            types.put(type, types.getOrDefault(type, 0) + 1);
            accounts.put(video.account, accounts.getOrDefault(video.account, 0) + 1);
            for (DataModels.Tag tag : video.tags) tags.put(tag.name, tags.getOrDefault(tag.name, 0) + 1);
            LocalDate date = parseDate(video);
            if (date != null) weekdays.merge(date.getDayOfWeek().getValue(), 1, Integer::sum);
            interaction += video.interactionRate();
            gap += video.gap_days;
        }
        int count = data.videos.size();
        addInsight(insightContainer, "平均互动率", String.format("%.2f%%", interaction / Math.max(1, count)));
        addInsight(insightContainer, "字幕覆盖",
                (int) data.videos.stream().filter(v -> v.sub_lines > 0).count() + " / " + count + " 个视频");
        addInsight(insightContainer, "平均更新间隔", String.format("%.1f 天", gap / Math.max(1, count)));
        addInsight(insightContainer, "最常见类型", maxEntry(types));
        addInsight(insightContainer, "账号分布", mapToText(accounts));
        addInsight(insightContainer, "高频标签", maxEntry(tags));
        addInsight(insightContainer, "最活跃星期", weekdayName(maxNumericKey(weekdays)));
        addInsight(insightContainer, "最强弹幕梗", data.top_memes.isEmpty() ? "暂无"
                : data.top_memes.get(0).content + " · " + (int) data.top_memes.get(0).count + " 次");
    }

    private void renderAnnual(DataModels.Dataset dataset) {
        TreeMap<Integer, Integer> yearly = new TreeMap<>();
        for (DataModels.Video video : dataset.data.videos) {
            LocalDate date = parseDate(video);
            if (date != null) yearly.merge(date.getYear(), 1, Integer::sum);
        }
        List<BarEntry> bars = new ArrayList<>();
        List<Entry> line = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int cumulative = 0;
        int index = 0;
        for (Map.Entry<Integer, Integer> entry : yearly.entrySet()) {
            cumulative += entry.getValue();
            bars.add(new BarEntry(index, entry.getValue()));
            line.add(new Entry(index, cumulative));
            labels.add(String.valueOf(entry.getKey()));
            index++;
        }
        BarDataSet barSet = new BarDataSet(bars, "年度投稿");
        barSet.setColor(Color.parseColor("#7C5CFF"));
        barSet.setDrawValues(false);
        LineDataSet lineSet = new LineDataSet(line, "累计视频");
        lineSet.setColor(Color.parseColor("#00B894"));
        lineSet.setLineWidth(2.6f);
        lineSet.setDrawCircles(false);
        lineSet.setDrawValues(false);

        CombinedData data = new CombinedData();
        data.setData(new BarData(barSet));
        data.setData(new LineData(lineSet));
        annualChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        annualChart.getXAxis().setLabelCount(Math.min(8, labels.size()), false);
        annualChart.setData(data);
        annualChart.invalidate();
    }

    private void renderClock(DataModels.Dataset dataset) {
        int[] hours = new int[24];
        for (DataModels.Video video : dataset.data.videos) {
            if (video.pubdate_iso != null && video.pubdate_iso.length() >= 13) {
                try {
                    hours[Integer.parseInt(video.pubdate_iso.substring(11, 13))]++;
                } catch (Exception ignored) {}
            }
        }
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < hours.length; i++) {
            entries.add(new BarEntry(i, hours[i]));
            labels.add(String.valueOf(i));
        }
        BarDataSet set = new BarDataSet(entries, "发布数量");
        set.setColor(Color.parseColor("#4CA6FF"));
        set.setDrawValues(false);
        clockChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        clockChart.getXAxis().setLabelCount(8, false);
        clockChart.setData(new BarData(set));
        clockChart.invalidate();
    }

    private void renderWeekday(DataModels.Dataset dataset) {
        int[] weekdays = new int[7];
        for (DataModels.Video video : dataset.data.videos) {
            LocalDate date = parseDate(video);
            if (date != null) weekdays[date.getDayOfWeek().getValue() - 1]++;
        }
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String[] names = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < weekdays.length; i++) {
            entries.add(new BarEntry(i, weekdays[i]));
            labels.add(names[i]);
        }
        BarDataSet set = new BarDataSet(entries, "更新数量");
        set.setColor(Color.parseColor("#FF9F43"));
        set.setDrawValues(false);
        weekdayChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        weekdayChart.setData(new BarData(set));
        weekdayChart.invalidate();
    }

    private void renderHonors(DataModels.Dataset dataset) {
        Map<String, Integer> honors = new LinkedHashMap<>();
        for (DataModels.Video video : dataset.data.videos) {
            for (DataModels.Honor honor : video.honors) {
                String label = honor.desc == null ? "站内荣誉" : honor.desc;
                if (label.contains("排行榜")) label = "全站排行榜";
                else if (label.contains("每周必看")) label = "每周必看";
                else if (label.contains("热门")) label = "热门收录";
                honors.put(label, honors.getOrDefault(label, 0) + 1);
            }
        }
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : honors.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }
        if (entries.isEmpty()) {
            honorChart.setCenterText("暂无荣誉数据");
            honorChart.setData(null);
            honorChart.invalidate();
            return;
        }
        honorChart.setCenterText("荣誉\n" + entries.size() + " 类");
        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(CHART_COLORS, requireContext());
        set.setSliceSpace(2f);
        PieData data = new PieData(set);
        data.setDrawValues(false);
        honorChart.setData(data);
        honorChart.invalidate();
    }

    private void renderTags(DataModels.Dataset dataset) {
        tagCloud.removeAllViews();
        tagAffinityContainer.removeAllViews();
        Map<String, Integer> tags = new LinkedHashMap<>();
        for (DataModels.Video video : dataset.data.videos) {
            for (DataModels.Tag tag : video.tags) {
                if (tag.name != null && !tag.name.trim().isEmpty()) {
                    tags.put(tag.name, tags.getOrDefault(tag.name, 0) + 1);
                }
            }
        }
        List<Map.Entry<String, Integer>> ranked = rank(tags);
        if (ranked.isEmpty()) return;
        int max = ranked.get(0).getValue();
        int min = ranked.get(Math.min(ranked.size() - 1, 24)).getValue();
        for (int i = 0; i < Math.min(ranked.size(), 32); i++) {
            Map.Entry<String, Integer> entry = ranked.get(i);
            Chip chip = new Chip(requireContext());
            chip.setText(entry.getKey() + " " + entry.getValue());
            chip.setCheckable(false);
            chip.setClickable(false);
            float scale = max == min ? 1f : (entry.getValue() - min) * 1f / (max - min);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11 + scale * 10);
            tagCloud.addView(chip);
        }
        renderTagAffinity(dataset, ranked.subList(0, Math.min(24, ranked.size())));
    }

    private void renderTagAffinity(DataModels.Dataset dataset, List<Map.Entry<String, Integer>> topTags) {
        Set<String> top = new HashSet<>();
        for (Map.Entry<String, Integer> entry : topTags) top.add(entry.getKey());
        Map<String, Integer> co = new HashMap<>();
        for (DataModels.Video video : dataset.data.videos) {
            List<String> names = new ArrayList<>();
            for (DataModels.Tag tag : video.tags) {
                if (tag.name != null && top.contains(tag.name)) names.add(tag.name);
            }
            names.sort(String::compareTo);
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    String key = names.get(i) + "\u0000" + names.get(j);
                    co.put(key, co.getOrDefault(key, 0) + 1);
                }
            }
        }
        Map<String, Integer> tagCounts = tags(dataset);
        List<Map.Entry<String, Integer>> ranked = rank(co);
        int shown = 0;
        for (Map.Entry<String, Integer> entry : ranked) {
            if (entry.getValue() < 2 || shown >= 8) break;
            String[] parts = entry.getKey().split("\u0000");
            double pab = entry.getValue() * 1.0 / dataset.data.videos.size();
            double pa = count(tagCounts, parts[0]) * 1.0 / dataset.data.videos.size();
            double pb = count(tagCounts, parts[1]) * 1.0 / dataset.data.videos.size();
            double pmi = Math.log(pab / Math.max(1e-12, pa * pb)) / Math.log(2);
            addInsight(tagAffinityContainer, parts[0] + " × " + parts[1],
                    "共现 " + entry.getValue() + " 次 · PMI " + String.format("%.2f", pmi));
            shown++;
        }
    }

    private void renderClusters(DataModels.Dataset dataset) {
        List<DataModels.Video> videos = dataset.data.videos;
        double[][] raw = new double[videos.size()][5];
        for (int i = 0; i < videos.size(); i++) {
            DataModels.Video video = videos.get(i);
            raw[i][0] = Math.log10(1 + video.view);
            raw[i][1] = Math.log10(1 + video.like);
            raw[i][2] = video.interactionRate();
            raw[i][3] = video.duration_seconds > 0 ? video.sub_lines / video.duration_seconds * 100 : 0;
            raw[i][4] = Math.log10(1 + video.duration_seconds);
        }
        double[][] features = standardize(raw);
        int[] assignment = kmeans(features, 4, 14);
        List<List<Entry>> groups = new ArrayList<>();
        for (int i = 0; i < 4; i++) groups.add(new ArrayList<>());
        for (int i = 0; i < features.length; i++) {
            groups.get(assignment[i]).add(new Entry((float) features[i][0], (float) features[i][2]));
        }

        ScatterData data = new ScatterData();
        int[] colors = {Color.parseColor("#7C5CFF"), Color.parseColor("#00B894"),
                Color.parseColor("#FF6B8A"), Color.parseColor("#FF9F43")};
        int labelIndex = 0;
        for (List<Entry> entries : groups) {
            if (entries.isEmpty()) continue;
            ScatterDataSet set = new ScatterDataSet(entries, "内容群体 " + (labelIndex + 1));
            set.setColor(colors[labelIndex % colors.length]);
            set.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
            set.setScatterShapeSize(10f);
            set.setDrawValues(false);
            data.addDataSet(set);
            labelIndex++;
        }
        clusterChart.getXAxis().setGranularity(0.5f);
        clusterChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"低", "", "中", "", "高"}));
        clusterChart.getAxisLeft().setAxisMinimum(-2.2f);
        clusterChart.getAxisLeft().setAxisMaximum(3.2f);
        clusterChart.setData(data);
        clusterChart.invalidate();
    }

    private void renderSimilar(DataModels.Dataset dataset) {
        List<DataModels.Video> videos = sortByDate(dataset.data.videos);
        List<String> titles = new ArrayList<>();
        int selectedPosition = 0;
        for (int i = 0; i < videos.size(); i++) {
            DataModels.Video video = videos.get(i);
            titles.add(video.title);
            if (video.bvid.equals(selectedSimilar)) selectedPosition = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        similarSource.setAdapter(adapter);
        if (titles.isEmpty()) return;
        similarSource.setSelection(selectedPosition, false);
        if (selectedSimilar.isEmpty()) {
            selectedSimilar = videos.get(selectedPosition).bvid;
            renderSimilarItems(dataset, videos.get(selectedPosition));
        }
    }

    private void renderSimilarItems(DataModels.Dataset dataset, DataModels.Video source) {
        similarList.removeAllViews();
        List<DataModels.Video> scored = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (DataModels.Video video : dataset.data.videos) {
            if (video.bvid.equals(source.bvid)) continue;
            scored.add(video);
            scores.add(similarity(source, video));
        }
        sortScores(scored, scores);
        for (int i = 0; i < Math.min(8, scored.size()); i++) {
            DataModels.Video video = scored.get(i);
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_insight, similarList, false);
            TextView title = card.findViewById(R.id.title);
            TextView body = card.findViewById(R.id.body);
            title.setText(video.title);
            body.setText(String.format("相似度 %.0f%% · %s · %s · %s 播放",
                    scores.get(i) * 100,
                    video.type == null || video.type.isEmpty() ? "未标注" : video.type,
                    video.account_short,
                    UiUtils.number(requireContext(), video.view)));
            card.setOnClickListener(v -> VideoDetailActivity.open(this, video.bvid));
            similarList.addView(card);
        }
    }

    private void sortScores(List<DataModels.Video> videos, List<Double> scores) {
        for (int i = 0; i < videos.size(); i++) {
            for (int j = i + 1; j < videos.size(); j++) {
                if (scores.get(j) > scores.get(i)) {
                    DataModels.Video tempVideo = videos.set(i, videos.get(j));
                    videos.set(j, tempVideo);
                    Double tempScore = scores.set(i, scores.get(j));
                    scores.set(j, tempScore);
                }
            }
        }
    }

    private double similarity(DataModels.Video a, DataModels.Video b) {
        Set<String> at = new HashSet<>();
        Set<String> bt = new HashSet<>();
        for (DataModels.Tag tag : a.tags) at.add(tag.name);
        for (DataModels.Tag tag : b.tags) bt.add(tag.name);
        Set<String> intersection = new HashSet<>(at);
        intersection.retainAll(bt);
        Set<String> union = new HashSet<>(at);
        union.addAll(bt);
        double tagScore = union.isEmpty() ? 0 : intersection.size() * 1.0 / union.size();
        double typeScore = a.type != null && a.type.equals(b.type) ? 1 : 0;
        double accountScore = a.account != null && a.account.equals(b.account) ? 1 : 0;
        double durationScore = Math.exp(-Math.abs(Math.log(1 + a.duration_seconds)
                - Math.log(1 + b.duration_seconds)));
        LocalDate ad = parseDate(a);
        LocalDate bd = parseDate(b);
        double dateScore = ad == null || bd == null ? 0
                : Math.exp(-Math.abs(ChronoUnit.DAYS.between(ad, bd)) / 365D);
        return tagScore * .45 + typeScore * .20 + accountScore * .10
                + durationScore * .15 + dateScore * .10;
    }

    private double[][] standardize(double[][] raw) {
        int n = raw.length;
        int dim = n == 0 ? 0 : raw[0].length;
        double[][] result = new double[n][dim];
        for (int d = 0; d < dim; d++) {
            double mean = 0;
            for (double[] row : raw) mean += row[d];
            mean /= Math.max(1, n);
            double variance = 0;
            for (double[] row : raw) variance += Math.pow(row[d] - mean, 2);
            double sd = Math.sqrt(variance / Math.max(1, n));
            if (sd < 1e-9) sd = 1;
            for (int i = 0; i < n; i++) result[i][d] = (raw[i][d] - mean) / sd;
        }
        return result;
    }

    private int[] kmeans(double[][] features, int k, int iterations) {
        Random random = new Random(42);
        int n = features.length;
        int dim = n == 0 ? 0 : features[0].length;
        int[] assignment = new int[n];
        double[][] centers = new double[k][dim];
        for (int c = 0; c < k; c++) {
            centers[c] = features[random.nextInt(Math.max(1, n))].clone();
        }
        for (int iter = 0; iter < iterations; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int best = 0;
                double bestDistance = Double.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    double distance = 0;
                    for (int d = 0; d < dim; d++) {
                        distance += Math.pow(features[i][d] - centers[c][d], 2);
                    }
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = c;
                    }
                }
                if (assignment[i] != best) changed = true;
                assignment[i] = best;
            }
            for (int c = 0; c < k; c++) {
                double[] next = new double[dim];
                int count = 0;
                for (int i = 0; i < n; i++) {
                    if (assignment[i] == c) {
                        count++;
                        for (int d = 0; d < dim; d++) next[d] += features[i][d];
                    }
                }
                if (count == 0) {
                    centers[c] = features[random.nextInt(n)].clone();
                } else {
                    for (int d = 0; d < dim; d++) centers[c][d] = next[d] / count;
                }
            }
            if (!changed) break;
        }
        return assignment;
    }

    private Map<String, Integer> tags(DataModels.Dataset dataset) {
        Map<String, Integer> tags = new LinkedHashMap<>();
        for (DataModels.Video video : dataset.data.videos) {
            for (DataModels.Tag tag : video.tags) {
                tags.put(tag.name, tags.getOrDefault(tag.name, 0) + 1);
            }
        }
        return tags;
    }

    private int count(Map<String, Integer> map, String key) {
        return map.getOrDefault(key, 0);
    }

    private List<Map.Entry<String, Integer>> rank(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return list;
    }

    private List<DataModels.Video> sortByDate(List<DataModels.Video> source) {
        List<DataModels.Video> list = new ArrayList<>(source);
        list.sort((a, b) -> (b.pubdate_iso == null ? b.date : b.pubdate_iso)
                .compareTo(a.pubdate_iso == null ? a.date : a.pubdate_iso));
        return list;
    }

    private LocalDate parseDate(DataModels.Video video) {
        String text = video.pubdate_iso == null || video.pubdate_iso.length() < 10
                ? video.date : video.pubdate_iso;
        if (text == null || text.length() < 10) return null;
        try {
            return LocalDate.parse(text.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private void addInsight(LinearLayout container, String title, String body) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_insight, container, false);
        TextView titleText = card.findViewById(R.id.title);
        TextView bodyText = card.findViewById(R.id.body);
        titleText.setText(title);
        bodyText.setText(body);
        container.addView(card);
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

