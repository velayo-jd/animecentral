package com.example.animecentralapp.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.animecentralapp.R;

/**
 * A grey box with a light band sweeping across it: the loading placeholder.
 * It keeps itself animating while it is on screen and stops by itself when it is not,
 * so it can be used as a view background or as a Glide placeholder.
 */
public class ShimmerDrawable extends Drawable {
    private static final long PERIOD_MS = 1300;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final Runnable tick = this::invalidateSelf;
    private final float radius;
    private final int highlight;
    private LinearGradient shader;
    private float bandWidth;

    /** @param radiusPx corner radius in pixels (0 for square corners) */
    public ShimmerDrawable(Context context, float radiusPx) {
        this.radius = radiusPx;
        basePaint.setColor(ContextCompat.getColor(context, R.color.ac_shimmer_base));
        highlight = ContextCompat.getColor(context, R.color.ac_shimmer_hi);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        bandWidth = Math.max(1f, bounds.width() * 0.7f);
        int clear = highlight & 0x00FFFFFF;
        shader = new LinearGradient(0, 0, bandWidth, 0,
                new int[]{clear, highlight, clear}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        bandPaint.setShader(shader);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || shader == null) return;
        rect.set(b);
        canvas.drawRoundRect(rect, radius, radius, basePaint);

        float phase = (SystemClock.uptimeMillis() % PERIOD_MS) / (float) PERIOD_MS;
        float x = b.left - bandWidth + (b.width() + bandWidth) * phase;
        matrix.setTranslate(x, b.top);
        shader.setLocalMatrix(matrix);
        canvas.drawRoundRect(rect, radius, radius, bandPaint);

        // Ask for the next frame (stops by itself once nothing draws this any more)
        unscheduleSelf(tick);
        scheduleSelf(tick, SystemClock.uptimeMillis() + 16);
    }

    @Override
    public void setAlpha(int alpha) {
        basePaint.setAlpha(alpha);
        bandPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        basePaint.setColorFilter(colorFilter);
        bandPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
