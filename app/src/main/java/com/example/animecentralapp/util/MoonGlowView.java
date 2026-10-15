package com.example.animecentralapp.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.animecentralapp.R;

/**
 * A soft glow that radiates from the moon in the top-bar logo. Dark mode only.
 * Never takes touches.
 */
public class MoonGlowView extends View {
    /** Where the moon sits inside the logo image (0..1). Nudge these if the glow looks off. */
    private static final float MOON_X = 0.5f, MOON_Y = 0.5f;

    private final Paint wide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp = getResources().getDisplayMetrics().density;

    private View anchor;
    private float cx, cy;                 // glow centre, in this view's pixels
    private float rx, ry, coreR;          // glow shape
    private boolean ready;

    public MoonGlowView(Context c) { this(c, null); }

    public MoonGlowView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(false);
        setFocusable(false);
    }

    /** Point the glow at the logo. Pass null to hide it. */
    public void anchorTo(View logo) {
        if (anchor != null) anchor.removeOnLayoutChangeListener(relayout);
        anchor = logo;
        if (logo == null) {
            ready = false;
            invalidate();
            return;
        }
        logo.addOnLayoutChangeListener(relayout);
        logo.post(this::rebuild);
    }

    private final View.OnLayoutChangeListener relayout =
            (v, l, t, r, b, ol, ot, or, ob) -> rebuild();

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuild();
    }

    /** A smooth bell curve of alpha, so there is no visible edge or banding. */
    private static RadialGradient bell(float cx, float cy, float r, int rgb, float peakAlpha) {
        int n = 12;
        int[] colors = new int[n];
        float[] stops = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float a = (float) Math.exp(-4.0 * t * t) * (1f - t * t * t * t); // ends at exactly 0
            colors[i] = ((int) (255 * Math.min(1f, peakAlpha) * a) << 24) | (rgb & 0x00FFFFFF);
            stops[i] = t;
        }
        return new RadialGradient(cx, cy, r, colors, stops, Shader.TileMode.CLAMP);
    }

    private void rebuild() {
        if (anchor == null || !anchor.isAttachedToWindow() || anchor.getWidth() == 0) return;

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (!night) {                     // no glow in light mode
            ready = false;
            invalidate();
            return;
        }

        int[] a = new int[2], me = new int[2];
        anchor.getLocationInWindow(a);
        getLocationInWindow(me);
        cx = a[0] - me[0] + anchor.getWidth() * MOON_X;
        cy = a[1] - me[1] + anchor.getHeight() * MOON_Y;

        int rgb = ContextCompat.getColor(getContext(), R.color.ac_glow_start) & 0x00FFFFFF;

        // Shaders are built around (0,0); onDraw moves the canvas to the moon and squashes
        // the wide halo into an ellipse.
        rx = 260 * dp; ry = 90 * dp; coreR = 46 * dp;
        wide.setShader(bell(0, 0, rx, rgb, 0.30f * 1.2f));
        core.setShader(bell(0, 0, coreR, rgb, 0.55f * 1.2f));
        wide.setDither(true);
        core.setDither(true);

        ready = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!ready) return;

        // wide halo: squashed vertically into an ellipse
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(1f, ry / rx);
        canvas.drawCircle(0, 0, rx, wide);
        canvas.restore();

        // small bloom right at the moon
        canvas.save();
        canvas.translate(cx, cy);
        canvas.drawCircle(0, 0, coreR, core);
        canvas.restore();
    }
}