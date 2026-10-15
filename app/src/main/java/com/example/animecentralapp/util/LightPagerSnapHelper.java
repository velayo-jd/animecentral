package com.example.animecentralapp.util;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A PagerSnapHelper that turns the page after a short drag (about 12% of the width),
 * instead of needing half a page or a hard fling.
 */
public class LightPagerSnapHelper extends PagerSnapHelper {
    private static final float ADVANCE_FRACTION = 0.12f;

    private float dragDx;     // how far the finger has dragged since the touch began

    public void resetDrag() { dragDx = 0f; }

    public void addDrag(int dx) { dragDx += dx; }

    /** The page the banner is currently closest to (no bias). Use this for the dots. */
    @Nullable
    public View nearestView(RecyclerView.LayoutManager lm) {
        return super.findSnapView(lm);
    }

    @Nullable
    @Override
    public View findSnapView(RecyclerView.LayoutManager lm) {
        View nearest = super.findSnapView(lm);
        if (nearest == null) return null;

        // Dragged far enough to count as a swipe? Then move on to the neighbour page.
        if (Math.abs(dragDx) > nearest.getWidth() * ADVANCE_FRACTION) {
            int pos = lm.getPosition(nearest);
            View neighbour = lm.findViewByPosition(dragDx > 0 ? pos + 1 : pos - 1);
            if (neighbour != null) return neighbour;
        }
        return nearest;
    }
}