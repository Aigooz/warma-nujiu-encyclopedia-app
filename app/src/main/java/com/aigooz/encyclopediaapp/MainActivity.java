package com.aigooz.encyclopediaapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static Site current = Site.WARMA;
    private int selectedTab;
    private MaterialToolbar toolbar;

    public static Site currentSite() {
        return current;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        current = Site.valueOf(getSharedPreferences("encyclopedia", MODE_PRIVATE).getString("site", Site.WARMA.name()));
        toolbar = findViewById(R.id.toolbar);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
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
        DataRepository.get().load(getApplicationContext(), current);
    }

    private boolean selectTab(int position) {
        selectedTab = position;
        Fragment fragment;
        switch (position) {
            case 1: fragment = new VideosFragment(); break;
            case 2: fragment = new RankingsFragment(); break;
            case 3: fragment = new QuizFragment(); break;
            case 4: fragment = new SettingsFragment(); break;
            default: fragment = new HomeFragment(); break;
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, fragment).commit();
        updateTitle();
        return true;
    }

    void switchSite(Site site) {
        if (current == site) return;
        current = site;
        getSharedPreferences("encyclopedia", MODE_PRIVATE).edit().putString("site", site.name()).apply();
        DataRepository.get().load(getApplicationContext(), site);
        updateTitle();
    }

    private void updateTitle() {
        String page = selectedTab == 1 ? "视频库" : selectedTab == 2 ? "榜单" : selectedTab == 3 ? "挑战" : selectedTab == 4 ? "我的" : "全景";
        toolbar.setTitle(current.title);
        toolbar.setSubtitle("原生阅读 · " + page);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", selectedTab);
    }
}
