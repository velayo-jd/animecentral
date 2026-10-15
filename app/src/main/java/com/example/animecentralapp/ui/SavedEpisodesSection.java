package com.example.animecentralapp.ui;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.animecentralapp.R;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.util.EpisodeStore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Saved episodes" list for the History tab: each anime title with its bookmarked episodes
 * listed underneath, no thumbnails.
 *
 *   Romelia War Chronicle
 *     • Episode 1
 *   Reincarnated as a Sword
 *     • Episode 1
 *     • Episode 3
 */
public final class SavedEpisodesSection {
    private SavedEpisodesSection() { }

    /** episodeId == null means the anime title was tapped (open its details page). */
    public interface OnOpen { void open(Anime anime, String episodeId); }

    private static class Entry {
        final Anime anime;
        final String episodeId;
        int number;
        Entry(Anime anime, String episodeId) { this.anime = anime; this.episodeId = episodeId; }
    }

    /** Inflates the section. Add the returned view to your list, then call load(). */
    public static View create(Context c, ViewGroup parent) {
        return LayoutInflater.from(c).inflate(R.layout.layout_saved_list, parent, false);
    }

    /** Reads the bookmarks and fills the section. Safe to call again (e.g. in onResume). */
    public static void load(Context c, View section, List<Anime> all, OnOpen onOpen) {
        LinearLayout container = section.findViewById(R.id.saved_container);
        TextView empty = section.findViewById(R.id.saved_empty);

        // A newer call makes any older, still-running one throw its result away
        int token = (section.getTag() instanceof Integer ? (Integer) section.getTag() : 0) + 1;
        section.setTag(token);

        List<Entry> entries = new ArrayList<>();
        for (Anime a : all) {
            if (a.getId() == null) continue;
            for (String eid : EpisodeStore.getBookmarks(c, a.getId())) {
                entries.add(new Entry(a, eid));
            }
        }
        if (entries.size() > 40) entries = entries.subList(0, 40);

        if (entries.isEmpty()) {
            container.removeAllViews();
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);

        final List<Entry> found = new ArrayList<>();
        final int[] left = {entries.size()};
        for (Entry e : entries) {
            FirebaseFirestore.getInstance()
                    .collection("anime").document(e.anime.getId())
                    .collection("episodes").document(e.episodeId)
                    .get().addOnCompleteListener(task -> {
                        if (!section.isAttachedToWindow() && section.getParent() == null) return;
                        if (!(section.getTag() instanceof Integer)
                                || (Integer) section.getTag() != token) return;
                        if (task.isSuccessful() && task.getResult() != null
                                && task.getResult().exists()) {
                            DocumentSnapshot d = task.getResult();
                            Object n = d.get("number"); // can be stored as a number or as text
                            if (n instanceof Number) {
                                e.number = ((Number) n).intValue();
                            } else if (n instanceof String) {
                                try { e.number = Integer.parseInt(((String) n).trim()); }
                                catch (NumberFormatException ignored) { }
                            }
                            found.add(e);
                        }
                        if (--left[0] == 0) render(c, container, empty, found, onOpen);
                    });
        }
    }

    private static void render(Context c, LinearLayout container, TextView empty,
                               List<Entry> found, OnOpen onOpen) {
        container.removeAllViews();
        if (found.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        Collections.sort(found, (x, y) -> {
            int t = String.valueOf(x.anime.getTitle())
                    .compareToIgnoreCase(String.valueOf(y.anime.getTitle()));
            return t != 0 ? t : Integer.compare(x.number, y.number);
        });

        // Group by anime, keeping the sorted order
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry e : found) {
            List<Entry> g = groups.get(e.anime.getId());
            if (g == null) { g = new ArrayList<>(); groups.put(e.anime.getId(), g); }
            g.add(e);
        }

        float dp = c.getResources().getDisplayMetrics().density;
        int text = ContextCompat.getColor(c, R.color.ac_text);
        int soft = ContextCompat.getColor(c, R.color.ac_text_soft);
        int faint = ContextCompat.getColor(c, R.color.ac_text_faint);

        TypedValue ripple = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);

        for (List<Entry> group : groups.values()) {
            Anime anime = group.get(0).anime;

            TextView title = new TextView(c);
            title.setText(anime.getTitle());
            title.setTextColor(text);
            title.setTextSize(16);
            title.setPadding(0, (int) (10 * dp), 0, (int) (4 * dp));
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setBackgroundResource(ripple.resourceId);
            title.setOnClickListener(v -> onOpen.open(anime, null));
            container.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (Entry e : group) {
                SpannableStringBuilder sb = new SpannableStringBuilder("•   Episode " + e.number);
                sb.setSpan(new ForegroundColorSpan(faint), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                TextView row = new TextView(c);
                row.setText(sb);
                row.setTextColor(soft);
                row.setTextSize(15);
                row.setPadding((int) (12 * dp), (int) (7 * dp), 0, (int) (7 * dp));
                row.setBackgroundResource(ripple.resourceId);
                row.setOnClickListener(v -> onOpen.open(e.anime, e.episodeId));
                container.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
    }
}
