package com.aigooz.encyclopediaapp;

import android.content.Context;
import java.util.HashSet;
import java.util.Set;

public final class LibraryStore {
    private LibraryStore() {}

    public static Set<String> favorites(Context context, Site site) {
        return new HashSet<>(context.getSharedPreferences("library", Context.MODE_PRIVATE)
                .getStringSet("favorites_" + site.name(), new HashSet<>()));
    }

    public static boolean toggle(Context context, Site site, String bvid) {
        Set<String> saved = favorites(context, site);
        boolean added = !saved.remove(bvid);
        if (added) saved.add(bvid);
        context.getSharedPreferences("library", Context.MODE_PRIVATE).edit()
                .putStringSet("favorites_" + site.name(), saved).apply();
        return added;
    }
}
