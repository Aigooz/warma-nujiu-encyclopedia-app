package com.aigooz.encyclopediaapp;

import android.content.Context;

import java.util.Locale;

public final class UiUtils {
    private UiUtils() {}

    public static String number(Context context, double value) {
        if (value >= 100_000_000) return String.format(Locale.CHINA, "%.2f 亿", value / 100_000_000D);
        if (value >= 10_000) return String.format(Locale.CHINA, "%.1f 万", value / 10_000D);
        return String.valueOf((long) value);
    }

    public static String percent(double value) {
        return String.format(Locale.CHINA, "%.1f%%", value);
    }

    public static double durationSeconds(DataModels.Video video) {
        if (video.duration_seconds > 0) return video.duration_seconds;
        try {
            double seconds = 0;
            for (String part : video.duration.split(":")) seconds = seconds * 60 + Double.parseDouble(part);
            return seconds;
        } catch (Exception ignored) { return 0; }
    }

    public static String duration(DataModels.Video video) {
        return timestamp(durationSeconds(video));
    }

    public static String timestamp(double seconds) {
        long total = Math.max(0, (long) seconds);
        return total >= 3600 ? String.format(Locale.CHINA, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
                : String.format(Locale.CHINA, "%d:%02d", total / 60, total % 60);
    }
}
