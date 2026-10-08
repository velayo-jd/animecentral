package com.example.animecentralapp.model;

import java.util.List;

/**
 * Series-level info. Shown on the Home grid, the Videos tab and the Details screen.
 * Firestore: collection "anime" -> one document per series.
 * Episodes live in the subcollection "episodes" inside each document.
 */
public class Anime {
    private String id;
    private String title;
    private String tagline;
    private String synopsis;
    private String thumbnailUrl;   // wide banner (optional)
    private String posterUrl;      // portrait poster (optional)
    private String status;         // e.g. "Airing", "Completed"
    private String type;           // "Anime" or "Movie" (missing = "Anime")
    private boolean recommended;   // true = pinned to the top of the Videos tab
    private int episodes;          // total number of episodes
    private int year;
    private List<String> genres;

    public Anime() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }

    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean recommended) { this.recommended = recommended; }

    public int getEpisodes() { return episodes; }
    public void setEpisodes(int episodes) { this.episodes = episodes; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
}
