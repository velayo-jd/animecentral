package com.example.animecentralapp.util;

import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.annotation.Nullable;

/** The three states of a bug report, and how they look. */
public final class BugStatus {
    private BugStatus() {}

    public static final String OPEN = "open";        // not solved yet
    public static final String WORKING = "working";  // working on it
    public static final String FIXED = "fixed";

    public static String label(@Nullable String s) {
        if (WORKING.equals(s)) return "Working on it";
        if (FIXED.equals(s)) return "Fixed";
        return "Not solved yet";
    }

    public static int color(@Nullable String s) {
        if (WORKING.equals(s)) return 0xFFF5A524;
        if (FIXED.equals(s)) return 0xFF3FB950;
        return 0xFFE5484D;
    }

    /** Fills a small TextView with the label and an outline in the status colour. */
    public static void style(TextView tv, @Nullable String status) {
        float dp = tv.getResources().getDisplayMetrics().density;
        int c = color(status);
        tv.setText(label(status));
        tv.setTextColor(c);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(6 * dp);
        g.setStroke((int) dp, c);
        tv.setBackground(g);
        tv.setPadding((int) (6 * dp), (int) dp, (int) (6 * dp), (int) dp);
    }
}