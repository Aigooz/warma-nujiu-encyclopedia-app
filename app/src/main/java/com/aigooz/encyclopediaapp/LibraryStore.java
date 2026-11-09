package com.aigooz.encyclopediaapp;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * 收藏的持久化存储。
 *
 * 旧实现每次调用都重新读一遍 SharedPreferences：而 favorites() 在
 * MainActivity.selectedVideos() 里被每一次筛选/排序/刷新调用，属于高频路径。
 * 这里加一层内存缓存，写入时同步更新，避免重复 IO。
 */
public final class LibraryStore {
    private LibraryStore() {}

    private static final String PREFS = "library";
    private static final java.util.Map<Site, Set<String>> cache = new java.util.concurrent.ConcurrentHashMap<>();

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> favorites(Context context, Site site) {
        Set<String> cached = cache.get(site);
        if (cached != null) return new HashSet<>(cached);
        Set<String> loaded = new HashSet<>(prefs(context).getStringSet("favorites_" + site.name(), new HashSet<>()));
        cache.put(site, loaded);
        return new HashSet<>(loaded);
    }

    public static boolean toggle(Context context, Site site, String bvid) {
        Set<String> saved = favorites(context, site);
        boolean added = !saved.remove(bvid);
        if (added) saved.add(bvid);
        cache.put(site, saved);
        prefs(context).edit().putStringSet("favorites_" + site.name(), saved).apply();
        return added;
    }

    /** 供测试或数据清理场景重置内存缓存。 */
    static void clearCache() {
        cache.clear();
    }
}
