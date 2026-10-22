package com.example.animecentralapp.model;

import com.google.firebase.firestore.Exclude;

/** One reply: anime/{animeId}/discussions/{threadId}/replies/{id} */
public class DiscussionReply {
    private String id;
    private String text;
    private boolean spoiler;
    private String userId;
    private String username;
    private String userPhotoUrl;
    private long timestamp;

    public DiscussionReply() {}

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
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
}