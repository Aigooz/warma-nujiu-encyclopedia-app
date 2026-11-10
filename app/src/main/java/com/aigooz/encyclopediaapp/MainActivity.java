package com.aigooz.encyclopediaapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ImageButton;
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
    private ImageButton siteButton;
    private BottomNavigationView bottomNav;
    private final EnumMap<Site, VideoFilter> filters = new EnumMap<>(Site.class);
    private static final int MENU_SITE = 101;
    private static final int MENU_FILTER = 102;
    /** 与 selectTab 的 position 一一对应，供定位已创建的分栏使用。 */
    private static final String[] TAB_TAGS = {"tab_0", "tab_1", "tab_2", "tab_3", "tab_4"};
    /** 非当前分栏在筛选变化后被标记为脏，切过去时再渲染，避免后台无谓重算。 */
    private final java.util.Set<Integer> dirtyTabs = new java.util.HashSet<>();

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
        // 站点偏好同样走容错解析：SharedPreferences 被外部改写或降级安装时不应崩溃。
        current = parseSite(getSharedPreferences("encyclopedia", MODE_PRIVATE).getString("site", Site.WARMA.name()));
        if (current == null) current = Site.WARMA;
        toolbar = findViewById(R.id.toolbar);
        siteButton = new ImageButton(this);
        int actionSize = (int) (48 * getResources().getDisplayMetrics().density);
        int iconPadding = (int) (12 * getResources().getDisplayMetrics().density);
        siteButton.setLayoutParams(new androidx.appcompat.widget.Toolbar.LayoutParams(actionSize, actionSize));
        siteButton.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        android.util.TypedValue background = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, background, true);
        siteButton.setBackgroundResource(background.resourceId);
        siteButton.setOnClickListener(v -> {
            siteButton.animate().cancel();
            siteButton.setRotationY(0f);
            switchSite(current == Site.WARMA ? Site.NUJIU : Site.WARMA);
            siteButton.animate().rotationYBy(360f).setDuration(350)
                    .withEndAction(() -> siteButton.setRotationY(0f)).start();
            View content = findViewById(R.id.fragmentContainer);
            content.animate().cancel();
            content.setAlpha(0.8f);
            content.animate().alpha(1f).setDuration(250).start();
        });
        MenuItem siteItem = toolbar.getMenu().add(0, MENU_SITE, 0, "切换百科");
        siteItem.setActionView(siteButton);
        siteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.getMenu().add(0, MENU_FILTER, 1, "筛选").setIcon(R.drawable.ic_filter)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_FILTER) { showFilters(); return true; }
            return false;
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
        // 不做全量通知：新建的 Fragment 会在自己的 onViewCreated 里首次渲染；
        // 只有被筛选变更标记为脏的分栏才需要在重新可见时补一次渲染。
        if (dirtyTabs.remove(position)) refreshTab(position);
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
        siteButton.setImageResource(current == Site.WARMA ? R.drawable.ic_warma : R.drawable.ic_nujiu);
        String switchLabel = current == Site.WARMA ? "切换到怒九百科" : "切换到 Warma 百科";
        siteButton.setContentDescription(switchLabel);
        androidx.appcompat.widget.TooltipCompat.setTooltipText(siteButton, switchLabel);
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
        final int videosTab = 1;
        if (selectedTab == videosTab) {
            // 已经停在视频库：setSelectedItemId 不会触发监听器，直接刷新当前分栏。
            notifyFiltersChanged();
        } else {
            // 切过去之前先全部标记为脏，selectTab 会顺带把视频库渲染一次。
            for (int tab = 0; tab < TAB_TAGS.length; tab++) dirtyTabs.add(tab);
            bottomNav.setSelectedItemId(R.id.nav_videos);
        }
    }

    /**
     * 筛选条件变化后的刷新。
     *
     * 旧实现会通知**全部**已创建的 Fragment。由于分栏用 hide/show 管理，访问过的页面
     * View 一直存活，于是改一次搜索词会连带让榜单页（9 个渲染方法 + 整表排序 + 新建适配器）
     * 和首页（统计/年度/分类/最新/弹幕梗聚合）各跑一整轮，是本应用最明显的卡顿来源。
     *
     * 现在只刷新当前可见分栏；其余分栏标记为"脏"，等用户真正切过去时再渲染一次。
     */
    void notifyFiltersChanged() {
        updateTitle();
        for (int tab = 0; tab < TAB_TAGS.length; tab++) {
            if (tab != selectedTab) dirtyTabs.add(tab);
        }
        dirtyTabs.remove(selectedTab);
        refreshTab(selectedTab);
    }

    private void refreshTab(int position) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAB_TAGS[position]);
        if (fragment != null && fragment.getView() != null && fragment instanceof DataRepository.Listener) {
            ((DataRepository.Listener) fragment).onDataChanged();
        }
    }

    void showFilters() {
        DataModels.Dataset dataset = DataRepository.get().current();
        if (dataset == null || dataset.site != current) return;
        final VideoFilter draft = new Gson().fromJson(new Gson().toJson(filter()), VideoFilter.class);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);

        TreeSet<String> types = new TreeSet<>();
        TreeSet<String> years = new TreeSet<>(java.util.Collections.reverseOrder());
        for (DataModels.Video video : dataset.data.videos) {
            if (video.type != null && !video.type.isEmpty()) types.add(video.type);
            if (video.date != null && video.date.length() >= 4) years.add(video.date.substring(0, 4));
        }
        List<String> accountOptions = java.util.Arrays.asList("全部账号", "主号", "小号");
        List<String> typeOptions = new ArrayList<>(); typeOptions.add("全部类型"); typeOptions.addAll(types);
        List<String> yearOptions = new ArrayList<>(); yearOptions.add("全部年份"); yearOptions.addAll(years);

        // 用 Material3 的 ExposedDropdownMenu 取代原生 Spinner：
        // 原生 Spinner 在 Material3 界面里观感与其余控件明显不一致（P3-4）。
        com.google.android.material.textfield.MaterialAutoCompleteTextView account =
                dropdown(form, "账号", accountOptions,
                        draft.account.equals("main") ? 1 : draft.account.equals("sub") ? 2 : 0);
        com.google.android.material.textfield.MaterialAutoCompleteTextView type =
                dropdown(form, "类型", typeOptions, Math.max(0, typeOptions.indexOf(draft.type)));
        com.google.android.material.textfield.MaterialAutoCompleteTextView year =
                dropdown(form, "年份", yearOptions, Math.max(0, yearOptions.indexOf(draft.year)));

        TextView linesLabel = new TextView(this);
        linesLabel.setText("字幕行数下限：" + draft.minLines);
        form.addView(linesLabel);
        Slider lines = new Slider(this);
        lines.setValueFrom(0); lines.setValueTo(2500); lines.setStepSize(50);
        lines.setValue(draft.minLines);
        lines.addOnChangeListener((slider, value, user) -> linesLabel.setText("字幕行数下限：" + (int) value));
        form.addView(lines);
        MaterialSwitch favorites = new MaterialSwitch(this);
        favorites.setText("仅看我的收藏"); favorites.setChecked(draft.favoritesOnly);
        form.addView(favorites);
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(form);
        new MaterialAlertDialogBuilder(this).setTitle("筛选内容").setView(scroll)
                .setNegativeButton("取消", null)
                .setNeutralButton("重置", (dialog, which) -> { filter().reset(); notifyFiltersChanged(); })
                .setPositiveButton("应用", (dialog, which) -> {
                    int accountIndex = indexOf(accountOptions, account.getText().toString());
                    filter().account = accountIndex == 1 ? "main" : accountIndex == 2 ? "sub" : "all";
                    int typeIndex = indexOf(typeOptions, type.getText().toString());
                    filter().type = typeIndex <= 0 ? "" : typeOptions.get(typeIndex);
                    int yearIndex = indexOf(yearOptions, year.getText().toString());
                    filter().year = yearIndex <= 0 ? "" : yearOptions.get(yearIndex);
                    filter().minLines = (int) lines.getValue(); filter().favoritesOnly = favorites.isChecked();
                    notifyFiltersChanged();
                }).show();
    }

    private static int indexOf(List<String> options, String value) {
        int index = options.indexOf(value);
        return index < 0 ? 0 : index;
    }

    /** Material3 样式的下拉选择框，替代原生 Spinner。 */
    private com.google.android.material.textfield.MaterialAutoCompleteTextView dropdown(
            LinearLayout form, String label, List<String> items, int selected) {
        com.google.android.material.textfield.TextInputLayout wrapper =
                new com.google.android.material.textfield.TextInputLayout(this,
                        null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        wrapper.setHint(label);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(-1, -2);
        wrapperParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        wrapper.setLayoutParams(wrapperParams);
        com.google.android.material.textfield.MaterialAutoCompleteTextView field =
                new com.google.android.material.textfield.MaterialAutoCompleteTextView(this);
        field.setInputType(android.text.InputType.TYPE_NULL);
        field.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items));
        field.setText(items.get(Math.max(0, Math.min(selected, items.size() - 1))), false);
        field.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        wrapper.addView(field);
        form.addView(wrapper);
        return field;
    }

    @Override public void onDataChanged() { updateTitle(); }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleSearchIntent(intent);
    }
    private void handleSearchIntent(android.content.Intent intent) {
        if (!intent.hasExtra("search")) return;
        if (intent.hasExtra("site")) {
            Site target = parseSite(intent.getStringExtra("site"));
            if (target != null) switchSite(target);
        }
        filter().reset(); openVideos(null, intent.getStringExtra("search")); intent.removeExtra("search");
    }

    /** 外部 Intent 传入的站点名不可信，非法值不能让应用崩溃。 */
    static Site parseSite(String name) {
        if (name == null) return null;
        for (Site site : Site.values()) {
            if (site.name().equals(name)) return site;
        }
        return null;
    }
    @Override protected void onResume() { super.onResume(); if (toolbar != null) notifyFiltersChanged(); }
    @Override protected void onDestroy() { DataRepository.get().removeListener(this); super.onDestroy(); }
}
