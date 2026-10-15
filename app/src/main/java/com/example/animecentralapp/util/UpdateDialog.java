package com.example.animecentralapp.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** "Update available" dialog, the download with a progress bar, and the install hand-off. */
public final class UpdateDialog {
    private UpdateDialog() {}

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    public static void show(Activity a, UpdateChecker.Info info) {
        StringBuilder msg = new StringBuilder("Version ").append(info.version);
        if (info.size > 0) {
            msg.append("  ·  ").append(String.format(Locale.US, "%.1f MB", info.size / 1048576.0));
        }
        msg.append("\n\n").append(cleanNotes(info.notes));

        new AlertDialog.Builder(a)
                .setTitle("Update available")
                .setMessage(msg.toString())
                .setPositiveButton("Update", (d, w) -> startDownload(a, info))
                .setNegativeButton("Later", null)
                .show();
    }

    // ---------- download ----------

    private static void startDownload(Activity a, UpdateChecker.Info info) {
        // Android asks once for permission to install apps from this app
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("Allow installing updates")
                    .setMessage("Android needs your OK, once, to let Anime Central install its own "
                            + "updates. Tap Open settings, switch on \"Allow from this source\", "
                            + "then come back and tap Update again.")
                    .setPositiveButton("Open settings", (d, w) -> a.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + a.getPackageName()))))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (24 * dp), (int) (16 * dp), (int) (24 * dp), (int) (8 * dp));
        TextView label = new TextView(a);
        label.setText("Downloading…");
        label.setGravity(Gravity.START);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(true);
        box.addView(label);
        box.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog progress = new AlertDialog.Builder(a)
                .setTitle("Updating Anime Central")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> cancelled.set(true))
                .show();

        IO.execute(() -> {
            File dir = new File(a.getCacheDir(), "updates");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
            File out = new File(dir, "AnimeCentral-" + info.version + ".apk");

            String error = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(info.apkUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "AnimeCentral-Android");
                long total = conn.getContentLengthLong() > 0 ? conn.getContentLengthLong() : info.size;

                long read = 0;
                int lastPct = -2;
                try (InputStream in = conn.getInputStream();
                     FileOutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[32 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        if (cancelled.get()) break;
                        os.write(buf, 0, n);
                        read += n;
                        int pct = total > 0 ? (int) (read * 100 / total) : -1;
                        if (pct != lastPct) {
                            lastPct = pct;
                            final int p = pct;
                            a.runOnUiThread(() -> {
                                if (p >= 0) {
                                    bar.setIndeterminate(false);
                                    bar.setProgress(p);
                                    label.setText("Downloading… " + p + "%");
                                }
                            });
                        }
                    }
                }
                if (cancelled.get()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    a.runOnUiThread(progress::dismiss);
                    return;
                }
                if (total > 0 && read != total) error = "The download was cut off. Try again.";
            } catch (Exception e) {
                error = "Download failed. Check your connection and try again.";
            } finally {
                if (conn != null) conn.disconnect();
            }

            final String err = error;
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                progress.dismiss();
                if (err != null) {
                    Toast.makeText(a, err, Toast.LENGTH_LONG).show();
                } else {
                    install(a, out);
                }
            });
        });
    }

    // ---------- install ----------

    private static void install(Activity a, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "Couldn't open the installer.", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- release notes ----------

    /** GitHub notes are Markdown; make them readable as plain text. */
    private static String cleanNotes(String notes) {
        if (notes == null || notes.trim().isEmpty()) return "No release notes.";
        String s = notes.replace("\r", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trim();
        return s.length() > 800 ? s.substring(0, 800) + "…" : s;
    }
}
