package com.example.animecentralapp.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.R;

/**
 * Swipe right = toggle watched, swipe left = toggle bookmark.
 *
 * The row has weight: it follows the finger with growing resistance (rubber band) and always
 * springs back to its place. Pull far enough and the revealed strip lights up; let go while it is
 * lit and the action fires, with the colour held while the row snaps back.
 */
public class EpisodeSwipeCallback extends ItemTouchHelper.SimpleCallback {

    public interface Host {
        boolean isWatchedAt(int position);
        boolean isBookmarkedAt(int position);
        void onSwipeRight(int position);
        void onSwipeLeft(int position);
    }

    /** How far you must pull (fraction of the row width) for the action to count. */
    private static final float COMMIT = 0.35f;
    /** The row can never travel further than this (fraction of the row width). Lower = heavier. */
    private static final float MAX_TRAVEL = 0.40f;
    /** Length of the snap-back, in ms. */
    private static final long SNAP_MS = 260;

    private final Host host;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable check, uncheck, bookmarkAdd, bookmarkRemove;
    private final int doneColor, bookmarkColor, idleColor, idleIconColor, iconSize, iconCenter;

    private boolean wasLit = false;     // crossed the commit point during this drag
    private boolean releasedLit = false; // let go while lit; the action has been (or is being) fired
    private boolean lastRight = false;

    public EpisodeSwipeCallback(Context c, Host host) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.host = host;
        float dp = c.getResources().getDisplayMetrics().density;
        iconSize = (int) (24 * dp);
        iconCenter = (int) (28 * dp);
        doneColor = ContextCompat.getColor(c, R.color.ac_accent);
        bookmarkColor = Color.parseColor("#C9951F");
        idleColor = ContextCompat.getColor(c, R.color.ac_row);
        idleIconColor = ContextCompat.getColor(c, R.color.ac_icon);
        check = icon(c, R.drawable.ic_check);
        uncheck = icon(c, R.drawable.ic_close_x);
        bookmarkAdd = icon(c, R.drawable.ic_bookmark_add);
        bookmarkRemove = icon(c, R.drawable.ic_bookmark_remove);
    }

    private static Drawable icon(Context c, int res) {
        return ContextCompat.getDrawable(c, res).mutate();
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    // The row never "completes" a swipe: it always snaps back. We fire the action ourselves.
    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder vh) {
        return 10f;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return Float.MAX_VALUE; // a fast flick must not throw the row away either
    }

    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
        return Float.MAX_VALUE;
    }

    @Override
    public long getAnimationDuration(@NonNull RecyclerView rv, int animationType,
                                     float animateDx, float animateDy) {
        return SNAP_MS;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        // never reached (see getSwipeThreshold)
    }

    @Override
    public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        super.clearView(rv, vh);
        wasLit = false;
        releasedLit = false;
    }

    /** Rubber band: 1:1 at first, then ever heavier, never past MAX_TRAVEL * width. */
    private static float resist(float rawDx, float width) {
        float cap = width * MAX_TRAVEL;
        float sign = Math.signum(rawDx);
        return sign * cap * (1f - (float) Math.exp(-Math.abs(rawDx) / cap));
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            return;
        }
        View v = vh.itemView;
        int pos = vh.getBindingAdapterPosition();
        float shown = resist(dX, v.getWidth()); // what the user actually sees

        if (dX != 0 && pos != RecyclerView.NO_POSITION) {
            boolean right = dX > 0;
            boolean pulledFar = Math.abs(dX) >= v.getWidth() * COMMIT;

            if (isCurrentlyActive) {
                // Tick the moment the pull becomes "enough"
                if (pulledFar && !wasLit) {
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                }
                wasLit = pulledFar;
                lastRight = right;
            } else if (wasLit && !releasedLit) {
                // Let go while lit: fire the action once, with a firmer buzz
                releasedLit = true;
                v.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                        ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
                final boolean fireRight = lastRight;
                v.post(() -> {
                    int p = vh.getBindingAdapterPosition();
                    if (p == RecyclerView.NO_POSITION) return;
                    if (fireRight) host.onSwipeRight(p);
                    else host.onSwipeLeft(p);
                });
            }

            // Colour stays lit while pulled far, and while snapping back after a commit
            boolean lit = isCurrentlyActive ? pulledFar : releasedLit;

            paint.setColor(lit ? (right ? doneColor : bookmarkColor) : idleColor);
            Drawable icon;
            if (right) icon = host.isWatchedAt(pos) ? uncheck : check;
            else icon = host.isBookmarkedAt(pos) ? bookmarkRemove : bookmarkAdd;
            icon.setTint(lit ? Color.WHITE : idleIconColor);

            float left = right ? v.getLeft() : v.getRight() + shown;
            float rightEdge = right ? v.getLeft() + shown : v.getRight();
            int top = v.getTop();
            int bottom = v.getBottom();

            c.save();
            c.clipRect(left, top, rightEdge, bottom);
            c.drawRect(left, top, rightEdge, bottom, paint);
            int cx = right ? Math.round(rightEdge) - iconCenter : Math.round(left) + iconCenter;
            int cy = (top + bottom) / 2;
            icon.setBounds(cx - iconSize / 2, cy - iconSize / 2,
                    cx + iconSize / 2, cy + iconSize / 2);
            icon.draw(c);
            c.restore();
        }
        super.onChildDraw(c, rv, vh, shown, dY, actionState, isCurrentlyActive);
    }
}