package com.example.animecentralapp.util;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads YouTube links or bare 11-character video IDs. */
public final class YouTubeUtil {
    private YouTubeUtil() {}

    private static final Pattern PLAIN = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern IN_URL = Pattern.compile(
            "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/|v/))"
                    + "([A-Za-z0-9_-]{11})");

    /** Accepts youtu.be/ID, youtube.com/watch?v=ID, /embed/ID, /shorts/ID, or just the ID. */
    @Nullable
    public static String parseId(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim();
        if (PLAIN.matcher(s).matches()) return s;
        Matcher m = IN_URL.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** YouTube serves a thumbnail for every video, no lookup needed. */
    public static String thumbnailUrl(String id) {
        return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }
}