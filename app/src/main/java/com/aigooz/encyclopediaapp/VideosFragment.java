package com.aigooz.encyclopediaapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.os.Handler;
import android.os.Looper;

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
    private TextView resultCount;
    private TextView emptyState;
    private TextView filterSummary;
    private VideoAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable search = () -> host().notifyFiltersChanged();
    private boolean binding;

    private MainActivity host() { return (MainActivity) requireActivity(); }

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
        resultCount = view.findViewById(R.id.resultCount);
        emptyState = view.findViewById(R.id.emptyState);
        filterSummary = view.findViewById(R.id.filterSummary);
        view.findViewById(R.id.clearFilters).setOnClickListener(v -> { host().filter().reset(); host().notifyFiltersChanged(); });
        filterSummary.setOnClickListener(v -> host().showFilters());
        videoList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new VideoAdapter(requireContext(), new ArrayList<>(), video -> VideoDetailActivity.open(this, video.bvid));
        videoList.setAdapter(adapter);
        refresh.setOnRefreshListener(() -> DataRepository.get().refresh(requireContext().getApplicationContext(), MainActivity.currentSite()));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                host().filter().query = s.toString();
                handler.removeCallbacks(search); handler.postDelayed(search, 180);
            }
        });
        buildAccountChips();
        buildSortChips();
        DataRepository.get().addListener(this);
        render();
    }

    private void buildAccountChips() {
        accountChips.removeAllViews();
        addSelectableChip(accountChips, "全部", "all", host().filter().account, id -> { host().filter().account = id; host().notifyFiltersChanged(); });
        addSelectableChip(accountChips, "主号", "main", host().filter().account, id -> { host().filter().account = id; host().notifyFiltersChanged(); });
        addSelectableChip(accountChips, "小号", "sub", host().filter().account, id -> { host().filter().account = id; host().notifyFiltersChanged(); });
        // Favorites is independent of the single-choice account group.
        ((android.widget.CompoundButton) requireView().findViewById(R.id.favoritesOnly)).setChecked(host().filter().favoritesOnly);
        requireView().findViewById(R.id.favoritesOnly).setOnClickListener(v -> {
            host().filter().favoritesOnly = ((android.widget.CompoundButton) v).isChecked(); host().notifyFiltersChanged();
        });
    }

    private void buildSortChips() {
        sortChips.removeAllViews();
        String[][] options = {{"date", "最新"}, {"oldest", "最早"}, {"view", "播放"}, {"like", "点赞"},
                {"danmaku", "弹幕"}, {"interaction", "互动率"}, {"lines", "字幕行"}, {"chars", "字符"}};
        for (String[] option : options) addSelectableChip(sortChips, option[1], option[0], host().filter().sort,
                id -> { host().filter().sort = id; host().notifyFiltersChanged(); });
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
            refresh.setRefreshing(DataRepository.get().isLoading(MainActivity.currentSite()));
            render();
        }
    }

    @Override
    public void onDestroyView() {
        DataRepository.get().removeListener(this);
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void render() {
        DataModels.Dataset dataset = DataRepository.get().current();
        if (!isAdded()) return;
        handler.removeCallbacks(search);
        binding = true;
        if (!searchInput.getText().toString().equals(host().filter().query)) searchInput.setText(host().filter().query);
        binding = false;
        buildAccountChips(); buildSortChips();
        filterSummary.setText(host().filter().summary());
        if (dataset == null || dataset.site != MainActivity.currentSite() || dataset.data == null) {
            adapter.submit(new ArrayList<>());
            boolean loading = DataRepository.get().isLoading(MainActivity.currentSite());
            resultCount.setText(loading ? "正在读取视频库…" : "视频库暂时无法加载");
            emptyState.setText(loading ? "正在加载数据" : "数据读取失败，请下拉重试"); emptyState.setVisibility(View.VISIBLE); return;
        }
        List<DataModels.Video> filtered = host().selectedVideos(dataset.data);
        resultCount.setText(filtered.size() + " / " + dataset.data.videos.size() + " 个视频");
        emptyState.setText("没有符合条件的视频\n可清除筛选后重新浏览");
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submit(filtered);
    }
}
