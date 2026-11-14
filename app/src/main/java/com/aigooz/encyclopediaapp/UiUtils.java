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

    /**
     * 坐标轴刻度专用格式：去掉没有信息量的尾数。
     *
     * 坐标轴常常落在 12000000 这种整刻度上，用 {@link #number} 会显示成
     * 「1200.0 万」「1.00 亿」，既占宽度又不好读。这里在保留一位小数的前提下，
     * 把末尾的 0 和小数点去掉，得到「1200 万」「1 亿」。
     */
    public static String axisNumber(Context context, double value) {
        if (value >= 100_000_000) return trim(value / 100_000_000D, 2) + " 亿";
        if (value >= 10_000) return trim(value / 10_000D, 1) + " 万";
        return trim(value, 0);
    }

    private static String trim(double value, int decimals) {
        String text = String.format(Locale.CHINA, "%." + decimals + "f", value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text;
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
