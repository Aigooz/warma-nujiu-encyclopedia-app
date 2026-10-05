package com.aigooz.encyclopediaapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VideosFragment extends Fragment implements DataRepository.Listener {
    private RecyclerView videoList;
    private SwipeRefreshLayout refresh;
    private EditText searchInput;
    private ChipGroup accountChips;
    private ChipGroup sortChips;
    private ChipGroup pronChips;
    private ChipGroup regionChips;
    private String account = "all";
    private String sort = "date";
    private String pron = "all";
    private String region = "all";
    private String query = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_videos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        videoList = view.findViewById(R.id.videoList);
        refresh = view.findViewById(R.id.refresh);
        searchInput = view.findViewById(R.id.searchInput);
        accountChips = view.findViewById(R.id.accountChips);
        sortChips = view.findViewById(R.id.sortChips);
        pronChips = view.findViewById(R.id.pronChips);
        regionChips = view.findViewById(R.id.regionChips);
        videoList.setLayoutManager(new LinearLayoutManager(requireContext()));
        refresh.setOnRefreshListener(() -> DataRepository.get().refresh(requireContext().getApplicationContext(), MainActivity.currentSite()));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { query = s.toString(); render(); }
        });
        buildAccountChips();
        buildSortChips();
        buildPronChips();
        buildRegionChips();
        DataRepository.get().addListener(this);
        render();
    }

    private void buildAccountChips() {
        accountChips.removeAllViews();
        addSelectableChip(accountChips, "全部", "all", account, id -> { account = id; render(); });
        addSelectableChip(accountChips, "主号", "main", account, id -> { account = id; render(); });
        addSelectableChip(accountChips, "小号", "sub", account, id -> { account = id; render(); });
    }

    private void buildSortChips() {
        sortChips.removeAllViews();
        addSelectableChip(sortChips, "最新", "date", sort, id -> { sort = id; render(); });
        addSelectableChip(sortChips, "播放", "view", sort, id -> { sort = id; render(); });
        addSelectableChip(sortChips, "点赞", "like", sort, id -> { sort = id; render(); });
        addSelectableChip(sortChips, "弹幕", "danmaku", sort, id -> { sort = id; render(); });
        addSelectableChip(sortChips, "互动", "interaction", sort, id -> { sort = id; render(); });
    }

    private void buildPronChips() {
        pronChips.removeAllViews();
        addSelectableChip(pronChips, "全部发音", "all", pron, id -> { pron = id; render(); });
        addSelectableChip(pronChips, "翻唱·唱歌", "song", pron, id -> { pron = id; render(); });
        addSelectableChip(pronChips, "配音", "dub", pron, id -> { pron = id; render(); });
        addSelectableChip(pronChips, "口音·方言", "accent", pron, id -> { pron = id; render(); });
        addSelectableChip(pronChips, "普通话", "mandarin", pron, id -> { pron = id; render(); });
    }

    private void buildRegionChips() {
        regionChips.removeAllViews();
        addSelectableChip(regionChips, "全部地区", "all", region, id -> { region = id; render(); });
        addSelectableChip(regionChips, "湖南·长沙", "hunan", region, id -> { region = id; render(); });
        addSelectableChip(regionChips, "广东", "guangdong", region, id -> { region = id; render(); });
        addSelectableChip(regionChips, "海南", "hainan", region, id -> { region = id; render(); });
        addSelectableChip(regionChips, "四川·成都", "sichuan", region, id -> { region = id; render(); });
        addSelectableChip(regionChips, "上海", "shanghai", region, id -> { region = id; render(); });
        addSelectableChip(regionChips, "其他地区", "provinces", region, id -> { region = id; render(); });
        addSelectableChip(regionChips, "海外", "overseas", region, id -> { region = id; render(); });
        addSelectableChip(regionChips, "未提及", "none", region, id -> { region = id; render(); });
    }

    private void addSelectableChip(ChipGroup group, String text, String id, String selected, java.util.function.Consumer<String> onSelect) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setId(View.generateViewId());
        chip.setChecked(id.equals(selected));
        chip.setOnClickListener(v -> onSelect.accept(id));
        group.addView(chip);
    }

    @Override
    public void onDataChanged() {
        if (isAdded()) {
            refresh.setRefreshing(false);
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
        if (!isAdded() || dataset == null || dataset.site != MainActivity.currentSite() || dataset.data == null) return;
        List<DataModels.Video> filtered = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (DataModels.Video video : dataset.data.videos) {
            boolean accountMatch = account.equals("all")
                    || (account.equals("main") && video.account.contains("主号"))
                    || (account.equals("sub") && video.account.contains("小号"));
            String tagText = "";
            StringBuilder builder = new StringBuilder();
            for (DataModels.Tag tag : video.tags) builder.append(tag.name).append(' ');
            tagText = builder.toString().toLowerCase(Locale.ROOT);
            boolean queryMatch = q.isEmpty()
                    || video.title.toLowerCase(Locale.ROOT).contains(q)
                    || video.bvid.toLowerCase(Locale.ROOT).contains(q)
                    || video.type.toLowerCase(Locale.ROOT).contains(q)
                    || tagText.contains(q);
            boolean pronMatch = pron.equals("all") || matchesPronunciation(video, pron);
            boolean regionMatch = region.equals("all") || matchesRegion(video, region);
            if (accountMatch && queryMatch && pronMatch && regionMatch) filtered.add(video);
        }
        filtered.sort((a, b) -> {
            switch (sort) {
                case "view": return Double.compare(b.view, a.view);
                case "like": return Double.compare(b.like, a.like);
                case "danmaku": return Double.compare(b.danmaku, a.danmaku);
                case "interaction": return Double.compare(b.interactionRate(), a.interactionRate());
                default: return b.date.compareTo(a.date);
            }
        });
        videoList.setAdapter(new VideoAdapter(requireContext(), filtered, video -> VideoDetailActivity.open(this, video.bvid)));
    }

    private String videoText(DataModels.Video video) {
        StringBuilder builder = new StringBuilder();
        builder.append(video.title).append(' ')
                .append(video.desc == null ? "" : video.desc).append(' ')
                .append(video.summary == null ? "" : video.summary).append(' ')
                .append(' ');
        for (DataModels.Tag tag : video.tags) builder.append(tag.name).append(' ');
        for (DataModels.Meme meme : video.memes) builder.append(meme.content).append(' ');
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private boolean matchesPronunciation(DataModels.Video video, String selected) {
        String text = videoText(video);
        switch (selected) {
            case "song":
                return containsAny(text, "翻唱", "唱歌", "演唱", "自唱", "弹唱", "歌喉", "唱功", "翻唱歌");
            case "dub":
                return containsAny(text, "配音", "声优", "角色音", "动画配音");
            case "accent":
                return containsAny(text, "口音", "方言", "湘式", "塑料英语", "塑料普通话", "英语听力", "德语", "日语");
            case "mandarin":
                return containsAny(text, "普通话");
            default:
                return false;
        }
    }

    private boolean matchesRegion(DataModels.Video video, String selected) {
        String text = videoText(video);
        switch (selected) {
            case "hunan":
                return containsAny(text, "湖南", "长沙", "湘");
            case "guangdong":
                return containsAny(text, "广东", "广州", "深圳");
            case "hainan":
                return containsAny(text, "海南");
            case "sichuan":
                return containsAny(text, "四川", "成都");
            case "shanghai":
                return containsAny(text, "上海");
            case "provinces":
                return containsAny(text, "河南", "河北", "福建", "山东", "云南", "北京", "天津", "重庆",
                        "江苏", "浙江", "安徽", "湖北", "江西", "陕西", "甘肃", "广西", "贵州",
                        "辽宁", "吉林", "黑龙江", "山西", "内蒙古", "宁夏", "青海", "新疆", "西藏",
                        "香港", "澳门", "台湾");
            case "overseas":
                return containsAny(text, "德国", "科隆", "美国", "日本", "英国", "法国", "韩国", "意大利",
                        "俄罗斯", "加拿大", "澳大利亚", "泰国", "新加坡", "东京", "大阪", "纽约",
                        "伦敦", "巴黎", "首尔", "出国", "国外", "海外", "境外");
            case "none":
                return !containsAny(text,
                        "湖南", "长沙", "湘", "广东", "广州", "深圳", "海南", "四川", "成都", "上海",
                        "河南", "河北", "福建", "山东", "云南", "北京", "天津", "重庆", "江苏", "浙江",
                        "安徽", "湖北", "江西", "陕西", "甘肃", "广西", "贵州", "辽宁", "吉林",
                        "黑龙江", "山西", "内蒙古", "宁夏", "青海", "新疆", "西藏", "香港", "澳门",
                        "台湾", "德国", "科隆", "美国", "日本", "英国", "法国", "韩国", "意大利",
                        "俄罗斯", "加拿大", "澳大利亚", "泰国", "新加坡", "东京", "大阪", "纽约",
                        "伦敦", "巴黎", "首尔", "出国", "国外", "海外", "境外");
            default:
                return false;
        }
    }
}
