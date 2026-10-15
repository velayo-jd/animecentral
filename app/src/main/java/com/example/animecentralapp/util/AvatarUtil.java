package com.example.animecentralapp.util;

import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseUser;

/** Shows the Google profile picture over the letter circle, if the account has one. */
public final class AvatarUtil {
    private AvatarUtil() {}

    public static void bind(ImageView photo, FirebaseUser user) {
        Uri uri = user != null ? user.getPhotoUrl() : null;
        if (uri == null) {
            photo.setVisibility(View.GONE);
            return;
        }
        // Google serves a tiny 96px picture by default; ask for a sharper one
        String url = uri.toString().replace("=s96-c", "=s400-c");
        photo.setVisibility(View.VISIBLE);
        Glide.with(photo).load(url).circleCrop().into(photo);
    }
}