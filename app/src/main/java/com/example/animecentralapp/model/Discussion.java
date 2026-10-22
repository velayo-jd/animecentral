package com.example.animecentralapp.model;

import com.google.firebase.firestore.Exclude;

/** One forum thread: anime/{animeId}/discussions/{id} */
public class Discussion {
    private String id;
    private String animeId;
    private String title;
    private String body;
    private boolean spoiler;
    private String userId;
    private String username;
    private String userPhotoUrl;
    private long timestamp;
    private int replyCount;

    public Discussion() {}

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getAnimeId() { return animeId; }
    public void setAnimeId(String animeId) { this.animeId = animeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isSpoiler() { return spoiler; }
    public void setSpoiler(boolean spoiler) { this.spoiler = spoiler; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getUserPhotoUrl() { return userPhotoUrl; }
    public void setUserPhotoUrl(String userPhotoUrl) { this.userPhotoUrl = userPhotoUrl; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public int getReplyCount() { return replyCount; }
    public void setReplyCount(int replyCount) { this.replyCount = replyCount; }
    private String status;   // bug reports only: "open", "working" or "fixed"

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}