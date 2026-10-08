package com.example.animecentralapp.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Random;
import java.util.List;

/**
 * The user's favorites and watch history, saved on this phone (SharedPreferences).
 * Newest entries come first.
 */
public final class LibraryStore {
    private static final String PREFS = "library_prefs";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 50;

    private LibraryStore() {}

    public static class WatchEntry {
        public final String animeId;
        public final String episodeId; // may be null
        public final boolean finished; // watched the last episode to the end
        public WatchEntry(String animeId, String episodeId, boolean finished) {
            this.animeId = animeId;
            this.episodeId = episodeId;
            this.finished = finished;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- Profile ----------

    private static final String KEY_NAME = "display_name";

    /** The name the user chose on the Profile page, or "" if none. */
    public static String getName(Context c) {
        return prefs(c).getString(KEY_NAME, "");
    }

    public static void setName(Context c, String name) {
        prefs(c).edit().putString(KEY_NAME, name == null ? "" : name.trim()).apply();
    }

    private static final String KEY_GUEST = "guest_name";
    private static final String[] GUEST_WORDS = {
            "swift", "sakura", "ninja", "fox", "mecha", "ramen", "kitsune", "storm", "moon",
            "blade", "spirit", "neko", "comet", "samurai", "dragon", "lucky", "mystic",
            "pixel", "shadow", "starry", "tiger", "wolf", "ember", "cosmic", "frost"};

    /**
     * The name a guest gets, like "guest_ninja_fox482". It is made once and then
     * kept, so a guest keeps the same name every time the app opens.
     */
    public static String getGuestName(Context c) {
        String saved = prefs(c).getString(KEY_GUEST, "");
        if (!saved.isEmpty()) return saved;

        Random r = new Random();
        String name = "guest_" + GUEST_WORDS[r.nextInt(GUEST_WORDS.length)]
                + "_" + GUEST_WORDS[r.nextInt(GUEST_WORDS.length)]
                + (100 + r.nextInt(900));
        prefs(c).edit().putString(KEY_GUEST, name).apply();
        return name;
    }

    /** Name to show: one picked on Profile, else the account name, else the guest name. */
    public static String displayName(Context c, String accountName) {
        String saved = getName(c);
        if (!saved.isEmpty()) return saved;
        if (accountName != null && !accountName.trim().isEmpty()) return accountName.trim();
        return getGuestName(c);
    }

    public static void clearHistory(Context c) {
        prefs(c).edit().remove(KEY_HISTORY).apply();
    }

    // ---------- Favorites ----------

    public static List<String> getFavorites(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_FAVORITES, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (JSONException ignored) { }
        return out;
    }

    public static boolean isFavorite(Context c, String animeId) {
        return animeId != null && getFavorites(c).contains(animeId);
    }

    /** Adds or removes the anime. Returns true if it is now a favorite. */
    public static boolean toggleFavorite(Context c, String animeId) {
        List<String> favs = getFavorites(c);
        boolean nowFavorite;
        if (favs.contains(animeId)) {
            favs.remove(animeId);
            nowFavorite = false;
        } else {
            favs.add(0, animeId);
            nowFavorite = true;
        }
        prefs(c).edit().putString(KEY_FAVORITES, new JSONArray(favs).toString()).apply();
        return nowFavorite;
    }

    // ---------- Watch history ----------

    public static List<WatchEntry> getHistory(Context c) {
        List<WatchEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String ep = o.optString("e", "");
                out.add(new WatchEntry(o.getString("a"), ep.isEmpty() ? null : ep,
                        o.optBoolean("f", false)));
            }
        } catch (JSONException ignored) { }
        return out;
    }

    /** The episode last opened for this anime, or null. */
    public static String getLastEpisode(Context c, String animeId) {
        for (WatchEntry e : getHistory(c)) {
            if (e.animeId.equals(animeId)) return e.episodeId;
        }
        return null;
    }

    /**
     * Moves the anime to the top of the history (opening the player). Keeps the saved episode
     * and "finished" state; a null episode changes nothing else.
     */
    public static void recordWatch(Context c, String animeId, String episodeId) {
        save(c, animeId, episodeId, null);
    }

    /** The user started an episode: remember it, and the anime counts as "in progress". */
    public static void markEpisode(Context c, String animeId, String episodeId) {
        save(c, animeId, episodeId, false);
    }

    /** The last episode ended: the anime leaves "Continue Watching". */
    public static void markFinished(Context c, String animeId) {
        save(c, animeId, null, true);
    }

    private static void save(Context c, String animeId, String episodeId, Boolean finished) {
        if (animeId == null) return;
        List<WatchEntry> history = getHistory(c);
        String episode = episodeId;
        boolean fin = false;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).animeId.equals(animeId)) {
                if (episode == null) episode = history.get(i).episodeId;
                fin = history.get(i).finished;
                history.remove(i);
                break;
            }
        }
        if (finished != null) fin = finished;
        history.add(0, new WatchEntry(animeId, episode, fin));
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);

        JSONArray arr = new JSONArray();
        try {
            for (WatchEntry e : history) {
                JSONObject o = new JSONObject();
                o.put("a", e.animeId);
                o.put("e", e.episodeId == null ? "" : e.episodeId);
                o.put("f", e.finished);
                arr.put(o);
            }
        } catch (JSONException ignored) { }
        prefs(c).edit().putString(KEY_HISTORY, arr.toString()).apply();
    }
}
