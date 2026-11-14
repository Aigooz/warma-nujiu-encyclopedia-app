package com.aigooz.encyclopediaapp.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用热力网格。
 *
 * 网页端用日历热力图、发布时段 × 星期矩阵、标签关联 PMI 矩阵三张热力图承载
 * 「密度/关联强度」这类信息，而 MPAndroidChart 没有热力图控件，手机端此前完全没有对应呈现。
 * 这里用一个自绘 View 统一实现，颜色取调色盘的低→高渐变，风格与网页端一致。
 */
public class HeatmapView extends View {

    /** 单个格子：行、列、数值、以及可选的悬浮标签。 */
    public static class Cell {
        public final int row;
        public final int column;
        public final float value;
        public final String label;

        public Cell(int row, int column, float value, String label) {
            this.row = row;
            this.column = column;
            this.value = value;
            this.label = label;
        }
    }

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private List<Cell> cells = new ArrayList<>();
    private String[] rowLabels = new String[0];
    private String[] columnLabels = new String[0];
    private boolean[] columnLabelVisible = new boolean[0];
    private float maxValue = 1f;
    private float minValue = 0f;
    private int emptyColor = 0xFF1B2124;
    private int lowColor = 0xFF7C5CFF;
    private int highColor = 0xFFFF6B8A;
    private int textColor = 0xFF9AA7B8;
    private boolean showValues = false;
    private boolean drawEmptyCells = true;
    private int rowLabelWidth;

    public HeatmapView(Context context) { this(context, null); }

