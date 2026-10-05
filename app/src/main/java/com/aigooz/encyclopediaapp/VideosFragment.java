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
    private String account = "all";
    private String sort = "date";
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
        videoList.setLayoutManager(new LinearLayoutManager(requireContext()));
        refresh.setOnRefreshListener(() -> DataRepository.get().refresh(requireContext().getApplicationContext(), MainActivity.currentSite()));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { query = s.toString(); render(); }
        });
        buildAccountChips();
        buildSortChips();
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
            if (accountMatch && queryMatch) filtered.add(video);
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
}
