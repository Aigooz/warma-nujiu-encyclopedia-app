package com.aigooz.encyclopediaapp;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

final class BilibiliLinks {
    private static final String BILIBILI_PACKAGE = "tv.danmaku.bili";

    private BilibiliLinks() {}

    static void openVideo(Context context, String bvid) {
        if (bvid == null || !bvid.matches("BV[0-9A-Za-z]{10}")) {
            Toast.makeText(context, "视频链接无效", Toast.LENGTH_SHORT).show();
            return;
        }
        open(context, "bilibili://video/" + bvid, "https://www.bilibili.com/video/" + bvid);
    }

    static void openProfile(Context context, String mid) {
        if (mid == null || !mid.matches("[0-9]{1,20}") || mid.matches("0+")) {
            Toast.makeText(context, "账号链接无效", Toast.LENGTH_SHORT).show();
            return;
        }
        open(context, "bilibili://space/" + mid, "https://space.bilibili.com/" + mid);
    }

    private static void open(Context context, String appUrl, String webUrl) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(appUrl)).setPackage(BILIBILI_PACKAGE));
            return;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // The official app may be absent, disabled, or unable to handle this link.
        }
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            Toast.makeText(context, "没有可用的哔哩哔哩客户端或浏览器", Toast.LENGTH_SHORT).show();
        }
    }
}
