package com.example.animecentralapp.util;

import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

/** Puts the signed-in Google account's profile picture into a round ImageView. */
public final class AvatarLoader {
    private AvatarLoader() {}

    /** The account's photo, or null for guests / accounts without one. */
    @Nullable
    public static Uri photoOf(@Nullable FirebaseUser user) {
        if (user == null) return null;
        if (user.getPhotoUrl() != null) return user.getPhotoUrl();
        for (UserInfo info : user.getProviderData()) {          // Google's copy, if Firebase's is empty
            if (info.getPhotoUrl() != null) return info.getPhotoUrl();
        }
        return null;
    }

    /**
     * Loads the photo into the view, round. Returns true if a photo is being shown,
     * false if there is none (the view is cleared so the caller can show its fallback).
     */
    public static boolean load(ImageView view, @Nullable FirebaseUser user) {
        Uri photo = photoOf(user);
        if (photo == null) {
            Glide.with(view).clear(view);
            view.setImageDrawable(null);
            return false;
        }
        view.setVisibility(View.VISIBLE);
        Glide.with(view).load(photo).circleCrop().into(view);
        return true;
    }
}