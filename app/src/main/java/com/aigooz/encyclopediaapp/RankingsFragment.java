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
    private RankingAdapter rankAdapter;
    private LinearLayout insightContainer;
    private CombinedChart annualChart;
    private BarChart clockChart;
    private BarChart weekdayChart;
    private BarChart monthChart;
    private com.github.mikephil.charting.charts.HorizontalBarChart gapChart;
    private BarChart durationChart;
    private PieChart honorChart;
    private LinearLayout honorLegend;
    private LinearLayout honorTimeline;
    private ScatterChart clusterChart;
    private ChipGroup tagCloud;
    private LinearLayout tagAffinityContainer;
    private View sectionHeat;
    private PieChart engageChart;
    private LinearLayout engageLegend;
    private BarChart yearViewsChart;
    private com.github.mikephil.charting.charts.HorizontalBarChart typeViewsChart;
    private ScatterChart engageScatterChart;
    private com.github.mikephil.charting.charts.HorizontalBarChart structureChart;
    private View sectionWeibo;
    private TextView weiboSummary;
    private LinearLayout weiboList;
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

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) {
            metric = state.getString("metric", "view"); section = state.getString("section", "ranking");
            selectedSimilar = state.getString("similar", "");
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("metric", metric); state.putString("section", section); state.putString("similar", selectedSimilar);
    }

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
        monthChart = view.findViewById(R.id.monthChart);
        gapChart = view.findViewById(R.id.gapChart);
        durationChart = view.findViewById(R.id.durationChart);
        honorChart = view.findViewById(R.id.honorChart);
        honorLegend = view.findViewById(R.id.honorLegend);
        honorTimeline = view.findViewById(R.id.honorTimeline);
        clusterChart = view.findViewById(R.id.clusterChart);
        tagCloud = view.findViewById(R.id.tagCloud);
        tagAffinityContainer = view.findViewById(R.id.tagAffinityContainer);
        similarSource = view.findViewById(R.id.similarSource);
        similarList = view.findViewById(R.id.similarList);
        sectionRanking = view.findViewById(R.id.sectionRanking);
        sectionRhythm = view.findViewById(R.id.sectionRhythm);
        sectionTags = view.findViewById(R.id.sectionTags);
        sectionHeat = view.findViewById(R.id.sectionHeat);
        sectionDeep = view.findViewById(R.id.sectionDeep);
        engageChart = view.findViewById(R.id.engageChart);
        engageLegend = view.findViewById(R.id.engageLegend);
        yearViewsChart = view.findViewById(R.id.yearViewsChart);
        typeViewsChart = view.findViewById(R.id.typeViewsChart);
        engageScatterChart = view.findViewById(R.id.engageScatterChart);
        structureChart = view.findViewById(R.id.structureChart);
        sectionWeibo = view.findViewById(R.id.sectionWeibo);
        weiboSummary = view.findViewById(R.id.weiboSummary);
        weiboList = view.findViewById(R.id.weiboList);
    }

    private void setupSections() {
        MaterialButtonToggleGroup toggle = requireView().findViewById(R.id.sectionChips);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.sectionRankButton) section = "ranking";
            else if (checkedId == R.id.sectionRhythmButton) section = "rhythm";
            else if (checkedId == R.id.sectionTagButton) section = "tags";
            else if (checkedId == R.id.sectionHeatButton) section = "heat";
            else if (checkedId == R.id.sectionWeiboButton) section = "weibo";
            else if (checkedId == R.id.sectionDeepButton) section = "deep";
            showSection();
        });
        toggle.check("rhythm".equals(section) ? R.id.sectionRhythmButton : "tags".equals(section)
                ? R.id.sectionTagButton : "heat".equals(section) ? R.id.sectionHeatButton
                : "weibo".equals(section) ? R.id.sectionWeiboButton
                : "deep".equals(section) ? R.id.sectionDeepButton : R.id.sectionRankButton);
        showSection();
    }

    private void showSection() {
        sectionRanking.setVisibility("ranking".equals(section) ? View.VISIBLE : View.GONE);
        sectionRhythm.setVisibility("rhythm".equals(section) ? View.VISIBLE : View.GONE);
        sectionTags.setVisibility("tags".equals(section) ? View.VISIBLE : View.GONE);
        sectionHeat.setVisibility("heat".equals(section) ? View.VISIBLE : View.GONE);
        sectionWeibo.setVisibility("weibo".equals(section) ? View.VISIBLE : View.GONE);
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
        rankChart.setTouchEnabled(true);
        rankChart.setScaleEnabled(false);
        rankChart.setDragEnabled(false);
        rankChart.setDoubleTapToZoomEnabled(false);
        rankChart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override public void onValueSelected(Entry entry, com.github.mikephil.charting.highlight.Highlight highlight) {
                if (entry.getData() instanceof String) VideoDetailActivity.open(RankingsFragment.this, (String) entry.getData());
            }
            @Override public void onNothingSelected() {}
        });
        rankChart.setDrawGridBackground(false);
        rankChart.getAxisRight().setEnabled(false);
        rankChart.getAxisLeft().setDrawGridLines(false);
        rankChart.getAxisLeft().setAxisMinimum(0f);
        rankChart.getAxisLeft().setTextColor(axisColor());
        rankChart.getAxisLeft().setTextSize(10f);
        rankChart.getAxisLeft().setValueFormatter(numberFormatter());
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
        setupBarChart(monthChart);
        setupBarChart(durationChart);
        setupHorizontalBarChart(gapChart);
        setupAnnualChart();
        setupHonorChart();
        setupClusterChart();
        setupEngageChart();
        setupScatterChart(engageScatterChart);
        setupHorizontalBarChart(typeViewsChart);
        setupHorizontalBarChart(structureChart);
        setupBarChart(yearViewsChart);
        enableValueSelection(clockChart);
        enableValueSelection(weekdayChart);
        enableValueSelection(monthChart);
        enableValueSelection(durationChart);
        enableValueSelection(yearViewsChart);
        enableValueSelection(annualChart);
        enableValueSelection(honorChart);
        enableValueSelection(engageChart);
    }

    private void enableValueSelection(com.github.mikephil.charting.charts.Chart<?> chart) {
        chart.setTouchEnabled(true);
        chart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override public void onValueSelected(Entry entry, com.github.mikephil.charting.highlight.Highlight highlight) {
                String label = entry instanceof PieEntry ? ((PieEntry) entry).getLabel() : "";
                if (chart instanceof com.github.mikephil.charting.charts.BarLineChartBase)
                    label = ((com.github.mikephil.charting.charts.BarLineChartBase<?>) chart).getXAxis().getValueFormatter().getFormattedValue(entry.getX());
                android.widget.Toast.makeText(requireContext(), label + " · " + UiUtils.number(requireContext(), entry.getY()), android.widget.Toast.LENGTH_SHORT).show();
            }
            @Override public void onNothingSelected() {}
        });
    }

    private void setupAnnualChart() {
        annualChart.getDescription().setEnabled(false);
        annualChart.setDrawGridBackground(false);
        annualChart.setDrawBarShadow(false);
        annualChart.setHighlightFullBarEnabled(false);
        annualChart.setPinchZoom(false);
        annualChart.setDragEnabled(false);
        annualChart.setScaleEnabled(false);
        annualChart.setDoubleTapToZoomEnabled(false);
        annualChart.getAxisRight().setEnabled(false);
        annualChart.getAxisLeft().setAxisMinimum(0f);
        annualChart.getAxisLeft().setDrawGridLines(false);
        annualChart.getAxisLeft().setTextColor(axisColor());
        annualChart.getAxisLeft().setTextSize(10f);
        annualChart.getAxisLeft().setValueFormatter(numberFormatter());
        annualChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        annualChart.getXAxis().setDrawGridLines(false);
        annualChart.getXAxis().setGranularity(1f);
        annualChart.getXAxis().setTextColor(axisColor());
        annualChart.getXAxis().setTextSize(10f);
        annualChart.getXAxis().setAvoidFirstLastClipping(true);
        annualChart.getLegend().setEnabled(true);
        annualChart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        annualChart.getLegend().setTextColor(axisColor());
        annualChart.getLegend().setTextSize(11f);
        annualChart.getLegend().setWordWrapEnabled(true);
        annualChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE
        });
    }

    private void setupHonorChart() {
        honorChart.getDescription().setEnabled(false);
        honorChart.setDrawHoleEnabled(true);
        honorChart.setHoleColor(Color.TRANSPARENT);
        honorChart.setCenterTextColor(axisColor());
        honorChart.getLegend().setEnabled(false);
        honorChart.setDrawEntryLabels(false);
        honorChart.setRotationEnabled(false);
    }

    private void setupEngageChart() {
        engageChart.getDescription().setEnabled(false);
        engageChart.setDrawHoleEnabled(true);
        engageChart.setHoleColor(Color.TRANSPARENT);
        engageChart.setCenterTextColor(axisColor());
        engageChart.getLegend().setEnabled(false);
        engageChart.setDrawEntryLabels(false);
        engageChart.setRotationEnabled(false);
    }

    private void setupScatterChart(ScatterChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);
        chart.setScaleEnabled(true);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(ContextCompat.getColor(requireContext(), R.color.chart_grid));
        chart.getAxisLeft().setTextColor(axisColor());
        chart.getAxisLeft().setTextSize(10f);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(true);
        chart.getXAxis().setGridColor(ContextCompat.getColor(requireContext(), R.color.chart_grid));
        chart.getXAxis().setTextColor(axisColor());
        chart.getXAxis().setTextSize(10f);
        chart.getLegend().setEnabled(false);
    }

    private void setupClusterChart() {
        clusterChart.getDescription().setEnabled(false);
        clusterChart.setDrawGridBackground(false);
        clusterChart.setPinchZoom(true);
        clusterChart.setScaleEnabled(true);
        clusterChart.getAxisRight().setEnabled(false);
        clusterChart.getAxisLeft().setDrawGridLines(true);
        clusterChart.getAxisLeft().setGridColor(ContextCompat.getColor(requireContext(), R.color.chart_grid));
        clusterChart.getAxisLeft().setTextColor(axisColor());
        clusterChart.getAxisLeft().setTextSize(10f);
        clusterChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        clusterChart.getXAxis().setDrawGridLines(true);
        clusterChart.getXAxis().setGridColor(ContextCompat.getColor(requireContext(), R.color.chart_grid));
        clusterChart.getXAxis().setTextColor(axisColor());
        clusterChart.getXAxis().setTextSize(10f);
        clusterChart.getLegend().setEnabled(true);
        clusterChart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        clusterChart.getLegend().setTextColor(axisColor());
        clusterChart.getLegend().setTextSize(11f);
        clusterChart.getLegend().setWordWrapEnabled(true);
    }

    private void setupBarChart(BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().setTextColor(axisColor());
        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisLeft().setValueFormatter(numberFormatter());
        XAxis axis = chart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM);
        axis.setDrawGridLines(false);
        axis.setGranularity(1f);
        axis.setTextColor(axisColor());
        axis.setTextSize(10f);
        axis.setAvoidFirstLastClipping(true);
    }

    /** 水平柱图：X 轴是纵向类别轴，数值轴为左侧的水平轴。 */
    private void setupHorizontalBarChart(com.github.mikephil.charting.charts.HorizontalBarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
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
        chart.setExtraBottomOffset(6f);
    }

    private com.github.mikephil.charting.formatter.ValueFormatter numberFormatter() {
        return new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return UiUtils.number(requireContext(), value);
            }
        };
    }

    private int axisLabelCount(int maximum) {
        float width = getResources().getConfiguration().screenWidthDp;
        float fontScale = getResources().getConfiguration().fontScale;
        return Math.max(2, Math.min(maximum, (int) (width / (75f * Math.max(1f, fontScale)))));
    }

    private int axisColor() {
        return ContextCompat.getColor(requireContext(), R.color.text_secondary);
    }

    private void setupSimilarSpinner() {
        similarSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                DataModels.Dataset dataset = DataRepository.get().current();
                if (dataset == null || dataset.data == null || position < 0) return;
                dataset = selectedDataset(dataset);
                if (position >= dataset.data.videos.size()) return;
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
        if (getView() != null) render();
    }

    @Override
    public void onDestroyView() {
        DataRepository.get().removeListener(this);
        rankAdapter = null;
        super.onDestroyView();
    }

    private void render() {
        DataModels.Dataset dataset = DataRepository.get().current();
        if (getView() == null) return;
        if (dataset == null || dataset.site != MainActivity.currentSite() || dataset.data == null) {
            ((TextView) requireView().findViewById(R.id.insightSelection)).setText("正在读取分析数据…");
            DataModels.RawData empty = new DataModels.RawData();
            dataset = new DataModels.Dataset(MainActivity.currentSite(), empty, java.util.Collections.emptyList(), true);
        }
        dataset = selectedDataset(dataset);
        ((TextView) requireView().findViewById(R.id.insightSelection)).setText(
                ((MainActivity) requireActivity()).filter().summary() + " · " + dataset.data.videos.size() + " 个视频");
        requireView().findViewById(R.id.insightSelection).setOnClickListener(v -> ((MainActivity) requireActivity()).showFilters());
        renderRanking(dataset);
        renderInsights(dataset);
        renderAnnual(dataset);
        renderClock(dataset);
        renderWeekday(dataset);
        renderMonth(dataset);
        renderGapBands(dataset);
        renderHonors(dataset);
        renderDuration(dataset);
        renderHeat(dataset);
        renderWeibo(dataset);
        renderTags(dataset);
        renderClusters(dataset);
        renderStructure(dataset);
        renderSimilar(dataset);
    }

    private DataModels.Dataset selectedDataset(DataModels.Dataset source) {
        DataModels.RawData data = new DataModels.RawData();
        data.videos = ((MainActivity) requireActivity()).selectedVideos(source.data);
        Map<String, Double> counts = new HashMap<>();
        for (DataModels.Video video : data.videos) for (DataModels.Meme meme : video.memes)
            counts.merge(meme.content, meme.count, Double::sum);
        for (Map.Entry<String, Double> entry : counts.entrySet()) {
            DataModels.Meme meme = new DataModels.Meme(); meme.content = entry.getKey(); meme.count = entry.getValue();
            data.top_memes.add(meme);
        }
        data.top_memes.sort((a, b) -> Double.compare(b.count, a.count));
        return new DataModels.Dataset(source.site, data, source.quiz, source.fromCache);
    }

    private void renderRanking(DataModels.Dataset dataset) {
        List<DataModels.Video> sorted = new ArrayList<>(dataset.data.videos);
        sorted.sort((a, b) -> Double.compare(b.metric(metric), a.metric(metric)));
        List<DataModels.Video> top = sorted.subList(0, Math.min(10, sorted.size()));
        // 复用同一个适配器实例，保留 ViewHolder 复用池与滚动位置（旧实现每次新建适配器）。
        if (rankAdapter == null) {
            rankAdapter = new RankingAdapter(requireContext(), new ArrayList<>(), metric,
                    video -> VideoDetailActivity.open(this, video.bvid));
            rankList.setAdapter(rankAdapter);
        }
        rankAdapter.submit(new ArrayList<>(top), metric);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = top.size() - 1; i >= 0; i--) {
            DataModels.Video video = top.get(i);
            entries.add(new BarEntry(labels.size(), (float) video.metric(metric), video.bvid));
            labels.add("#" + (i + 1));
        }
        BarDataSet set = new BarDataSet(entries, "榜值");
        set.setColor(ContextCompat.getColor(requireContext(), R.color.primary));
        set.setDrawValues(false);
        rankChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        rankChart.getXAxis().setLabelCount(Math.max(2, labels.size()), false);
        if (entries.isEmpty()) { rankChart.clear(); rankChart.setNoDataText("暂无符合条件的视频"); return; }
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
        TreeMap<String, List<LocalDate>> dates = new TreeMap<>();
        for (DataModels.Video video : data.videos) {
            String type = video.type == null || video.type.isEmpty() ? "未标注" : video.type;
            types.put(type, types.getOrDefault(type, 0) + 1);
            accounts.put(video.account, accounts.getOrDefault(video.account, 0) + 1);
            for (DataModels.Tag tag : video.tags) tags.put(tag.name, tags.getOrDefault(tag.name, 0) + 1);
            LocalDate date = parseDate(video);
            if (date != null) {
                weekdays.merge(date.getDayOfWeek().getValue(), 1, Integer::sum);
                dates.computeIfAbsent(video.account, key -> new ArrayList<>()).add(date);
            }
            interaction += video.interactionRate();
        }
        int count = data.videos.size();
        addInsight(insightContainer, "平均互动率", count == 0 ? "暂无数据" : String.format(java.util.Locale.CHINA, "%.2f%%", interaction / count));
        addInsight(insightContainer, "可阅读字幕覆盖",
                (int) data.videos.stream().filter(v -> v.subtitle_available_lines > 0).count() + " / " + count + " 个视频");
        long gap = 0; int intervals = 0;
        for (List<LocalDate> accountDates : dates.values()) {
            java.util.Collections.sort(accountDates);
            for (int i = 1; i < accountDates.size(); i++) {
                gap += java.time.temporal.ChronoUnit.DAYS.between(accountDates.get(i - 1), accountDates.get(i)); intervals++;
            }
        }
        addInsight(insightContainer, "平均更新间隔（同账号）", intervals == 0 ? "暂无足够投稿" : String.format(java.util.Locale.CHINA, "%.1f 天", gap / (double) intervals));
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
        if (bars.isEmpty()) { annualChart.clear(); annualChart.setNoDataText("暂无投稿数据"); return; }
        BarDataSet barSet = new BarDataSet(bars, "年度投稿");
        barSet.setColor(ContextCompat.getColor(requireContext(), R.color.blue));
        barSet.setDrawValues(false);
        LineDataSet lineSet = new LineDataSet(line, "累计视频");
        lineSet.setColor(ContextCompat.getColor(requireContext(), R.color.mint));
        lineSet.setLineWidth(2.6f);
        lineSet.setDrawCircles(false);
        lineSet.setDrawValues(false);

        CombinedData data = new CombinedData();
        data.setData(new BarData(barSet));
        data.setData(new LineData(lineSet));
        annualChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        annualChart.getXAxis().setLabelCount(axisLabelCount(Math.min(8, labels.size())), false);
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
        set.setColor(ContextCompat.getColor(requireContext(), R.color.blue));
        set.setDrawValues(false);
        clockChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        clockChart.getXAxis().setLabelCount(axisLabelCount(8), false);
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
        set.setColor(ContextCompat.getColor(requireContext(), R.color.orange));
        set.setDrawValues(false);
        weekdayChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        weekdayChart.getXAxis().setLabelCount(axisLabelCount(7), false);
        weekdayChart.setData(new BarData(set));
        weekdayChart.invalidate();
    }

    /** 月度分布：1-12 月的投稿热度，对应网页端「月度分布」模块。 */
    private void renderMonth(DataModels.Dataset dataset) {
        int[] months = new int[12];
        for (DataModels.Video video : dataset.data.videos) {
            LocalDate date = parseDate(video);
            if (date != null) months[date.getMonthValue() - 1]++;
        }
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < months.length; i++) {
            entries.add(new BarEntry(i, months[i]));
            labels.add((i + 1) + "月");
        }
        BarDataSet set = new BarDataSet(entries, "投稿数量");
        set.setColor(ContextCompat.getColor(requireContext(), R.color.purple));
        set.setDrawValues(false);
        monthChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        monthChart.getXAxis().setLabelCount(axisLabelCount(6), false);
        monthChart.setData(new BarData(set));
        monthChart.invalidate();
    }

    /** 投稿间隔分层：把相邻投稿间隔归纳成四档，对应网页端「间隔等级分布」。 */
    private void renderGapBands(DataModels.Dataset dataset) {
        TreeMap<String, Integer> bands = new TreeMap<>();
        bands.put("≤7天", 0);
        bands.put("≤30天", 0);
        bands.put("≤90天", 0);
        bands.put(">90天", 0);
        Map<String, LocalDate> lastDate = new HashMap<>();
        List<DataModels.Video> ordered = sortByDateAscending(dataset.data.videos);
        for (DataModels.Video video : ordered) {
            LocalDate date = parseDate(video);
            if (date == null) continue;
            String account = video.account == null ? "" : video.account;
            LocalDate previous = lastDate.get(account);
            if (previous != null) {
                long days = ChronoUnit.DAYS.between(previous, date);
                String band = days <= 7 ? "≤7天" : days <= 30 ? "≤30天" : days <= 90 ? "≤90天" : ">90天";
                bands.merge(band, 1, Integer::sum);
            }
            lastDate.put(account, date);
        }
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Integer> entry : bands.entrySet()) {
            entries.add(new BarEntry(index, entry.getValue()));
            labels.add(entry.getKey() + " · " + entry.getValue());
            index++;
        }
        BarDataSet set = new BarDataSet(entries, "间隔数量");
        set.setColor(ContextCompat.getColor(requireContext(), R.color.mint));
        set.setDrawValues(false);
        gapChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        gapChart.getXAxis().setLabelCount(Math.max(2, labels.size()), false);
        gapChart.getAxisLeft().setValueFormatter(numberFormatter());
        gapChart.setData(new BarData(set));
        gapChart.invalidate();
    }

    /** 时长 × 点赞率：按 2 分钟以内的短、中、长、超长区间统计平均点赞率。 */
    private void renderDuration(DataModels.Dataset dataset) {
        String[] names = {"≤2分", "2-10分", "10-30分", ">30分"};
        double[] likeRate = new double[4];
        int[] counts = new int[4];
        for (DataModels.Video video : dataset.data.videos) {
            if (video.view <= 0) continue;
            double seconds = video.duration_seconds;
            int bucket = seconds <= 120 ? 0 : seconds <= 600 ? 1 : seconds <= 1800 ? 2 : 3;
            likeRate[bucket] += video.like / video.view;
            counts[bucket]++;
        }
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            double average = counts[i] == 0 ? 0 : likeRate[i] / counts[i] * 100;
            entries.add(new BarEntry(i, (float) average));
            labels.add(names[i] + " · " + counts[i]);
        }
        BarDataSet set = new BarDataSet(entries, "平均点赞率");
        set.setColor(ContextCompat.getColor(requireContext(), R.color.pink));
        set.setDrawValues(false);
        durationChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        durationChart.getXAxis().setLabelCount(Math.max(2, labels.size()), false);
        durationChart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return String.format(java.util.Locale.CHINA, "%.1f%%", value);
            }
        });
        durationChart.setData(new BarData(set));
        durationChart.invalidate();
    }

    /** 热度分区：互动构成、年度播放趋势、类型播放贡献与播放 × 点赞率散点。 */
    private void renderHeat(DataModels.Dataset dataset) {
        double[] sums = new double[6];
        for (DataModels.Video video : dataset.data.videos) {
            sums[0] += video.like; sums[1] += video.coin; sums[2] += video.favorite;
            sums[3] += video.share; sums[4] += video.reply; sums[5] += video.danmaku;
        }
        String[] names = {"点赞", "投币", "收藏", "分享", "评论", "弹幕"};
        List<PieEntry> pieEntries = new ArrayList<>();
        engageLegend.removeAllViews();
        double total = 0;
        for (double value : sums) total += value;
        int colorIndex = 0;
        for (int i = 0; i < names.length; i++) {
            pieEntries.add(new PieEntry((float) sums[i], names[i]));
            double percent = total <= 0 ? 0 : sums[i] / total * 100;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int spacing = (int) (8 * getResources().getDisplayMetrics().density);
            row.setPadding(spacing, spacing / 2, spacing, spacing / 2);
            View swatch = new View(requireContext());
            swatch.setBackgroundColor(ContextCompat.getColor(requireContext(), CHART_COLORS[colorIndex++ % CHART_COLORS.length]));
            row.addView(swatch, new LinearLayout.LayoutParams(spacing * 2, spacing * 2));
            TextView text = new TextView(requireContext());
            text.setText(names[i] + " · " + UiUtils.number(requireContext(), sums[i])
                    + String.format(java.util.Locale.CHINA, "（%.1f%%）", percent));
            text.setTextColor(axisColor());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            params.setMarginStart(spacing);
            row.addView(text, params);
            engageLegend.addView(row);
        }
        if (total <= 0) {
            engageChart.setCenterText("暂无互动数据");
            engageChart.setData(null);
        } else {
            engageChart.setCenterText("互动\n" + UiUtils.number(requireContext(), total));
            PieDataSet set = new PieDataSet(pieEntries, "");
            set.setColors(CHART_COLORS, requireContext());
            set.setSliceSpace(2f);
            PieData data = new PieData(set);
            data.setDrawValues(false);
            engageChart.setData(data);
        }
        engageChart.invalidate();

        // 年度播放趋势
        TreeMap<Integer, Double> yearViews = new TreeMap<>();
        for (DataModels.Video video : dataset.data.videos) {
            LocalDate date = parseDate(video);
            if (date != null) yearViews.merge(date.getYear(), video.view, Double::sum);
        }
        List<BarEntry> yearEntries = new ArrayList<>();
        List<String> yearLabels = new ArrayList<>();
        int yearIndex = 0;
        for (Map.Entry<Integer, Double> entry : yearViews.entrySet()) {
            yearEntries.add(new BarEntry(yearIndex++, entry.getValue().floatValue()));
            yearLabels.add(String.valueOf(entry.getKey()));
        }
        BarDataSet yearSet = new BarDataSet(yearEntries, "年度播放");
        yearSet.setColor(ContextCompat.getColor(requireContext(), R.color.blue));
        yearSet.setDrawValues(false);
        yearViewsChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(yearLabels));
        yearViewsChart.getXAxis().setLabelCount(axisLabelCount(Math.min(8, Math.max(2, yearLabels.size()))), false);
        yearViewsChart.setData(new BarData(yearSet));
        yearViewsChart.invalidate();

        // 类型播放贡献
        Map<String, Double> typeViews = new LinkedHashMap<>();
        for (DataModels.Video video : dataset.data.videos) {
            String type = video.type == null || video.type.isEmpty() ? "未标注" : video.type;
            typeViews.merge(type, video.view, Double::sum);
        }
        List<Map.Entry<String, Double>> rankedTypes = new ArrayList<>(typeViews.entrySet());
        rankedTypes.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<BarEntry> typeEntries = new ArrayList<>();
        List<String> typeLabels = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Double> entry : rankedTypes) {
            if (shown++ >= 6) break;
            typeEntries.add(new BarEntry(typeLabels.size(), entry.getValue().floatValue()));
            typeLabels.add(entry.getKey() + " · " + UiUtils.number(requireContext(), entry.getValue()));
        }
        BarDataSet typeSet = new BarDataSet(typeEntries, "类型播放");
        typeSet.setColor(ContextCompat.getColor(requireContext(), R.color.orange));
        typeSet.setDrawValues(false);
        typeViewsChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(typeLabels));
        typeViewsChart.getXAxis().setLabelCount(Math.max(2, typeLabels.size()), false);
        typeViewsChart.getAxisLeft().setValueFormatter(numberFormatter());
        typeViewsChart.setData(new BarData(typeSet));
        typeViewsChart.invalidate();

        // 播放量 × 点赞率散点（横轴为对数播放量，纵轴为点赞率）
        List<Entry> points = new ArrayList<>();
        for (DataModels.Video video : dataset.data.videos) {
            if (video.view <= 0) continue;
            double logViews = Math.log10(Math.max(1, video.view));
            double likeRate = video.like / video.view * 100;
            points.add(new Entry((float) logViews, (float) likeRate));
        }
        if (points.isEmpty()) {
            engageScatterChart.clear();
            engageScatterChart.setNoDataText("暂无足够的播放数据");
        } else {
            ScatterDataSet scatterSet = new ScatterDataSet(points, "视频");
            scatterSet.setColor(ContextCompat.getColor(requireContext(), R.color.purple));
            scatterSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
            scatterSet.setScatterShapeSize(9f);
            scatterSet.setDrawValues(false);
            ScatterData scatterData = new ScatterData(scatterSet);
            engageScatterChart.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) {
                    return UiUtils.number(requireContext(), Math.pow(10, value));
                }
            });
            engageScatterChart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) {
                    return String.format(java.util.Locale.CHINA, "%.1f%%", value);
                }
            });
            engageScatterChart.setData(scatterData);
            engageScatterChart.invalidate();
        }
    }

    private void renderHonors(DataModels.Dataset dataset) {
        honorLegend.removeAllViews();
        honorTimeline.removeAllViews();
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
        // 荣誉时间线：按时间顺序列出最近获得站内认可的视频（对应网页端里程碑时间线）。
        List<DataModels.Video> withHonors = new ArrayList<>();
        for (DataModels.Video video : dataset.data.videos) {
            if (!video.honors.isEmpty()) withHonors.add(video);
        }
        withHonors.sort((a, b) -> b.date.compareTo(a.date));
        int timelineCount = 0;
        for (DataModels.Video video : withHonors) {
            if (timelineCount++ >= 6) break;
            StringBuilder detail = new StringBuilder();
            for (DataModels.Honor honor : video.honors) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(honor.desc == null || honor.desc.isEmpty() ? "站内荣誉" : honor.desc);
            }
            addInsight(honorTimeline, video.date + " · " + video.title, detail.toString());
        }
        List<PieEntry> entries = new ArrayList<>();
        int colorIndex = 0;
        for (Map.Entry<String, Integer> entry : honors.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int spacing = (int) (8 * getResources().getDisplayMetrics().density);
            row.setPadding(spacing, spacing / 2, spacing, spacing / 2);
            View swatch = new View(requireContext());
            swatch.setBackgroundColor(ContextCompat.getColor(requireContext(), CHART_COLORS[colorIndex++ % CHART_COLORS.length]));
            row.addView(swatch, new LinearLayout.LayoutParams(spacing * 2, spacing * 2));
            TextView text = new TextView(requireContext());
            text.setText(entry.getKey() + " · " + entry.getValue());
            text.setTextColor(axisColor());
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            textParams.setMarginStart(spacing);
            row.addView(text, textParams);
            honorLegend.addView(row);
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
            chip.setOnClickListener(v -> ((MainActivity) requireActivity()).openVideos(null, entry.getKey()));
            float scale = max == min ? 1f : (entry.getValue() - min) * 1f / (max - min);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12 + Math.max(0, scale) * 5);
            chip.setMaxWidth((int) (280 * getResources().getDisplayMetrics().density));
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
                    "共现 " + entry.getValue() + " 次 · PMI " + String.format(java.util.Locale.CHINA, "%.2f", pmi));
            shown++;
        }
    }

    private void renderClusters(DataModels.Dataset dataset) {
        List<DataModels.Video> videos = dataset.data.videos;
        if (videos.isEmpty()) { clusterChart.clear(); clusterChart.setNoDataText("暂无符合条件的视频"); return; }
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
        int[] colors = {ContextCompat.getColor(requireContext(), R.color.blue), ContextCompat.getColor(requireContext(), R.color.mint),
                ContextCompat.getColor(requireContext(), R.color.chart_accent_pink), ContextCompat.getColor(requireContext(), R.color.orange)};
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
        clusterChart.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override public String getFormattedValue(float value) { return String.format(java.util.Locale.CHINA, "%.1f", value); }
        });
        clusterChart.getAxisLeft().resetAxisMinimum();
        clusterChart.getAxisLeft().resetAxisMaximum();
        clusterChart.setData(data);
        clusterChart.invalidate();
    }

    /**
     * 微博动态：数据来自 m.weibo.cn 公开接口，按时间倒序列出。
     *
     * 网页端把微博作为独立模块；移动端这里做成紧凑卡片，正文默认截断，
     * 点击卡片用系统浏览器打开微博原文。
     */
    private void renderWeibo(DataModels.Dataset dataset) {
        weiboList.removeAllViews();
        DataModels.RawData data = dataset.data;
        Map<String, DataModels.WeiboAccount> accounts = data.weibo;
        if (accounts == null || accounts.isEmpty()) {
            weiboSummary.setText("暂未收录微博动态");
            return;
        }
        List<DataModels.WeiboPost> posts = new ArrayList<>();
        for (DataModels.WeiboAccount account : accounts.values()) {
            if (account == null || account.posts == null) continue;
            posts.addAll(account.posts);
        }
        if (posts.isEmpty()) {
            weiboSummary.setText("暂未收录微博动态");
            return;
        }
        posts.sort((a, b) -> b.date.compareTo(a.date));
        weiboSummary.setText("共 " + posts.size() + " 条近期动态 · 点击查看原文");
        int shown = 0;
        for (DataModels.WeiboPost post : posts) {
            if (shown++ >= 20) break;
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_insight, weiboList, false);
            TextView title = card.findViewById(R.id.title);
            TextView body = card.findViewById(R.id.body);
            title.setText(post.date + (post.region.isEmpty() ? "" : " · " + post.region));
            body.setText(post.preview(140) + "\n赞 " + UiUtils.number(requireContext(), post.likes)
                    + " · 评论 " + UiUtils.number(requireContext(), post.comments)
                    + " · 转发 " + UiUtils.number(requireContext(), post.reposts));
            card.setOnClickListener(v -> {
                if (!post.url.isEmpty()) {
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW, android.net.Uri.parse(post.url));
                    try { startActivity(intent); } catch (Exception ignored) {}
                }
            });
            weiboList.addView(card);
        }
    }

    /** 类型 × 年代结构：把每个「类型 + 年代」组合的投稿量做成横向条形图。 */
    private void renderStructure(DataModels.Dataset dataset) {
        Map<String, Integer> structure = new LinkedHashMap<>();
        for (DataModels.Video video : dataset.data.videos) {
            LocalDate date = parseDate(video);
            String type = video.type == null || video.type.isEmpty() ? "未标注" : video.type;
            String decade = date == null ? "未知" : (date.getYear() / 5 * 5) + "–" + (date.getYear() / 5 * 5 + 4);
            structure.merge(type + " · " + decade, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(structure.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : ranked) {
            if (shown++ >= 8) break;
            entries.add(new BarEntry(labels.size(), entry.getValue()));
            labels.add(entry.getKey() + " · " + entry.getValue());
        }
        if (entries.isEmpty()) {
            structureChart.clear();
            structureChart.setNoDataText("暂无结构数据");
            return;
        }
        BarDataSet set = new BarDataSet(entries, "投稿数量");
        set.setColor(ContextCompat.getColor(requireContext(), R.color.primary));
        set.setDrawValues(false);
        structureChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        structureChart.getXAxis().setLabelCount(Math.max(2, labels.size()), false);
        structureChart.getXAxis().setTextSize(9f);
        structureChart.getAxisLeft().setValueFormatter(numberFormatter());
        structureChart.setData(new BarData(set));
        structureChart.invalidate();
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
        if (titles.isEmpty()) { similarList.removeAllViews(); selectedSimilar = ""; return; }
        similarSource.setSelection(selectedPosition, false);
        selectedSimilar = videos.get(selectedPosition).bvid;
        renderSimilarItems(dataset, videos.get(selectedPosition));
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
            body.setText(String.format(java.util.Locale.CHINA, "相似度 %.0f%% · %s · %s · %s 播放",
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

    private List<DataModels.Video> sortByDateAscending(List<DataModels.Video> source) {
        List<DataModels.Video> list = new ArrayList<>(source);
        list.sort((a, b) -> (a.pubdate_iso == null ? a.date : a.pubdate_iso)
                .compareTo(b.pubdate_iso == null ? b.date : b.pubdate_iso));
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

