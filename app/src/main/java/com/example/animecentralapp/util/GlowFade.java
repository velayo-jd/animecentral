package com.example.animecentralapp.util;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.R;

/**
 * The moon glow sits in the background of the main screen area (drawable/ac_page_bg), behind
 * every page. This makes the pages see-through so the glow shows behind the banner and posters,
 * and fades the glow away as the page scrolls down (it comes back at the top).
 *
 * It listens for scrolling anywhere in the window and asks the page that is on screen how far
 * it has scrolled, so it does not depend on when a page was created.
 */
public final class GlowFade {
    private static final String TAG = "GLOW";

    /** The glow is completely gone after scrolling this far. */
    private static final float FADE_DISTANCE_DP = 320f;

    private GlowFade() {}

    /**
     * @param fm   the nav host's child fragment manager (every page is a fragment in it)
     * @param host the view that has android:background="@drawable/ac_page_bg"
     */
    public static void install(@NonNull FragmentManager fm, @NonNull View host) {
        Drawable background = host.getBackground();
        if (!(background instanceof LayerDrawable)) {
            Log.w(TAG, "The main content view needs android:background=\"@drawable/ac_page_bg\"");
            return;
        }

        // Give this view its own copy, so changing the alpha is safe
        LayerDrawable layers = (LayerDrawable) background.mutate();
        host.setBackground(layers);
        Drawable glow = layers.findDrawableByLayerId(R.id.ac_glow_layer);
        if (glow == null) {
            Log.w(TAG, "ac_page_bg has no ac_glow_layer");
            return;
        }

        float fadePx = FADE_DISTANCE_DP * host.getResources().getDisplayMetrics().density;

        Runnable update = () -> {
            Fragment page = fm.getPrimaryNavigationFragment(); // the screen being shown
            View root = page != null ? page.getView() : null;
            int offset = root == null ? 0 : Math.max(0, scrollOffset(root));
            float left = Math.max(0f, 1f - offset / fadePx);
            glow.setAlpha((int) (255 * left));
        };

        // Called whenever anything in the window scrolls (ScrollView, lists, ...)
        host.getViewTreeObserver().addOnScrollChangedListener(update::run);

        fm.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager manager,
                                              @NonNull Fragment f, @NonNull View v,
                                              @Nullable Bundle savedInstanceState) {
                // Let the glow shine through the page
                v.setBackgroundColor(Color.TRANSPARENT);
                glow.setAlpha(255); // every new page starts with the full glow
            }

            @Override
            public void onFragmentResumed(@NonNull FragmentManager manager, @NonNull Fragment f) {
                host.post(update); // pages that reopen already scrolled
            }
        }, false);

        Log.d(TAG, "Glow fade installed");
    }

    /** How far the first visible vertical scroller in this page has scrolled, or -1 if none. */
    private static int scrollOffset(View v) {
        if (v.getVisibility() != View.VISIBLE) return -1;
        if (v instanceof ScrollView || v instanceof NestedScrollView) return v.getScrollY();
        if (v instanceof RecyclerView) {
            RecyclerView list = (RecyclerView) v;
            RecyclerView.LayoutManager lm = list.getLayoutManager();
            // Sideways rows (like the banner) don't count
            return lm != null && lm.canScrollVertically() ? list.computeVerticalScrollOffset() : -1;
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                int offset = scrollOffset(group.getChildAt(i));
                if (offset >= 0) return offset;
            }
        }
        return -1;
    }
}
