package com.example.animecentralapp.util;

import android.content.Context;
import android.content.SharedPreferences;

/** App-wide switches from the Settings page, saved on the phone. */
public final class AppSettings {
    private static final String PREFS = "app_settings";
    private static final String KEY_AUTOPLAY = "autoplay_next";

    private AppSettings() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Play the next episode automatically when one ends (default: on). */
    public static boolean isAutoplay(Context c) {
        return prefs(c).getBoolean(KEY_AUTOPLAY, true);
    }

    public static void setAutoplay(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_AUTOPLAY, on).apply();
    }

    /** Light mode on/off (default: off, the app is dark). */
    public static boolean isLight(Context c) {
        return prefs(c).getBoolean("light_mode", false);
    }

    public static void setLight(Context c, boolean light) {
        prefs(c).edit().putBoolean("light_mode", light).apply();
    }

    /** True once the user has passed the sign-in screen (signed in or chose guest). */
    public static boolean hasSeenLogin(Context c) {
        return prefs(c).getBoolean("seen_login", false);
    }

    public static void setSeenLogin(Context c, boolean seen) {
        prefs(c).edit().putBoolean("seen_login", seen).apply();
    }
}
