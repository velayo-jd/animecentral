package com.example.animecentralapp.util;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.fragment.FragmentNavigator;
import androidx.navigation.fragment.NavHostFragment;

import com.example.animecentralapp.R;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.fragments.WatchFragment;
import com.google.android.material.transition.MaterialFadeThrough;

import java.lang.ref.WeakReference;

/** Small helpers for screen-to-screen animation. */
public final class Motion {
    /** The poster's name on the Details screen (see android:transitionName in fragment_details.xml). */
    public static final String POSTER = "anime_poster";

    private static WeakReference<View> lastShared;

    private Motion() {}

    /**
     * Opens Details. If a poster view is given, that picture glides into the Details poster;
     * pass null to just open the page.
     */
    public static void openDetails(@NonNull Fragment host, @NonNull Anime anime,
                                   @Nullable View posterView) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        NavController nav = NavHostFragment.findNavController(host);

        if (posterView == null) {
            nav.navigate(R.id.details, args);
            return;
        }

        // Only one view on screen may carry the shared name at a time
        if (lastShared != null && lastShared.get() != null) {
            ViewCompat.setTransitionName(lastShared.get(), null);
        }
        ViewCompat.setTransitionName(posterView, POSTER);
        lastShared = new WeakReference<>(posterView);

        FragmentNavigator.Extras extras = new FragmentNavigator.Extras.Builder()
                .addSharedElement(posterView, POSTER)
                .build();
        nav.navigate(R.id.details, args, null, extras);
    }

    /**
     * Makes every page fade in and out smoothly when you move between screens.
     * Call once with the nav host's child fragment manager.
     */
    public static void installPageTransitions(@NonNull FragmentManager fm) {
        fm.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentPreCreated(@NonNull FragmentManager manager,
                                             @NonNull Fragment f, @Nullable Bundle state) {
                if (f instanceof WatchFragment) return; // the video page keeps it simple
                if (f.getEnterTransition() == null) f.setEnterTransition(new MaterialFadeThrough());
                if (f.getReturnTransition() == null) f.setReturnTransition(new MaterialFadeThrough());
                if (f.getExitTransition() == null) f.setExitTransition(new MaterialFadeThrough());
                if (f.getReenterTransition() == null) f.setReenterTransition(new MaterialFadeThrough());
            }
        }, false);
    }
}
