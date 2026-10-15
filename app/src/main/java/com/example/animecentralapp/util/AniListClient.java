package com.example.animecentralapp.util;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import java.util.Locale;

import com.google.firebase.firestore.FieldValue;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny AniList (GraphQL) client: search anime, or list the most popular ones. No API key needed. */
public final class AniListClient {
    private AniListClient() {}

    private static final String URL_GRAPHQL = "https://graphql.anilist.co";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final String QUERY =
            "query ($search: String, $sort: [MediaSort]) {"
          + "  Page(page: 1, perPage: 20) {"
          + "    media(search: $search, type: ANIME, isAdult: false, sort: $sort) {"
          + "      id title { romaji english } description(asHtml: false) genres status"
          + "      episodes format season seasonYear startDate { year }"
          + "      coverImage { extraLarge large } bannerImage"
          + "    }"
          + "  }"
          + "}";

    public interface Callback {
        void onResult(@Nullable List<Media> results, @Nullable String error);
    }

    /** One anime as AniList describes it. */
    public static class Media {
        public int id;
        public String title;        // English title if there is one, else romaji
        public String altTitle;     // the other one (may be null)
        public String synopsis;     // plain text
        public List<String> genres = new ArrayList<>();
        public String status;       // already mapped to the app's wording
        public int episodes;
        public int year;
        public String season;       // "Fall 2026"
        public String format;       // TV, MOVIE, OVA...
        public String posterUrl;
        public String bannerUrl;

        public boolean isMovie() { return "MOVIE".equals(format); }

        /** "TV · 2026 · 12 eps · Ongoing" */
        public String summaryLine() {
            List<String> parts = new ArrayList<>();
            parts.add(formatName() == null ? "Anime" : formatName());
            if (season != null) parts.add(season); else if (year > 0) parts.add(String.valueOf(year));
            if (episodes > 0) parts.add(episodes + " eps");
            if (status != null) parts.add(status);
            return android.text.TextUtils.join(" · ", parts);
        }

        /** AniList's format as people read it. */
        public String formatName() {
            if (format == null) return null;
            switch (format) {
                case "TV_SHORT": return "TV Short";
                case "MOVIE":    return "Movie";
                case "TV": case "SPECIAL": case "OVA": case "ONA": case "MUSIC":
                    return format.length() <= 3 ? format : format.charAt(0) + format.substring(1).toLowerCase();
                default:         return format.replace('_', ' ');
            }
        }

        /** The fields saved on the Firestore document anime/{id}. */
        public Map<String, Object> toFirestoreMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("anilistId", id);
            m.put("title", title);
            m.put("synopsis", synopsis == null ? "" : synopsis);
            m.put("genres", genres);
            if (status != null) m.put("status", status);
            m.put("episodes", episodes);
            m.put("year", year);
            if (season != null) m.put("season", season);
            if (formatName() != null) m.put("format", formatName());
            m.put("type", FieldValue.delete()); // removes the old field when you press Update
            if (posterUrl != null) m.put("posterUrl", posterUrl);
            if (bannerUrl != null) m.put("thumbnailUrl", bannerUrl);
            return m;
        }
    }

    /** Blank query = the most popular anime right now. Results arrive on the main thread. */
    public static void search(String query, Callback callback) {
        final String q = query == null ? "" : query.trim();
        IO.execute(() -> {
            List<Media> list = null;
            String error = null;
            try {
                list = fetch(q);
            } catch (RateLimited e) {
                error = "AniList is busy. Wait a minute and try again.";
            } catch (Exception e) {
                error = "Couldn't reach AniList. Check your connection.";
            }
            final List<Media> fl = list;
            final String fe = error;
            MAIN.post(() -> callback.onResult(fl, fe));
        });
    }

    private static class RateLimited extends Exception {}

    private static List<Media> fetch(String q) throws Exception {
        JSONObject vars = new JSONObject();
        if (q.isEmpty()) {
            vars.put("sort", new JSONArray().put("POPULARITY_DESC"));
        } else {
            vars.put("search", q);
            vars.put("sort", new JSONArray().put("SEARCH_MATCH"));
        }
        JSONObject body = new JSONObject();
        body.put("query", QUERY);
        body.put("variables", vars);

        HttpURLConnection c = (HttpURLConnection) new URL(URL_GRAPHQL).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if (code == 429) throw new RateLimited();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            if (code >= 400) throw new Exception("HTTP " + code);
            JSONObject root = new JSONObject(out.toString("UTF-8"));
            JSONArray media = root.getJSONObject("data").getJSONObject("Page")
                    .getJSONArray("media");
            List<Media> result = new ArrayList<>();
            for (int i = 0; i < media.length(); i++) {
                result.add(parse(media.getJSONObject(i)));
            }
            return result;
        } finally {
            c.disconnect();
        }
    }

    private static Media parse(JSONObject o) {
        Media m = new Media();
        m.id = o.optInt("id");
        JSONObject t = o.optJSONObject("title");
        String english = str(t, "english");
        String romaji = str(t, "romaji");
        m.title = english != null ? english : romaji;
        m.altTitle = english != null ? romaji : null;

        String desc = str(o, "description");
        m.synopsis = desc == null ? "" : cleanDescription(desc);

        JSONArray g = o.optJSONArray("genres");
        if (g != null) for (int i = 0; i < g.length(); i++) {
            String s = g.optString(i, "").trim();
            if (!s.isEmpty()) m.genres.add(s);
        }

        m.status = mapStatus(str(o, "status"));
        m.episodes = o.isNull("episodes") ? 0 : o.optInt("episodes", 0);
        m.format = str(o, "format");
        int year = o.isNull("seasonYear") ? 0 : o.optInt("seasonYear", 0);
        if (year == 0) {
            JSONObject sd = o.optJSONObject("startDate");
            if (sd != null && !sd.isNull("year")) year = sd.optInt("year", 0);
        }
        m.year = year;
        String sn = str(o, "season");
        if (sn != null && year > 0) {
            m.season = sn.charAt(0) + sn.substring(1).toLowerCase(Locale.ROOT) + " " + year;
        }

        JSONObject cover = o.optJSONObject("coverImage");
        String poster = str(cover, "extraLarge");
        m.posterUrl = poster != null ? poster : str(cover, "large");
        m.bannerUrl = str(o, "bannerImage");
        return m;
    }

    @Nullable
    private static String str(@Nullable JSONObject o, String key) {
        if (o == null || o.isNull(key)) return null;
        String s = o.optString(key, "").trim();
        return s.isEmpty() ? null : s;
    }

    /** AniList descriptions contain <br>, <i> and ~!spoiler!~ markers. */
    private static String cleanDescription(String raw) {
        String s = raw.replaceAll("(?s)~!.*?!~", "");
        s = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
        s = s.replaceAll("\\n{3,}", "\n\n").trim();
        return s;
    }

    private static String mapStatus(@Nullable String s) {
        if (s == null) return null;
        switch (s) {
            case "FINISHED": return "Completed";
            case "RELEASING": return "Ongoing";
            case "NOT_YET_RELEASED": return "Upcoming";
            case "CANCELLED": return "Cancelled";
            case "HIATUS": return "Hiatus";
            default: return null;
        }
    }
}
