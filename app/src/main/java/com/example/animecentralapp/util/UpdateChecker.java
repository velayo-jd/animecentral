package com.example.animecentralapp.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Looks at the newest GitHub release and says whether it is newer than the installed app.
 * The release tag (v1.0.1) is compared with the app's versionName (1.0.1).
 */
public final class UpdateChecker {
    private UpdateChecker() {}

    /** The GitHub repo whose Releases page holds the APKs. */
    public static final String REPO = "velayo-jd/animecentral";

    private static final String PREFS = "app_update";
    private static final long AUTO_INTERVAL_MS = 12L * 60 * 60 * 1000; // at most twice a day

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static class Info {
        public String version;  // "1.0.1"
        public String notes;    // release notes
        public String apkUrl;   // direct download link
        public long size;       // bytes (0 if unknown)
    }

    public interface Callback {
        /** newer != null: update found. Both null: already up to date. error != null: failed. */
        void onResult(@Nullable Info newer, @Nullable String error);
    }

    // ---------- public API ----------

    /** Checks now, from any screen. The callback runs on the main thread. */
    public static void check(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            Info found = null;
            String error = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.github.com/repos/" + REPO + "/releases/latest");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "AnimeCentral-Android");
                int code = conn.getResponseCode();
                if (code == 404) {
                    // no release published yet: nothing to update to
                } else if (code != 200) {
                    error = "Couldn't check for updates (GitHub said " + code + ")";
                } else {
                    found = parse(readAll(conn.getInputStream()), app);
                    if (found == null) {
                        // either up to date, or the release has no APK; treated as up to date
                    }
                }
            } catch (Exception e) {
                error = "Couldn't reach GitHub. Check your connection.";
            } finally {
                if (conn != null) conn.disconnect();
            }

            if (error == null) {
                prefs(app).edit().putString("latest", found != null ? found.version : "").apply();
            }
            final Info result = found;
            final String err = error;
            MAIN.post(() -> callback.onResult(result, err));
        });
    }

    /** Called when the app starts: checks quietly and shows the dialog only if there is an update. */
    public static void autoCheck(Activity activity) {
        SharedPreferences p = prefs(activity);
        long now = System.currentTimeMillis();
        if (now - p.getLong("last_check", 0) < AUTO_INTERVAL_MS) return;
        check(activity, (info, error) -> {
            if (error == null) p.edit().putLong("last_check", System.currentTimeMillis()).apply();
            if (info != null && !activity.isFinishing() && !activity.isDestroyed()) {
                UpdateDialog.show(activity, info);
            }
        });
    }

    /** Version number of a newer release found earlier (for the Settings row), or null. */
    @Nullable
    public static String pendingVersion(Context context) {
        String latest = prefs(context).getString("latest", "");
        if (latest == null || latest.isEmpty()) return null;
        return isNewer(latest, currentVersion(context)) ? latest : null;
    }

    public static String currentVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    // ---------- internals ----------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    private static Info parse(String json, Context app) throws Exception {
        JSONObject o = new JSONObject(json);
        String tag = o.optString("tag_name", "");
        if (tag.isEmpty() || !isNewer(tag, currentVersion(app))) return null;

        JSONArray assets = o.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name", "");
            if (name.toLowerCase().endsWith(".apk")) {
                Info info = new Info();
                info.version = stripV(tag);
                info.notes = o.optString("body", "");
                info.apkUrl = a.optString("browser_download_url", "");
                info.size = a.optLong("size", 0);
                return info.apkUrl.isEmpty() ? null : info;
            }
        }
        return null; // release without an APK attached
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String stripV(String v) {
        String s = v.trim();
        return (s.startsWith("v") || s.startsWith("V")) ? s.substring(1) : s;
    }

    /** True when version a is higher than version b ("1.0.10" > "1.0.9"). */
    static boolean isNewer(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        int len = Math.max(x.length, y.length);
        for (int i = 0; i < len; i++) {
            int xi = i < x.length ? x[i] : 0;
            int yi = i < y.length ? y[i] : 0;
            if (xi != yi) return xi > yi;
        }
        return false;
    }

    private static int[] numbers(String v) {
        String s = stripV(v);
        int dash = s.indexOf('-');
        if (dash >= 0) s = s.substring(0, dash); // ignore "-beta" etc.
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("\\D", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
