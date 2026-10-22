package com.example.animecentralapp.util;

public final class TimeAgo {
    private TimeAgo() {}

    public static String ago(long ts) {
        if (ts <= 0) return "";
        long minutes = (System.currentTimeMillis() - ts) / 60000;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 30) return days + "d ago";
        return (days / 30) + "mo ago";
    }
}