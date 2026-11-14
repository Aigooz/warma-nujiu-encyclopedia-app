package com.aigooz.encyclopediaapp.chart;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.buffer.BarBuffer;
import com.github.mikephil.charting.buffer.HorizontalBarBuffer;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.model.GradientColor;
import com.github.mikephil.charting.renderer.BarChartRenderer;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.List;

/**
 * 让柱子带上圆角与渐变的渲染器。
 *
 * MPAndroidChart 原生只支持单色方块柱，而网页端的柱子都是「圆角 + 线性渐变」，
 * 这正是手机端显得简陋的主要原因之一。这里沿用库原有的取数、动画与可视范围逻辑，
 * 只把每根柱子的方块换成圆角路径，并按数据集的 {@link GradientColor} 填充渐变。
 *
 * 圆角方向：竖向柱只收顶部两角；横向条形图四角全收（网页端的横向条同样是两端圆角）。
 */
public class RoundedBarChartRenderer extends BarChartRenderer {

    private final RectF barRect = new RectF();
    private final Path barPath = new Path();
    private final float[] radii = new float[8];
    private final float cornerRadiusPx;
    private final boolean horizontal;

    public RoundedBarChartRenderer(BarDataProvider chart, ChartAnimator animator,
                                   ViewPortHandler viewPortHandler, float cornerRadiusDp,
                                   boolean horizontal) {
        super(chart, animator, viewPortHandler);
        this.cornerRadiusPx = Utils.convertDpToPixel(cornerRadiusDp);
        this.horizontal = horizontal;
    }

    /**
     * 横向条形图必须用 {@link HorizontalBarBuffer}：它在缓冲里交换了数值轴与类别轴，
     * 若沿用父类的竖向缓冲，柱子会被压成近乎零宽（这正是初次渲染横向图失败的原因）。
     */
    @Override
    public void initBuffers() {
        BarData barData = mChart.getBarData();
        mBarBuffers = new BarBuffer[barData.getDataSetCount()];
        for (int i = 0; i < mBarBuffers.length; i++) {
            IBarDataSet set = barData.getDataSetByIndex(i);
            int size = set.getEntryCount() * 4 * (set.isStacked() ? set.getStackSize() : 1);
            mBarBuffers[i] = horizontal
                    ? new HorizontalBarBuffer(size, barData.getDataSetCount(), set.isStacked())
                    : new BarBuffer(size, barData.getDataSetCount(), set.isStacked());
        }
    }

    @Override
    protected void drawDataSet(Canvas canvas, IBarDataSet dataSet, int index) {
        Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());
        mShadowPaint.setColor(dataSet.getBarShadowColor());
        mBarBorderPaint.setColor(dataSet.getBarBorderColor());
        mBarBorderPaint.setStrokeWidth(dataSet.getBarBorderWidth());

        boolean drawBorder = dataSet.getBarBorderWidth() > 0f;

        BarBuffer buffer = mBarBuffers[index];
        buffer.setPhases(mAnimator.getPhaseX(), mAnimator.getPhaseY());
        buffer.setDataSet(index);
        buffer.setInverted(mChart.isInverted(dataSet.getAxisDependency()));
        buffer.setBarWidth(mChart.getBarData().getBarWidth());
        buffer.feed(dataSet);

        trans.pointValuesToPixel(buffer.buffer);

        // 逐个数据点的渐变优先；库在未设置时会返回 null，这里统一兜底到单色。
        List<GradientColor> gradientColors = dataSet.getGradientColors();
        GradientColor singleGradient = dataSet.getGradientColor();

        for (int j = 0; j < buffer.size(); j += 4) {
            float left = buffer.buffer[j];
            float top = buffer.buffer[j + 1];
            float right = buffer.buffer[j + 2];
            float bottom = buffer.buffer[j + 3];

            // 可视范围裁剪与父类保持一致的语义，只是两个方向交换了判据。
            if (horizontal) {
                if (!mViewPortHandler.isInBoundsTop(bottom)) break;
                if (!mViewPortHandler.isInBoundsBottom(top)) continue;
            } else {
                if (!mViewPortHandler.isInBoundsLeft(right)) continue;
                if (!mViewPortHandler.isInBoundsRight(left)) break;
            }

            float x0 = Math.min(left, right);
            float x1 = Math.max(left, right);
            float y0 = Math.min(top, bottom);
            float y1 = Math.max(top, bottom);
            if (x1 - x0 < 1f || y1 - y0 < 1f) continue;

            GradientColor gradient = gradientColors != null && !gradientColors.isEmpty()
                    ? gradientColors.get((j / 4) % gradientColors.size()) : singleGradient;
            int startColor;
            int endColor;
            if (gradient != null) {
                startColor = gradient.getStartColor();
                endColor = gradient.getEndColor();
            } else {
                startColor = dataSet.getColor(j / 4);
                endColor = startColor;
            }

            applyRadii(x1 - x0, y1 - y0);
            barRect.set(x0, y0, x1, y1);
            barPath.reset();
            if (cornerRadiusPx > 0.5f) {
                barPath.addRoundRect(barRect, radii, Path.Direction.CW);
            } else {
                barPath.addRect(barRect, Path.Direction.CW);
            }

            mRenderPaint.setStyle(Paint.Style.FILL);
            mRenderPaint.setShader(horizontal
                    ? new LinearGradient(x0, 0f, x1, 0f, startColor, endColor, Shader.TileMode.CLAMP)
                    : new LinearGradient(0f, y0, 0f, y1, startColor, endColor, Shader.TileMode.CLAMP));
            canvas.drawPath(barPath, mRenderPaint);
            mRenderPaint.setShader(null);

            if (drawBorder) {
                mBarBorderPaint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(barPath, mBarBorderPaint);
            }
        }
    }

    /** 写入 8 个圆角半径：[左上×2, 右上×2, 右下×2, 左下×2]。 */
    private void applyRadii(float width, float height) {
        float radius = Math.min(cornerRadiusPx, Math.min(width, height) / 2f);
        if (horizontal) {
            for (int i = 0; i < radii.length; i++) radii[i] = radius;
        } else {
            radii[0] = radius; radii[1] = radius;   // 左上
            radii[2] = radius; radii[3] = radius;   // 右上
            radii[4] = 0f; radii[5] = 0f;           // 右下
            radii[6] = 0f; radii[7] = 0f;           // 左下
        }
    }
}
