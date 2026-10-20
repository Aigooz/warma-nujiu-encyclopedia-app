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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment implements DataRepository.Listener {
    private SwipeRefreshLayout refresh;
    private View progress;
    private View error;
    private View content;
    private LinearLayout profileContainer;
    private LinearLayout statGrid;
    private BarChart yearlyChart;
    private ChipGroup typeChips;
    private RecyclerView latestList;
    private ChipGroup memeChips;
    private TextView dataStatus;
    private TextView selectionSummary;
    private TextView latestTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refresh = view.findViewById(R.id.refresh);
        progress = view.findViewById(R.id.progress);
        error = view.findViewById(R.id.error);
        content = view.findViewById(R.id.content);
        profileContainer = view.findViewById(R.id.profileContainer);
        statGrid = view.findViewById(R.id.statGrid);
        yearlyChart = view.findViewById(R.id.yearlyChart);
        typeChips = view.findViewById(R.id.typeChips);
        latestList = view.findViewById(R.id.latestList);
        memeChips = view.findViewById(R.id.memeChips);
        dataStatus = view.findViewById(R.id.dataStatus);
        selectionSummary = view.findViewById(R.id.selectionSummary);
        latestTitle = view.findViewById(R.id.latestTitle);
        view.findViewById(R.id.browseVideos).setOnClickListener(v -> ((MainActivity) requireActivity()).openVideos(null, null));
        selectionSummary.setOnClickListener(v -> ((MainActivity) requireActivity()).showFilters());
        error.setOnClickListener(v -> DataRepository.get().load(requireContext().getApplicationContext(), MainActivity.currentSite()));
        refresh.setOnRefreshListener(() -> DataRepository.get().refresh(requireContext().getApplicationContext(), MainActivity.currentSite()));
        latestList.setLayoutManager(new LinearLayoutManager(requireContext()));
        latestList.setNestedScrollingEnabled(false);
        setupChart(yearlyChart);
        DataRepository.get().addListener(this);
        render();
    }

    private void setupChart(BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        XAxis axis = chart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM);
        axis.setDrawGridLines(false);
        axis.setGranularity(1f);
        axis.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
    }

    @Override
    public void onDataChanged() {
        if (isAdded()) {
            if (refresh != null) refresh.setRefreshing(DataRepository.get().isLoading(MainActivity.currentSite()));
            render();
        }
    }

    @Override
    public void onDestroyView() {
        DataRepository.get().removeListener(this);
        super.onDestroyView();
    }

    private void render() {
        DataModels.Dataset dataset = DataRepository.get().current();
        if (!isAdded() || dataset == null || dataset.site != MainActivity.currentSite() || dataset.data == null) {
            boolean failed = !DataRepository.get().error(MainActivity.currentSite()).isEmpty();
            if (progress != null) progress.setVisibility(failed ? View.GONE : View.VISIBLE);
            if (content != null) content.setVisibility(View.GONE);
            if (error != null) { error.setVisibility(failed ? View.VISIBLE : View.GONE); ((TextView) error).setText("数据暂时无法加载，点击重试"); }
            return;
        }
        DataModels.RawData data = dataset.data;
        progress.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        MainActivity host = (MainActivity) requireActivity();
        selectionSummary.setText(host.filter().summary() + "  ·  " + host.selectedVideos(data).size() + " 个视频");
        String syncError = DataRepository.get().error(dataset.site);
        dataStatus.setText((DataRepository.get().isLoading(dataset.site) ? "正在同步" : !syncError.isEmpty() ? "离线可用 · 同步失败" : dataset.fromCache ? "离线快照" : "已同步")
                + " · " + data.docx_version);
        renderProfiles(data);
        DataModels.RawData selection = new DataModels.RawData();
        selection.videos = host.selectedVideos(data);
        for (DataModels.Video video : selection.videos) {
            selection.total_view += (long) video.view; selection.total_like += (long) video.like;
            selection.total_danmaku += (long) video.danmaku; selection.total_coin += (long) video.coin;
            selection.total_favorite += (long) video.favorite; selection.total_reply += (long) video.reply;
            selection.total_share += (long) video.share; selection.total_sub_lines += video.sub_lines;
        }
        renderStats(selection);
        renderYearly(data);
        renderTypes(selection);
        renderLatest(selection);
        renderMemes(selection);
    }

    private void renderProfiles(DataModels.RawData data) {
        profileContainer.removeAllViews();
        int shown = 0;
        for (DataModels.Profile profile : data.profiles.values()) {
            if (shown++ >= 2) break;
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_profile, profileContainer, false);
            TextView name = card.findViewById(R.id.name);
            TextView fans = card.findViewById(R.id.fans);
            TextView sign = card.findViewById(R.id.sign);
            name.setText(profile.name);
            fans.setText(UiUtils.number(requireContext(), profile.fans) + " 粉丝 · Lv." + profile.level);
            sign.setText(profile.sign);
            card.setOnClickListener(v -> {
                try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://space.bilibili.com/" + profile.mid))); }
                catch (android.content.ActivityNotFoundException ignored) {
                    android.widget.Toast.makeText(requireContext(), "没有可用的浏览器", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            String avatar = profile.face == null || profile.face.isEmpty() ? profile.avatar : profile.face;
            if (avatar != null) avatar = avatar.replace("http://", "https://");
            Glide.with(this).load(avatar).circleCrop().placeholder(R.drawable.ic_home).into((com.google.android.material.imageview.ShapeableImageView) card.findViewById(R.id.avatar));
            profileContainer.addView(card);
        }
    }

    private void renderStats(DataModels.RawData data) {
        statGrid.removeAllViews();
        addStatRow(data.videos.size(), "视频总数", data.total_sub_lines, "字幕行数");
        addStatRow(data.total_view, "总播放", data.total_like, "总点赞");
        addStatRow(data.total_danmaku, "总弹幕", data.total_coin, "总投币");
        addStatRow(data.total_favorite, "总收藏", data.total_reply, "总评论");
    }

    private void addStatRow(double leftValue, String leftLabel, double rightValue, String rightLabel) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        statGrid.addView(row, statGrid.getChildCount());
        row.addView(statCard(leftValue, leftLabel));
        row.addView(statCard(rightValue, rightLabel));
    }

    private View statCard(double value, String label) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_stat_card, statGrid, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int margin = (int) (getResources().getDisplayMetrics().density * 4);
        params.setMargins(margin, margin, margin, 0);
        card.setLayoutParams(params);
        TextView title = card.findViewById(R.id.label);
        TextView valueText = card.findViewById(R.id.value);
        title.setText(label);
        valueText.setText(UiUtils.number(requireContext(), value));
        int[] accents = {R.color.primary, R.color.pink, R.color.blue, R.color.orange};
        valueText.setTextColor(ContextCompat.getColor(requireContext(), accents[(statGrid.getChildCount() - 1) % accents.length]));
        return card;
    }

    private void renderYearly(DataModels.RawData data) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Long> entry : data.yearly_danmaku.entrySet()) {
            entries.add(new BarEntry(index++, entry.getValue().floatValue()));
            labels.add(entry.getKey());
        }
        if (entries.isEmpty()) { yearlyChart.clear(); yearlyChart.setNoDataText("暂无年度弹幕数据"); return; }
        BarDataSet set = new BarDataSet(entries, "弹幕");
        set.setColor(ContextCompat.getColor(requireContext(), R.color.blue));
        set.setDrawValues(false);
        yearlyChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        yearlyChart.getXAxis().setLabelCount(Math.min(6, labels.size()), false);
        yearlyChart.setData(new BarData(set));
        yearlyChart.invalidate();
    }

    private void renderTypes(DataModels.RawData data) {
        typeChips.removeAllViews();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DataModels.Video video : data.videos) {
            String type = video.type == null || video.type.isEmpty() ? "未标注" : video.type;
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> entry : sorted) {
            Chip chip = new Chip(requireContext());
            chip.setText(entry.getKey() + " · " + entry.getValue());
            chip.setCheckable(false);
            chip.setOnClickListener(v -> ((MainActivity) requireActivity()).openVideos(entry.getKey(), ""));
            typeChips.addView(chip);
        }
    }

    private void renderLatest(DataModels.RawData data) {
        List<DataModels.Video> latest = new ArrayList<>();
        for (DataModels.Video video : data.videos) latest.add(video);
        latest.sort((a, b) -> b.date.compareTo(a.date));
        latestTitle.setText(latest.isEmpty() ? "暂无符合条件的视频" : "最新投稿 · " + latest.get(0).date);
        latestList.setAdapter(new VideoAdapter(requireContext(), latest.subList(0, Math.min(5, latest.size())), video -> VideoDetailActivity.open(this, video.bvid)));
    }

    private void renderMemes(DataModels.RawData data) {
        memeChips.removeAllViews();
        Map<String, Double> counts = new HashMap<>();
        for (DataModels.Video video : data.videos) for (DataModels.Meme meme : video.memes)
            counts.merge(meme.content, meme.count, Double::sum);
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int count = 0;
        for (Map.Entry<String, Double> meme : sorted) {
            if (count++ >= 16) break;
            Chip chip = new Chip(requireContext());
            chip.setText(meme.getKey() + " · " + UiUtils.number(requireContext(), meme.getValue()));
            chip.setCheckable(false);
            chip.setMaxWidth((int) (280 * getResources().getDisplayMetrics().density));
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.setOnClickListener(v -> {
                MainActivity host = (MainActivity) requireActivity();
                host.filter().type = "";
                host.openVideos(null, meme.getKey());
            });
            memeChips.addView(chip);
        }
    }
}
