package com.aigooz.encyclopediaapp.chart;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.aigooz.encyclopediaapp.R;

/**
 * 与网页端视觉对齐的配色与渐变工具。
 *
 * 网页端（ECharts）每根柱子都是「起点色 → 终点色」的线性渐变，
 * 手机端原先只用单一纯色，所以显得扁平。这里统一从 {@code R.color}
 * 取色，深色模式会自动跟随 values-night 定义，不必在 Java 里硬编码。
 */
public final class ChartPalette {
    private ChartPalette() {}

    /** 与网页端 PALETTE 一一对应的语义色名。 */
    private static final int[] COLORS = {
            R.color.chart_pink, R.color.chart_purple, R.color.chart_blue,
            R.color.chart_mint, R.color.chart_amber, R.color.chart_cyan,
            R.color.chart_rose, R.color.chart_lime, R.color.chart_lavender
    };

    public static int color(Context context, int index) {
        return ContextCompat.getColor(context, COLORS[Math.floorMod(index, COLORS.length)]);
    }

    /** 整个调色盘，按网页端顺序返回。 */
    public static int[] colors(Context context) {
        int[] result = new int[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) result[i] = color(context, i);
        return result;
    }

    /** 柱状/条状图用的竖向渐变（上亮下深），接近网页端 {@code LinearGradient(0,0,0,1)}。 */
    public static int gradientTop(Context context, int index) {
        return color(context, index);
    }

    public static int gradientBottom(Context context, int index) {
        return blend(color(context, index), Color.BLACK, 0.35f);
    }

    /** 横向条形图的渐变（左深右亮）。 */
    public static int gradientStart(Context context, int index) {
        return blend(color(context, index), Color.BLACK, 0.22f);
    }

    public static int gradientEnd(Context context, int index) {
        return color(context, index);
    }

    /** 折线图面积填充色：取主色的低透明度版本。 */
    public static int areaFill(Context context, int index, float alpha) {
        int base = color(context, index);
        return Color.argb((int) (255 * alpha), Color.red(base), Color.green(base), Color.blue(base));
    }

    /** 把颜色向目标色混合，ratio 为 0–1。 */
    public static int blend(int from, int to, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        return Color.argb(
                Color.alpha(from),
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * r),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * r),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * r));
    }
}
