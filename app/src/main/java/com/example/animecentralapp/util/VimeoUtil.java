package com.example.animecentralapp.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Vimeo links and asks Vimeo for a video's thumbnail (oEmbed, no key needed). */
public final class VimeoUtil {
    private VimeoUtil() {}

    public static class Parsed {
        public final String id;
        @Nullable public final String hash; // only unlisted videos have one
        Parsed(String id, @Nullable String hash) { this.id = id; this.hash = hash; }

        /** A normal Vimeo page link for this video. */
        public String pageUrl() {
            return "https://vimeo.com/" + id + (hash == null ? "" : "/" + hash);
        }
    }

    private static final Pattern PLAIN = Pattern.compile("^\\s*(\\d{5,})\\s*$");
    private static final Pattern ID_IN_URL =
            Pattern.compile("(?:player\\.vimeo\\.com/video/|vimeo\\.com/(?:[^?#\\s]*/)?)(\\d{5,})");
    private static final Pattern HASH_QUERY = Pattern.compile("[?&]h=([0-9a-fA-F]{6,})");
    private static final Pattern HASH_PATH =
            Pattern.compile("vimeo\\.com/(?:[^?#\\s]*/)?\\d{5,}/([0-9a-fA-F]{6,})");

    /**
     * Accepts https://vimeo.com/76979871, https://vimeo.com/76979871/abc123def4 (unlisted),
     * https://player.vimeo.com/video/76979871?h=abc123def4, or just 76979871.
     * Returns null if it doesn't look like a Vimeo video.
     */
    @Nullable
    public static Parsed parse(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim();
        Matcher plain = PLAIN.matcher(s);
        if (plain.matches()) return new Parsed(plain.group(1), null);

        Matcher id = ID_IN_URL.matcher(s);
        if (!id.find()) return null;
        String hash = null;
        Matcher hq = HASH_QUERY.matcher(s);
        if (hq.find()) hash = hq.group(1);
        else {
            Matcher hp = HASH_PATH.matcher(s);
            if (hp.find()) hash = hp.group(1);
        }
        return new Parsed(id.group(1), hash);
    }

    // ---------- thumbnail lookup ----------

    public interface ThumbCallback { void onResult(@Nullable String thumbnailUrl); }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Calls back on the main thread with the thumbnail URL, or null if Vimeo wouldn't say. */
    public static void fetchThumbnail(Parsed p, ThumbCallback callback) {
        IO.execute(() -> {
            String result = null;
            HttpURLConnection c = null;
            try {
                String api = "https://vimeo.com/api/oembed.json?width=640&url="
                        + URLEncoder.encode(p.pageUrl(), "UTF-8");
                c = (HttpURLConnection) new URL(api).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() == 200) {
                    InputStream in = c.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    JSONObject o = new JSONObject(out.toString("UTF-8"));
                    String t = o.optString("thumbnail_url", "");
                    if (!t.isEmpty()) result = t;
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            final String r = result;
            MAIN.post(() -> callback.onResult(r));
        });
    }
}
