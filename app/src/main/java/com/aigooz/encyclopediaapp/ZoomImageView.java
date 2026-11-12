package com.aigooz.encyclopediaapp;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 支持双指缩放与拖拽的图片视图，用于应用内的微博配图查看。
 *
 * 只覆盖微博看图这一处需求，因此实现保持精简：缩放范围 1x–4x，
 * 松手后若已放大则允许拖动，双击可复位。
 */
public class ZoomImageView extends AppCompatImageView {
    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 4f;

    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final PointF lastTouch = new PointF();

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float scale = 1f;
    private float translateX, translateY;
    private boolean dragging;

    public ZoomImageView(Context context) { this(context, null); }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float target = clamp(scale * factor);
                float applied = target / scale;
                scale = target;
                matrix.postScale(applied, applied, detector.getFocusX(), detector.getFocusY());
                constrain();
                setImageMatrix(matrix);
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent event) {
                reset();
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                dragging = scale > MIN_SCALE;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;
                    lastTouch.set(event.getX(), event.getY());
                    matrix.postTranslate(dx, dy);
                    constrain();
                    setImageMatrix(matrix);
                } else {
                    lastTouch.set(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private float clamp(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    /** 把图片限制在可视范围内，避免放大后拖出空白。 */
    private void constrain() {
        matrix.getValues(values);
        float currentScale = values[Matrix.MSCALE_X];
        translateX = values[Matrix.MTRANS_X];
        translateY = values[Matrix.MTRANS_Y];
        float width = getDrawable() == null ? getWidth() : getDrawable().getIntrinsicWidth() * currentScale;
        float height = getDrawable() == null ? getHeight() : getDrawable().getIntrinsicHeight() * currentScale;
        float maxX = Math.max(0, (width - getWidth()) / 2f);
        float maxY = Math.max(0, (height - getHeight()) / 2f);
        float clampedX = Math.max(-maxX, Math.min(maxX, translateX));
        float clampedY = Math.max(-maxY, Math.min(maxY, translateY));
        if (clampedX != translateX || clampedY != translateY) {
            matrix.postTranslate(clampedX - translateX, clampedY - translateY);
        }
    }

    public void reset() {
        scale = 1f;
        matrix.reset();
        setImageMatrix(matrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        reset();
    }
}
