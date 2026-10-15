package com.example.animecentralapp.model;

public class Episode {
    private String id;
    private String animeId;
    private int number;
    private String title;
    private String vimeoId;     // e.g. "76979871"
    private String vimeoHash;   // optional, only for unlisted videos
    private String thumbnailUrl;

    private String rumbleId;    // e.g. "v70bqqu" (from the embed link, not the page URL)
    private String rumblePub;   // optional, the "pub=" value from Rumble's embed code

    public String getRumbleId() { return rumbleId; }
    public String getRumblePub() { return rumblePub; }
    public void setRumbleId(String rumbleId) { this.rumbleId = rumbleId; }
    public void setRumblePub(String rumblePub) { this.rumblePub = rumblePub; }

    public Episode() {}

    public String getId() { return id; }
    public String getAnimeId() { return animeId; }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public String getVimeoId() { return vimeoId; }
    public String getVimeoHash() { return vimeoHash; }
    public String getThumbnailUrl() { return thumbnailUrl; }

    public void setId(String id) { this.id = id; }
    public void setAnimeId(String animeId) { this.animeId = animeId; }
    public void setNumber(int number) { this.number = number; }
    public void setTitle(String title) { this.title = title; }
    public void setVimeoId(String vimeoId) { this.vimeoId = vimeoId; }
    public void setVimeoHash(String vimeoHash) { this.vimeoHash = vimeoHash; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
}