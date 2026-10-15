package com.example.animecentralapp.model;

public class User {
    private String uid;
    private String email;
    private String username;
    private String displayName;
    private String profilePhotoUrl;
    private String bio;
    private long createdAt;

    public User() {}

    public User(String uid, String email, String username, String displayName) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters
    public String getUid() { return uid; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public String getBio() { return bio; }
    public long getCreatedAt() { return createdAt; }

    // Setters
    public void setUid(String uid) { this.uid = uid; }
    public void setEmail(String email) { this.email = email; }
    public void setUsername(String username) { this.username = username; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }
    public void setBio(String bio) { this.bio = bio; }
}
