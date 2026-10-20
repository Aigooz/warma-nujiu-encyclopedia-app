package com.aigooz.encyclopediaapp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared selection for the overview, library and insights of one site. */
public final class VideoFilter {
    public String query = "";
    public String account = "all";
    public String type = "";
    public String year = "";
    public String sort = "date";
    public int minLines;
    public boolean favoritesOnly;

    public void reset() {
        query = type = year = "";
        account = "all";
        sort = "date";
        minLines = 0;
        favoritesOnly = false;
    }

    public boolean isActive() {
        return !query.trim().isEmpty() || !account.equals("all") || !type.isEmpty()
                || !year.isEmpty() || minLines > 0 || favoritesOnly;
    }

    public List<DataModels.Video> apply(List<DataModels.Video> source, Set<String> favorites) {
        List<DataModels.Video> result = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (DataModels.Video video : source) {
            if (account.equals("main") && !text(video.account).contains("主号")) continue;
            if (account.equals("sub") && !text(video.account).contains("小号")) continue;
            if (!type.isEmpty() && !type.equals(text(video.type))) continue;
            if (!year.isEmpty() && !text(video.date).startsWith(year + "-")) continue;
            if (video.sub_lines < minLines) continue;
            if (favoritesOnly && !favorites.contains(video.bvid)) continue;
            if (!q.isEmpty()) {
                StringBuilder searchable = new StringBuilder(text(video.title)).append(' ')
                        .append(text(video.bvid)).append(' ').append(text(video.type)).append(' ')
                        .append(text(video.account));
                if (video.tags != null) for (DataModels.Tag tag : video.tags) searchable.append(' ').append(tag.name);
                if (video.memes != null) for (DataModels.Meme meme : video.memes) searchable.append(' ').append(meme.content);
                if (!searchable.toString().toLowerCase(Locale.ROOT).contains(q)) continue;
            }
            result.add(video);
        }
        Comparator<DataModels.Video> order;
        switch (sort) {
            case "oldest": order = Comparator.comparing(v -> text(v.date)); break;
            case "lines": order = Comparator.comparingDouble((DataModels.Video v) -> v.sub_lines).reversed(); break;
            case "chars": order = Comparator.comparingDouble((DataModels.Video v) -> v.sub_chars).reversed(); break;
            case "interaction": order = Comparator.comparingDouble(DataModels.Video::interactionRate).reversed(); break;
            case "view": case "like": case "danmaku": case "coin": case "favorite": case "reply": case "share":
                order = Comparator.comparingDouble((DataModels.Video v) -> v.metric(sort)).reversed(); break;
            default: order = Comparator.comparing((DataModels.Video v) -> text(v.date)).reversed(); break;
        }
        result.sort(order.thenComparing(v -> text(v.bvid)));
        return result;
    }

    public String summary() {
        List<String> parts = new ArrayList<>();
        if (!query.trim().isEmpty()) parts.add("“" + query.trim() + "”");
        if (!account.equals("all")) parts.add(account.equals("main") ? "主号" : "小号");
        if (!type.isEmpty()) parts.add(type);
        if (!year.isEmpty()) parts.add(year + " 年");
        if (minLines > 0) parts.add("字幕 ≥ " + minLines + " 行");
        if (favoritesOnly) parts.add("我的收藏");
        return parts.isEmpty() ? "全部内容" : String.join(" · ", parts);
    }

    private static String text(String value) { return value == null ? "" : value; }
}
