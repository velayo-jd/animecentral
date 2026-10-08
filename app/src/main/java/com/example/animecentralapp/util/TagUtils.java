package com.example.animecentralapp.util;

import com.example.animecentralapp.model.Anime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/** Tag logic shared by the Videos tab and the tag screen. Tags = types + genres. */
public final class TagUtils {
    private TagUtils() {}


    public static String typeOf(Anime anime) {
        String t = anime.getType();
        return (t == null || t.trim().isEmpty()) ? "Series" : t.trim();
    }

    /** All tags found in the data: types first ("Anime", "Movie"...), then genres A-Z. */
    public static List<String> collectTags(List<Anime> all) {
        TreeMap<String, String> types = new TreeMap<>();
        TreeMap<String, String> genres = new TreeMap<>();
        for (Anime a : all) {
            String type = typeOf(a);
            types.putIfAbsent(type.toLowerCase(Locale.ROOT), type);
            if (a.getGenres() != null) {
                for (String g : a.getGenres()) {
                    if (g != null && !g.trim().isEmpty()) {
                        genres.putIfAbsent(g.trim().toLowerCase(Locale.ROOT), g.trim());
                    }
                }
            }
        }
        List<String> tags = new ArrayList<>(types.values());
        for (java.util.Map.Entry<String, String> e : genres.entrySet()) {
            if (!types.containsKey(e.getKey())) tags.add(e.getValue());
        }
        return tags;
    }

    /** Every distinct genre in the data, A-Z. */
    public static List<String> collectGenres(List<Anime> all) {
        TreeMap<String, String> genres = new TreeMap<>();
        for (Anime a : all) {
            if (a.getGenres() == null) continue;
            for (String g : a.getGenres()) {
                if (g != null && !g.trim().isEmpty()) {
                    genres.putIfAbsent(g.trim().toLowerCase(Locale.ROOT), g.trim());
                }
            }
        }
        return new ArrayList<>(genres.values());
    }

    /** Every distinct type in the data ("Anime", "Movie"...), A-Z. */
    public static List<String> collectTypes(List<Anime> all) {
        TreeMap<String, String> types = new TreeMap<>();
        for (Anime a : all) {
            String type = typeOf(a);
            types.putIfAbsent(type.toLowerCase(Locale.ROOT), type);
        }
        return new ArrayList<>(types.values());
    }

    public static boolean matches(Anime anime, String tag) {
        if (tag == null) return false;
        if (typeOf(anime).equalsIgnoreCase(tag.trim())) return true;
        if (anime.getGenres() != null) {
            for (String g : anime.getGenres()) {
                if (g != null && g.trim().equalsIgnoreCase(tag.trim())) return true;
            }
        }
        return false;
    }

    public static List<Anime> filterByTag(List<Anime> all, String tag) {
        List<Anime> out = new ArrayList<>();
        for (Anime a : all) {
            if (matches(a, tag)) out.add(a);
        }
        return out;
    }

    /**
     * Anime with "recommended: true" come first; the rest are filled in randomly.
     * The random order only changes once per day, so the tab doesn't reshuffle on every visit.
     */
    public static List<Anime> recommended(List<Anime> all, int count) {
        List<Anime> flagged = new ArrayList<>();
        List<Anime> others = new ArrayList<>();
        for (Anime a : all) {
            (a.isRecommended() ? flagged : others).add(a);
        }
        Collections.shuffle(others, new Random(System.currentTimeMillis() / 86_400_000L));
        List<Anime> out = new ArrayList<>(flagged);
        out.addAll(others);
        return new ArrayList<>(out.subList(0, Math.min(count, out.size())));
    }

    /**
     * Random recommendations for the Watch screen: anime that share at least one genre with
     * the current one come first (shuffled), then anime of the same type, then anything else.
     * The current anime is never included.
     */
    public static List<Anime> relatedTo(Anime current, List<Anime> all, int count) {
        Set<String> genres = new HashSet<>();
        if (current.getGenres() != null) {
            for (String g : current.getGenres()) {
                if (g != null && !g.trim().isEmpty()) genres.add(g.trim().toLowerCase(Locale.ROOT));
            }
        }

        List<Anime> sharing = new ArrayList<>();
        List<Anime> sameType = new ArrayList<>();
        List<Anime> rest = new ArrayList<>();
        for (Anime a : all) {
            if (a.getId() != null && a.getId().equals(current.getId())) continue;

            boolean shares = false;
            if (a.getGenres() != null) {
                for (String g : a.getGenres()) {
                    if (g != null && genres.contains(g.trim().toLowerCase(Locale.ROOT))) {
                        shares = true;
                        break;
                    }
                }
            }
            if (shares) sharing.add(a);
            else if (typeOf(a).equalsIgnoreCase(typeOf(current))) sameType.add(a);
            else rest.add(a);
        }

        Random random = new Random();
        Collections.shuffle(sharing, random);
        Collections.shuffle(sameType, random);
        Collections.shuffle(rest, random);

        List<Anime> out = new ArrayList<>(sharing);
        out.addAll(sameType);
        out.addAll(rest);
        return new ArrayList<>(out.subList(0, Math.min(count, out.size())));
    }
}
