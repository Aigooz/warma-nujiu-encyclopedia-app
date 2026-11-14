package com.aigooz.encyclopediaapp.chart;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.core.content.ContextCompat;

import com.aigooz.encyclopediaapp.R;
import com.aigooz.encyclopediaapp.UiUtils;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.charts.RadarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一的手机端图表样式。
 *
 * 网页端每张图都有一致的坐标轴、网格、渐变柱子与数据标签；手机端原先各图各写一套，
 * 且普遍是纯色无标签，观感明显单薄。这里把「同一套视觉语言」收敛到一处：
 *
 * <ul>
 *   <li>坐标轴、网格、图例使用统一颜色与字号；</li>
 *   <li>柱状图统一换成 {@link RoundedBarChartRenderer}，带圆角与线性渐变；</li>
 *   <li>数值用「万 / 亿」压缩，避免长数字把柱子挤变形；</li>
 *   <li>折线图面积填充与柱状渐变的取向与网页端一致。</li>
 * </ul>
 */
public final class ChartStyler {
    private ChartStyler() {}

    /** 竖向柱状图：圆角 + 竖向渐变。 */
    public static void bar(BarChart chart, Context context) {
        baseAxis(chart, context);
        chart.setRenderer(new RoundedBarChartRenderer(chart, chart.getAnimator(),
                chart.getViewPortHandler(), 8f, false));
    }

    /** 横向条形图：圆角 + 横向渐变。 */
    public static void horizontalBar(HorizontalBarChart chart, Context context) {
        baseAxis(chart, context);
        chart.setRenderer(new RoundedBarChartRenderer(chart, chart.getAnimator(),
                chart.getViewPortHandler(), 8f, true));
    }

    private static void baseAxis(BarLineChartBase<?> chart, Context context) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setHighlightPerTapEnabled(true);
        chart.setExtraOffsets(6f, 8f, 8f, 4f);
        chart.setNoDataText("暂无数据");
        chart.setNoDataTextColor(muted(context));

        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(ContextCompat.getColor(context, R.color.chart_grid));
        chart.getAxisLeft().setGridLineWidth(1f);
        chart.getAxisLeft().setTextColor(muted(context));
        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisLeft().setValueFormatter(compactNumber(context));

        XAxis axis = chart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM);
        axis.setDrawGridLines(false);
        axis.setDrawAxisLine(false);
        axis.setGranularity(1f);
        axis.setTextColor(muted(context));
        axis.setTextSize(10f);
        axis.setAvoidFirstLastClipping(true);
        axis.setYOffset(4f);
    }

    /** 饼图：空心圆环 + 中心文字，标签交由外部自绘（与网页端一致）。 */
    public static void pie(PieChart chart, Context context) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(58f);
        chart.setTransparentCircleRadius(0f);
        chart.setDrawCenterText(true);
        chart.setCenterTextColor(ContextCompat.getColor(context, R.color.text_primary));
        chart.setCenterTextSize(13f);
        chart.setCenterTextTypeface(Typeface.DEFAULT_BOLD);
        chart.setDrawEntryLabels(false);
        chart.setRotationEnabled(false);
        chart.setHighlightPerTapEnabled(true);
        chart.setExtraOffsets(4f, 4f, 4f, 4f);
        chart.setNoDataText("暂无数据");
        chart.setNoDataTextColor(muted(context));
    }

    /** 组合图（柱 + 折线）：沿用坐标轴风格，但保留图例。 */
    public static void combined(CombinedChart chart, Context context) {
        baseAxis(chart, context);
        chart.getLegend().setEnabled(true);
        legend(chart, context);
    }

    /** 折线/面积图：沿用坐标轴风格，保留图例并允许轻微拖动查看。 */
    public static void line(LineChart chart, Context context) {
        baseAxis(chart, context);
        chart.setPinchZoom(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.getXAxis().setDrawGridLines(false);
        chart.getLegend().setEnabled(true);
        legend(chart, context);
    }

    /** 雷达图：只保留极坐标网格，样式对齐网页端的类型形态雷达。 */
    public static void radar(RadarChart chart, Context context) {
        chart.getDescription().setEnabled(false);
        chart.setRotationEnabled(false);
        chart.setHighlightPerTapEnabled(false);
        chart.setDrawWeb(true);
        chart.setWebLineWidth(1f);
        chart.setWebColor(ContextCompat.getColor(context, R.color.chart_grid));
        chart.setWebLineWidthInner(1f);
        chart.setWebColorInner(ContextCompat.getColor(context, R.color.chart_grid));
        chart.setWebAlpha(120);
        chart.getYAxis().setDrawLabels(false);
        chart.getYAxis().setAxisMinimum(0f);
        chart.getYAxis().setDrawAxisLine(false);
        chart.getYAxis().setGridColor(ContextCompat.getColor(context, R.color.chart_grid));
        chart.getXAxis().setTextColor(muted(context));
        chart.getXAxis().setTextSize(10f);
        chart.getXAxis().setDrawLabels(true);
        chart.getLegend().setEnabled(true);
        legend(chart, context);
    }

    public static void legend(Chart<?> chart, Context context) {
        Legend legend = chart.getLegend();
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setTextColor(muted(context));
        legend.setTextSize(11f);
        legend.setWordWrapEnabled(true);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(9f);
        legend.setXEntrySpace(10f);
        legend.setYEntrySpace(4f);
    }

    /** 竖向柱：上亮下深，贴近网页端从上到下的渐变。 */
    public static void verticalGradient(BarDataSet set, Context context, int colorIndex) {
        set.setGradientColor(ChartPalette.gradientTop(context, colorIndex),
                ChartPalette.gradientBottom(context, colorIndex));
        set.setHighLightAlpha(90);
    }

    /** 横向条：左深右亮。 */
    public static void horizontalGradient(BarDataSet set, Context context, int colorIndex) {
        set.setGradientColor(ChartPalette.gradientStart(context, colorIndex),
                ChartPalette.gradientEnd(context, colorIndex));
        set.setHighLightAlpha(90);
    }

    public static void pieColors(PieDataSet set, Context context) {
        List<Integer> colors = new ArrayList<>();
        for (int color : ChartPalette.colors(context)) colors.add(color);
        set.setColors(colors);
        set.setSliceSpace(2.5f);
        set.setHighlightEnabled(true);
        set.setDrawValues(false);
    }

    public static ValueFormatter compactNumber(Context context) {
        return new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return UiUtils.number(context, value);
            }
        };
    }

    public static int muted(Context context) {
        return ContextCompat.getColor(context, R.color.text_secondary);
    }
}
