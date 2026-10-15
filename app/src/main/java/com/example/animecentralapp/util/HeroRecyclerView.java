package com.example.animecentralapp.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A sideways RecyclerView that lives inside a vertically scrolling page (and pull-to-refresh).
 * It decides at the start of each gesture whether it is horizontal or vertical, and only lets
 * the parent take it when it is clearly vertical. Diagonal swipes stay with the banner.
 */
public class HeroRecyclerView extends RecyclerView {
    private final int slop;
    private float startX, startY;
    private boolean decided;

    public HeroRecyclerView(@NonNull Context c) { this(c, null); }

    public HeroRecyclerView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        ViewParent parent = getParent();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = e.getX();
                startY = e.getY();
                decided = false;
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!decided) {
                    float dx = Math.abs(e.getX() - startX);
                    float dy = Math.abs(e.getY() - startY);
                    if (Math.max(dx, dy) > slop) {
                        decided = true;
                        // vertical only when clearly more vertical than horizontal
                        boolean vertical = dy > dx * 1.7f;
                        if (vertical && parent != null) {
                            parent.requestDisallowInterceptTouchEvent(false);
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                decided = false;
                break;
        }
        return super.dispatchTouchEvent(e);
    }
}