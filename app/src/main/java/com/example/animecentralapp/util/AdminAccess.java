package com.example.animecentralapp.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Who may add / import anime. This only hides the screen; the real protection is your
 * Firestore security rules (see the notes that came with the AniList import).
 */
public final class AdminAccess {
    private AdminAccess() {}

    // >>> PUT THE EMAIL ADDRESSES OF THE ACCOUNTS THAT MAY IMPORT HERE (lower case) <<<
    private static final Set<String> ADMIN_EMAILS = new HashSet<>(Arrays.asList(
            "tetraseiya@gmail.com"
    ));

    public static boolean isAdmin() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return false;
        return ADMIN_EMAILS.contains(user.getEmail().toLowerCase(Locale.ROOT));
    }
}
