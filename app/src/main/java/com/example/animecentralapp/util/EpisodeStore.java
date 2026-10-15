package com.example.animecentralapp.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Per-anime "watched" and "bookmarked" episode ids, kept on the device. */
public final class EpisodeStore {
    private static final String PREFS = "episode_state";

    private EpisodeStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> getWatched(Context c, String animeId) {
        return new HashSet<>(prefs(c).getStringSet("watched_" + animeId, new HashSet<>()));
    }

    public static Set<String> getBookmarks(Context c, String animeId) {
        return new HashSet<>(prefs(c).getStringSet("bookmarks_" + animeId, new HashSet<>()));
    }

    /** Marks as watched, or un-marks if it already was. Returns the new state. */
    public static boolean toggleWatched(Context c, String animeId, String episodeId) {
        return toggle(c, "watched_" + animeId, episodeId);
    }

    /** Bookmarks, or removes the bookmark if it already was. Returns the new state. */
    public static boolean toggleBookmark(Context c, String animeId, String episodeId) {
        return toggle(c, "bookmarks_" + animeId, episodeId);
    }

    private static boolean toggle(Context c, String key, String episodeId) {
        Set<String> set = new HashSet<>(prefs(c).getStringSet(key, new HashSet<>()));
        boolean now = !set.remove(episodeId);
        if (now) set.add(episodeId);
        prefs(c).edit().putStringSet(key, set).apply();
        return now;
    }
}
