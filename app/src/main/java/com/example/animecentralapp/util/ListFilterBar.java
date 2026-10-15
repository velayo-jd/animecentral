package com.example.animecentralapp.util;

import android.content.Context;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.LayoutListFilterBinding;
import com.example.animecentralapp.model.Anime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Drives layout_list_filter.xml: a search box plus format toggles (tap a format to hide it).
 * Each screen passes its own key, so every screen remembers its own toggles.
 *
 * Use:  bar = new ListFilterBar(ctx, binding.listFilter, "favorites", true, this::refresh);
 *       bar.setSource(items);                 // builds the format chips
 *       List<Anime> shown = bar.filter(items); // applies search + toggles
 */
public final class ListFilterBar {
    private static final String PREFS = "list_filter_prefs";

    private final Context ctx;
    private final LayoutListFilterBinding b;
    private final String key;
    private final boolean withFormats;
    private final Runnable onChange;
    private Set<String> hidden = new HashSet<>(); // formats switched off (lower case)

    public ListFilterBar(Context ctx, LayoutListFilterBinding b, String key,
                         boolean withFormats, Runnable onChange) {
        this.ctx = ctx;
        this.b = b;
        this.key = key;
        this.withFormats = withFormats;
        this.onChange = onChange;

        Set<String> saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(key, null);
        if (saved != null) hidden = new HashSet<>(saved);

        b.filterFormatsScroll.setVisibility(View.GONE);
        b.filterInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {
                ListFilterBar.this.onChange.run();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    // ---------- what the screens use ----------

    public void setHint(String hint) {
        b.filterInput.setHint(hint);
    }

    /** What the user typed, lower case. */
    public String query() {
        return b.filterInput.getText().toString().trim().toLowerCase(Locale.ROOT);
    }

    /** True when the text matches (an empty search matches everything). */
    public boolean matches(String text) {
        String q = query();
        return q.isEmpty() || (text != null && text.toLowerCase(Locale.ROOT).contains(q));
    }

    /** Applies the search text and the format toggles. */
    public List<Anime> filter(List<Anime> items) {
        List<Anime> out = new ArrayList<>();
        for (Anime a : items) {
            if (withFormats && hidden.contains(keyOf(a))) continue;
            if (matches(a.getTitle())) out.add(a);
        }
        return out;
    }

    /** Whole bar shown or hidden (e.g. nothing to search in an empty list). */
    public void setVisible(boolean visible) {
        ((View) b.filterInput.getParent()).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Rebuilds the format chips from the items this screen is showing. */
    public void setSource(List<Anime> items) {
        if (!withFormats) return;

        final Map<String, Integer> counts = new HashMap<>();
        final Map<String, String> names = new HashMap<>();
        for (Anime a : items) {
            String k = keyOf(a);
            if (k.isEmpty()) continue;
            counts.merge(k, 1, Integer::sum);
            if (!names.containsKey(k)) names.put(k, TagUtils.formatOf(a).trim());
        }
        List<String> keys = new ArrayList<>(counts.keySet());
        Collections.sort(keys, (x, y) -> {
            int byCount = counts.get(y) - counts.get(x);
            return byCount != 0 ? byCount : x.compareTo(y);
        });

        int scrollX = b.filterFormatsScroll.getScrollX();
        LinearLayout row = b.filterFormatRow;
        row.removeAllViews();
        // With only one format there is nothing to toggle (and keep "Show all" reachable)
        boolean show = keys.size() >= 2 || !hidden.isEmpty();
        b.filterFormatsScroll.setVisibility(show ? View.VISIBLE : View.GONE);

        for (String k : keys) {
            boolean on = !hidden.contains(k);
            TextView chip = makeChip(names.get(k) + "  " + counts.get(k), on);
            chip.setOnClickListener(v -> {
                if (!hidden.remove(k)) hidden.add(k);
                save();
                setSource(items);
                onChange.run();
            });
            row.addView(chip);
        }
        if (!hidden.isEmpty()) {
            TextView reset = makeChip("Show all", false);
            reset.setPaintFlags(reset.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            reset.setTextColor(ContextCompat.getColor(ctx, R.color.ac_text));
            reset.setOnClickListener(v -> {
                hidden.clear();
                save();
                setSource(items);
                onChange.run();
            });
            row.addView(reset);
        }
        b.filterFormatsScroll.post(() -> b.filterFormatsScroll.scrollTo(scrollX, 0));
    }

    // ---------- internals ----------

    private void save() {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(key, new HashSet<>(hidden)).apply();
    }

    private static String keyOf(Anime a) {
        String f = TagUtils.formatOf(a);
        return f == null ? "" : f.trim().toLowerCase(Locale.ROOT);
    }

    private TextView makeChip(String text, boolean on) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (34 * dp));
        lp.setMarginEnd((int) (10 * dp));
        t.setLayoutParams(lp);
        t.setBackgroundResource(on ? R.drawable.ac_fmt_on : R.drawable.ac_fmt_off);
        t.setTextColor(ContextCompat.getColor(ctx, on ? R.color.ac_on_accent : R.color.ac_text_faint));
        if (!on) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        return t;
    }
}
