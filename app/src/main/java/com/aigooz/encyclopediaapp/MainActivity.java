package com.aigooz.encyclopediaapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.view.View;
import android.view.MenuItem;
import com.google.android.material.slider.Slider;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.Gson;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements DataRepository.Listener {
    private static Site current = Site.WARMA;
    private int selectedTab;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private final EnumMap<Site, VideoFilter> filters = new EnumMap<>(Site.class);
    private static final int MENU_SITE = 101;
    private static final int MENU_FILTER = 102;

    VideoFilter filter() { return filters.get(current); }

    List<DataModels.Video> selectedVideos(DataModels.RawData data) {
        return filter().apply(data.videos, LibraryStore.favorites(this, current));
    }

    public static Site currentSite() {
        return current;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(getSharedPreferences("encyclopedia", MODE_PRIVATE)
                .getInt("theme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        super.onCreate(savedInstanceState);
        for (Site site : Site.values()) {
            String json = savedInstanceState == null ? null : savedInstanceState.getString("filter_" + site.name());
            filters.put(site, json == null ? new VideoFilter() : new Gson().fromJson(json, VideoFilter.class));
        }
        setContentView(R.layout.activity_main);
        current = Site.valueOf(getSharedPreferences("encyclopedia", MODE_PRIVATE).getString("site", Site.WARMA.name()));
        toolbar = findViewById(R.id.toolbar);
        toolbar.getMenu().add(0, MENU_SITE, 0, "切换百科").setIcon(R.drawable.ic_warma)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.getMenu().add(0, MENU_FILTER, 1, "筛选").setIcon(R.drawable.ic_filter)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_SITE) {
                new MaterialAlertDialogBuilder(this).setTitle("切换百科")
                        .setSingleChoiceItems(new String[]{"Warma 百科", "怒九百科"}, current.ordinal(), (dialog, which) -> {
                            switchSite(Site.values()[which]); dialog.dismiss();
                        }).show();
            } else showFilters();
            return true;
        });
        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return selectTab(0);
            if (id == R.id.nav_videos) return selectTab(1);
            if (id == R.id.nav_rank) return selectTab(2);
            if (id == R.id.nav_quiz) return selectTab(3);
            if (id == R.id.nav_settings) return selectTab(4);
            return false;
        });
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        } else {
            selectedTab = savedInstanceState.getInt("tab", 0);
            bottomNav.setSelectedItemId(selectedTab == 0 ? R.id.nav_home
                    : selectedTab == 1 ? R.id.nav_videos
                    : selectedTab == 2 ? R.id.nav_rank
                    : selectedTab == 3 ? R.id.nav_quiz : R.id.nav_settings);
        }
        DataRepository.get().addListener(this);
        DataRepository.get().load(getApplicationContext(), current);
        handleSearchIntent(getIntent());
    }

    private boolean selectTab(int position) {
        selectedTab = position;
        String tag = "tab_" + position;
        Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
        Fragment fragment = existing;
        if (fragment == null) {
            switch (position) {
                case 1: fragment = new VideosFragment(); break;
                case 2: fragment = new RankingsFragment(); break;
                case 3: fragment = new QuizFragment(); break;
                case 4: fragment = new SettingsFragment(); break;
                default: fragment = new HomeFragment(); break;
            }
        }
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (Fragment other : getSupportFragmentManager().getFragments()) transaction.hide(other);
        if (existing == null) transaction.add(R.id.fragmentContainer, fragment, tag);
        else transaction.show(fragment);
        transaction.setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.RESUMED);
        for (Fragment other : getSupportFragmentManager().getFragments()) {
            if (other != fragment) transaction.setMaxLifecycle(other, androidx.lifecycle.Lifecycle.State.STARTED);
        }
        transaction.commitNow();
        updateTitle();
        notifyFiltersChanged();
        return true;
    }

    void switchSite(Site site) {
        if (current == site) return;
        current = site;
        getSharedPreferences("encyclopedia", MODE_PRIVATE).edit().putString("site", site.name()).apply();
        DataRepository.get().load(getApplicationContext(), site);
        updateTitle();
        notifyFiltersChanged();
    }

    private void updateTitle() {
        String page = selectedTab == 1 ? "视频库" : selectedTab == 2 ? "榜单" : selectedTab == 3 ? "挑战" : selectedTab == 4 ? "设置" : "全景";
        toolbar.setTitle(current.title);
        toolbar.setSubtitle(page + (selectedTab < 3 && filter().isActive() ? " · 已筛选" : ""));
        toolbar.getMenu().findItem(MENU_SITE).setIcon(current == Site.WARMA ? R.drawable.ic_warma : R.drawable.ic_nujiu);
        toolbar.getMenu().findItem(MENU_FILTER).setVisible(selectedTab < 3);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", selectedTab);
        for (Site site : Site.values()) outState.putString("filter_" + site.name(), new Gson().toJson(filters.get(site)));
    }

    void openVideos(String type, String query) {
        if (type != null) filter().type = type;
        if (query != null) filter().query = query;
        bottomNav.setSelectedItemId(R.id.nav_videos);
        notifyFiltersChanged();
    }

    void notifyFiltersChanged() {
        updateTitle();
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment.getView() != null && fragment instanceof DataRepository.Listener)
                ((DataRepository.Listener) fragment).onDataChanged();
        }
    }

    void showFilters() {
        DataModels.Dataset dataset = DataRepository.get().current();
        if (dataset == null || dataset.site != current) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);
        Spinner account = choice(form, "账号", java.util.Arrays.asList("全部账号", "主号", "小号"),
                filter().account.equals("main") ? 1 : filter().account.equals("sub") ? 2 : 0);
        TreeSet<String> types = new TreeSet<>();
        TreeSet<String> years = new TreeSet<>(java.util.Collections.reverseOrder());
        for (DataModels.Video video : dataset.data.videos) {
            if (video.type != null && !video.type.isEmpty()) types.add(video.type);
            if (video.date != null && video.date.length() >= 4) years.add(video.date.substring(0, 4));
        }
        List<String> typeOptions = new ArrayList<>(); typeOptions.add("全部类型"); typeOptions.addAll(types);
        List<String> yearOptions = new ArrayList<>(); yearOptions.add("全部年份"); yearOptions.addAll(years);
        Spinner type = choice(form, "类型", typeOptions, Math.max(0, typeOptions.indexOf(filter().type)));
        Spinner year = choice(form, "年份", yearOptions, Math.max(0, yearOptions.indexOf(filter().year)));
        TextView linesLabel = new TextView(this);
        linesLabel.setText("字幕行数下限：" + filter().minLines);
        form.addView(linesLabel);
        Slider lines = new Slider(this);
        lines.setValueFrom(0); lines.setValueTo(2500); lines.setStepSize(50);
        lines.setValue(filter().minLines);
        lines.addOnChangeListener((slider, value, user) -> linesLabel.setText("字幕行数下限：" + (int) value));
        form.addView(lines);
        MaterialSwitch favorites = new MaterialSwitch(this);
        favorites.setText("仅看我的收藏"); favorites.setChecked(filter().favoritesOnly);
        form.addView(favorites);
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(form);
        new MaterialAlertDialogBuilder(this).setTitle("筛选内容").setView(scroll)
                .setNegativeButton("取消", null)
                .setNeutralButton("重置", (dialog, which) -> { filter().reset(); notifyFiltersChanged(); })
                .setPositiveButton("应用", (dialog, which) -> {
                    filter().account = account.getSelectedItemPosition() == 1 ? "main" : account.getSelectedItemPosition() == 2 ? "sub" : "all";
                    filter().type = type.getSelectedItemPosition() == 0 ? "" : type.getSelectedItem().toString();
                    filter().year = year.getSelectedItemPosition() == 0 ? "" : year.getSelectedItem().toString();
                    filter().minLines = (int) lines.getValue(); filter().favoritesOnly = favorites.isChecked();
                    notifyFiltersChanged();
                }).show();
    }

    private Spinner choice(LinearLayout form, String label, List<String> items, int selected) {
        TextView title = new TextView(this); title.setText(label); form.addView(title);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selected);
        form.addView(spinner, new LinearLayout.LayoutParams(-1, (int) (48 * getResources().getDisplayMetrics().density)));
        return spinner;
    }

    @Override public void onDataChanged() { updateTitle(); }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleSearchIntent(intent);
    }
    private void handleSearchIntent(android.content.Intent intent) {
        if (!intent.hasExtra("search")) return;
        if (intent.hasExtra("site")) switchSite(Site.valueOf(intent.getStringExtra("site")));
        filter().reset(); openVideos(null, intent.getStringExtra("search")); intent.removeExtra("search");
    }
    @Override protected void onResume() { super.onResume(); if (toolbar != null) notifyFiltersChanged(); }
    @Override protected void onDestroy() { DataRepository.get().removeListener(this); super.onDestroy(); }
}
