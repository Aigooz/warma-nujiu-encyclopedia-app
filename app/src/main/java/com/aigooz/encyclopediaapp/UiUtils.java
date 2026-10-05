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
        return String.format("%.1f%%", value);
    }
}