    public HeatmapView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }

    public HeatmapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        labelPaint.setTextSize(sp(10f));
        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        axisPaint.setTextSize(sp(10f));
        axisPaint.setColor(textColor);
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        rowLabelWidth = (int) dp(46);
    }

    /** 行数为 0 时显示占位文案。 */
    public void setData(List<Cell> cells, String[] rowLabels, String[] columnLabels) {
        setData(cells, rowLabels, columnLabels, null);
    }

    public void setData(List<Cell> cells, String[] rowLabels, String[] columnLabels,
                        boolean[] columnLabelVisible) {
        this.cells = cells == null ? new ArrayList<>() : cells;
        this.rowLabels = rowLabels == null ? new String[0] : rowLabels;
        this.columnLabels = columnLabels == null ? new String[0] : columnLabels;
        this.columnLabelVisible = columnLabelVisible == null ? new boolean[0] : columnLabelVisible;
        this.maxValue = 1f;
        this.minValue = 0f;
        for (Cell cell : this.cells) maxValue = Math.max(maxValue, cell.value);
        requestLayout();
        invalidate();
    }

    public void setColors(int empty, int low, int high, int text) {
        this.emptyColor = empty;
        this.lowColor = low;
        this.highColor = high;
        this.textColor = text;
        axisPaint.setColor(text);
        invalidate();
    }

    public void setShowValues(boolean show) {
        this.showValues = show;
        invalidate();
    }

    /** 是否先铺满底色网格。日历/矩阵类热力图铺底更像网页端的网格。 */
    public void setDrawEmptyCells(boolean draw) {
        this.drawEmptyCells = draw;
        invalidate();
    }

    public void setRowLabelWidthDp(float dp) {
        this.rowLabelWidth = (int) dp(dp);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int rows = Math.max(1, rowLabels.length);
        int columns = Math.max(1, columnLabels.length);
        // 让格子接近正方形：整体高度按列数推算，同时限制上下界避免过高/过矮。
        float available = Math.max(80f, width - getPaddingLeft() - getPaddingRight()
                - rowLabelWidth - dp(10));
        float cell = available / columns;
        float contentHeight = cell * rows + dp(22) + getPaddingTop() + getPaddingBottom();
        int desired = (int) Math.max(dp(120), Math.min(contentHeight, dp(420)));
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cells.isEmpty() || rowLabels.length == 0 || columnLabels.length == 0) {
            axisPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无数据", getWidth() / 2f, getHeight() / 2f, axisPaint);
            axisPaint.setTextAlign(Paint.Align.RIGHT);
            return;
        }

        int rows = rowLabels.length;
        int columns = columnLabels.length;
        // 尊重布局里声明的 padding，避免自定义 View 直接吞掉它。
        float gridLeft = getPaddingLeft() + rowLabelWidth;
        float gridRight = getWidth() - getPaddingRight() - dp(6);
        float gridTop = getPaddingTop() + dp(18);
        float gridBottom = getHeight() - getPaddingBottom() - dp(4);
        if (gridRight - gridLeft < dp(20) || gridBottom - gridTop < dp(20)) return;
        float cellWidth = (gridRight - gridLeft) / columns;
        float cellHeight = (gridBottom - gridTop) / rows;

        // 列标签
        axisPaint.setTextAlign(Paint.Align.CENTER);
        for (int c = 0; c < columns; c++) {
            boolean visible = columnLabelVisible.length == 0 || columnLabelVisible[c];
            if (!visible) continue;
            float x = gridLeft + cellWidth * (c + 0.5f);
            canvas.drawText(columnLabels[c], x, gridTop - dp(6), axisPaint);
        }

        // 行标签
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        for (int r = 0; r < rows; r++) {
            float y = gridTop + cellHeight * (r + 0.5f) + labelPaint.getTextSize() / 3f;
            canvas.drawText(rowLabels[r], gridLeft - dp(6), y, axisPaint);
        }

        cellPaint.setStyle(Paint.Style.FILL);

        // 密集网格（例如 52 周的日历）里，固定的 1.5dp 内缩会把格子本身吃掉，
        // 圆角也会显得过大。这里按格子尺寸自适应，保证任何密度下都能看出方块。
        float inset = Math.min(dp(1.5f), Math.min(cellWidth, cellHeight) * 0.14f);
        float radius = Math.min(dp(3f), Math.min(cellWidth, cellHeight) * 0.3f);

        // 先铺一层空网格底色：网页端的日历与矩阵都会画出空格子，
        // 否则只有零星几个有色格子会显得很稀疏、看不出结构。
        if (drawEmptyCells) {
            cellPaint.setColor(emptyColor);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    float left = gridLeft + cellWidth * c + inset;
                    float top = gridTop + cellHeight * r + inset;
                    float right = gridLeft + cellWidth * (c + 1) - inset;
                    float bottom = gridTop + cellHeight * (r + 1) - inset;
                    if (right - left < 0.5f || bottom - top < 0.5f) continue;
                    rect.set(left, top, right, bottom);
                    canvas.drawRoundRect(rect, radius, radius, cellPaint);
                }
            }
        }

        for (Cell cell : cells) {
            if (cell.row < 0 || cell.row >= rows || cell.column < 0 || cell.column >= columns) continue;
            float left = gridLeft + cellWidth * cell.column + inset;
            float top = gridTop + cellHeight * cell.row + inset;
            float right = gridLeft + cellWidth * (cell.column + 1) - inset;
            float bottom = gridTop + cellHeight * (cell.row + 1) - inset;
            if (right - left < 0.5f || bottom - top < 0.5f) continue;
            rect.set(left, top, right, bottom);
            cellPaint.setColor(colorFor(cell.value));
            canvas.drawRoundRect(rect, radius, radius, cellPaint);
            if (showValues && cell.label != null && cellWidth > dp(22)) {
                canvas.drawText(cell.label, rect.centerX(),
                        rect.centerY() + labelPaint.getTextSize() / 3f, labelPaint);
            }
        }
    }

    /** 数值 → 颜色：0 用底色，其余在线性插值到最高值。 */
    private int colorFor(float value) {
        if (value <= 0f) return emptyColor;
        float ratio = maxValue <= minValue ? 1f : (value - minValue) / (maxValue - minValue);
        ratio = Math.max(0.12f, Math.min(1f, ratio));
        return blend(lowColor, highColor, ratio);
    }

    private static int blend(int from, int to, float ratio) {
        return android.graphics.Color.argb(
                android.graphics.Color.alpha(from),
                (int) (android.graphics.Color.red(from)
                        + (android.graphics.Color.red(to) - android.graphics.Color.red(from)) * ratio),
                (int) (android.graphics.Color.green(from)
                        + (android.graphics.Color.green(to) - android.graphics.Color.green(from)) * ratio),
                (int) (android.graphics.Color.blue(from)
                        + (android.graphics.Color.blue(to) - android.graphics.Color.blue(from)) * ratio));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                value, getResources().getDisplayMetrics());
    }

    /** 供外部按当前主题刷新文字/底色的便捷方法。 */
    public void applyThemeColors(int empty, int low, int high, int text) {
        setColors(empty, low, high, text);
        invalidate();
    }

}
