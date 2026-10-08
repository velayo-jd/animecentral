package com.example.animecentralapp.model;

public class Comment {
    private String commentId;
    private String animeId;
    private String userId;
    private String username;
    private String userPhotoUrl;
    private String text;
    private long timestamp;
    private int likes;

    public Comment() {}

    public Comment(String animeId, String userId, String username, String text) {
        this.animeId = animeId;
        this.userId = userId;
        this.username = username;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
        this.likes = 0;
    }

    // Getters
    public String getCommentId() { return commentId; }
    public String getAnimeId() { return animeId; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getUserPhotoUrl() { return userPhotoUrl; }
    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }
    public int getLikes() { return likes; }

    // Setters
    public void setCommentId(String commentId) { this.commentId = commentId; }
    public void setAnimeId(String animeId) { this.animeId = animeId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setUserPhotoUrl(String userPhotoUrl) { this.userPhotoUrl = userPhotoUrl; }
    public void setText(String text) { this.text = text; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setLikes(int likes) { this.likes = likes; }
}
